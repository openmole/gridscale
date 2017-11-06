package gridscale

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.{ KeyPair, KeyPairGenerator, Security }
import java.util.{ Calendar, GregorianCalendar, TimeZone }
import javax.net.ssl.{ SSLContext, SSLSocket }

import freedsl.dsl._
import gridscale.authentication.AuthenticationException

import scala.language.{ higherKinds, postfixOps }

package object egi {

  import java.security.{ KeyStore, PrivateKey }
  import java.security.cert.X509Certificate
  import gridscale.http._
  import squants._
  import time.TimeConversions._
  import information.InformationConversions._

  import freedsl.filesystem._
  import freedsl.io._
  import cats._
  import cats.implicits._
  import freestyle.tagless._
  import scala.util._
  import gridscale.authentication._

  case class BDIIServer(host: String, port: Int, timeout: Time = 1 minutes)
  case class CREAMCELocation(hostingCluster: String, port: Int, uniqueId: String, contact: String, memory: Int, maxWallTime: Int, maxCPUTime: Int, status: String)

  object BDIIIntepreter {

    def trimSlashes(path: String) =
      path.reverse.dropWhile(_ == '/').reverse.dropWhile(_ == '/')

    def location(host: String, port: Int, basePath: String) =
      "https://" + trimSlashes(host) + ":" + port + "/" + trimSlashes(basePath) + "/"

  }

  case class BDIIIntepreter() extends BDII.Handler[Evaluated] {
    def webDAVs(server: BDIIServer, vo: String) = guard {
      val creamCEServiceType = "org.glite.ce.CREAM"

      BDIIQuery.withBDIIQuery(server.host, server.port, server.timeout) { q ⇒
        def searchPhrase = "(GLUE2EndpointInterfaceName=webdav)"

        val services =
          for {
            webdavService ← q.query(searchPhrase, bindDN = "o=glue").toSeq
            id = webdavService.getAttributes.get("GLUE2EndpointID").get.toString
            url = webdavService.getAttributes.get("GLUE2EndpointURL").get.toString
          } yield (id, url)

        for {
          (id, url) ← services.toVector
          urlObject = new java.net.URI(url)
          host = urlObject.getHost
          pathQuery ← q.query(s"(&(GlueChunkKey=GlueSEUniqueID=$host)(GlueVOInfoAccessControlBaseRule=VO:$vo))")
          path = pathQuery.getAttributes.get("GlueVOInfoPath").get.toString
        } yield BDIIIntepreter.location(urlObject.getHost, urlObject.getPort, path)
      }
    }

    def creamCEs(server: BDIIServer, vo: String) = guard {
      BDIIQuery.withBDIIQuery(server.host, server.port, server.timeout) { q ⇒
        val res = q.query(s"(&(GlueCEAccessControlBaseRule=VO:$vo)(GlueCEImplementationName=CREAM))")

        case class Machine(memory: Int)
        def machineInfo(host: String) = {
          val info = q.query(s"(GlueChunkKey=GlueClusterUniqueID=$host)")(0)
          Machine(memory = info.getAttributes.get("GlueHostMainMemoryRAMSize").get().toString.toInt)
        }

        for {
          info ← res.toVector
          maxWallTime = info.getAttributes.get("GlueCEPolicyMaxWallClockTime").get.toString.toInt
          maxCpuTime = info.getAttributes.get("GlueCEPolicyMaxCPUTime").get.toString.toInt
          port = info.getAttributes.get("GlueCEInfoGatekeeperPort").get.toString.toInt
          uniqueId = info.getAttributes.get("GlueCEUniqueID").get.toString
          contact = info.getAttributes.get("GlueCEInfoContactString").get.toString
          status = info.getAttributes.get("GlueCEStateStatus").get.toString
          hostingCluster = info.getAttributes.get("GlueCEHostingCluster").get.toString
          memory = machineInfo(hostingCluster).memory
        } yield {
          CREAMCELocation(
            hostingCluster = hostingCluster,
            port = port,
            uniqueId = uniqueId,
            contact = contact,
            memory = memory,
            maxCPUTime = maxCpuTime,
            maxWallTime = maxWallTime,
            status = status)
        }
      }
    }

  }

  @tagless trait BDII {
    def webDAVs(server: BDIIServer, vo: String): FS[Vector[String]]
    def creamCEs(server: BDIIServer, vo: String): FS[Vector[CREAMCELocation]]
  }

  def webDAVs[M[_]: BDII](server: BDIIServer, vo: String) = BDII[M].webDAVs(server, vo)
  def creamCEs[M[_]: BDII](server: BDIIServer, vo: String) = BDII[M].creamCEs(server, vo)

  case class P12VOMSAuthentication(
    p12: P12Authentication,
    lifeTime: Time,
    serverURLs: Vector[String],
    voName: String,
    renewTime: Time = 1 hours,
    fqan: Option[String] = None,
    proxySize: Int = 1024)

  object VOMS {

    import freedsl.errorhandler._
    import cats._
    import cats.instances.all._
    import cats.syntax.all._

    case class VOMSCredential(
      certificate: HTTPS.KeyStoreOperations.Credential,
      p12: P12Authentication,
      serverCertificates: Vector[HTTPS.KeyStoreOperations.Certificate],
      ending: java.util.Date,
      factory: HTTPS.SSLSocketFactory)

    object ProxyError {
      def apply(reason: Reason, message: Option[String]) = new ProxyError(reason, message)
    }

    class ProxyError(val reason: Reason, val message: Option[String]) extends AuthenticationException(s"${reason}: ${message.getOrElse("No message")}")

    sealed trait Reason

    object Reason {
      case object NoSuchUser extends Reason
      case object BadRequest extends Reason
      case object SuspendedUser extends Reason
      case object InternalError extends Reason
      case object Unknown extends Reason
    }

    sealed trait ProxySize

    object ProxySize {
      case object PS1024 extends ProxySize
      case object PS2048 extends ProxySize
    }

    def proxy[M[_]: Monad: ErrorHandler: HTTP: FileSystem](
      voms: String,
      p12: P12Authentication,
      certificateDirectory: java.io.File,
      lifetime: Time = 24 hours,
      fquan: Option[String] = None,
      proxySize: ProxySize = ProxySize.PS2048) = {

      case class VOMSProxy(ac: String, p12: P12Authentication, serverCertificates: Vector[HTTPS.KeyStoreOperations.Certificate])

      def parseAC(s: String, p12: P12Authentication, serverCertificates: Vector[HTTPS.KeyStoreOperations.Certificate]) = {
        val xml = scala.xml.XML.loadString(s)
        def content = (xml \\ "voms" \\ "ac").headOption.map(_.text)
        def error = (xml \\ "voms" \\ "error")

        content match {
          case Some(c) ⇒ util.Success(VOMSProxy(c, p12, serverCertificates))
          case None ⇒
            def message = (error \\ "message").headOption.map(_.text)

            (error \\ "code").headOption.map(_.text) match {
              case Some("NoSuchUser")    ⇒ util.Failure(ProxyError(Reason.NoSuchUser, message))
              case Some("BadRequest")    ⇒ util.Failure(ProxyError(Reason.BadRequest, message))
              case Some("SuspendedUser") ⇒ util.Failure(ProxyError(Reason.SuspendedUser, message))
              case Some(r)               ⇒ util.Failure(ProxyError(Reason.InternalError, message))
              case _                     ⇒ util.Failure(ProxyError(Reason.Unknown, message))
            }
        }
      }

      def credential(proxy: VOMSProxy) = {
        import org.bouncycastle.asn1.x500.{ X500Name }
        import org.bouncycastle.asn1.x500.style.RFC4519Style
        import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
        import org.bouncycastle.jce.provider.BouncyCastleProvider

        import org.apache.commons.codec.binary.Base64
        import org.bouncycastle.asn1._
        import org.bouncycastle.asn1.x509._
        import java.security.Security

        val acBytes = new Base64().decode(proxy.ac.trim().replaceAll("\n", ""))
        val asn1InputStream = new ASN1InputStream(acBytes)
        val asn1Object = asn1InputStream.readObject()
        val attributeCertificate = AttributeCertificate.getInstance(asn1Object)
        asn1InputStream.close()

        val cred = P12Authentication.loadPKCS12Credentials(proxy.p12)

        import org.bouncycastle.cert.X509v3CertificateBuilder
        Security.addProvider(new BouncyCastleProvider)

        val keys = KeyPairGenerator.getInstance("RSA", "BC")

        proxySize match {
          case ProxySize.PS1024 ⇒ keys.initialize(1024)
          case ProxySize.PS2048 ⇒ keys.initialize(2048)
        }

        val pair = keys.genKeyPair

        val number = Math.abs(scala.util.Random.nextLong)
        val serial: BigInteger = new BigInteger(String.valueOf(Math.abs(number)))
        val issuer: X500Name = new X500Name(RFC4519Style.INSTANCE, cred.certificate.getSubjectDN.getName)

        val now = new java.util.Date()
        val notBefore: Time = {
          val notBeforeDate = new GregorianCalendar(TimeZone.getTimeZone("GMT"))
          notBeforeDate.setGregorianChange(now)
          notBeforeDate.add(Calendar.MINUTE, -5)
          new Time(notBeforeDate.getTime)
        }

        val (notAfter, notAfterDate) = {
          val notAfterDate = new GregorianCalendar(TimeZone.getTimeZone("GMT"))
          notAfterDate.setGregorianChange(now)
          notAfterDate.add(Calendar.SECOND, lifetime.toSeconds.toInt)
          (new Time(notAfterDate.getTime), notAfterDate.getTime)
        }

        import org.bouncycastle.asn1.x500.X500NameBuilder

        val subject = new X500Name(RFC4519Style.INSTANCE, cred.certificate.getSubjectDN.getName)
        val builder = new X500NameBuilder(RFC4519Style.INSTANCE)
        subject.getRDNs.foreach(rdn ⇒ builder.addMultiValuedRDN(rdn.getTypesAndValues))
        builder.addRDN(RFC4519Style.cn, number.toString)

        import org.bouncycastle.asn1.ASN1Sequence
        import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(pair.getPublic.getEncoded))

        val certGen = new X509v3CertificateBuilder(
          issuer,
          serial,
          notBefore,
          notAfter,
          builder.build(),
          subjectPublicKeyInfo)

        val acVector = new ASN1EncodableVector
        acVector.add(attributeCertificate)
        val seqac = new DERSequence(acVector)
        val seqacwrap = new DERSequence(seqac)

        certGen.addExtension(new ASN1ObjectIdentifier("1.3.6.1.4.1.8005.100.100.5").intern(), false, seqacwrap)

        val PROXY_CERT_INFO_V4_OID: String = "1.3.6.1.5.5.7.1.14"
        val IMPERSONATION = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.21.1")
        val INDEPENDENT = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.21.2")
        val LIMITED = new ASN1ObjectIdentifier("1.3.6.1.4.1.3536.1.1.1.9")

        val proxyTypeVector = new ASN1EncodableVector
        proxyTypeVector.add(IMPERSONATION)
        certGen.addExtension(new ASN1ObjectIdentifier(PROXY_CERT_INFO_V4_OID).intern(), true, new DERSequence(new DERSequence(proxyTypeVector)))

        import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
        val sigGen = new JcaContentSignerBuilder("SHA512WithRSAEncryption").setProvider("BC").build(cred.key)

        val certificateHolder = certGen.build(sigGen)
        val generatedCertificate = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateHolder)

        (HTTPS.KeyStoreOperations.Credential(pair.getPrivate, Vector(generatedCertificate) ++ cred.chain.toVector, proxy.p12.password), notAfterDate)
      }

      def socketFactory[M[_]: HTTP: ErrorHandler](certificate: HTTPS.KeyStoreOperations.Credential, serverCertificates: Vector[HTTPS.KeyStoreOperations.Certificate], password: String) =
        ErrorHandler[M].get(HTTPS.socketFactory(Vector(certificate) ++ serverCertificates, password))

      val options =
        List(
          Some("lifetime=" + lifetime.toSeconds.toLong),
          fquan.map("fquans=" + _)).flatten.mkString("&")

      val location = s"/generate-ac${if (!options.isEmpty) "?" + options else ""}"

      for {
        userCertificate ← HTTPS.readP12[M](p12.certificate, p12.password).flatMap(r ⇒ ErrorHandler[M].get(r))
        certificates ← HTTPS.readPEMCertificates[M](certificateDirectory)
        factory ← ErrorHandler[M].get(HTTPS.socketFactory(certificates ++ Vector(userCertificate), p12.password))
        server = HTTPSServer(s"https://$voms", factory)
        content ← HTTP[M].content(server, location)
        proxy ← ErrorHandler[M].get(parseAC(content, p12, certificates))
        (cred, notAfter) = credential(proxy)
        factory ← socketFactory(cred, certificates, p12.password)
        vomsCredential = VOMSCredential(cred, p12, certificates, notAfter, factory)
      } yield vomsCredential

    }

    import squants.time.TimeConversions._

    //    def keyStore(p12Authentication: P12Authentication, certificates: Vector[java.io.File]) =
    //      P12Authentication.load(p12Authentication).map { ks ⇒ HTTPS.addToKeyStore(certificates, ks) }

    //
    //     def query(
    //       host: String,
    //       port: Int,
    //       p12Authentication: P12Authentication,
    //       lifetime: Option[Int] = None,
    //       fquan: Option[String] = None,
    //       timeout: Time = 1 minutes)(authentication: A)(f: RESTVOMSResponse ⇒ T): T = {
    //
    //
    //
    //       val _timeout = timeout
    //       val client = new HTTPSClient {
    //         override val timeout = _timeout
    //         override val factory = implicitly[HTTPSAuthentication[A]].factory(authentication)
    //       }
    //
    //       client.withClient { c ⇒
    //
    //         val options =
    //           List(
    //             lifetime.map("lifetime=" + _),
    //             fquan.map("fquans=" + _)
    //           ).flatten.mkString("&")
    //         val uri = new URI(s"https://$host:$port/generate-ac${if (!options.isEmpty) "?" + options else ""}")
    //         val get = new HttpGet(uri)
    //         val r = c.execute(get)
    //
    //         if (r.getStatusLine.getStatusCode != HttpStatus.SC_OK)
    //           throw new AuthenticationException("VOMS server returned " + r.getStatusLine.toString)
    //
    //         val parse = new RESTVOMSResponseParsingStrategy()
    //         val parsed = parse.parse(r.getEntity.getContent)
    //
    //         f(parsed)
    //       }
    //     }

  }

}

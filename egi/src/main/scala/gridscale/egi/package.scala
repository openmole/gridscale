package gridscale

import java.math.BigInteger
import java.security.{ KeyPair, KeyPairGenerator, Security }
import java.util.{ Calendar, GregorianCalendar, TimeZone }

import effectaside._
import scala.language.{ higherKinds, postfixOps }

package object egi {

  import gridscale.http._
  import squants._
  import time.TimeConversions._
  import information.InformationConversions._

  import gridscale.authentication._

  case class BDIIServer(host: String, port: Int, timeout: Time = 1 minutes)
  case class CREAMCELocation(hostingCluster: String, port: Int, uniqueId: String, contact: String, memory: Int, maxWallTime: Int, maxCPUTime: Int, status: String)

  object BDII {

    def trimSlashes(path: String) =
      path.reverse.dropWhile(_ == '/').reverse.dropWhile(_ == '/')

    def location(host: String, port: Int, basePath: String) =
      "https://" + trimSlashes(host) + ":" + port + "/" + trimSlashes(basePath) + "/"

    def apply(): Effect[BDII] = Effect(new BDII())
  }

  class BDII() {
    def webDAVs(server: BDIIServer, vo: String) = {
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
        } yield BDII.location(urlObject.getHost, urlObject.getPort, path)
      }
    }

    def creamCEs(server: BDIIServer, vo: String) = {
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

  def webDAVs(server: BDIIServer, vo: String)(implicit bdii: Effect[BDII]) = bdii().webDAVs(server, vo)
  def creamCEs(server: BDIIServer, vo: String)(implicit bdii: Effect[BDII]) = bdii().creamCEs(server, vo)

  object VOMS {

    case class VOMSCredential(
      certificate: HTTPS.KeyStoreOperations.Credential,
      p12: P12Authentication,
      serverCertificates: Vector[HTTPS.KeyStoreOperations.Certificate],
      ending: java.util.Date,
      lifetime: Time,
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

    def renewProxy(renewOperation: () ⇒ VOMSCredential)(credential: VOMSCredential, margin: Time = 1 hours)(implicit system: Effect[System]) = {
      def renew(now: Long): VOMSCredential =
        if (credential.ending.getTime - margin.millis > now) renewOperation() else credential

      val now = system().currentTime()
      renew(now)
    }

    def proxy(
      voms: String,
      p12: P12Authentication,
      certificateDirectory: java.io.File,
      lifetime: Time = 24 hours,
      fqan: Option[String] = None,
      proxySize: ProxySize = ProxySize.PS2048,
      timeout: Time = 1 minutes)(implicit http: Effect[HTTP], fileSystem: Effect[FileSystem]) = {

      case class VOMSProxy(ac: String, p12: P12Authentication, serverCertificates: Vector[HTTPS.KeyStoreOperations.Certificate])

      def parseAC(s: String, p12: P12Authentication, serverCertificates: Vector[HTTPS.KeyStoreOperations.Certificate]) = {
        val xml = scala.xml.XML.loadString(s)
        def content = (xml \\ "voms" \\ "ac").headOption.map(_.text)
        def error = (xml \\ "voms" \\ "error")

        content match {
          case Some(c) ⇒ VOMSProxy(c, p12, serverCertificates)
          case None ⇒
            def message = (error \\ "message").headOption.map(_.text)

            (error \\ "code").headOption.map(_.text) match {
              case Some("NoSuchUser")    ⇒ throw ProxyError(Reason.NoSuchUser, message)
              case Some("BadRequest")    ⇒ throw ProxyError(Reason.BadRequest, message)
              case Some("SuspendedUser") ⇒ throw ProxyError(Reason.SuspendedUser, message)
              case Some(r)               ⇒ throw ProxyError(Reason.InternalError, message)
              case _                     ⇒ throw ProxyError(Reason.Unknown, message)
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

        // TODO use system monad
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

      def socketFactory(certificate: HTTPS.KeyStoreOperations.Credential, serverCertificates: Vector[HTTPS.KeyStoreOperations.Certificate], password: String)(implicit http: Effect[HTTP]) =
        HTTPS.socketFactory(Vector(certificate) ++ serverCertificates, password)

      val options =
        List(
          Some("lifetime=" + lifetime.toSeconds.toLong),
          fqan.map("fqans=" + _)).flatten.mkString("&")

      val location = s"/generate-ac${if (!options.isEmpty) "?" + options else ""}"

      val userCertificate = HTTPS.readP12(p12.certificate, p12.password)
      val certificates = HTTPS.readPEMCertificates(certificateDirectory)
      val vomsFactory = HTTPS.socketFactory(certificates ++ Vector(userCertificate), p12.password)
      val server = HTTPSServer(s"https://$voms", vomsFactory, timeout)
      val content = http().content(server, location)
      val proxy = parseAC(content, p12, certificates)
      val (cred, notAfter) = credential(proxy)
      val factory = socketFactory(cred, certificates, p12.password)
      VOMSCredential(cred, p12, certificates, notAfter, lifetime, factory)
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

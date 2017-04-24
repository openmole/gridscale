package gridscale

package object egi {

  import java.security.{ KeyStore, PrivateKey }
  import java.security.cert.X509Certificate
  import gridscale.http._
  import squants._
  import time.TimeConversions._
  import information.InformationConversions._

  import freedsl.dsl._
  import freedsl.filesystem._
  import freedsl.io._
  import cats._
  import cats.implicits._

  object BDII {

    case class Server(host: String, port: Int, timeout: Time = 1 minutes)

    def trimSlashes(path: String) =
      path.reverse.dropWhile(_ == '/').reverse.dropWhile(_ == '/')

    def location(host: String, port: Int, basePath: String) =
      "https://" + trimSlashes(host) + ":" + port + "/" + trimSlashes(basePath) + "/"

    case class CREAMCELocation(hostingCluster: String, port: Int, uniqueId: String, contact: String, memory: Int, maxWallTime: Int, maxCPUTime: Int, status: String)

    def interpreter = new Interpreter {
      def webDAVs(server: Server, vo: String)(implicit context: Context) = result {
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
          } yield location(urlObject.getHost, urlObject.getPort, path)
        }
      }

      def creamCEs(server: Server, vo: String)(implicit context: Context) = result {
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
              status = status
            )
          }
        }
      }

    }
  }

  @dsl trait BDII[M[_]] {
    def webDAVs(server: BDII.Server, vo: String): M[Vector[String]]
    def creamCEs(server: BDII.Server, vo: String): M[Vector[BDII.CREAMCELocation]]
  }

  //  import
  //
  //
  //  case class BDII(host: String, port: Int, timeout: Time = 1 minute)
  //
  //  {
  //
  //    val creamCEServiceType = "org.glite.ce.CREAM"
  //
  //    def queryWebDAVLocations(vo: String) = BDIIQuery.withBDIIQuery(host, port, timeout) { q ⇒
  //      def searchPhrase = "(GLUE2EndpointInterfaceName=webdav)"
  //
  //      val services =
  //        for {
  //          webdavService ← q.query(searchPhrase, bindDN = "o=glue").toSeq
  //          id = webdavService.getAttributes.get("GLUE2EndpointID").get.toString
  //          url = webdavService.getAttributes.get("GLUE2EndpointURL").get.toString
  //        } yield (id, url)
  //
  //      for {
  //        (id, url) ← services
  //        urlObject = new URI(url)
  //        host = urlObject.getHost
  //        pathQuery ← q.query(s"(&(GlueChunkKey=GlueSEUniqueID=$host)(GlueVOInfoAccessControlBaseRule=VO:$vo))")
  //        path = pathQuery.getAttributes.get("GlueVOInfoPath").get.toString
  //      } yield WebDAVLocation(urlObject.getHost, path, urlObject.getPort)
  //    }
  //
  //    case class CREAMCELocation(hostingCluster: String, port: Int, uniqueId: String, contact: String, memory: Int, maxWallTime: Int, maxCPUTime: Int, status: String)
  //
  //    def queryCREAMCELocations(vo: String) = BDIIQuery.withBDIIQuery(host, port, timeout) { q ⇒
  //      val res = q.query(s"(&(GlueCEAccessControlBaseRule=VO:$vo)(GlueCEImplementationName=CREAM))")
  //
  //      case class Machine(memory: Int)
  //      def machineInfo(host: String) = {
  //        val info = q.query(s"(GlueChunkKey=GlueClusterUniqueID=$host)").get(0)
  //        Machine(memory = info.getAttributes.get("GlueHostMainMemoryRAMSize").get().toString.toInt)
  //      }
  //
  //      for {
  //        info ← res
  //        maxWallTime = info.getAttributes.get("GlueCEPolicyMaxWallClockTime").get.toString.toInt
  //        maxCpuTime = info.getAttributes.get("GlueCEPolicyMaxCPUTime").get.toString.toInt
  //        port = info.getAttributes.get("GlueCEInfoGatekeeperPort").get.toString.toInt
  //        uniqueId = info.getAttributes.get("GlueCEUniqueID").get.toString
  //        contact = info.getAttributes.get("GlueCEInfoContactString").get.toString
  //        status = info.getAttributes.get("GlueCEStateStatus").get.toString
  //        hostingCluster = info.getAttributes.get("GlueCEHostingCluster").get.toString
  //        memory = machineInfo(hostingCluster).memory
  //      } yield {
  //        CREAMCELocation(
  //          hostingCluster = hostingCluster,
  //          port = port,
  //          uniqueId = uniqueId,
  //          contact = contact,
  //          memory = memory,
  //          maxCPUTime = maxCpuTime,
  //          maxWallTime = maxWallTime,
  //          status = status
  //        )
  //      }
  //    }
  //
  //    def searchService(vo: String, serviceType: String) = {
  //      def serviceTypeQuery = s"(GlueServiceType=$serviceType)"
  //      s"(&(objectClass=GlueService)(GlueServiceUniqueID=*)(GlueServiceAccessControlRule=$vo)($serviceTypeQuery))"
  //    }
  //  }

  //
  //  object DIRACJobDescription {
  //    val Linux_x86_64_glibc_2_11 = "Linux_x86_64_glibc-2.11"
  //    val Linux_x86_64_glibc_2_12 = "Linux_x86_64_glibc-2.12"
  //    val Linux_x86_64_glibc_2_5 = "Linux_x86_64_glibc-2.5"
  //
  //    val Linux_i686_glibc_2_34 = "Linux_i686_glibc-2.3.4"
  //    val Linux_i686_glibc_2_5 = "Linux_i686_glibc-2.5"
  //  }
  //
  //  case class DIRACJobDescription(
  //    executable: String,
  //    arguments: String,
  //    stdOut: Option[String] = None,
  //    stdErr: Option[String] = None,
  //    inputSandbox: Seq[java.io.File] = List.empty,
  //    outputSandbox: Seq[(String, java.io.File)] = List.empty,
  //    platforms: Seq[String] = Seq.empty,
  //    cpuTime: Option[Time] = None)
  //
  //  def toJSON(jobDescription: DIRACJobDescription, jobGroup: Option[String] = None) = {
  //    import jobDescription._
  //    def inputSandboxArray = JArray(inputSandbox.map(f ⇒ JString(f.getName)).toList)
  //    def outputSandboxArray = JArray(outputSandbox.map(f ⇒ JString(f._1)).toList)
  //    def platformsArray = JArray(platforms.map(f ⇒ JString(f)).toList)
  //
  //    val fields = List(
  //      "Executable" -> JString(executable),
  //      "Arguments" -> JString(arguments)) ++
  //      stdOut.map(s ⇒ "StdOutput" -> JString(s)) ++
  //      stdErr.map(s ⇒ "StdError" -> JString(s)) ++
  //      (if (!inputSandbox.isEmpty) Some("InputSandbox" -> inputSandboxArray) else None) ++
  //      (if (!outputSandbox.isEmpty) Some("OutputSandbox" -> outputSandboxArray) else None) ++
  //      cpuTime.map(s ⇒ "CPUTime" -> JString(s.toSeconds.toString)) ++
  //      (if (!platforms.isEmpty) Some("Platform" -> platformsArray) else None) ++
  //      jobGroup.map(s ⇒ "JobGroup" -> JString(s))
  //
  //    pretty(JObject(fields: _*))
  //  }
  //
  //
  //  object DIRAC {
  //    def requestContent[T](request: HttpRequestBase with HttpRequest)(f: InputStream ⇒ T): T = withClient { httpClient ⇒
  //      request.setConfig(HTTPStorage.requestConfig(timeout))
  //      val response = httpClient.execute(httpHost, request)
  //      HTTPStorage.testResponse(response).get
  //      val is = response.getEntity.getContent
  //      try f(is)
  //      finally is.close
  //    }
  //
  //    def request[T](request: HttpRequestBase with HttpRequest)(f: String ⇒ T): T =
  //      requestContent(request) { is ⇒
  //        f(Source.fromInputStream(is).mkString)
  //      }
  //  }
  //
  //  @dsl trait DIRAC[M[_]] {
  //
  //  }
  //
  //
  //  def submit(diracJobDescription: DIRACJobDescription) = {
  //
  //  }

  //  def submit(jobDescription: D): J = submit(jobDescription, None)
  //
  //  def submit(jobDescription: D, group: Option[String]): J = {
  //    def files = {
  //      val builder = MultipartEntityBuilder.create()
  //      jobDescription.inputSandbox.foreach {
  //        f ⇒ builder.addBinaryBody(f.getName, f)
  //      }
  //      builder.addTextBody("access_token", tokenCache().token)
  //      builder.addTextBody("manifest", jobDescription.toJSON(group))
  //      builder.build
  //    }
  //
  //    val uri = new URI(jobs)
  //
  //    val post = new HttpPost(uri)
  //    post.setEntity(files)
  //    request(post) { r ⇒
  //      (parse(r) \ "jids")(0).extract[String]
  //    }
  //  }
  //
  //  def submit(d: D): J = {
  //    val (group, _) =
  //      jobsServiceJobGroup.update {
  //        case (group, jobs) ⇒
  //          if (jobs >= jobsByGroup) (newGroup, 1) else (group, jobs + 1)
  //      }
  //
  //    val jid = jobService.submit(d, Some(group))
  //    val time = System.currentTimeMillis
  //    Job(jid, time, group)
  //  }

  object P12Authentication {

    //    def load[M[_]: Monad](a: P12Authentication)(implicit fileSystem: FileSystem[M], io: IO[M]) = {
    //
    //      def loadKS(is: java.io.InputStream) = {
    //        val ks = KeyStore.getInstance("pkcs12")
    //        ks.load(is, a.password.toCharArray)
    //        ks
    //      }
    //
    //      for {
    //        loadResult ← fileSystem.readStream(a.certificate, loadKS)
    //        loaded ← io(loadResult)
    //      } yield loaded
    //    }

    def loadPKCS12Credentials(a: P12Authentication) = {
      val ks = KeyStore.getInstance("pkcs12")
      ks.load(new java.io.FileInputStream(a.certificate), a.password.toCharArray)

      val aliases = ks.aliases
      import collection.JavaConverters._

      // FIXME GET
      val alias = aliases.asScala.find(e ⇒ ks.isKeyEntry(e)).get
      //if (alias == null) throw new VOMSException("No aliases found inside pkcs12 certificate!")

      val userCert = ks.getCertificate(alias).asInstanceOf[X509Certificate]
      val userKey = ks.getKey(alias, a.password.toCharArray).asInstanceOf[PrivateKey]
      val userChain = Array[X509Certificate](userCert)

      Loaded(userCert, userKey, userChain)
    }

    case class Loaded(certificate: X509Certificate, key: PrivateKey, chain: Array[X509Certificate])
  }

  case class P12Authentication(certificate: java.io.File, password: String)

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

    case class Proxy(ac: String, p12: P12Authentication, serverCertificates: Vector[HTTPS.KeyStoreOperations.Certificate])

    case class ProxyError(reason: Reason, message: Option[String]) extends Throwable {
      override def toString = s"${reason}: ${message.getOrElse("No message")}"
    }

    sealed trait Reason

    object Reason {
      case object NoSuchUser extends Reason
      case object BadRequest extends Reason
      case object SuspendedUser extends Reason
      case object InternalError extends Reason
      case object Unknown extends Reason
    }

    def serverCertificates[M[_]: HTTP: FileSystem: ErrorHandler: Monad](certificateDirectory: java.io.File) =
      for {
        certificateFiles ← FileSystem[M].list(certificateDirectory)
        certificates ← certificateFiles.traverse(f ⇒ HTTPS.readPem[M](f)).map(_.flatMap(_.toOption))
      } yield certificates

    def proxy[M[_]: Monad: ErrorHandler: HTTP: FileSystem](
      voms: String,
      p12: P12Authentication,
      certificateDirectory: java.io.File,
      lifetime: Option[Int] = None,
      fquan: Option[String] = None): M[Proxy] = {

      def parseAC(s: String, p12: P12Authentication, serverCertificates: Vector[HTTPS.KeyStoreOperations.Certificate]) = {
        val xml = scala.xml.XML.loadString(s)
        def content = (xml \\ "voms" \\ "ac").headOption.map(_.text)
        def error = (xml \\ "voms" \\ "error")

        content match {
          case Some(c) ⇒ util.Success(Proxy(c, p12, serverCertificates))
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

      val options =
        List(
          lifetime.map("lifetime=" + _),
          fquan.map("fquans=" + _)
        ).flatten.mkString("&")

      val location = s"generate-ac${if (!options.isEmpty) "?" + options else ""}"

      for {
        userCertificate ← HTTPS.readP12[M](p12.certificate, p12.password).flatMap(r ⇒ ErrorHandler[M].get(r))
        certificates ← serverCertificates[M](certificateDirectory)
        factory ← ErrorHandler[M].get(HTTPS.socketFactory(certificates ++ Vector(userCertificate), p12.password))
        server = HTTPSServer(s"https://$voms/", factory)
        content ← HTTP[M].content(server, location)
        proxy ← ErrorHandler[M].get(parseAC(content, p12, certificates))
      } yield proxy

    }

    def sockerFactory[M[_]: HTTP: ErrorHandler](proxy: VOMS.Proxy) = {
      import org.apache.commons.codec.binary.Base64
      import org.bouncycastle.asn1._
      import org.bouncycastle.asn1.x509._
      import eu.emi.security.authn.x509.proxy._

      def certificate = {
        val acBytes = new Base64().decode(proxy.ac.trim().replaceAll("\n", ""))
        val asn1InputStream = new ASN1InputStream(acBytes)
        val attributeCertificate = AttributeCertificate.getInstance(asn1InputStream.readObject)
        asn1InputStream.close()
        val cred = P12Authentication.loadPKCS12Credentials(proxy.p12)
        val proxyOptions = new ProxyCertificateOptions(cred.chain)
        proxyOptions.setAttributeCertificates(Array[AttributeCertificate](attributeCertificate))
        val proxyKS = ProxyGenerator.generate(proxyOptions, cred.key).getCredential
        HTTPS.KeyStoreOperations.Credential(proxyKS.getKey, proxyKS.getCertificateChain.toVector, proxy.p12.password)
      }

      ErrorHandler[M].get(HTTPS.socketFactory(Vector(certificate) ++ proxy.serverCertificates, proxy.p12.password))
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

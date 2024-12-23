package gridscale

import gridscale.authentication.P12Authentication

import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.KeyStore.{PasswordProtection, PrivateKeyEntry}
import java.security.PrivateKey
import java.security.cert.{Certificate, CertificateFactory, X509Certificate}
import org.apache.commons.codec.binary
import org.apache.http.{HttpEntity, client}
import org.apache.http.client.methods
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler
import org.apache.http.message.BasicHttpRequest
import sun.security.provider.X509Factory

import javax.security.auth.x500.X500Principal
import scala.io.Source
import scala.language.{higherKinds, postfixOps}

package object http {

  import gridscale.http.methods._
  import org.apache.http.{ HttpRequest, HttpResponse, HttpStatus }
  import org.apache.http.client.config.RequestConfig
  import org.apache.http.impl.client.{ HttpClients, LaxRedirectStrategy }
  import org.apache.http.impl.conn.BasicHttpClientConnectionManager
  import org.htmlparser.Parser
  import org.htmlparser.filters.NodeClassFilter
  import org.htmlparser.tags.LinkTag
  import squants._
  import squants.time.TimeConversions._
  import squants.information.InformationConversions._
  import java.io.IOException
  import java.net.URI
  import org.apache.http.client.methods._
  import scala.util.Try
  import gridscale.tools._
  import java.io.InputStream
  import org.apache.http.config.{ RegistryBuilder, SocketConfig }
  import org.apache.http.conn.socket.ConnectionSocketFactory
  import squants.information.Information

  def buildServer(url: String, timeout: Time = 1 minutes): Server =
    val scheme = new URI(url).getScheme

    scheme match {
      case "https" ⇒ HTTPSServer(url, HTTPS.SSLSocketFactory.default, timeout)
      case "http"  ⇒ HTTPServer(url, timeout)
      case _       ⇒ throw new HTTP.ConnectionError(s"Unknown protocol $scheme, in url $url. Only http and https are supported.", null)
    }

  def get[T](url: String, headers: Headers = Seq.empty) =
    HTTP.withHTTP:
      read(buildServer(url), "", method = Get(headers))

  def getStream[T](url: String)(f: InputStream ⇒ T) =
    HTTP.withHTTP:
      readStream[T](buildServer(url), "", f)

  def getResponse[T](url: String)(f: Response ⇒ T) =
    HTTP.withHTTP:
      readResponse[T](buildServer(url), "", f)

  type Headers = Seq[(String, String)]
  case class Response(inputStream: InputStream, headers: Headers)

  object Headers:
    def expectContinue = (org.apache.http.protocol.HTTP.EXPECT_DIRECTIVE, org.apache.http.protocol.HTTP.EXPECT_CONTINUE)


  sealed trait HTTPMethod
  case class Get(headers: Headers = Seq.empty) extends HTTPMethod
  case class PropFind(headers: Headers = Seq.empty) extends HTTPMethod
  case class Delete(headers: Headers = Seq.empty) extends HTTPMethod
  case class Put(stream: () ⇒ InputStream, headers: Headers = Seq.empty) extends HTTPMethod
  case class Post(entity: () ⇒ HttpEntity, headers: Headers = Seq.empty) extends HTTPMethod
  case class MkCol(headers: Headers = Seq.empty) extends HTTPMethod
  case class Head(headers: Headers = Seq.empty) extends HTTPMethod
  case class Move(to: String, headers: Headers = Seq.empty) extends HTTPMethod

  object HTTP {

    def withHTTP[T](f: HTTP ?=> T) = f(using new HTTP())

    def client(server: Server) =
      server match {
        case s: HTTPServer  ⇒ httpClient(s.timeout, s.retry)
        case s: HTTPSServer ⇒ HTTPS.newClient(s.socketFactory, s.timeout, s.retry)
      }

    def requestConfig(timeout: Time) =
      RequestConfig.custom()
        .setSocketTimeout(timeout.toMillis.toInt)
        .setConnectTimeout(timeout.toMillis.toInt)
        .setConnectionRequestTimeout(timeout.toMillis.toInt)
        .build()

    def httpClient(timeout: Time, retry: Int) =
      def connectionManager(timeout: Time) =
        val client = new BasicHttpClientConnectionManager()
        val socketConfig = SocketConfig.custom().setSoTimeout(timeout.toMillis.toInt).build()
        client.setSocketConfig(socketConfig)
        client

      def newClient(timeout: Time, retry: Int) =
        HttpClients.custom().
          //setRedirectStrategy(redirectStrategy).
          setConnectionManager(connectionManager(timeout)).
          setDefaultRequestConfig(requestConfig(timeout)).
          setRetryHandler(new DefaultHttpRequestRetryHandler(retry, false)).
          build()

      newClient(timeout, retry)

    def wrapError[T](f: ⇒ T) =
      try f
      catch
        case t: ConnectionError ⇒ throw t
        case t: HTTPError       ⇒ throw t
        case t: Throwable       ⇒ throw HTTPError(t)

    case class ConnectionError(msg: String, t: Throwable) extends Exception(msg, t)

    object HTTPError:
      def apply(p: String | Throwable) =
        p match
          case p: String => new HTTPError(message = Some(p))
          case p: Throwable => new HTTPError(cause = Some(p))

    case class HTTPError(message: Option[String] = None, cause: Option[Throwable] = None) extends Exception(cause.orNull):
      override def toString = "HTTP error: " + message.orElse(cause.map(_.getMessage)).getOrElse(super.toString)


  }

  class HTTP {

    def withInputStream[T](server: Server, path: String, f: (HttpRequest, HttpResponse) ⇒ T, method: HTTPMethod, test: Boolean) =
      def fullURI(path: String) =
        if (path.isEmpty) server.url else new URI(server.url.toString + path)

      val uri = fullURI(path)

      val (methodInstance, headers, closeable) =
        method match {
          case Get(headers) ⇒
            val getInstance = new HttpGet(uri)
            (getInstance, headers, None)
          case PropFind(headers) ⇒ (new HttpPropFind(uri), headers, None)
          case Delete(headers)   ⇒ (new HttpDelete(uri), headers, None)
          case MkCol(headers)    ⇒ (new HttpMkCol(uri), headers, None)
          case Head(headers)     ⇒ (new HttpHead(uri), headers, None)
          case Put(is, headers) ⇒
            val putInstance = new HttpPut(uri)
            val createdStream = is()
            putInstance.setEntity(new InputStreamEntity(createdStream))
            (putInstance, headers, Some(createdStream))
          case Post(fentity, headers) ⇒
            val postInstance = new HttpPost(uri)
            postInstance.setEntity(fentity())
            (postInstance, headers, None)
          case Move(to, headers) ⇒ (new HttpMove(uri, fullURI(to), true), headers, None)
        }

      headers.foreach { case (k, v) ⇒ methodInstance.addHeader(k, v) }

      def testResponse(response: HttpResponse) =
        def isResponseOk(response: HttpResponse) =
          response.getStatusLine.getStatusCode >= HttpStatus.SC_OK &&
            response.getStatusLine.getStatusCode < HttpStatus.SC_BAD_REQUEST

        if !isResponseOk(response)
        then
          val content = new String(getBytes(response.getEntity.getContent, server.bufferSize.toBytes.toInt))
          throw HTTP.HTTPError(
            s""""$uri responded with an error: ${response.getStatusLine.getStatusCode} ${response.getStatusLine.getReasonPhrase}. The content was:
               |$content""".stripMargin)

      import util._

      def error(e: Throwable) = new HTTP.ConnectionError(s"Error while connecting to ${uri}, method ${method}", e)

      try
        val httpClient = HTTP.client(server)
        try
          val response = httpClient.execute(methodInstance)
          try
            if (test) testResponse(response)
            f(methodInstance, response)
          finally response.close()
        catch
          case e: org.apache.http.conn.ConnectTimeoutException  ⇒ throw error(e)
          case e: SocketTimeoutException                        ⇒ throw error(e)
          case e: java.net.ConnectException                     ⇒ throw error(e)
          case e: org.apache.http.conn.HttpHostConnectException ⇒ throw error(e)
        finally httpClient.close()
      finally closeable.foreach(_.close())

    def request[T](server: Server, path: String, f: (HttpRequest, HttpResponse) ⇒ T, method: HTTPMethod, testResponse: Boolean = true) =
      HTTP.wrapError:
        withInputStream(server, path, f, method, testResponse)


    def content(server: Server, path: String, method: HTTPMethod = Get()): String = HTTP.wrapError:
      def getString(is: InputStream) = new String(getBytes(is, server.bufferSize.toBytes.toInt))
      def getContent(r: HttpResponse) = Option(r.getEntity).map(e ⇒ getString(e.getContent)).getOrElse("")

      withInputStream(server, path, (_, r) ⇒ getContent(r), method, test = true)

  }

  //  @tagless trait HTTP {
  //    def request[T](server: Server, path: String, f: (HttpRequest, HttpResponse) ⇒ T, method: HTTPMethod, testResponse: Boolean = true): FS[T]
  //    def content(server: Server, path: String, method: HTTPMethod = Get()): FS[String]
  //  }

  object Server {
    def apply(url: String, timeout: Time = 1 minutes) = buildServer(url, timeout)

    def copy(s: Server)(url: URI = s.url) =
      s match {
        case s: HTTPServer  ⇒ s.copy(url = url)
        case s: HTTPSServer ⇒ s.copy(url = url)
      }
  }

  sealed trait Server {
    def url: URI
    def timeout: Time
    def bufferSize: Information
  }

  object HTTPServer:
    def apply(url: String, timeout: Time = 1 minutes, bufferSize: Information = 64 kilobytes, retry: Int = 0) =
      new HTTPServer(new URI(url), timeout, bufferSize, retry)

  case class HTTPServer(url: URI, timeout: Time, bufferSize: Information, retry: Int) extends Server

  object HTTPSServer:
    def apply(url: String, socketFactory: HTTPS.SSLSocketFactory, timeout: Time = 1 minutes, bufferSize: Information = 64 kilobytes, retry: Int = 0) =
      new HTTPSServer(new URI(url), socketFactory, timeout, bufferSize, retry)

  case class HTTPSServer(url: URI, socketFactory: HTTPS.SSLSocketFactory, timeout: Time, bufferSize: Information, retry: Int) extends Server

  def parseHTMLListing(page: String) = {
    val parser = new Parser
    parser.setInputHTML(page)
    val list = parser.extractAllNodesThatMatch(new NodeClassFilter(classOf[LinkTag]))

    list.toNodeArray.flatMap {
      l ⇒
        val entryName = l.getText.substring("a href=\"".size, l.getText.size - 1)
        val isDir = entryName.endsWith("/")
        val name = if (isDir) entryName.substring(0, entryName.length - 1) else entryName
        if (!name.isEmpty && !name.contains("/") && !name.contains("?") && !name.contains("#")) {
          val ret = name.replaceAll("&amp;", "%26")
          Some(
            ListEntry(
              new java.io.File(java.net.URLDecoder.decode(ret, "utf-8")).getPath,
              if (isDir) FileType.Directory else FileType.File,
              None))
        } else None
    }.toVector
  }

  def list(server: Server, path: String)(using http: HTTP) = parseHTMLListing(http.content(server, path))
  def read(server: Server, path: String, method: HTTPMethod = Get())(using http: HTTP) = http.content(server, path, method)

  def readStream[T](server: Server, path: String, f: InputStream ⇒ T, method: HTTPMethod = Get())(using http: HTTP): T =
    http.request(server, path, (_, r) ⇒ f(r.getEntity.getContent), method)

  def readResponse[T](server: Server, path: String, f: Response ⇒ T, method: HTTPMethod = Get())(using http: HTTP): T =
    def toResponse(response: HttpResponse) =
      Response(response.getEntity.getContent, response.getAllHeaders.toSeq.map(h ⇒ h.getName -> h.getValue))

    http.request(server, path, (_, r) ⇒ f(toResponse(r)), method)

  object HTTPS {

    import squants._
    import javax.net.ssl.X509TrustManager
    import java.security.cert.CertificateException
    import java.security.{ KeyStore, SecureRandom }
    import javax.net.ssl._
    import org.apache.http.conn.ssl.{ BrowserCompatHostnameVerifier, SSLConnectionSocketFactory }

    object SSLSocketFactory {
      def default = (timeout: Time) ⇒
        new SSLConnectionSocketFactory(org.apache.http.ssl.SSLContexts.createDefault()) {
          override protected def prepareSocket(socket: SSLSocket) =
            super.prepareSocket(socket)
            socket.setSoTimeout(timeout.millis.toInt)
        }
    }

    type SSLSocketFactory = (Time ⇒ SSLConnectionSocketFactory)

    def socketFactory(s: Vector[KeyStoreOperations.Storable], password: String, verifyHostName: Boolean = true, forceServerTrust: Boolean = false) =
      KeyStoreOperations.socketFactory(() ⇒ KeyStoreOperations.createSSLContext(KeyStoreOperations.createKeyStore(s, password), password, forceServerTrust = forceServerTrust), verifyHostName)

    object KeyStoreOperations {

      def createKeyStore(s: Vector[KeyStoreOperations.Storable], password: String) =
        val keyStore =
          val ks = KeyStore.getInstance(KeyStore.getDefaultType)
          ks.load(null, password.toCharArray)
          ks

        s.zipWithIndex.foreach: (s, i) ⇒
          store(keyStore, i.toString, s)

        keyStore

      def store(ks: KeyStore, name: String, t: Storable) =
        t match
          case t: Credential ⇒
            val entry = new PrivateKeyEntry(t.privateKey, t.certificateChain.toArray)
            ks.setEntry(name, entry, new PasswordProtection(t.password.toCharArray))
          case t: Certificate ⇒
            ks.setCertificateEntry(name, t.certificate)

      sealed trait Storable
      case class Credential(privateKey: PrivateKey, certificateChain: Vector[java.security.cert.Certificate], password: String) extends Storable
      case class Certificate(certificate: java.security.cert.Certificate) extends Storable
      //case class Key(key: java.security.Key, password: String, certificates: Vector[java.security.cert.Certificate], pat) extends Storable

      def createSSLContext(keyStore: KeyStore, password: String, forceServerTrust: Boolean = false): SSLContext = {
        class TrustManagerDelegate(val mainTrustManager: X509TrustManager, val fallbackTrustManager: X509TrustManager) extends X509TrustManager:
          override def checkClientTrusted(x509Certificates: Array[java.security.cert.X509Certificate], authType: String) =
            try mainTrustManager.checkClientTrusted(x509Certificates, authType)
            catch
              case ignored: CertificateException ⇒ fallbackTrustManager.checkClientTrusted(x509Certificates, authType)

          override def checkServerTrusted(x509Certificates: Array[java.security.cert.X509Certificate], authType: String) =
            if !forceServerTrust
            then
              try mainTrustManager.checkServerTrusted(x509Certificates, authType)
              catch
                case ignored: CertificateException ⇒ fallbackTrustManager.checkServerTrusted(x509Certificates, authType)

          override def getAcceptedIssuers() = fallbackTrustManager.getAcceptedIssuers()

        val javaDefaultTrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        javaDefaultTrustManager.init(null: KeyStore)

        createSSLContext(
          keyStore,
          new TrustManagerDelegate(
            trustManager(keyStore),
            javaDefaultTrustManager.getTrustManagers()(0).asInstanceOf[X509TrustManager]),
          password)
      }

      //def createSSLContext(path: String, password: String): SSLContext = createSSLContext(getKeyStore(path, password), password)

      def createSSLContext(keyStore: KeyStore, trustManager: TrustManager, password: String) = {
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        kmf.init(keyStore, password.toCharArray)
        sslContext.init(kmf.getKeyManagers, Array[TrustManager](trustManager), new SecureRandom())
        sslContext
      }

      def socketFactory(sslContext: () ⇒ SSLContext, verifyHostName: Boolean): SSLSocketFactory = {
        val hostnameVerifier =
          if (verifyHostName) org.apache.http.conn.ssl.SSLConnectionSocketFactory.getDefaultHostnameVerifier
          else new HostnameVerifier {
            override def verify(s: String, sslSession: SSLSession): Boolean = true
          }

        (timeout: Time) ⇒
          new org.apache.http.conn.ssl.SSLConnectionSocketFactory(sslContext(), hostnameVerifier) {
            override protected def prepareSocket(socket: SSLSocket) = {
              super.prepareSocket(socket)
              socket.setSoTimeout(timeout.millis.toInt)
            }
          }
      }

    }

    def trustManager(keyStore: KeyStore) = {
      val customCaTrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
      customCaTrustManager.init(keyStore)
      customCaTrustManager.getTrustManagers()(0).asInstanceOf[X509TrustManager]
    }

    //    def getKeyStore(path: String, password: String) = {
    //      val ks = KeyStore.getInstance("JKS")
    //      val is = this.getClass.getResourceAsStream(path)
    //      try ks.load(is, password.toCharArray())
    //      finally is.close()
    //      ks
    //    }

    def connectionManager(factory: org.apache.http.conn.ssl.SSLConnectionSocketFactory, timeout: Time) = {
      val registry =
        RegistryBuilder.create[ConnectionSocketFactory]().
          register("https", factory).
          register("http", PlainConnectionSocketFactory.getSocketFactory).build()

      val client = new BasicHttpClientConnectionManager(registry)
      val socketConfig = SocketConfig.custom().setSoTimeout(timeout.toMillis.toInt).build()
      client.setSocketConfig(socketConfig)
      client
    }

    def newClient(factory: SSLSocketFactory, timeout: Time, retry: Int) = {
      val client =
        HttpClients.custom().
          setConnectionManager(connectionManager(factory(timeout), timeout)).
          setDefaultRequestConfig(HTTP.requestConfig(timeout)).
          setRetryHandler(new DefaultHttpRequestRetryHandler(retry, false))

      client.build()
    }

    def readPem(pem: java.io.File) =
      val content = FileSystem.readStream(pem)(is ⇒ Source.fromInputStream(is).mkString)
      val stripped = content.replaceAll(X509Factory.BEGIN_CERT, "").replaceAll(X509Factory.END_CERT, "")
      val decoded = new binary.Base64().decode(stripped)
      val certificate = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(decoded))
      KeyStoreOperations.Certificate(certificate)

    def readP12(file: java.io.File, password: String) =
      def keyStore =
        FileSystem.readStream(file): is ⇒
          val ks = KeyStore.getInstance("pkcs12")
          ks.load(is, password.toCharArray)
          ks

      def extractCertificate(ks: KeyStore) =
        import java.security.cert._
        val alias = P12Authentication.getAlias(ks)

        //if (alias == null) throw new VOMSException("No aliases found inside pkcs12 certificate!")
        val userCert = ks.getCertificate(alias).asInstanceOf[X509Certificate]
        val userKey = ks.getKey(alias, password.toCharArray).asInstanceOf[PrivateKey]
        val userChain = Array[X509Certificate](userCert)

        KeyStoreOperations.Credential(userKey, userChain.toVector, password)

        // Loaded(userCert, userKey, userChain)

      extractCertificate(keyStore)

    def readPEMCertificates(certificateDirectory: java.io.File) =
      val certificateFiles = FileSystem.list(certificateDirectory).sortBy(_.lastModified())
      certificateFiles.map: f ⇒
        util.Try:
          val pem = HTTPS.readPem(f)
          pem
      .flatMap(_.toOption)

    // case class Loaded(certficate: X509Certificate, key: PrivateKey, chain: Array[X509Certificate])

    //    def addToKeyStore[M[_]: Monad](pems: Vector[java.io.File], ks: KeyStore = emptyKeyStore)(implicit keyStoreOperations: KeyStoreOperations[M]) = {
    //      for {
    //        (file, i) ← pems.zipWithIndex
    //        pem ← readPem(file).tried
    //      } Try(ks.setCertificateEntry(i.toString, pem))
    //      ks
    //    }
    //
    //    def addToKeyStore(key: PrivateKey, certficate: Vector[Certificate], ks: KeyStore, password: String) = {
    //      val entry = new PrivateKeyEntry(key, certficate.toArray)
    //      ks.setEntry("test", entry, new PasswordProtection(password.toCharArray))
    //    }

  }

}

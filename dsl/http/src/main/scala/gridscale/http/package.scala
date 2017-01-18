package gridscale

import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl._

import org.apache.http.config.{ RegistryBuilder, SocketConfig }
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.ssl.{ BrowserCompatHostnameVerifier, SSLConnectionSocketFactory }
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.ssl.SSLContexts
import squants.information.Information
import sun.security.util.Password

import scala.concurrent.duration.Duration

package object http {

  import com.github.sardine.impl.methods.HttpPropFind
  import org.apache.http.{ HttpRequest, HttpResponse, HttpStatus }
  import org.apache.http.client.config.RequestConfig
  import org.apache.http.client.methods.HttpUriRequest
  import org.apache.http.config.SocketConfig
  import org.apache.http.impl.client.{ HttpClients, LaxRedirectStrategy }
  import org.apache.http.impl.conn.BasicHttpClientConnectionManager
  import org.apache.http.protocol.HttpContext
  import gridscale._
  import freedsl.dsl._
  import org.htmlparser.Parser
  import org.htmlparser.filters.NodeClassFilter
  import org.htmlparser.tags.LinkTag
  import cats._
  import cats.implicits._
  import squants._
  import squants.time.TimeConversions._
  import squants.information.InformationConversions._
  import java.io.IOException
  import java.net.URI
  import org.apache.http.client.methods._
  import scala.util.Try
  import gridscale.tools._
  import java.io.InputStream

  object HTTP {

    def redirectStrategy = new LaxRedirectStrategy {
      override def getRedirect(request: HttpRequest, response: HttpResponse, context: HttpContext): HttpUriRequest = {
        assert(response.getStatusLine.getStatusCode < HttpStatus.SC_BAD_REQUEST, "Error while redirecting request")
        super.getRedirect(request, response, context)
      }

      override protected def isRedirectable(method: String) =
        method match {
          case HttpPropFind.METHOD_NAME ⇒ true
          //  case HttpPut.METHOD_NAME      ⇒ true
          case _                        ⇒ super.isRedirectable(method)
        }
    }

    def client(server: Server, timeout: Time) =
      server match {
        case s: HTTPServer  ⇒ httpClient(timeout)
        case s: HTTPSServer ⇒ HTTPS.newClient(s.sockerFactor, timeout)
      }

    def requestConfig(timeout: Time) =
      RequestConfig.custom()
        .setSocketTimeout(timeout.millis.toInt)
        .setConnectTimeout(timeout.millis.toInt)
        .setConnectionRequestTimeout(timeout.millis.toInt)
        .build()

    def httpClient(timeout: Time) = {
      def connectionManager(timeout: Time) = {
        val client = new BasicHttpClientConnectionManager()
        val socketConfig = SocketConfig.custom().setSoTimeout(timeout.millis.toInt).build()
        client.setSocketConfig(socketConfig)
        client
      }

      def newClient(timeout: Time) =
        HttpClients.custom().
          setRedirectStrategy(redirectStrategy).
          setConnectionManager(connectionManager(timeout)).
          setDefaultRequestConfig(requestConfig(timeout)).build()

      newClient(timeout)
    }

    def isResponseOk(response: HttpResponse) =
      response.getStatusLine.getStatusCode >= HttpStatus.SC_OK &&
        response.getStatusLine.getStatusCode < HttpStatus.SC_BAD_REQUEST

    def testResponse(response: HttpResponse) = Try {
      if (!isResponseOk(response)) throw new IOException(s"Server responded with an error: ${response.getStatusLine.getStatusCode} ${response.getStatusLine.getReasonPhrase}")
    }

    def interpreter(server: Server, timeout: Time = 1 minutes, bufferSize: Information = 64 kilobytes) = new Interpreter[Id] {

      def withInputStream[T](path: String, f: InputStream ⇒ T): Try[T] = {
        val uri = new URI(server.url + "/" + path)
        val get = new HttpGet(uri)
        get.addHeader(org.apache.http.protocol.HTTP.EXPECT_DIRECTIVE, org.apache.http.protocol.HTTP.EXPECT_CONTINUE)

        import util._

        for {
          httpClient ← Try(client(server, timeout))
          response ← Try(httpClient.execute(get))
          _ ← Try(testResponse(response))
          stream ← Try(response.getEntity.getContent)
          res ← Try(f(stream))
          _ ← Try {
            stream.close()
            get.releaseConnection()
            response.close()
            httpClient.close()
          }
        } yield res
      }

      def interpret[_] = {
        case request(path, f) ⇒ withInputStream(path, f).toEither.leftMap(t ⇒ HTTPError(t))
        case content(path) ⇒
          def getString(is: InputStream) = new String(getBytes(is, bufferSize.toBytes.toInt, timeout))
          withInputStream(path, getString).toEither.leftMap(t ⇒ HTTPError(t))
      }
    }

    case class HTTPError(t: Throwable) extends Error
  }

  object HTTPS {
    import squants._
    import javax.net.ssl.X509TrustManager
    import java.security.cert.CertificateException
    import java.security.cert.X509Certificate

    type SSLSocketFactory = (Time ⇒ SSLConnectionSocketFactory)

    class TrustManagerDelegate(val mainTrustManager: X509TrustManager, val fallbackTrustManager: X509TrustManager) extends X509TrustManager {
      override def checkClientTrusted(x509Certificates: Array[X509Certificate], authType: String) =
        try {
          mainTrustManager.checkClientTrusted(x509Certificates, authType)
        } catch {
          case ignored: CertificateException ⇒ fallbackTrustManager.checkClientTrusted(x509Certificates, authType)
        }

      override def checkServerTrusted(x509Certificates: Array[X509Certificate], authType: String) =
        try {
          mainTrustManager.checkServerTrusted(x509Certificates, authType)
        } catch {
          case ignored: CertificateException ⇒ fallbackTrustManager.checkServerTrusted(x509Certificates, authType)
        }

      override def getAcceptedIssuers() = fallbackTrustManager.getAcceptedIssuers()
    }

    def createSSLContext(path: String, password: String) = {
      val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
      val javaDefaultTrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
      javaDefaultTrustManager.init(null: KeyStore)
      val customCaTrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
      customCaTrustManager.init(getKeyStore(path, password))

      sslContext.init(
        null,
        Array[TrustManager](
          new TrustManagerDelegate(
            customCaTrustManager.getTrustManagers()(0).asInstanceOf[X509TrustManager],
            javaDefaultTrustManager.getTrustManagers()(0).asInstanceOf[X509TrustManager]
          )
        ), new SecureRandom()
      )

      sslContext
    }

    def getKeyStore(path: String, password: String) = {
      val ks = KeyStore.getInstance("JKS")
      val is = this.getClass.getResourceAsStream(path)
      try ks.load(is, password.toCharArray())
      finally is.close()
      ks
    }

    def socketFactory(sslContext: SSLContext): SSLSocketFactory =
      (timeout: Time) ⇒
        new org.apache.http.conn.ssl.SSLConnectionSocketFactory(sslContext) {
          override protected def prepareSocket(socket: SSLSocket) = {
            socket.setSoTimeout(timeout.millis.toInt)
          }
        }

    def keyStoreFactory(path: String, password: String) = socketFactory(createSSLContext(path, password))

    def connectionManager(factory: org.apache.http.conn.ssl.SSLConnectionSocketFactory) = {
      val registry = RegistryBuilder.create[ConnectionSocketFactory]().register("https", factory).build()
      val client = new BasicHttpClientConnectionManager(registry)
      val socketConfig = SocketConfig.custom().build()
      client.setSocketConfig(socketConfig)
      client
    }

    def newClient(factory: SSLSocketFactory, timeout: Time) =
      HttpClients.custom().
        setRedirectStrategy(HTTP.redirectStrategy).
        setConnectionManager(connectionManager(factory(timeout))).
        setDefaultRequestConfig(HTTP.requestConfig(timeout)).build()

  }

  @dsl trait HTTP[M[_]] {
    def request[T](path: String, f: java.io.InputStream ⇒ T): M[T]
    def content(path: String): M[String]
  }

  sealed trait Server {
    def url: String
  }
  case class HTTPServer(url: String) extends Server
  case class HTTPSServer(url: String, sockerFactor: HTTPS.SSLSocketFactory) extends Server

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
              None
            )
          )
        } else None
    }.toVector
  }

  def list[M[_]: Monad](path: String)(implicit http: HTTP[M]) = http.content(path).map(parseHTMLListing)
  def read[M[_]: Monad](path: String)(implicit http: HTTP[M]) = http.content(path)
}

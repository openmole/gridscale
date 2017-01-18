package gridscale

import squants.information.Information

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

    def connectionManager(timeout: Time) = {
      val client = new BasicHttpClientConnectionManager()
      val socketConfig = SocketConfig.custom().setSoTimeout(timeout.millis.toInt).build()
      client.setSocketConfig(socketConfig)
      client
    }

    def requestConfig(timeout: Time) =
      RequestConfig.custom()
        .setSocketTimeout(timeout.millis.toInt)
        .setConnectTimeout(timeout.millis.toInt)
        .setConnectionRequestTimeout(timeout.millis.toInt)
        .build()

    def newClient(timeout: Time) =
      HttpClients.custom().
        setRedirectStrategy(redirectStrategy).
        setConnectionManager(connectionManager(timeout)).
        setDefaultRequestConfig(requestConfig(timeout)).build()

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
          httpClient ← Try(newClient(timeout))
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

  @dsl trait HTTP[M[_]] {
    def request[T](path: String, f: java.io.InputStream ⇒ T): M[T]
    def content(path: String): M[String]
  }

  case class Server(url: String)

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

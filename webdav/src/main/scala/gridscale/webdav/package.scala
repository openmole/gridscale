package gridscale

import cats._
import cats.implicits._
import gridscale.webdav.WebDAV._
import java.io.{ ByteArrayInputStream, IOException, InputStream }

import freedsl.errorhandler._
import org.apache.http.{ HttpRequest, HttpResponse, HttpStatus }
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.DefaultRedirectStrategy

import scala.language.{ higherKinds, postfixOps }

package object webdav {

  type Server = http.Server
  lazy val WebDAVServer = http.HTTPServer
  lazy val WebDAVSServer = http.HTTPSServer

  def listProperties[M[_]: http.HTTP: Monad](server: http.Server, path: String) =
    for {
      content ← http.read[M](server, path, http.PropFind())
    } yield parsePropsResponse(content)

  def list[M[_]: http.HTTP: Monad](server: http.Server, path: String) = listProperties[M](server, path).map(_.map(_.displayName))

  def exists[M[_]: http.HTTP: ErrorHandler: Monad](server: http.Server, path: String): M[Boolean] = {
    def readResponse(response: HttpResponse) =
      response.getStatusLine.getStatusCode match {
        case x if x < HttpStatus.SC_MULTIPLE_CHOICES ⇒ util.Success(true)
        case HttpStatus.SC_NOT_FOUND                 ⇒ util.Success(false)
        case _                                       ⇒ util.Failure(new IOException(s"Server responded with an unexpected response: ${response.getStatusLine.getStatusCode} ${response.getStatusLine.getReasonPhrase}"))
      }

    for {
      response ← http.HTTP[M].request(server, path, (_, resp) ⇒ resp, http.Head(), testResponse = false)
      result ← ErrorHandler[M].get(readResponse(response))
    } yield result
  }

  def rmFile[M[_]: http.HTTP: Monad](server: http.Server, path: String): M[Unit] = http.read[M](server, path, http.Delete()).map(_ ⇒ ())
  def rmDirectory[M[_]: http.HTTP: Monad](server: Server, path: String): M[Unit] = http.read[M](server, path, http.Delete(headers = Seq("Depth" -> "infinity"))).map(_ ⇒ ())
  def mkDirectory[M[_]: http.HTTP: Monad](server: Server, path: String): M[Unit] = http.read[M](server, path, http.MkCol()).map(_ ⇒ ())

  def writeStream[M[_]: Monad: http.HTTP](server: Server, path: String, is: () ⇒ InputStream, redirect: Boolean = true): M[Unit] = {
    def redirectedServer =
      if (!redirect) server.pure[M]
      else
        for {
          diskURI ← http.HTTP[M].request(server, path, WebDAV.getRedirectURI, http.Put(() ⇒ WebDAV.emptyStream(), headers = Seq(http.Headers.expectContinue)))
          destination = http.Server.copy(server)(url = diskURI)
        } yield destination

    for {
      s ← redirectedServer
      _ ← http.read[M](s, "", http.Put(is))
    } yield {}
  }

  def read[M[_]: Monad: http.HTTP](server: Server, path: String, method: http.HTTPMethod = http.Get()) = http.read[M](server, path, method)
  def readStream[M[_]: Monad: http.HTTP, T](server: Server, path: String, f: InputStream ⇒ T, method: http.HTTPMethod = http.Get()): M[T] = http.readStream[M, T](server, path, f, method)

  import java.time.ZoneId

  import scala.util.Try
  import scala.xml.{ Node, XML }

  object WebDAV {

    def gmt = ZoneId.of("GMT")

    private def dateFormats = {
      import java.time.format._
      def createFormat(f: String) = DateTimeFormatter.ofPattern(f).withLocale(java.util.Locale.US).withZone(gmt)

      Vector(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        //"yyyy-MM-dd'T'HH:mm:ss.sss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "EEE MMM dd HH:mm:ss zzz yyyy",
        //      "EEEEEE, dd-MMM-yy HH:mm:ss zzz",
        "EEE MMMM d HH:mm:ss yyyy").map(createFormat)
    }

    private def parseDate(s: String) = {
      import java.time._
      dateFormats.view.flatMap { format ⇒ Try { LocalDate.parse(s, format) }.toOption }.headOption
    }

    case class Prop(
      displayName: String,
      isCollection: Boolean,
      modified: java.time.LocalDate)

    def parseProp(n: Node) =
      Prop(
        displayName = n \\ "displayname" text,
        isCollection = (n \\ "iscollection" text) == "1",
        modified = parseDate(n \\ "getlastmodified" text).get)

    def parsePropsResponse(r: String) =
      (XML.loadString(r) \\ "multistatus" \\ "response" \\ "propstat" \\ "prop").map(parseProp)

    def emptyStream() = new java.io.ByteArrayInputStream(Array())
    def getRedirectURI(put: HttpRequest, redirect: HttpResponse) =
      new DefaultRedirectStrategy().getLocationURI(put, redirect, new HttpClientContext())

    def doNothing(put: HttpRequest, redirect: HttpResponse) = {}

  }

}

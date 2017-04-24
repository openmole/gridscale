package gridscale

import cats._
import cats.implicits._
import gridscale.webdav.WebDAV._
import java.io.{ ByteArrayInputStream, InputStream }

import org.apache.http.{ HttpRequest, HttpResponse }
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.DefaultRedirectStrategy

package object webdav {

  def listProperties[M[_]: http.HTTP: Monad](server: http.Server, path: String) =
    for {
      content ← http.read[M](server, path, http.HTTP.PropFind())
    } yield parsePropsResponse(content)

  def list[M[_]: http.HTTP: Monad](server: http.Server, path: String) = listProperties[M](server, path).map(_.map(_.displayName))

  def rmFile[M[_]: http.HTTP: Monad](server: http.Server, path: String) = http.read[M](server, path, http.HTTP.Delete())
  def rmDirectory[M[_]: http.HTTP: Monad](server: http.Server, path: String) = http.read[M](server, path, http.HTTP.Delete(headers = Seq("Depth" -> "infinity")))

  def read[M[_]: http.HTTP: Monad](server: http.Server, path: String) = http.read[M](server, path)
  def readStream[M[_]: Monad: http.HTTP, T](server: http.Server, path: String, f: InputStream ⇒ T): M[T] = http.readStream[M, T](server, path, f)

  def writeStream[M[_]: Monad: http.HTTP](server: http.Server, path: String, is: () ⇒ InputStream, redirect: Boolean = true) = {

    def redirectedServer =
      if (!redirect) server.pure[M]
      else
        for {
          diskURI ← http.HTTP[M].request(server, path, WebDAV.getRedirectURI, http.HTTP.Put(WebDAV.emptyStream))
          destination = http.Server.copy(server)(url = diskURI.toString)
        } yield destination

    for {
      s ← redirectedServer
      _ ← http.read[M](s, "", http.HTTP.Put(is))
    } yield {}
  }

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
        "EEE MMMM d HH:mm:ss yyyy"
      ).map(createFormat)
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
        modified = parseDate(n \\ "getlastmodified" text).get
      )

    def parsePropsResponse(r: String) =
      (XML.loadString(r) \\ "multistatus" \\ "response" \\ "propstat" \\ "prop").map(parseProp)

    def emptyStream() = new java.io.ByteArrayInputStream(Array())
    def getRedirectURI(put: HttpRequest, redirect: HttpResponse) = new DefaultRedirectStrategy().getLocationURI(put, redirect, new HttpClientContext())
    def doNothing(put: HttpRequest, redirect: HttpResponse) = {}

  }

}

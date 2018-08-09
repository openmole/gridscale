package gridscale

import gridscale.webdav.WebDAV._
import java.io.{ ByteArrayInputStream, IOException, InputStream }
import java.time.ZoneOffset

import effectaside._
import org.apache.http.{ HttpRequest, HttpResponse, HttpStatus }
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.DefaultRedirectStrategy

import scala.language.{ higherKinds, postfixOps }

package object webdav {

  type Server = http.Server
  lazy val WebDAVServer = http.HTTPServer
  lazy val WebDAVSServer = http.HTTPSServer

  def listProperties(server: http.Server, path: String)(implicit httpEffect: Effect[http.HTTP]) = {
    val content = http.read(server, path, http.PropFind())
    parsePropsResponse(content)
  }

  def list(server: http.Server, path: String)(implicit httpEffect: Effect[http.HTTP]) = {
    val properties = listProperties(server, path)
    properties.drop(1).map { p: Prop ⇒ ListEntry(p.displayName, if (p.isCollection) FileType.Directory else FileType.File, Some(p.modified.toEpochSecond(ZoneOffset.UTC) * 1000)) }
  }

  def exists(server: http.Server, path: String)(implicit httpEffect: Effect[http.HTTP]): Boolean = {
    def readResponse(response: HttpResponse) =
      response.getStatusLine.getStatusCode match {
        case x if x < HttpStatus.SC_MULTIPLE_CHOICES ⇒ true
        case HttpStatus.SC_NOT_FOUND                 ⇒ false
        case _                                       ⇒ throw new IOException(s"Server responded with an unexpected response: ${response.getStatusLine.getStatusCode} ${response.getStatusLine.getReasonPhrase}")
      }

    val response = httpEffect().request(server, path, (_, resp) ⇒ resp, http.Head(), testResponse = false)
    readResponse(response)
  }

  def rmFile(server: http.Server, path: String)(implicit httpEffect: Effect[http.HTTP]): Unit = http.read(server, path, http.Delete())
  def rmDirectory(server: Server, path: String)(implicit httpEffect: Effect[http.HTTP]): Unit = http.read(server, path, http.Delete(headers = Seq("Depth" -> "infinity")))
  def mkDirectory(server: Server, path: String)(implicit httpEffect: Effect[http.HTTP]): Unit = http.read(server, path, http.MkCol())
  def mv(server: Server, from: String, to: String)(implicit httpEffect: Effect[http.HTTP]): Unit = http.read(server, from, http.Move(to))

  def writeStream(server: Server, is: () ⇒ InputStream, path: String, redirect: Boolean = true)(implicit httpEffect: Effect[http.HTTP]): Unit = {
    def redirectedServer =
      if (!redirect) server
      else {
        val diskURI = httpEffect().request(server, path, WebDAV.getRedirectURI, http.Put(() ⇒ WebDAV.emptyStream(), headers = Seq(http.Headers.expectContinue)))
        http.Server.copy(server)(url = diskURI)
      }

    val s = redirectedServer
    http.read(s, "", http.Put(is))
  }

  def read(server: Server, path: String, method: http.HTTPMethod = http.Get())(implicit httpEffect: Effect[http.HTTP]) = http.read(server, path, method)
  def readStream[T](server: Server, path: String, f: InputStream ⇒ T, method: http.HTTPMethod = http.Get())(implicit httpEffect: Effect[http.HTTP]): T =
    http.readStream[T](server, path, f, method)

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
      dateFormats.view.flatMap { format ⇒ Try { LocalDateTime.parse(s, format) }.toOption }.headOption
    }

    case class Prop(
      displayName: String,
      isCollection: Boolean,
      modified: java.time.LocalDateTime)

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

package gridscale

import gridscale.webdav.WebDAV.*

import java.io.{ByteArrayInputStream, IOException, InputStream}
import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import org.apache.http.{HttpRequest, HttpResponse, HttpStatus}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.DefaultRedirectStrategy

import scala.collection.mutable.ListBuffer
import scala.language.{higherKinds, postfixOps}
import scala.util.control.Breaks

package object webdav {

  type Server = http.Server
  lazy val WebDAVServer = http.HTTPServer
  lazy val WebDAVSServer = http.HTTPSServer

  def listProperties(server: http.Server, path: String)(using http.HTTP) =
    http.readStream(server, path, parsePropsResponse, http.PropFind())

  def list(server: http.Server, path: String)(using http.HTTP) =
    import squants.time.*
    val properties = listProperties(server, path)
    properties.drop(1).map { p ⇒ ListEntry(p.displayName, if (p.isCollection) FileType.Directory else FileType.File, Some(Seconds(p.modified.toEpochSecond(ZoneOffset.UTC)))) }

  def exists(server: http.Server, path: String)(using http.HTTP): Boolean =
    def readResponse(response: HttpResponse) =
      response.getStatusLine.getStatusCode match
        case x if x < HttpStatus.SC_MULTIPLE_CHOICES ⇒ true
        case HttpStatus.SC_NOT_FOUND                 ⇒ false
        case _                                       ⇒ throw new IOException(s"Server responded with an unexpected response: ${response.getStatusLine.getStatusCode} ${response.getStatusLine.getReasonPhrase}")

    val response = summon[http.HTTP].request(server, path, (_, resp) ⇒ resp, http.Head(), testResponse = false)
    readResponse(response)

  def rmFile(server: http.Server, path: String)(using http.HTTP): Unit = http.read(server, path, http.Delete())
  def rmDirectory(server: Server, path: String)(using http.HTTP): Unit = http.read(server, path, http.Delete(headers = Seq("Depth" -> "infinity")))
  def mkDirectory(server: Server, path: String)(using http.HTTP): Unit = http.read(server, path, http.MkCol())
  def mv(server: Server, from: String, to: String)(using http.HTTP): Unit = http.read(server, from, http.Move(to))

  def writeStream(server: Server, is: () ⇒ InputStream, path: String, redirect: Boolean = true)(using http.HTTP): Unit =
    def redirectedServer =
      if !redirect
      then server
      else
        val diskURI = summon[http.HTTP].request(server, path, WebDAV.getRedirectURI, http.Put(() ⇒ WebDAV.emptyStream(), headers = Seq(http.Headers.expectContinue)))
        http.Server.copy(server)(url = diskURI)

    val s = redirectedServer
    http.read(s, "", http.Put(is))

  def read(server: Server, path: String, method: http.HTTPMethod = http.Get())(using http.HTTP) = http.read(server, path, method)
  def readStream[T](server: Server, path: String, f: InputStream ⇒ T, method: http.HTTPMethod = http.Get())(using http.HTTP): T =
    http.readStream[T](server, path, f, method)

  import java.time.ZoneId

  import scala.util.Try

  object WebDAV {

    def gmt = ZoneId.of("GMT")

    private def dateFormats =
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

    private def parseDate(s: String) =
      import java.time._
      dateFormats.view.flatMap { format ⇒ Try { LocalDateTime.parse(s, format) }.toOption }.headOption

    case class Prop(
      displayName: String,
      isCollection: Boolean,
      modified: java.time.LocalDateTime)

    def parsePropsResponse(r: InputStream) =
      import scala.jdk.CollectionConverters.*
      //import scala.xml.pull._
      //println(io.Source.fromInputStream(r).mkString)

      import javax.xml.stream._
      import javax.xml.stream.events._
      import scala.io._
      val er = XMLInputFactory.newDefaultFactory().createXMLEventReader(r)
      val props = ListBuffer[Prop]()

      def isStartWithName(e: XMLEvent, name: String) = e.isStartElement && e.asStartElement().getName.getLocalPart == name
      def isEndWithName(x: XMLEvent, name: String) = x.isEndElement && x.asEndElement().getName.getLocalPart == name


      while (er.hasNext) {
        val eventBreak = new Breaks
        import eventBreak.{break, breakable}

        er.nextEvent() match {
          case e if isStartWithName(e, "prop") ⇒
            var x = er.nextEvent()
            var displayName: Option[String] = None
            var isCollection: Option[Boolean] = None
            var lastModified: Option[LocalDateTime] = None

            while !isEndWithName(x, "prop")
            do
              x match
                case e if isStartWithName(e, "displayname") ⇒ displayName = Some(er.nextEvent().asCharacters().getData)
                case e if isStartWithName(e, "iscollection") ⇒ isCollection = Some(er.nextEvent().asCharacters().getData == "1")
                case e if isStartWithName(e, "resourcetype") =>
                  x = er.nextEvent()
                  isCollection = Some(false)
                  while !isEndWithName(x, "resourcetype")
                  do
                    if isStartWithName(x, "collection") then isCollection = Some(true)
                    x = er.nextEvent()
                    
                case e if isStartWithName(e, "getlastmodified") ⇒ lastModified = parseDate(er.nextEvent().asCharacters().getData)
                case _ ⇒
              x = er.nextEvent()

            for
              d <- displayName
              c <- isCollection
              m <- lastModified
            do props += Prop(d, c, m)
          case _ ⇒
        }
      }


      props.toVector

    def emptyStream() = new java.io.ByteArrayInputStream(Array())
    def getRedirectURI(put: HttpRequest, redirect: HttpResponse) =
      new DefaultRedirectStrategy().getLocationURI(put, redirect, new HttpClientContext())

    def doNothing(put: HttpRequest, redirect: HttpResponse) = {}

  }

}
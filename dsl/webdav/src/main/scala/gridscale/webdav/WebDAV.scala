package gridscale.webdav

import java.time.ZoneId

import scala.util.Try
import scala.xml.{ Node, XML }
import gridscale.http._
import cats._
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

  //  def listProp(path: String) = withClient { httpClient ⇒
  //    val entity = new HttpPropFind(fullUrl(path))
  //    entity.setDepth(1.toString)
  //    try {
  //      val multistatus = httpClient.execute(entity)
  //      DPMWebDAVStorage.parsePropsResponse(scala.io.Source.fromInputStream(multistatus.getEntity.getContent).mkString)
  //    } finally entity.releaseConnection
  //  }

}

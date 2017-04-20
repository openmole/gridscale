package fr.iscpif.gridscale.http

import scala.util.Try

package object internal {

  def dateFormats = {
    import org.joda.time.format._
    def createFormat(f: String) = DateTimeFormat.forPattern(f).withLocale(java.util.Locale.US).withZoneUTC()

    Vector(
      "yyyy-MM-dd'T'HH:mm:ss'Z'",
      "EEE, dd MMM yyyy HH:mm:ss zzz",
      "yyyy-MM-dd'T'HH:mm:ss.sss'Z'",
      "yyyy-MM-dd'T'HH:mm:ssZ",
      "EEE MMM dd HH:mm:ss zzz yyyy",
      "EEEEEE, dd-MMM-yy HH:mm:ss zzz",
      "EEE MMMM d HH:mm:ss yyyy"
    ).map(createFormat)
  }

  def parseDate(s: String) =
    dateFormats.view.flatMap { p ⇒ Try(p.parseDateTime(s).toDate).toOption }.headOption

}

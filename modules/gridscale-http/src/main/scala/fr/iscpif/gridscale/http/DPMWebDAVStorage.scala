/*
 * Copyright (C) 2015 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.iscpif.gridscale.http

import java.io._
import java.net.URI
import fr.iscpif.gridscale.http.methods._
import fr.iscpif.gridscale.storage._
import org.apache.http._
import org.apache.http.client.methods._
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.DefaultRedirectStrategy
import org.apache.http.protocol.{HTTP, HttpContext}
import org.joda.time.format._

import scala.concurrent.duration._
import scala.util._
import scala.xml._

case class WebDAVLocation(host: String, basePath: String, port: Int = 443)

object DPMWebDAVStorage {
  def apply[A: HTTPSAuthentication](location: WebDAVLocation, timeout: Duration = 1 minute)(authentication: A) = {
    val (_location, _timeout) = (location, timeout)
    new DPMWebDAVStorage {
      override def location = _location
      override def factory = implicitly[HTTPSAuthentication[A]].factory(authentication)
      override def timeout = _timeout
    }
  }

  def dateFormats = {
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

  case class Prop(
    displayName: String,
    isCollection: Boolean,
    modified: java.util.Date)

  def parseProp(n: Node) =
    Prop(
      displayName = n \\ "displayname" text,
      isCollection = (n \\ "iscollection" text) == "1",
      modified = parseDate(n \\ "getlastmodified" text).get
    )

  def parsePropsResponse(r: String) =
    (XML.loadString(r) \\ "multistatus" \\ "response" \\ "propstat" \\ "prop").map(parseProp)

}

trait DPMWebDAVStorage <: HTTPSClient with Storage { dav ⇒

  def location: WebDAVLocation
  def timeout: Duration

  def fullUrl(path: String) =
    "https://" + trimSlashes(location.host) + ":" + location.port + "/" + trimSlashes(location.basePath) + "/" + trimSlashes(path)

  override def _write(is: InputStream, path: String) = {
    val put = new HttpPut(fullUrl(path))
    put.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE)
    put.setEntity(new InputStreamEntity(new ByteArrayInputStream(Array())))

    val redirect = withClient { _.execute(put) }
    HTTPStorage.testResponse(redirect).get

    val uri = new DefaultRedirectStrategy().getLocationURI(put, redirect, new HttpClientContext())

    val entity = new InputStreamEntity(is, -1)
    val putOnDiskNode = new HttpPut(uri)
    putOnDiskNode.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE)
    putOnDiskNode.setEntity(entity)

    val response = withClient { _.execute(putOnDiskNode) }
    HTTPStorage.testResponse(response).get
  }

  override def _read(path: String): InputStream = HTTPStorage.toInputStream(new URI(fullUrl(path)), newClient).stream

  override def _makeDir(path: String): Unit = withClient { httpClient =>
    val mkcol = new HttpMkCol(fullUrl(path))
    HTTPStorage.execute(httpClient.execute, mkcol)
  }

  override def _mv(from: String, to: String): Unit = withClient { httpClient =>
    val move = new HttpMove(fullUrl(from), fullUrl(to), true)
    HTTPStorage.execute(httpClient.execute, move)
  }

  override def _rmDir(path: String): Unit =  withClient { httpClient =>
    val delete = new HttpDelete(fullUrl(path))
    delete.addHeader("Depth", "infinity")
    HTTPStorage.execute(httpClient.execute, delete)
  }

  def listProp(path: String) = withClient { httpClient =>
      val entity = new HttpPropFind(fullUrl(path))
      entity.setDepth(1.toString)
      try {
        val multistatus = httpClient.execute(entity)
        DPMWebDAVStorage.parsePropsResponse(scala.io.Source.fromInputStream(multistatus.getEntity.getContent).mkString)
      } finally entity.releaseConnection
   }

  override def _list(path: String): Seq[ListEntry] = {
    for { r ← listProp(path).drop(1) } yield {
      ListEntry(
        name = r.displayName,
        `type` = if (r.isCollection) DirectoryType else FileType,
        Some(r.modified.getTime)
      )
    }
  }

  override def _rmFile(path: String): Unit =  withClient { httpClient =>
    val delete = new HttpDelete(fullUrl(path))
    HTTPStorage.execute(httpClient.execute, delete)
  }

  override def _exists(path: String): Boolean =  withClient { httpClient =>
    val head = new HttpHead(fullUrl(path))
    try {
      val response = httpClient.execute(head)

      try {
        response.getStatusLine.getStatusCode match {
          case x if x < HttpStatus.SC_MULTIPLE_CHOICES ⇒ true
          case HttpStatus.SC_NOT_FOUND ⇒ false
          case _ ⇒ throw new IOException(s"Server responded with an unexpected response: ${response.getStatusLine.getStatusCode} ${response.getStatusLine.getReasonPhrase}")
        }
      } finally response.close()
    } finally head.releaseConnection()
  }

  override def toString = fullUrl("")

}

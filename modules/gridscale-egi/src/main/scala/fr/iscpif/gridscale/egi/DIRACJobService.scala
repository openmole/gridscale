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
package fr.iscpif.gridscale.egi

import java.io.{ BufferedInputStream, BufferedOutputStream, FileOutputStream, InputStream }
import java.net.{ URI, URL }

import fr.iscpif.gridscale.cache.SingleValueAsynchronousCache
import fr.iscpif.gridscale.http.{ HTTPSClient, HTTPSAuthentication }
import fr.iscpif.gridscale.jobservice._
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.http.client.methods._
import org.apache.http.client.utils.URIBuilder
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.{ HttpHost, HttpRequest }
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.duration._
import scala.io.Source
import scala.sys.process.BasicIO

object DIRACJobService {

  def apply[A: HTTPSAuthentication](
    service: String,
    group: String,
    timeout: Duration = 1 minutes)(authentication: A) = {
    val (_service, _group, _timeout) = (service, group, timeout)
    new DIRACJobService {
      override val timeout = _timeout
      override val group: String = _group
      override val factory = implicitly[HTTPSAuthentication[A]].factory(authentication)
      override val service: String = _service
    }
  }

}

trait DIRACJobService extends JobService with HTTPSClient {

  type J = String
  type D = DIRACJobDescription

  case class Token(token: String, expires_in: Long)

  private implicit def strToURL(s: String) = new URL(s)

  def service: String
  def group: String
  def timeout: Duration
  def setup = "Dirac-Production"
  def auth2Auth = service + "/oauth2/token"
  def jobs = service + "/jobs"

  def tokenExpirationMargin = 10 -> MINUTES

  @transient lazy val tokenCache =
    new SingleValueAsynchronousCache[Token] {
      def compute() = token
      def expiresIn(t: Token) = (t.expires_in, SECONDS) - tokenExpirationMargin
    }

  def token = {
    val uri = new URIBuilder(auth2Auth)
      .setParameter("grant_type", "client_credentials")
      .setParameter("group", group)
      .setParameter("setup", setup)
      .build()

    val get = new HttpGet(uri)

    request(get) { r ⇒
      val f = r.trim.parseJson.asJsObject.getFields("token", "expires_in")
      Token(f(0).convertTo[String], f(1).convertTo[Long])
    }
  }

  def submit(jobDescription: D): String = {
    def files = {
      val builder = MultipartEntityBuilder.create()
      jobDescription.inputSandbox.zipWithIndex.foreach {
        case (f, i) ⇒ builder.addBinaryBody(f.getName, f)
      }
      builder.build
    }

    val uri = new URIBuilder(jobs)
      .setParameter("access_token", tokenCache().token)
      .setParameter("manifest", jobDescription.toJSON)
      .build

    val post = new HttpPost(uri)
    post.setEntity(files)
    request(post) { r ⇒
      r.parseJson.asJsObject.getFields("jids").head.toJson.convertTo[JsArray].elements.head.toString
    }
  }

  def state(jobId: J) = {
    val uri =
      new URIBuilder(jobs + "/" + jobId)
        .setParameter("access_token", tokenCache().token)
        .build

    val get = new HttpGet(uri)

    request(get) { r ⇒
      r.parseJson.asJsObject.getFields("status").head.toJson.convertTo[String] match {
        case "Received"  ⇒ Submitted
        case "Checking"  ⇒ Submitted
        case "Staging"   ⇒ Submitted
        case "Waiting"   ⇒ Submitted
        case "Matched"   ⇒ Submitted
        case "Running"   ⇒ Running
        case "Completed" ⇒ Running
        case "Stalled"   ⇒ Running
        case "Killed"    ⇒ Failed
        case "Deleted"   ⇒ Failed
        case "Done"      ⇒ Done
        case "Failed"    ⇒ Failed
      }
    }
  }

  def downloadOutputSandbox(desc: D, jobId: J) = {
    val outputSandboxMap = desc.outputSandbox.toMap

    val uri =
      new URIBuilder(jobs + "/" + jobId + "/outputsandbox")
        .setParameter("access_token", tokenCache().token)
        .build

    val get = new HttpGet(uri)

    requestContent(get) { str ⇒
      val is = new TarArchiveInputStream(new BufferedInputStream(str))

      Iterator.continually(is.getNextEntry).takeWhile(_ != null).
        filter { e ⇒ outputSandboxMap.contains(e.getName) }.foreach {
          e ⇒
            val os = new BufferedOutputStream(new FileOutputStream(outputSandboxMap(e.getName)))
            try BasicIO.transferFully(is, os)
            finally os.close
        }
    }

  }

  def cancel(jobId: J) = {
    val uri =
      new URIBuilder(jobs + "/" + jobId)
        .setParameter("access_token", tokenCache().token)
        .build

    val delete = new HttpDelete(uri)

    request(delete) { identity }
  }

  def purge(job: J) = {}

  def httpHost: HttpHost = {
    val uri = new URI(service)
    new HttpHost(uri.getHost, uri.getPort, uri.getScheme)
  }

  def requestContent[T](request: HttpRequestBase with HttpRequest)(f: InputStream ⇒ T): T = withClient { httpClient ⇒
    request.setConfig(requestConfig)
    val response = httpClient.execute(httpHost, request)
    val is = response.getEntity.getContent
    try f(is)
    finally is.close
  }

  def request[T](request: HttpRequestBase with HttpRequest)(f: String ⇒ T): T =
    requestContent(request) { is ⇒
      f(Source.fromInputStream(is).mkString)
    }
}

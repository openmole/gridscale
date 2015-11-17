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

import java.io.{ BufferedOutputStream, FileOutputStream, BufferedInputStream, InputStream }
import java.net.{ URI, URL }
import fr.iscpif.gridscale.cache.SingleValueCache
import fr.iscpif.gridscale.egi.https.HTTPSAuthentication
import fr.iscpif.gridscale.tools.DefaultTimeout
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.utils.URIBuilder
import org.apache.http.config.{ RegistryBuilder }
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.{ HttpHost, HttpRequest }
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods._
import org.apache.http.impl.client.{ HttpClients }
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import spray.json._
import DefaultJsonProtocol._
import scala.sys.process.BasicIO
import scala.io.Source
import fr.iscpif.gridscale.jobservice._
import concurrent.duration._

object DIRACJobService {

  def apply[A: HTTPSAuthentication](service: String, group: String)(authentication: A) = {
    val (_service, _group) = (service, group)
    new DIRACJobService {
      override def group: String = _group
      override def factory: (Duration) ⇒ ConnectionSocketFactory = implicitly[HTTPSAuthentication[A]].factory(authentication)
      override def service: String = _service
    }
  }

}

trait DIRACJobService extends JobService with DefaultTimeout with HTTPSClient {

  type J = String
  type D = DIRACJobDescription

  case class Token(token: String, expires_in: Long)

  private implicit def strToURL(s: String) = new URL(s)

  def group: String
  def setup = "Dirac-Production"
  def auth2Auth = service + "/oauth2/token"
  def jobs = service + "/jobs"
  def factory: Duration ⇒ ConnectionSocketFactory

  def tokenExpirationMargin = 10 -> MINUTES

  @transient lazy val tokenCache =
    new SingleValueCache[Token] {
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
            println(e)
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
}

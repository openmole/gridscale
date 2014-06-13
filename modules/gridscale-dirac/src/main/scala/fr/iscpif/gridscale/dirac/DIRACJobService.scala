/*
 * Copyright (C) 05/06/13 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.iscpif.gridscale.dirac

import java.net.{ HttpURLConnection, URL }
import javax.net.ssl.HttpsURLConnection
import spray.json._
import DefaultJsonProtocol._
import scalaj.http.{ HttpOptions, MultiPart, Http }
import scala.io.Source
import fr.iscpif.gridscale._
import scalaj.http.Http.Request
import concurrent.duration._

trait DIRACJobService extends JobService with DefaultTimeout {

  type A = HTTPSAuthentication
  type J = String
  type D = DIRACJobDescription

  case class Token(token: String, expires_in: Long)

  private implicit def strToURL(s: String) = new URL(s)

  def service: String
  def group: String
  def setup = "Dirac-Production"
  def auth2Auth = service + "/oauth2/token"
  def jobs = service + "/jobs"

  def tokenExpirationMargin = 10 -> MINUTES
  def httpsCredentialOption(c: HttpURLConnection) =
    credential.connect(c.asInstanceOf[HttpsURLConnection])

  implicit class RequestDecorator[R <: Request](r: R) {
    def initialise = r.option(httpsCredentialOption).
      option(HttpOptions.connTimeout(timeout.toMillis.toInt)).
      option(HttpOptions.readTimeout(timeout.toMillis.toInt))
    def withToken = r.param("access_token", tokenCache().token)
    def asStringChecked = {
      val (responseCode, headersMap, resultString) = r.asHeadersAndParse(Http.readString)
      if (responseCode != HttpURLConnection.HTTP_OK) throw new RuntimeException(s"Response code was $responseCode when to the request $r, message is $resultString")
      resultString
    }
  }

  lazy val tokenCache =
    new SingleValueCache[Token] {
      def compute() = token
      def expiresIn(t: Token) = (t.expires_in, SECONDS) - tokenExpirationMargin
    }

  def token = {
    val o =
      Http(auth2Auth).param("grant_type", "client_credentials").param("group", group).param("setup", setup).initialise.asStringChecked.asJson.asJsObject
    val f = o.getFields("token", "expires_in")
    Token(f(0).convertTo[String], f(1).convertTo[Long])
  }

  def submit(jobDescription: D): String = {
    def files =
      jobDescription.inputSandbox.zipWithIndex.map {
        case (f, i) ⇒
          val s = Source.fromFile(f)
          try MultiPart(i.toString, f.getName, "text/plain", s.map(_.toByte).toArray)
          finally s.close
      }
    val res = Http.multipart(jobs, files.toSeq: _*).withToken.param("manifest", jobDescription.toJSON).initialise.asStringChecked
    res.asJson.asJsObject.getFields("jids").head.toJson.convertTo[JsArray].elements.head.toString
  }

  def state(jobId: J) = {
    val res = Http(jobs + "/" + jobId).withToken.initialise.asStringChecked
    res.asJson.asJsObject.getFields("status").head.toJson.convertTo[String] match {
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

  /*def downloadOutputSandbox(desc: D, jobId: J)(implicit credential: A) = {
    val outputSandboxMap = desc.outputSandbox.toMap

    val bytes = initialiseRequest(Http(jobs + "/" + jobId + "/outputsandbox").param("access_token", tokenCache(credential).token))(credential).asBytes

    val is = new TarArchiveInputStream(new ByteArrayInputStream(bytes))

    Iterator.continually(is.getNextEntry).takeWhile(_ != null).
      filter { e ⇒  outputSandboxMap.contains(e.getName) }.foreach {
        e ⇒
          println(e)
          val os = new FileOutputStream(outputSandboxMap(e.getName))
          try BasicIO.transferFully(is, os)
          finally os.close
      }

  } */

  def cancel(jobId: J) = {
    val request = Http(jobs + "/" + jobId).option(HttpOptions.method("DELETE")).withToken.initialise
    request.asStringChecked
  }

  def purge(job: J) = {}
}

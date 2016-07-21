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

import java.io._
import java.net.{ URI, URL }
import java.util.UUID
import java.util.concurrent.{ Executors, ThreadFactory, TimeUnit }

import com.google.common.cache
import fr.iscpif.gridscale.cache._
import fr.iscpif.gridscale.http.{ HTTPSAuthentication, HTTPSClient, HTTPStorage }
import fr.iscpif.gridscale.jobservice._
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.http.client.methods._
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.{ HttpHost, HttpRequest }
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.duration._
import scala.io.Source
import scala.sys.process.BasicIO
import scala.util.Try

object DIRACJobService {

  implicit def format = DefaultFormats

  def directoryURL = "http://dirac.france-grilles.fr/defaults/DiracServices.json"

  def apply[A: HTTPSAuthentication](
    vo: String,
    service: Option[Service] = None,
    timeout: Duration = 1 minutes)(authentication: A) = {
    val s = service.getOrElse(getService(vo, timeout))

    val (_timeout) = (timeout)
    new DIRACJobService {
      override val timeout = _timeout
      override val group: String = s.group
      override val factory = implicitly[HTTPSAuthentication[A]].factory(authentication)
      override val service: String = s.service
    }
  }

  case class Service(service: String, group: String)

  def getService(vo: String, timeout: Duration = 1 minute) =
    getServices(timeout).getOrElse(vo, throw new RuntimeException(s"Service not fond for the vo $vo in the DIRAC service directory"))

  def getServices(timeout: Duration) = {
    val uri: URI = new URI(directoryURL)
    val is = HTTPStorage.toInputStream(uri, HTTPStorage.newClient(timeout))
    try {
      val page = Source.fromInputStream(is).mkString
      parse(page).children.map {
        s ⇒
          val vo = (s \ "DIRACVOName").extract[String]
          val server = (s \ "RESTServer").extract[String]
          val group = (s \ "DIRACDefaultGroup").extract[String]

          vo -> Service("https://" + server, group)
      }.toMap
    } finally is.close
  }

  def supportedVOs(timeout: Duration = 1 minute) = getServices(timeout).keys

}

import DIRACJobService.format

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
  def delegation = service + s"/proxy/unknown/$group"

  def tokenExpirationMargin = 10 -> MINUTES

  @transient lazy val tokenCache =
    ValueCache[Token]((t: Token) ⇒ (t.expires_in, SECONDS) - tokenExpirationMargin) {
      () ⇒ token
    }

  def delegate(p12: File, password: String) = {
    val files = MultipartEntityBuilder.create()
    files.addBinaryBody("p12", p12)
    files.addTextBody("Password", password)
    files.addTextBody("access_token", tokenCache().token)

    val uri = new URIBuilder(delegation)
      .build

    val post = new HttpPost(uri)
    post.setEntity(files.build())

    request(post) { identity }
  }

  def token = {
    val uri = new URIBuilder(auth2Auth)
      .setParameter("grant_type", "client_credentials")
      .setParameter("group", group)
      .setParameter("setup", setup)
      .build()

    val get = new HttpGet(uri)

    request(get) { r ⇒
      val parsed = parse(r.trim)
      Token((parsed \ "token").extract[String], (parsed \ "expires_in").extract[Long])
    }
  }

  def submit(jobDescription: D): J = submit(jobDescription, None)

  def submit(jobDescription: D, group: Option[String]): J = {
    def files = {
      val builder = MultipartEntityBuilder.create()
      jobDescription.inputSandbox.foreach {
        f ⇒ builder.addBinaryBody(f.getName, f)
      }
      builder.addTextBody("access_token", tokenCache().token)
      builder.addTextBody("manifest", jobDescription.toJSON(group))
      builder.build
    }

    val uri = new URI(jobs)

    val post = new HttpPost(uri)
    post.setEntity(files)
    request(post) { r ⇒
      (parse(r) \ "jids")(0).extract[String]
    }
  }

  def state(jobId: J) = {
    val uri =
      new URIBuilder(jobs + "/" + jobId.id)
        .setParameter("access_token", tokenCache().token)
        .build

    val get = new HttpGet(uri)

    request(get) { r ⇒
      val s = (parse(r) \ "status").extract[String]
      translateState(s)
    }
  }

  def translateState(s: String) =
    s match {
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

  def downloadOutputSandbox(desc: D, jobId: J) = {
    val outputSandboxMap = desc.outputSandbox.toMap

    val uri =
      new URIBuilder(jobs + "/" + jobId.id + "/outputsandbox")
        .setParameter("access_token", tokenCache().token)
        .build

    val get = new HttpGet(uri)

    requestContent(get) { str ⇒
      val is = new TarArchiveInputStream(str)

      Iterator.continually(is.getNextEntry).takeWhile(_ != null).
        filter { e ⇒ outputSandboxMap.contains(e.getName) }.foreach {
          e ⇒
            val os = new BufferedOutputStream(new FileOutputStream(outputSandboxMap(e.getName)))
            try BasicIO.transferFully(is, os)
            finally os.close
        }
    }

  }

  def delete(job: J) = Try {
    val uri =
      new URIBuilder(jobs + "/" + job.id)
        .setParameter("access_token", tokenCache().token)
        .build

    val delete = new HttpDelete(uri)
    request(delete) { identity }: Unit
  }

  def httpHost: HttpHost = {
    val uri = new URI(service)
    new HttpHost(uri.getHost, uri.getPort, uri.getScheme)
  }

  def requestContent[T](request: HttpRequestBase with HttpRequest)(f: InputStream ⇒ T): T = withClient { httpClient ⇒
    request.setConfig(HTTPStorage.requestConfig(timeout))
    val response = httpClient.execute(httpHost, request)
    HTTPStorage.testResponse(response).get
    val is = response.getEntity.getContent
    try f(is)
    finally is.close
  }

  def request[T](request: HttpRequestBase with HttpRequest)(f: String ⇒ T): T =
    requestContent(request) { is ⇒
      f(Source.fromInputStream(is).mkString)
    }
}

object DIRACGroupedJobService {
  case class Job(id: DIRACJobService#J, submissionTime: Long, group: String)
  case class Updatable[T](var value: T) {
    def update(f: T ⇒ T) = synchronized {
      value = f(value)
      value
    }
  }

  def apply[A: HTTPSAuthentication](
    vo: String,
    service: Option[DIRACJobService.Service] = None,
    statusQueryInterval: Duration = 1 minute,
    jobsByGroup: Int = 10000,
    timeout: Duration = 1 minutes)(authentication: A) = {

    val (_statusQueryInterval, _jobsByGroup) = (statusQueryInterval, jobsByGroup)
    new DIRACGroupedJobService {
      override val jobService = DIRACJobService(vo, service, timeout)(authentication)
      override val statusQueryInterval: Duration = _statusQueryInterval
      override def jobsByGroup: Int = _jobsByGroup
    }
  }
}

trait DIRACGroupedJobService extends JobService {
  import DIRACGroupedJobService._
  import com.google.common.cache.{ CacheBuilder, CacheLoader }

  type J = Job
  type D = DIRACJobDescription

  def jobService: DIRACJobService
  def jobsByGroup: Int

  def statusQueryInterval: Duration

  def newGroup = UUID.randomUUID().toString.filter(_ != '-')
  @transient lazy val jobsServiceJobGroup = Updatable((newGroup, 0))

  @transient private lazy val statusesCache = {
    CacheBuilder.newBuilder().
      expireAfterWrite(statusQueryInterval.toMillis, TimeUnit.MILLISECONDS).
      build(
        CacheLoader.asyncReloading(
          new CacheLoader[String, (Long, Map[DIRACJobService#J, JobState])] {
            def load(group: String) = {
              val time = System.currentTimeMillis
              (time, queryGroupStatus(group).toMap)
            }
          }, fr.iscpif.gridscale.tools.defaultExecutor
        )
      )
  }

  def submit(d: D): J = {
    val (group, _) =
      jobsServiceJobGroup.update {
        case (group, jobs) ⇒
          if (jobs >= jobsByGroup) (newGroup, 1) else (group, jobs + 1)
      }

    val jid = jobService.submit(d, Some(group))
    val time = System.currentTimeMillis
    Job(jid, time, group)
  }

  def queryGroupStatus(groupId: String): Seq[(String, JobState)] = {
    val uri =
      new URIBuilder(jobService.jobs)
        .setParameter("access_token", jobService.tokenCache().token)
        .setParameter("jobGroup", groupId)
        .setParameter("startJob", 0.toString)
        .setParameter("maxJobs", Int.MaxValue.toString)
        .build

    val get = new HttpGet(uri)

    jobService.requestContent(get) { is ⇒
      (parse(is) \ "jobs").children.foldLeft(List[(String, JobState)]()) {
        (acc, j) ⇒
          val status = (j \ "status").extract[String]

          status match {
            case "Killed" | "Deleted" ⇒ acc
            case _                    ⇒ (j \ "jid").extract[String] -> jobService.translateState(status) :: acc
          }
      }
    }
  }

  def state(j: J): JobState = {
    val (cacheTime, c) = statusesCache.get(j.group)
    c.get(j.id) match {
      case None if cacheTime <= j.submissionTime ⇒ Submitted
      case None                                  ⇒ Failed
      case Some(s)                               ⇒ s
    }
  }

  def delegate(p12: File, password: String) = jobService.delegate(p12, password)
  def downloadOutputSandbox(desc: D, jobId: J) = jobService.downloadOutputSandbox(desc, jobId.id)
  override def delete(job: Job): Try[Unit] = jobService.delete(job.id)

}

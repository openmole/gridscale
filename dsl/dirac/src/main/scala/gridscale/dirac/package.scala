package gridscale

import java.net.URI

import freedsl.errorhandler._
import freedsl.filesystem._
import gridscale.authentication.P12Authentication
import gridscale.http.{ HTTP, HTTPS, HTTPSServer }
import org.apache.http.client.utils.URIBuilder
import cats._
import cats.implicits._
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.json4s._
import org.json4s.jackson.JsonMethods._
import squants._
import squants.time.TimeConversions._

package object dirac {

  implicit def format = DefaultFormats

  object JobDescription {
    val Linux_x86_64_glibc_2_11 = "Linux_x86_64_glibc-2.11"
    val Linux_x86_64_glibc_2_12 = "Linux_x86_64_glibc-2.12"
    val Linux_x86_64_glibc_2_5 = "Linux_x86_64_glibc-2.5"

    val Linux_i686_glibc_2_34 = "Linux_i686_glibc-2.3.4"
    val Linux_i686_glibc_2_5 = "Linux_i686_glibc-2.5"

    def toJSON(jobDescription: JobDescription, jobGroup: Option[String] = None) = {
      import jobDescription._

      def inputSandboxArray = JArray(inputSandbox.map(f ⇒ JString(f.getName)).toList)
      def outputSandboxArray = JArray(outputSandbox.map(f ⇒ JString(f._1)).toList)
      def platformsArray = JArray(platforms.map(f ⇒ JString(f)).toList)

      val fields = List(
        "Executable" -> JString(executable),
        "Arguments" -> JString(arguments)) ++
        stdOut.map(s ⇒ "StdOutput" -> JString(s)) ++
        stdErr.map(s ⇒ "StdError" -> JString(s)) ++
        (if (!inputSandbox.isEmpty) Some("InputSandbox" -> inputSandboxArray) else None) ++
        (if (!outputSandbox.isEmpty) Some("OutputSandbox" -> outputSandboxArray) else None) ++
        cpuTime.map(s ⇒ "CPUTime" -> JString(s.toSeconds.toString)) ++
        (if (!platforms.isEmpty) Some("Platform" -> platformsArray) else None) ++
        jobGroup.map(s ⇒ "JobGroup" -> JString(s))

      pretty(JObject(fields: _*))
    }

  }

  case class JobDescription(
    executable: String,
    arguments: String,
    stdOut: Option[String] = None,
    stdErr: Option[String] = None,
    inputSandbox: Seq[java.io.File] = List.empty,
    outputSandbox: Seq[(String, java.io.File)] = List.empty,
    platforms: Seq[String] = Seq.empty,
    cpuTime: Option[Time] = None)

  case class Token(token: String, expires_in: Long)
  case class DIRACServer(server: HTTPSServer, service: Service)
  case class Service(service: String, group: String)

  def getService[M[_]: HTTP: Monad: ErrorHandler](vo: String, timeout: Time = 1 minutes) = for {
    services ← getServices(timeout)
    service ← ErrorHandler[M].get(util.Try { services.getOrElse(vo, throw new RuntimeException(s"Service not fond for the vo $vo in the DIRAC service directory")) })
  } yield service

  def getServices[M[_]: HTTP: Monad](
    timeout: Time = 1 minutes,
    directoryURL: String = "http://dirac.france-grilles.fr/defaults/DiracServices.json") = {
    val indexServer = http.HTTPServer(directoryURL)

    def getService(json: String) = {
      parse(json).children.map {
        s ⇒
          val vo = (s \ "DIRACVOName").extract[String]
          val server = (s \ "RESTServer").extract[String]
          val group = (s \ "DIRACDefaultGroup").extract[String]

          vo -> Service("https://" + server, group)
      }.toMap
    }

    http.read[M](indexServer, "").map(getService)
  }

  def supportedVOs[M[_]: HTTP: Monad](timeout: Time = 1 minutes) =
    getServices[M](timeout).map(_.keys)

  def server[M[_]: Monad: HTTP: FileSystem: ErrorHandler](service: Service, p12: P12Authentication, certificateDirectory: java.io.File) =
    for {
      userCertificate ← HTTPS.readP12[M](p12.certificate, p12.password).flatMap(r ⇒ ErrorHandler[M].get(r))
      certificates ← HTTPS.readPEMCertificates[M](certificateDirectory)
      factory ← ErrorHandler[M].get(HTTPS.socketFactory(certificates ++ Vector(userCertificate), p12.password))
      server = HTTPSServer(service.service, factory)
    } yield DIRACServer(server, service)

  def token[M[_]: HTTP: Monad](server: DIRACServer, setup: String = "Dirac-Production") = {
    def auth2Auth = "/oauth2/token"

    val uri = new URIBuilder(auth2Auth)
      .setParameter("grant_type", "client_credentials")
      .setParameter("group", server.service.group)
      .setParameter("setup", setup)
      .build()

    gridscale.http.read[M](server.server, auth2Auth + "?" + uri.getQuery).map { r ⇒
      val parsed = parse(r.trim)
      Token((parsed \ "token").extract[String], (parsed \ "expires_in").extract[Long])
    }
  }

  def submit[M[_]: Monad: HTTP](server: DIRACServer, jobDescription: JobDescription, token: Token, jobGroup: Option[String]) = {
    def files() = {
      val builder = MultipartEntityBuilder.create()
      jobDescription.inputSandbox.foreach {
        f ⇒ builder.addBinaryBody(f.getName, f)
      }
      builder.addTextBody("access_token", token.token)
      builder.addTextBody("manifest", JobDescription.toJSON(jobDescription, jobGroup))
      builder.build
    }

    def jobs = "/jobs"

    gridscale.http.read[M](server.server, jobs, HTTP.Post(files)).map { r ⇒
      (parse(r) \ "jids")(0).extract[String]
    }
  }

  def translateState(s: String) =
    s match {
      case "Received"  ⇒ JobState.Submitted
      case "Checking"  ⇒ JobState.Submitted
      case "Staging"   ⇒ JobState.Submitted
      case "Waiting"   ⇒ JobState.Submitted
      case "Matched"   ⇒ JobState.Submitted
      case "Running"   ⇒ JobState.Running
      case "Completed" ⇒ JobState.Running
      case "Stalled"   ⇒ JobState.Running
      case "Killed"    ⇒ JobState.Failed
      case "Deleted"   ⇒ JobState.Failed
      case "Done"      ⇒ JobState.Done
      case "Failed"    ⇒ JobState.Failed
    }

}
package gridscale

import freedsl.errorhandler._
import freedsl.filesystem._
import gridscale.authentication.P12Authentication
import gridscale.http._
import org.apache.http.client.utils.URIBuilder
import cats._
import cats.implicits._
import freedsl.system.SystemInterpreter
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.json4s._
import org.json4s.jackson.JsonMethods._
import squants._
import squants.time.TimeConversions._

import scala.language.{ higherKinds, postfixOps }

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
        jobGroup.map(s ⇒ "JobGroup" -> JString(s)) ++
        cores.map(c ⇒ "Tags" -> JArray(List(JString(s"""${c}Processors"""))))

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
    cpuTime: Option[Time] = None,
    cores: Option[Int] = None)

  case class Token(token: String, lifetime: Time)
  case class DIRACServer(server: HTTPSServer, service: Service)
  case class Service(service: String, group: String)
  case class JobID(id: String, description: JobDescription)
  type GroupId = String

  import enumeratum._
  sealed trait DIRACState extends EnumEntry

  object DIRACState extends Enum[DIRACState] {
    val values = findValues

    case object Received extends DIRACState
    case object Checking extends DIRACState
    case object Staging extends DIRACState
    case object Waiting extends DIRACState
    case object Matched extends DIRACState
    case object Running extends DIRACState
    case object Completed extends DIRACState
    case object Stalled extends DIRACState
    case object Killed extends DIRACState
    case object Deleted extends DIRACState
    case object Done extends DIRACState
    case object Failed extends DIRACState
  }

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

    val uri = new URIBuilder()
      .setParameter("grant_type", "client_credentials")
      .setParameter("group", server.service.group)
      .setParameter("setup", setup)
      .build()

    gridscale.http.read[M](server.server, auth2Auth + "?" + uri.getQuery).map { r ⇒
      val parsed = parse(r.trim)
      Token((parsed \ "token").extract[String], (parsed \ "expires_in").extract[Long] seconds)
    }
  }

  def jobsLocation = "/jobs"

  def submit[M[_]: Monad: HTTP](server: DIRACServer, jobDescription: JobDescription, token: Token, jobGroup: Option[String] = None): M[JobID] = {
    def files() = {
      val builder = MultipartEntityBuilder.create()
      jobDescription.inputSandbox.foreach {
        f ⇒ builder.addBinaryBody(f.getName, f)
      }
      builder.addTextBody("access_token", token.token)
      builder.addTextBody("manifest", JobDescription.toJSON(jobDescription, jobGroup))
      builder.build
    }

    gridscale.http.read[M](server.server, jobsLocation, Post(files)).map { r ⇒
      val id = (parse(r) \ "jids")(0).extract[String]
      JobID(id, jobDescription)
    }
  }

  def state[M[_]: Monad: HTTP](server: DIRACServer, token: Token, jobId: JobID): M[JobState] = {
    val uri =
      new URIBuilder()
        .setParameter("access_token", token.token)
        .build

    gridscale.http.read[M](server.server, s"$jobsLocation/${jobId.id}?${uri.getQuery}").map { r ⇒
      val s = (parse(r) \ "status").extract[String]
      translateState(s)
    }
  }

  def translateState(s: String): JobState =
    DIRACState.withName(s) match {
      case DIRACState.Received  ⇒ JobState.Submitted
      case DIRACState.Checking  ⇒ JobState.Submitted
      case DIRACState.Staging   ⇒ JobState.Submitted
      case DIRACState.Waiting   ⇒ JobState.Submitted
      case DIRACState.Matched   ⇒ JobState.Submitted
      case DIRACState.Running   ⇒ JobState.Running
      case DIRACState.Completed ⇒ JobState.Running
      case DIRACState.Stalled   ⇒ JobState.Running
      case DIRACState.Killed    ⇒ JobState.Failed
      case DIRACState.Deleted   ⇒ JobState.Failed
      case DIRACState.Done      ⇒ JobState.Done
      case DIRACState.Failed    ⇒ JobState.Failed
    }

  def queryGroupState[M[_]: Monad: HTTP](
    server: DIRACServer,
    token: Token,
    groupId: GroupId,
    states: Seq[DIRACState] = DIRACState.values.filter(s ⇒ s != DIRACState.Killed && s != DIRACState.Deleted)): M[Vector[(String, JobState)]] = {

    val uri =
      new URIBuilder(server.server.url)
        .setParameter("access_token", token.token)
        .setParameter("jobGroup", groupId)
        .setParameter("startJob", 0.toString)
        .setParameter("maxJobs", Int.MaxValue.toString)
        .build

    def statusesQuery = states.map(s ⇒ s"status=${s.entryName}").mkString("&")

    gridscale.http.readStream[M, JValue](server.server, s"$jobsLocation?${uri.getQuery}&$statusesQuery", is ⇒ parse(is)).map { json ⇒
      (json \ "jobs").children.map { j ⇒
        val status = (j \ "status").extract[String]
        (j \ "jid").extract[String] -> translateState(status)
      }.toVector
    }
  }

  def delete[M[_]: Monad: HTTP](server: DIRACServer, token: Token, jobId: JobID) = {
    val uri =
      new URIBuilder()
        .setParameter("access_token", token.token)
        .build

    gridscale.http.read[M](server.server, s"$jobsLocation/${jobId.id}?${uri.getQuery}", Delete()).map { _ ⇒ () }
  }

  def delegate[M[_]: Monad: HTTP](server: DIRACServer, p12: P12Authentication, token: Token): M[Unit] = {
    def entity() = {
      val entity = MultipartEntityBuilder.create()
      entity.addBinaryBody("p12", p12.certificate)
      entity.addTextBody("Password", p12.password)
      entity.addTextBody("access_token", token.token)
      entity.build()
    }

    def delegation = s"/proxy/unknown/${server.service.group}"

    gridscale.http.read[M](server.server, delegation, Post(entity)).map(_ ⇒ ())
  }

  object DIRACInterpreter {

    class Interpreters {
      implicit val fileSystemInterpreter = FileSystemInterpreter()
      implicit val errorHandlerInterpreter = ErrorHandlerInterpreter()
      implicit val systemInterpreter = SystemInterpreter()
      implicit val httpInterpreter = HTTPInterpreter()
    }

    def apply[T](f: Interpreters ⇒ T) = {
      val interpreters = new Interpreters()
      f(interpreters)
    }

  }

}
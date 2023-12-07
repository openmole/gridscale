package gridscale

import java.io.{BufferedOutputStream, FileOutputStream}
import gridscale.effectaside.*
import gridscale.authentication.P12Authentication
import gridscale.dirac.DIRACState
import gridscale.http.*
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.utils.IOUtils
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.json4s.*
import org.json4s.jackson.JsonMethods.*
import squants.*
import squants.time.TimeConversions.*

import scala.language.{higherKinds, postfixOps}
import scala.reflect.ClassTag

package object dirac {

  import tools.ManifestCompat._

  implicit def format: DefaultFormats = DefaultFormats

  object JobDescription {
    val Linux_x86_64_glibc_2_11 = "Linux_x86_64_glibc-2.11"
    val Linux_x86_64_glibc_2_12 = "Linux_x86_64_glibc-2.12"
    val Linux_x86_64_glibc_2_5 = "Linux_x86_64_glibc-2.5"

    val Linux_i686_glibc_2_34 = "Linux_i686_glibc-2.3.4"
    val Linux_i686_glibc_2_5 = "Linux_i686_glibc-2.5"

    def toJSON(jobDescription: JobDescription, jobGroup: Option[String] = None) = {
      import jobDescription._

      def inputSandboxArray = JArray(inputSandbox.map(f ⇒ JString(f.getName)).toList)
      def outputSandboxArray = JArray(outputSandbox.map(f ⇒ JString(f)).toList)
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
        cores.map(c ⇒ "NumberOfProcessors" -> JString(c.toString)) ++
        site.map(s => "Site" -> JString(s))

      pretty(JObject(fields: _*))
    }

  }

  case class JobDescription(
    executable: String,
    arguments: String,
    stdOut: Option[String] = None,
    stdErr: Option[String] = None,
    inputSandbox: Seq[java.io.File] = List.empty,
    outputSandbox: Seq[String] = List.empty,
    platforms: Seq[String] = Seq.empty,
    cpuTime: Option[Time] = None,
    cores: Option[Int] = None,
    site: Option[String] = None)

  case class Token(token: String, lifetime: Time)
  case class DIRACServer(server: HTTPSServer, service: Service)
  case class Service(service: String, group: String)
  case class JobID(id: String, description: Option[JobDescription])
  type GroupId = String
  type UserId = String

  object DIRACState:
    lazy val stateIndex = DIRACState.values.map(v ⇒ name(v).toLowerCase -> v).toMap

    def withName(s: String) = stateIndex.getOrElse(s.toLowerCase, throw new RuntimeException(s"Unmatched state $s"))

    def name(s: DIRACState) =
      s match
        case Received   ⇒ "Received"
        case Checking   ⇒ "Checking"
        case Staging    ⇒ "Staging"
        case Waiting    ⇒ "Waiting"
        case Matched    ⇒ "Matched"
        case Running    ⇒ "Running"
        case Completing => "Completing"
        case Completed  ⇒ "Completed"
        case Stalled    ⇒ "Stalled"
        case Killed     ⇒ "Killed"
        case Deleted    ⇒ "Deleted"
        case Done       ⇒ "Done"
        case Failed     ⇒ "Failed"

  enum DIRACState:
    case Received, Checking, Staging, Waiting, Matched, Running, Completing, Completed, Stalled, Killed, Deleted, Done, Failed

  def getService(vo: String, certificatesDirectory: java.io.File, timeout: Time = 1 minutes)(implicit http: Effect[HTTP], fileSystem: Effect[FileSystem]) = {
    val services = getServices(certificatesDirectory, timeout)
    services.getOrElse(vo, throw new RuntimeException(s"Service not fond for the vo $vo in the DIRAC service directory"))
  }

  def getServices(
    certificatesDirectory: java.io.File,
    timeout: Time = 1 minutes,
    directoryURL: String = "https://dirac.france-grilles.fr/defaults/DiracServices.json")(implicit http: Effect[HTTP], fileSystem: Effect[FileSystem]) = {

    def getService(json: String) =
      parse(json).children.map {
        s ⇒
          val vo = (s \ "DIRACVOName").extract[String]
          val server = (s \ "RESTServer").extract[String]
          val group = (s \ "DIRACDefaultGroup").extract[String]

          vo -> Service("https://" + server, group)
      }.toMap

    val certificates = HTTPS.readPEMCertificates(certificatesDirectory)
    val factory = HTTPS.socketFactory(certificates, "", verifyHostName = false)
    val indexServer = HTTPSServer(directoryURL, factory, timeout = timeout)
    getService(read(indexServer, ""))
  }

  def supportedVOs(certificatesDirectory: java.io.File, timeout: Time = 1 minutes)(implicit http: Effect[HTTP], fileSystem: Effect[FileSystem]) =
    getServices(certificatesDirectory, timeout).keys

  def server(service: Service, p12: P12Authentication, certificateDirectory: java.io.File)(implicit fileSystem: Effect[FileSystem], http: Effect[HTTP]) = {
    val userCertificate = HTTPS.readP12(p12.certificate, p12.password)
    val certificates = HTTPS.readPEMCertificates(certificateDirectory)
    val factory = HTTPS.socketFactory(certificates ++ Vector(userCertificate), p12.password)
    val server = HTTPSServer(service.service, factory)
    DIRACServer(server, service)
  }

  def token(server: DIRACServer, setup: String = "Dirac-Production")(implicit http: Effect[HTTP]) = {
    def auth2Auth = "/oauth2/token"

    val uri = new URIBuilder()
      .setParameter("grant_type", "client_credentials")
      .setParameter("group", server.service.group)
      .setParameter("setup", setup)
      .build()

    val r = gridscale.http.read(server.server, auth2Auth + "?" + uri.getQuery)
    val parsed = parse(r.trim)
    Token((parsed \ "token").extract[String], (parsed \ "expires_in").extract[Long] seconds)
  }

  def jobsLocation = "/jobs"

  def submit(server: DIRACServer, jobDescription: JobDescription, token: Token, jobGroup: Option[String] = None)(implicit http: Effect[HTTP]): JobID = {
    def files() = {
      val builder = MultipartEntityBuilder.create()
      jobDescription.inputSandbox.foreach {
        f ⇒ builder.addBinaryBody(f.getName, f)
      }
      builder.addTextBody("access_token", token.token)
      builder.addTextBody("manifest", JobDescription.toJSON(jobDescription, jobGroup))
      builder.build
    }

    val r = gridscale.http.read(server.server, jobsLocation, Post(files))
    val id = (parse(r) \ "jids")(0).extract[String]
    JobID(id, Some(jobDescription))
  }

  def state(server: DIRACServer, token: Token, jobId: JobID)(implicit http: Effect[HTTP]): JobState = {
    val uri =
      new URIBuilder()
        .setParameter("access_token", token.token)
        .build

    val r = gridscale.http.read(server.server, s"$jobsLocation/${jobId.id}?${uri.getQuery}")
    val s = (parse(r) \ "status").extract[String]
    translateState(s)
  }

  def translateState(s: String): JobState =
    DIRACState.withName(s) match
      case DIRACState.Received   ⇒ JobState.Submitted
      case DIRACState.Checking   ⇒ JobState.Submitted
      case DIRACState.Staging    ⇒ JobState.Submitted
      case DIRACState.Waiting    ⇒ JobState.Submitted
      case DIRACState.Matched    ⇒ JobState.Submitted
      case DIRACState.Running    ⇒ JobState.Running
      case DIRACState.Completing => JobState.Running
      case DIRACState.Completed  ⇒ JobState.Running
      case DIRACState.Stalled    ⇒ JobState.Running
      case DIRACState.Killed     ⇒ JobState.Failed
      case DIRACState.Deleted    ⇒ JobState.Failed
      case DIRACState.Done       ⇒ JobState.Done
      case DIRACState.Failed     ⇒ JobState.Failed


  def queryState(
    server: DIRACServer,
    token: Token,
    groupId: Option[GroupId] = None,
    userId: Option[UserId] = None,
    states: Seq[DIRACState] = DIRACState.values.filter(s ⇒ s != DIRACState.Killed && s != DIRACState.Deleted))(implicit hTTP: Effect[HTTP]): Vector[(String, JobState)] = {

    val uri = {
      val uri = new URIBuilder(server.server.url)
        .setParameter("access_token", token.token)
        .setParameter("startJob", 0.toString)
        .setParameter("maxJobs", Int.MaxValue.toString)

      groupId.foreach(groupId ⇒ uri.setParameter("jobGroup", groupId))
      userId.foreach(userId ⇒ uri.setParameter("owner", userId))
      uri.build
    }

    def statusesQuery =
      if (states.isEmpty) "" else "&" + states.map(s ⇒ s"status=${DIRACState.name(s)}").mkString("&")

    val json = gridscale.http.readStream[JValue](server.server, s"$jobsLocation?${uri.getQuery}${statusesQuery}", is ⇒ parse(is))
    (json \ "jobs").children.map { j ⇒
      val status = (j \ "status").extract[String]
      (j \ "jid").extract[String] -> translateState(status)
    }.toVector
  }

  def delete(server: DIRACServer, token: Token, jobId: JobID)(implicit http: Effect[HTTP]) = {
    val uri =
      new URIBuilder()
        .setParameter("access_token", token.token)
        .build

    gridscale.http.read(server.server, s"$jobsLocation/${jobId.id}?${uri.getQuery}", Delete()).map { _ ⇒ () }
  }

  def downloadOutputSandbox(server: DIRACServer, token: Token, jobId: JobID, outputDirectory: Path)(implicit http: Effect[HTTP], fileSystem: Effect[FileSystem]) = {
    val uri =
      new URIBuilder()
        .setParameter("access_token", token.token)
        .build

    def extract(str: java.io.InputStream, outputDirectory: java.io.File) = {
      outputDirectory.mkdirs()

      val is = new TarArchiveInputStream(new BZip2CompressorInputStream(str))

      try Iterator.continually(is.getNextEntry).takeWhile(_ != null).foreach {
        e ⇒
          fileSystem().writeStream(new java.io.File(outputDirectory, e.getName)) { os ⇒
            IOUtils.copy(is, os)
          }
      }
      finally is.close
    }

    gridscale.http.readStream(server.server, s"$jobsLocation/${jobId.id}/outputsandbox?${uri.getQuery}", is ⇒ extract(is, outputDirectory.path))
  }

  def delegate(server: DIRACServer, p12: P12Authentication, token: Token)(implicit http: Effect[HTTP]): Unit = {
    def entity() = {
      val entity = MultipartEntityBuilder.create()
      entity.addBinaryBody("p12", p12.certificate)
      entity.addTextBody("Password", p12.password)
      entity.addTextBody("access_token", token.token)
      entity.build()
    }

    def delegation = s"/proxy/unknown/${server.service.group}"

    gridscale.http.read(server.server, delegation, Post(entity))
  }

  object DIRAC:

    class Interpreters:
      implicit val fileSystemInterpreter: Effect[FileSystem] = FileSystem()
      implicit val systemInterpreter: Effect[System] = System()
      implicit val httpInterpreter: Effect[HTTP] = HTTP()

    def apply[T](f: Interpreters ⇒ T) = 
      val interpreters = new Interpreters()
      f(interpreters)
  

}
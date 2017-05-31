package gridscale

import java.net.URI

import freedsl.errorhandler._
import freedsl.filesystem._
import gridscale.authentication.P12Authentication
import gridscale.http.{ HTTP, HTTPS, HTTPSServer }
import org.apache.http.client.utils.URIBuilder
import cats._
import cats.implicits._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import squants._
import squants.time.TimeConversions._

package object dirac {

  implicit def format = DefaultFormats

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

  case class Token(token: String, expires_in: Long)
  case class DIRACServer(server: HTTPSServer, service: Service)

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

}
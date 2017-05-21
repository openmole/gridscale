package gridscale.egi

import java.io.{ ByteArrayInputStream, File }

import cats.Monad

//import eu.emi.security.authn.x509.impl.CertificateUtils
//import eu.emi.security.authn.x509.impl.CertificateUtils.Encoding
import freedsl.dsl._
import freedsl.errorhandler._
import freedsl.filesystem._
import gridscale.http._
import gridscale.webdav._
import freedsl.tool._
import cats.syntax._
import cats.implicits._
import cats.instances.all._

object TestVOMS extends App {

  val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim
  val p12 = P12Authentication(new java.io.File("/home/reuillon/.globus/certificate.p12"), password)
  val certificateDirectory = new java.io.File("/home/reuillon/.openmole/simplet/persistent/CACertificates/")
  val bdii = BDII.Server("topbdii.grif.fr", 2170)

  val intp = merge(HTTP.interpreter, FileSystem.interpreter, ErrorHandler.interpreter, BDII.interpreter)
  import intp.implicits._

  val prg =
    for {
      proxy ← VOMS.proxy[intp.M]("voms.hellasgrid.gr:15160", p12, certificateDirectory)
      factory ← VOMS.socketFactory[intp.M](proxy)
      lal ← BDII[intp.M].webDAVs(bdii, "vo.complex-systems.eu").map(_.find(_.contains("lal")).get)
      webdav = HTTPSServer(lal, factory)
      c ← listProperties[intp.M](webdav, "/")
      _ ← exists(webdav, "youpi2.txt").ifM(rmFile[intp.M](webdav, "youpi2.txt"), noop[intp.M])
      _ ← writeStream[intp.M](webdav, "youpi2.txt", () ⇒ new ByteArrayInputStream("youpi doky\n".getBytes))
    } yield c

  println(intp.run(prg).toTry.get)

}
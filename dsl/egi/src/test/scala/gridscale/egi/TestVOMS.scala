package gridscale.egi

import freedsl.dsl._
import freedsl.errorhandler._
import freedsl.filesystem._
import gridscale.http._
import gridscale.webdav._

object TestVOMS extends App {

  val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim
  val p12 = P12Authentication(new java.io.File("/home/reuillon/.globus/certificate.p12"), password)
  val certificateDirectory = new java.io.File("/home/reuillon/.openmole/simplet/CACertificates/")

  val intp = merge(HTTP.interpreter, FileSystem.interpreter, ErrorHandler.interpreter)
  import intp.implicits._

  val prg =
    for {
      proxy ← VOMS.proxy[intp.M]("voms.hellasgrid.gr:15160", p12, certificateDirectory)
      factory ← VOMS.sockerFactory(proxy)
      webdav = HTTPSServer(
        "https://grid05.lal.in2p3.fr/dpm/lal.in2p3.fr/home/vo.complex-systems.eu/",
        factory
      )
      c ← listProperties[intp.M](webdav, "/")
    } yield c

  println(intp.run(prg).toTry.get)

}
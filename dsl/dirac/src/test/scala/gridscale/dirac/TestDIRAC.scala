package gridscale.dirac

import freedsl.dsl.merge
import freedsl.errorhandler._
import freedsl.filesystem._
import gridscale.authentication._
import gridscale.http._

object TestDIRAC extends App {

  val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim
  val p12 = P12Authentication(new java.io.File("/home/reuillon/.globus/certificate.p12"), password)
  val certificateDirectory = new java.io.File("/home/reuillon/.openmole/simplet/persistent/CACertificates/")

  val intp = merge(HTTP.interpreter, FileSystem.interpreter, ErrorHandler.interpreter)
  import intp.implicits._

  val prg =
    for {
      service ← getService[intp.M]("vo.complex-systems.eu")
      s ← server[intp.M](service, p12, certificateDirectory)
      t ← token(s)
    } yield t

  println(intp.run(prg))

}

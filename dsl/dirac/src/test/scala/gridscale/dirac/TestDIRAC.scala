package gridscale.dirac

import freedsl.dsl.merge
import freedsl.errorhandler._
import freedsl.filesystem._
import freedsl.system._
import gridscale.authentication._
import gridscale.http._
import gridscale._

object TestDIRAC extends App {

  val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim
  val p12 = P12Authentication(new java.io.File("/home/reuillon/.globus/certificate.p12"), password)
  val certificateDirectory = new java.io.File("/home/reuillon/.openmole/simplet/persistent/CACertificates/")

  val intp = merge(HTTP.interpreter, FileSystem.interpreter, ErrorHandler.interpreter, System.interpreter)
  import intp.implicits._
  import intp._

  val description = JobDescription("/bin/echo", "hello")

  val prg =
    for {
      service ← getService[M]("vo.complex-systems.eu")
      s ← server[M](service, p12, certificateDirectory)
      t ← token(s)
      j ← submit[M](s, description, t, None)
      st ← waitUntilEnded[M](state[M](s, t, j))
      //st ← state[M](s, t, j)
    } yield st

  println(intp.run(prg))

}

package gridscale.dirac

import cats._
import cats.implicits._
import freedsl.dsl._
import freedsl.errorhandler._
import freedsl.filesystem._
import freedsl.system._
import gridscale._
import gridscale.authentication._
import gridscale.http._

object TestDIRAC extends App {

  val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim
  val p12 = P12Authentication(new java.io.File("/home/reuillon/.globus/certificate.p12"), password)
  val certificateDirectory = new java.io.File("/home/reuillon/.openmole/simplet/persistent/CACertificates/")

  val description = JobDescription("/bin/uname", "-a", stdOut = Some("output"), outputSandbox = Seq("output" -> new java.io.File("/tmp/output")))

  def prg[M[_]: Monad: HTTP: ErrorHandler: FileSystem: System] =
    for {
      service ← getService[M]("vo.complex-systems.eu")
      s ← server[M](service, p12, certificateDirectory)
      t ← token(s)
      j ← submit[M](s, description, t) //.repeat(10)
      //gs ← queryGroupState[M](s, t, "testgroup")
      st ← waitUntilEnded[M](state[M](s, t, j))
      //_ <- delete[M](s, t, j)
      //_ ← j.traverse(delete[M](s, t, _))
    } yield j

  DIRACInterpreter { interpreters ⇒
    import interpreters._
    println(prg[DSL].eval)
  }

}

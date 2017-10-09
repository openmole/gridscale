package gridscale.dirac

import cats._
import freedsl.dsl._
import freedsl.errorhandler._
import freedsl.filesystem._
import freedsl.system._
import gridscale.authentication._
import gridscale.http._
import gridscale._
import freedsl.tool._
import cats.implicits._

object TestDIRAC extends App {

  val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim
  val p12 = P12Authentication(new java.io.File("/home/reuillon/.globus/certificate.p12"), password)
  val certificateDirectory = new java.io.File("/home/reuillon/.openmole/simplet/persistent/CACertificates/")

  val description = JobDescription("/bin/echo", "hello")

  def prg[M[_]: Monad: HTTP: ErrorHandler: FileSystem] =
    for {
      service ← getService[M]("vo.complex-systems.eu")
      s ← server[M](service, p12, certificateDirectory)
      t ← token(s)
      j ← submit[M](s, description, t, Some("testgroup")).repeat(10)
      gs ← queryGroupState[M](s, t, "testgroup")
      _ ← j.traverse(delete[M](s, t, _))
      //st ← waitUntilEnded[M](state[M](s, t, j))
    } yield gs

  DIRACInterpreter { interpreters ⇒
    import interpreters._
    println(prg[DSL].eval)
  }

}

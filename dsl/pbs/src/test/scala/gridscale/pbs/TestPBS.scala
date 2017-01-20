package gridscale.pbs

import freedsl.system._
import freedsl.io._
import gridscale._
import gridscale.authentication._
import gridscale.ssh._
import freedsl.dsl._
import freek._

object TestPBS extends App {

  val localhost = Server("localhost", 10022)
  val authentication = UserPassword("testuser", "testuser")

  val context = merge(SSH, System, IO)

  import context.M
  import context.implicits._

  val jobDescription = PBSJobDescription("echo hello", "/tmp/")

  val res =
    for {
      job ← submit[M](jobDescription)
      s ← waitUntilEnded[M](state[M](job))
      out ← stdOut[M](job)
      _ ← clean[M](job)
    } yield s

  val interpreter = merge(SSH.interpreter(localhost, authentication), System.interpreter, IO.interpreter)
  println(interpreter.run(res))

}

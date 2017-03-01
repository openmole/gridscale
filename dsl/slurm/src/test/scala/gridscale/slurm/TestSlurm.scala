package gridscale.slurm

import freedsl.system._
import freedsl.io._
import gridscale._
import gridscale.authentication._
import gridscale.ssh._
import freedsl.dsl._

object TestSlurm extends App {

  val predict5 = Server("localhost", 22)
  val authentication = PrivateKey(new java.io.File("/home/jopasserat/.ssh/id_rsa"), "", "jopasserat")

  val context = merge(SSH, System, IO)

  import scala.language.reflectiveCalls
  import context.M
  import context.implicits._

  val jobDescription = SlurmJobDescription(executable = "/bin/echo", arguments = "hello", workDirectory = "/home/jopasserat/test_gridscale", queue = Some("short"))

  val res =
    for {
      job ← submit[M](jobDescription)
      s ← waitUntilEnded[M](state[M](job))
      out ← stdOut[M](job)
      _ ← clean[M](job)
    } yield s

  val interpreter = merge(SSH.interpreter(predict5, authentication), System.interpreter, IO.interpreter)
  println(interpreter.run(res))

}

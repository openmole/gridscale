package gridscale.slurm

import freedsl.system._
import freedsl.errorhandler._
import gridscale._
import gridscale.authentication._
import gridscale.ssh._
import freedsl.dsl._

object TestSlurm extends App {

  val authentication = PrivateKey(new java.io.File("/home/jopasserat/.ssh/id_rsa"), "", "jopasserat")
  val headNode = SSHServer("localhost", 22)(authentication)

  val context = merge(SSH, System, ErrorHandler)

  import scala.language.reflectiveCalls
  import context.M
  import context.implicits._

  val jobDescription = SlurmJobDescription(executable = "/bin/echo", arguments = "hello", workDirectory = "/home/jopasserat/test_gridscale", queue = Some("short"))

  val res = for {
    job ← submit[M, SSHServer](headNode, jobDescription)
    s ← waitUntilEnded[M](state[M, SSHServer](headNode, job))
    out ← stdOut[M, SSHServer](headNode, job)
    _ ← clean[M, SSHServer](headNode, job)
  } yield (s, out)

  val interpreter = merge(SSH.interpreter, System.interpreter, ErrorHandler.interpreter)
  println(interpreter.run(res))

}

package gridscale.pbs

import freedsl.system._
import freedsl.io._
import gridscale._
import gridscale.authentication._
import gridscale.ssh._
import freedsl.dsl._

object TestPBS extends App {

  val authentication = UserPassword("testuser", "testuser")
  val localhost = SSHServer("localhost", 10022)(authentication)

  val context = merge(SSH, System, IO)

  import context.M
  import context.implicits._

  val jobDescription = PBSJobDescription("echo hello", "/tmp/")

  val res =
    for {
      job ← submit[M, SSHServer](localhost, jobDescription)
      s ← waitUntilEnded[M](state[M, SSHServer](localhost, job))
      out ← stdOut[M, SSHServer](localhost, job)
      _ ← clean[M, SSHServer](localhost, job)
    } yield s

  val interpreter = merge(SSH.interpreter, System.interpreter, IO.interpreter)
  println(interpreter.run(res))

}

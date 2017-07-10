package gridscale.ssh

object TestSSH extends App {

  import gridscale._
  import gridscale.authentication._
  import freedsl.system._
  import freedsl.dsl._

  val c = merge(SSH, System)
  import c.implicits._

  def job = SSHJobDescription(command = s"""echo -n Hello SSH World""", workDirectory = "/tmp/")

  val localhost = SSHServer("localhost", port = 2222)(UserPassword("root", "root"))

  val prg =
    for {
      jobId ← submit[c.M](localhost, job)
      _ ← waitUntilEnded(state[c.M](localhost, jobId))
      out ← stdOut[c.M](localhost, jobId)
      _ ← clean[c.M](localhost, jobId)
    } yield s"""Job  stdout is "$out"."""

  val interpreter = merge(SSH.interpreter, System.interpreter)
  println(interpreter.run(prg))
  println(interpreter.run(prg))

}

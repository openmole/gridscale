package gridscale.ssh

object TestSSH extends App {

  import gridscale._
  import gridscale.authentication._
  import freedsl.system._
  import freedsl.dsl._

  val c = merge(SSH, System)
  import c.implicits._

  def job = SSHJobDescription(command = s"""echo -n Hello SSH World""", workDirectory = "/tmp/")

  val prg =
    for {
      jobId ← submit[c.M](job)
      _ ← waitUntilEnded(state[c.M](jobId))
      out ← stdOut[c.M](jobId)
      _ ← clean[c.M](jobId)
    } yield s"""Job  stdout is "$out"."""

  val localhost = Server("localhost")
  val authentication = UserPassword("test", "test!")

  val interpreter = merge(SSH.interpreter(localhost, authentication), System.interpreter)
  println(interpreter.run(prg))

}

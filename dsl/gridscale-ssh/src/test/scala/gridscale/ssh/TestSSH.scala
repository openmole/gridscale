package gridscale.ssh

object TestSSH extends App {

  import gridscale._
  import gridscale.authentication._
  import freedsl.system._
  import freek._

  val c = freedsl.dsl.merge(SSH, System)
  import c._
  import c.implicits._

  def job = SSHJobDescription(command = s"""echo -n Hello SSH World""", workDirectory = "/tmp/")

  val prg =
    for {
      jobId ← submit[M](job)
      _ ← waitUntilEnded(state[M](jobId))
      out ← stdOut[M](jobId)
      _ ← clean[M](jobId)
    } yield s"""Job  stdout is "$out"."""

  val localhost = Server("localhost")
  val authentication = UserPassword("test", "test!")
  val sshClient = SSH.client(localhost, authentication)
  val interpreter = SSH.interpreter(sshClient) :&: System.interpreter

  println(result(prg, interpreter))
  sshClient.foreach(_.close())
}

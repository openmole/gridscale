package fr.iscpif.gridscale.ssh

object TestSSH extends App {

  import fr.iscpif.gridscale.authentication._
  import fr.iscpif.gridscale._
  import freek._
  import freedsl.util._

  val c = freedsl.dsl.merge(SSH, Util)
  import c._
  import c.implicits._

  def job = SSHJobDescription(command = s"sleep 30", workDirectory = "/tmp/")

  val prg =
    for {
      jobId ← submit[M](job)
      _ ← waitUntilEnded(state[M](jobId))
      out ← stdOut[M](jobId)
      _ ← clean[M](jobId)
    } yield s"""Job  stdout is "$out"."""

  val localhost = Server("localhost")
  val authentication = UserPassword("test", "test")
  val sshClient = SSH.client(localhost, authentication)
  val interpreter = SSH.interpreter(sshClient) :&: Util.interpreter

  println(result(prg, interpreter))
  sshClient.foreach(_.close())
}

package gridscale.ssh

object TestSSH extends App {

  import gridscale._
  import gridscale.authentication._
  import freedsl.system._
  import cats._
  import cats.implicits._

  def job = SSHJobDescription(command = s"""echo -n Hello SSH World""", workDirectory = "/tmp/")

  val localhost = SSHServer("localhost", port = 2222)(UserPassword("root", "root"))

  def prg[M[_]: Monad: SSH: System] =
    for {
      jobId ← submit[M](localhost, job)
      _ ← waitUntilEnded(state[M](localhost, jobId))
      out ← stdOut[M](localhost, jobId)
      _ ← clean[M](localhost, jobId)
    } yield s"""Job  stdout is "$out"."""

  implicit val systemInterpreter = new SystemInterpreter

  SSHInterpreter { implicit sshInterpreter ⇒
    println(prg[util.Try])
    println(prg[util.Try])
  }

}

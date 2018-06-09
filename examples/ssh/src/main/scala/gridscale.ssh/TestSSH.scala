package gridscale.ssh

import squants.time.TimeConversions._
import scala.language.postfixOps

object TestSSH extends App {

  import gridscale._
  import gridscale.authentication._
  import effectaside._

  def job = SSHJobDescription(command = s"""echo -n greatings `whoami`""", workDirectory = "/tmp/")

  val localhost = SSHServer("localhost", port = 2222)(UserPassword("root", "root"))

  def prg(implicit system: Effect[System], ssh: Effect[SSH]) = {
    val jobId = submit(localhost, job)
    waitUntilEnded(() ⇒ state(localhost, jobId))
    val out = stdOut(localhost, jobId)
    clean(localhost, jobId)
    out
  }

  implicit val system = System()
  implicit val ssh = SSH()

  try println(prg)
  finally ssh().close()
}

package gridscale.ssh

import gridscale.ssh.sshj.SSHClient
import squants.time.TimeConversions._

import java.io.File
import scala.language.postfixOps

object TestSSH extends App {

  import gridscale._
  import gridscale.authentication._
  import gridscale.effectaside._

  def job = SSHJobDescription(command = s"""echo -n greatings `whoami`""", workDirectory = "/tmp/")

  //val localhost = SSHServer("localhost", port = 2222)(UserPassword("root", "root"))
  val localhost = SSHServer("localhost", 2222, 1 minutes, Some(10 seconds), "localhost", 2222)(UserPassword("root", "root"))

  def prg(implicit system: Effect[System], ssh: Effect[SSH]): String = {
    val jobId = submit(localhost, job)
    waitUntilEnded(() ⇒ state(localhost, jobId))
    val out = stdOut(localhost, jobId)
    clean(localhost, jobId)
    out
  }


  implicit val system: Effect[System] = System()
  implicit val ssh: Effect[SSH] = SSH()

  try println(prg)
  finally ssh().close()

}

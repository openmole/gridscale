package gridscale.ssh

import gridscale.ssh.sshj.SSHClient
import squants.time.TimeConversions._

import java.io.File
import scala.language.postfixOps

object TestSSH extends App {

  import gridscale._
  import gridscale.authentication._

  def job = SSHJobDescription(command = s"""echo -n greatings `whoami`""", workDirectory = "/tmp/")

  //val localhost = SSHServer("localhost", port = 2222)(UserPassword("root", "root"))
  val proxyServer = SSHServer("localhost", 2222)(UserPassword("root", "root"))
  val localhost = SSHServer("localhost", 2222, 1 minutes, Some(10 seconds), sshProxy = Some(proxyServer))(UserPassword("root", "root"))

  def prg(using SSH): String =
    val jobId = submit(localhost, job)
    waitUntilEnded(() ⇒ state(localhost, jobId))
    val out = stdOut(localhost, jobId)
    clean(localhost, jobId)
    out

  SSH.withSSH:
    println(prg)

}

package gridscale.ssh

import gridscale.ssh.sshj.SSHClient
import squants.time.TimeConversions._

import java.io.File
import scala.language.postfixOps

object TestSSH extends App {

  import gridscale._
  import gridscale.authentication._
  import gridscale.effectaside._

  //def job = SSHJobDescription(command = s"""echo -n greatings `whoami`""", workDirectory = "/tmp/")
  //def job = SSHJobDescription(command = s"""echo -n greatings `whoami`; ls""", workDirectory = "/home/ubuntu")
  def job = SSHJobDescription(command = s"""ls""", workDirectory = "/home/ubuntu")

  //val localhost = SSHServer("localhost", port = 2222)(UserPassword("root", "root"))
  //val host = SSHServer("localhost", port = 22)(UserPassword("root", "root"))
  val host = SSHServer("134.158.74.194", port = 22)(PrivateKey(privateKey = new File(java.lang.System.getenv("HOME")+"/.ssh/id_ed25519"), password = "", user = "ubuntu"))


  def prg(implicit system: Effect[System], ssh: Effect[SSH]) = {
    val jobId = submit(host, job)
    println(jobId)
    waitUntilEnded(() ⇒ state(host, jobId))
    val out = stdOut(host, jobId)
    clean(host, jobId)
    out
  }


  implicit val system: Effect[System] = System()
  implicit val ssh: Effect[SSH] = SSH()

  //println(ssh().launch(host, "ls /home"))
  val jump = SSH.client(host)

  try println(prg)
  finally ssh().close()
  //ssh().close()
}

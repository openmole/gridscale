package gridscale.ssh

import gridscale.ssh.sshj.SSHClient
import squants.time.TimeConversions._

import java.io.File
import scala.language.postfixOps

object TestSSH extends App:

  import gridscale._
  import gridscale.authentication._

//  def job = SSHJobDescription(command = s"""echo -n greatings `whoami`""", workDirectory = "/tmp/")
//
//  //val localhost = SSHServer("localhost", port = 2222)(UserPassword("root", "root"))
//  val proxyServer = SSHServer("localhost", 2222)(UserPassword("root", "root"))
//  //val localhost = SSHServer("localhost", 22, 1 minutes, Some(10 seconds), sshProxy = Some(proxyServer))(UserPassword("root", "root"))
//
//  SSH.withSSH(proxyServer):
//    val jobId = submit(job)
//
//    waitUntilEnded: () =>
//      state(jobId)
//
//    val out = stdOut(jobId)
//    clean(jobId)
//    println(out)


package gridscale.condor

import gridscale.*
import gridscale.ssh
import gridscale.authentication.*
import gridscale.cluster.SSHHeadNode
import squants.information.InformationConversions.*

import scala.language.postfixOps

object CondorExample extends App {

  val authentication = PrivateKey(new java.io.File("/home/jopasserat/.ssh/id_rsa"), "", "jopasserat")
  val server = ssh.SSHServer("myhost.co.uk")(authentication)
  val jobDescription = CondorJobDescription(executable = "/bin/echo", arguments = "hello from $(hostname)", workDirectory = "/home/foobar/test_gridscale", Some(2000 megabytes))

  def res(using ssh.SSH) =
    val job = submit(server, jobDescription)
    val s = waitUntilEnded(() ⇒ state(server, job))
    val out = stdOut(server, job)
    clean(server, job)
    (s, out)

  ssh.SSH.withSSH:
    println(res)

}

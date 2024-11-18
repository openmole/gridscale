package gridscale.condor

import gridscale.*
import gridscale.ssh
import gridscale.authentication.*
import gridscale.cluster.*
import squants.information.InformationConversions.*

import scala.language.postfixOps

object CondorExample extends App:

  val authentication = PrivateKey(new java.io.File("/home/jopasserat/.ssh/id_rsa"), "", "jopasserat")
  val server = ssh.SSHServer("myhost.co.uk")(authentication)
  val jobDescription = CondorJobDescription(executable = "/bin/echo", arguments = "hello from $(hostname)", workDirectory = "/home/foobar/test_gridscale", Some(2000 megabytes))

  ssh.SSH.withSSH(server):
    val job = submit(HeadNode.ssh, jobDescription)
    val s = waitUntilEnded(() ⇒ state(HeadNode.ssh, job))
    val out = stdOut(HeadNode.ssh, job)
    clean(HeadNode.ssh, job)
    (s, out)


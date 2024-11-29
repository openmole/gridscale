package gridscale.slurm

import gridscale.*
import gridscale.cluster.*
import gridscale.authentication.*
import gridscale.ssh
import squants.information.InformationConversions.*
import squants.time.TimeConversions.*

import scala.language.postfixOps

object SlurmExample extends App:

  //val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim

  //val authentication = PrivateKey(new java.io.File("/home/reuillon/.ssh/id_rsa"), password, "rreuil01")
  //val headNode = ssh.SSHServer("myria.criann.fr", 22)(authentication)
  //val headNode = ssh.SSHServer("134.158.75.71")(authentication)
  // proxyjump, same auth
  //val headNode = ssh.SSHServer("134.158.75.71", 22, 1 minutes, Some(10 seconds), proxyJumpHost = "134.158.74.194", proxyJumpPort = 22)(authentication)
  // proxyjump, different auth
  val proxyAuth = PrivateKey(new java.io.File("/home/JRaimbault/.ssh/id_rsa_MBP"), "", "ubuntu")
  val sshProxy = ssh.SSHServer("134.158.74.194")(proxyAuth)

  val authentication = PrivateKey(new java.io.File("/home/JRaimbault/.ssh/id_ed25519"), "", "ubuntu")
  val headNode = ssh.SSHServer("134.158.75.71", 22, 1 minutes, Some(10 seconds), sshProxy = Some(sshProxy))(authentication)

  val jobDescription = SLURMJobDescription(command = """/bin/echo hello from $(hostname)""",
    workDirectory = "/home/ubuntu", partition = Some("main"), memory = Some(500 megabytes))

  ssh.SSH.withSSH(headNode):
    val job = submit(HeadNode.ssh, jobDescription)
    val s = waitUntilEnded { () ⇒ state(HeadNode.ssh, job) }
    val out = stdOut(HeadNode.ssh, job)
    clean(HeadNode.ssh, job)
    (s, out)


package gridscale.slurm

import gridscale.*
import gridscale.cluster.*
import gridscale.authentication.*
import gridscale.ssh
import squants.information.InformationConversions.*
import squants.time.TimeConversions.*
import java.io.*

import scala.language.postfixOps

object SlurmExample extends App:

  //val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim

  //val authentication = PrivateKey(new java.io.File("/home/reuillon/.ssh/id_rsa"), password, "rreuil01")
  //val headNode = ssh.SSHServer("myria.criann.fr", 22)(authentication)
  //val headNode = ssh.SSHServer("134.158.75.71")(authentication)
  // proxyjump, same auth
  //val headNode = ssh.SSHServer("134.158.75.71", 22, 1 minutes, Some(10 seconds), proxyJumpHost = "134.158.74.194", proxyJumpPort = 22)(authentication)
  // proxyjump, different auth


  val key = PrivateKey(File("/home/reuillon/.openmole/simplet/user/keys/2v53KdRHa3/phileas_key"), "", "romain.reuillon.7@cnrs.fr", Some(File("/home/reuillon/.ssh/phileas_key-cert.pub")))
  val bastion = ssh.SSHServer("bastion.phileas.ec-nantes.fr")(key)
  val server = ssh.SSHServer("phileas-devel-001.nodes.intra.phileas.ec-nantes.fr", sshProxy = Some(bastion))(key)


  println(key)

  ssh.SSH.withSSH(bastion):
    println(summon[ssh.SSH].execute("pwd"))

//  val proxyAuth = PrivateKey(new java.io.File("/home/JRaimbault/.ssh/id_rsa_MBP"), "", "ubuntu")
//  val sshProxy = ssh.SSHServer("134.158.74.194")(proxyAuth)
//
//  val authentication = PrivateKey(new java.io.File("/home/JRaimbault/.ssh/id_ed25519"), "", "ubuntu")
//  val headNode = ssh.SSHServer("134.158.75.71", 22, 1 minutes, Some(10 seconds), sshProxy = Some(sshProxy))(authentication)

//  val jobDescription = SLURMJobDescription(command = """/bin/echo hello from $(hostname)""", workDirectory = s"/scratch/nautilus/phileas/home/romain.reuillon.7@cnrs.fr/", memory = Some(500 megabytes), account = Some("m25235"))
//
//  ssh.SSH.withSSH(server):
//    val job = submit(HeadNode.ssh, jobDescription)
//    val s =
//      waitUntilEnded: () ⇒
//        val s = state(HeadNode.ssh, job)
//        println(s)
//        s
//
//    val out = stdOut(HeadNode.ssh, job)
//    clean(HeadNode.ssh, job)
//    println(out)
//    (s, out)


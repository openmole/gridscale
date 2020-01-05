package gridscale.slurm

import effectaside._
import gridscale._
import gridscale.authentication._
import gridscale.cluster.SSHCluster
import gridscale.ssh
import squants.information.InformationConversions._

import scala.language.postfixOps

object SlurmExample extends App {

  val password = scala.io.Source.fromFile("/home/reuillon/.globus/password").getLines().next().trim

  val authentication = PrivateKey(new java.io.File("/home/reuillon/.ssh/id_rsa"), password, "rreuil01")
  val headNode = ssh.SSHServer("myria.criann.fr", 22)(authentication)

  val jobDescription = SLURMJobDescription(command = """/bin/echo hello from $(hostname)""", workDirectory = "/home/2019902/rreuil01", queue = Some("debug"), memory = Some(2000 megabytes))

  def res(implicit system: Effect[System], sshEffect: Effect[ssh.SSH]) = {
    val job = submit(headNode, jobDescription)
    val s = waitUntilEnded { () ⇒ state(headNode, job) }
    val out = stdOut(headNode, job)
    clean(headNode, job)
    (s, out)
  }

  SSHCluster { intp ⇒
    import intp._
    println(res)
  }

}

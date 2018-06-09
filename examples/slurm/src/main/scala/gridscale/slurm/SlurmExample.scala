package gridscale.slurm

import effectaside._
import gridscale._
import gridscale.authentication._
import gridscale.cluster.SSHCluster
import gridscale.ssh._
import squants.information.InformationConversions._

import scala.language.postfixOps

object SlurmExample extends App {

  val authentication = PrivateKey(new java.io.File("/home/jopasserat/.ssh/id_rsa"), "", "jopasserat")
  val headNode = SSHServer("localhost", 22)(authentication)

  val jobDescription = SLURMJobDescription(command = """/bin/echo hello from $(hostname)""", workDirectory = "/home/foobar/test_gridscale", queue = Some("short"), memory = Some(2000 megabytes))

  def res(implicit system: Effect[System], ssh: Effect[SSH]) = {
    val job = submit(headNode, jobDescription)
    val s = waitUntilEnded(() ⇒ state(headNode, job))
    val out = stdOut(headNode, job)
    clean(headNode, job)
    (s, out)
  }

  SSHCluster { intp ⇒
    import intp._
    println(res)
  }

}

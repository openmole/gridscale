package gridscale.condor

import effectaside._
import gridscale._
import gridscale.ssh._
import gridscale.authentication._
import gridscale.cluster.SSHCluster

import squants.information.InformationConversions._
import scala.language.postfixOps

object CondorExample extends App {

  val authentication = PrivateKey(new java.io.File("/home/jopasserat/.ssh/id_rsa"), "", "jopasserat")
  val headNode = ssh.SSHServer("myhost.co.uk")(authentication)

  val jobDescription = CondorJobDescription(executable = "/bin/echo", arguments = "hello from $(hostname)", workDirectory = "/home/foobar/test_gridscale", Some(2000 megabytes))

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

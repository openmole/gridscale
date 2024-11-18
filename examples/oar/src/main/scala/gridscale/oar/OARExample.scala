package gridscale.oar

import gridscale._
import gridscale.authentication._
import gridscale.cluster._
import gridscale.ssh
import squants.time.TimeConversions._

import scala.language.postfixOps

object OARExample extends App {

  val authentication = UserPassword("docker", "docker")
  val localhost = ssh.SSHServer("172.17.0.3", 22)(authentication)

  val jobDescription = OARJobDescription("""echo "hello world from $(hostname)"""", "/data/test_gridscale", wallTime = Some(10 minutes))

  def res(using ssh.SSH) =
    val job = submit(HeadNode.ssh, jobDescription)
    val s = waitUntilEnded(() ⇒ state(HeadNode.ssh, job))
    val out = stdOut(HeadNode.ssh, job)
    clean(HeadNode.ssh, job)
    (s, out)

  ssh.SSH.withSSH(localhost):
    println(res)

}

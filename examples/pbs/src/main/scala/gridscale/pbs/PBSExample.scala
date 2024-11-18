package gridscale.pbs

import gridscale._
import gridscale.authentication._
import gridscale.ssh
import gridscale.cluster._
import squants.time.TimeConversions._
import squants.information.InformationConversions._

import scala.language.{ higherKinds, postfixOps }

object PBSExample extends App {

  val authentication = UserPassword("testuser", "testuser")
  val localhost = ssh.SSHServer("localhost", 10022)(authentication)

  // by default flavour = Torque, there's no need to specify it
  val jobDescription = PBSJobDescription("""echo "hello world from $(hostname)"""", "/work/foobar/test_gridscale", wallTime = Some(10 minutes), memory = Some(2 gigabytes), flavour = PBSPro)

  def res(using ssh.SSH) = 
    val job = submit(HeadNode.ssh, jobDescription)
    val s = waitUntilEnded(() ⇒ state(HeadNode.ssh, job))
    val out = stdOut(HeadNode.ssh, job)
    clean(HeadNode.ssh, job)
    (s, out)

  ssh.SSH.withSSH(localhost):
    println(res)

}

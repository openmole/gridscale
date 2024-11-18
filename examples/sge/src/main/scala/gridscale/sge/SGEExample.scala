package gridscale.sge

import gridscale._
import gridscale.authentication._
import gridscale.cluster._
import gridscale.ssh
import squants.time.TimeConversions._

object SGEExample extends App:

  val authentication = UserPassword("sgeuser", "sgeuser")
  val localhost = ssh.SSHServer("172.17.0.7", 22)(authentication)

  val jobDescription = SGEJobDescription("""echo "hello world from $(hostname)"""", "/tmp/test_gridscale", wallTime = Some(10 minutes))

  ssh.SSH.withSSH(localhost):
    val job = submit(HeadNode.ssh, jobDescription)
    val s = waitUntilEnded(() ⇒ state(HeadNode.ssh, job))
    val out = stdOut(HeadNode.ssh, job)
    clean(HeadNode.ssh, job)
    println(s"$s $out")


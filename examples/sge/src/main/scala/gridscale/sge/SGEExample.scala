package gridscale.sge

import gridscale.effectaside._
import gridscale._
import gridscale.authentication._
import gridscale.cluster._
import gridscale.ssh
import squants.time.TimeConversions._

object SGEExample extends App {

  val authentication = UserPassword("sgeuser", "sgeuser")
  val localhost = ssh.SSHServer("172.17.0.7", 22)(authentication)

  val jobDescription = SGEJobDescription("""echo "hello world from $(hostname)"""", "/tmp/test_gridscale", wallTime = Some(10 minutes))

  def res(implicit system: Effect[System], sshEffect: Effect[ssh.SSH]) = {
    val job = submit(localhost, jobDescription)
    val s = waitUntilEnded(() ⇒ state(localhost, job))
    val out = stdOut(localhost, job)
    clean[ssh.SSHServer](localhost, job)
    (s, out)
  }

  SSHCluster { intp ⇒
    import intp._
    println(res)
  }

}

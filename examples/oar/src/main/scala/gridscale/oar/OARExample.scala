package gridscale.oar

import effectaside._
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

  def res(implicit system: Effect[System], sshEffect: Effect[ssh.SSH]) = {
    val job = submit(localhost, jobDescription)
    val s = waitUntilEnded(() ⇒ state(localhost, job))
    val out = stdOut(localhost, job)
    clean(localhost, job)
    (s, out)
  }

  SSHCluster { intp ⇒
    import intp._
    println(res)
  }

}

package gridscale.pbs

import effectaside._
import gridscale._
import gridscale.authentication._
import gridscale.ssh._
import gridscale.cluster._
import squants.time.TimeConversions._
import squants.information.InformationConversions._

import scala.language.{ higherKinds, postfixOps }

object PBSExample extends App {

  val authentication = UserPassword("testuser", "testuser")
  val localhost = SSHServer("localhost", 10022)(authentication)

  // by default flavour = Torque, there's no need to specify it
  val jobDescription = PBSJobDescription("""echo "hello world from $(hostname)"""", "/work/foobar/test_gridscale", wallTime = Some(10 minutes), memory = Some(2 gigabytes), flavour = PBSPro)

  def res(implicit system: Effect[System], ssh: Effect[SSH]) = {
    val job = submit(localhost, jobDescription)
    val s = waitUntilEnded(() ⇒ state(localhost, job))
    val out = stdOut(localhost, job)
    clean[SSHServer](localhost, job)
    (s, out)
  }

  SSHCluster { intp ⇒
    import intp._
    println(res)
  }

}

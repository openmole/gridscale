package gridscale.slurm

import effectaside._
import gridscale._
import gridscale.authentication._
import gridscale.cluster.ClusterInterpreter
import gridscale.ssh._

object SlurmExample extends App {

  val authentication = PrivateKey(new java.io.File("/home/jopasserat/.ssh/id_rsa"), "", "jopasserat")
  val headNode = SSHServer("localhost", 22)(authentication)

  import scala.language.reflectiveCalls
  import gridscale.slurm._

  val jobDescription = SLURMJobDescription(command = """/bin/echo hello from $(hostname)""", workDirectory = "/homes/jpassera/test_gridscale", queue = Some("short"))

  def res(implicit system: Effect[System], ssh: Effect[SSH]) = {
    val job = submit(headNode, jobDescription)
    val s = waitUntilEnded(() ⇒ state(headNode, job))
    val out = stdOut(headNode, job)
    clean(headNode, job)
    (s, out)
  }

  ClusterInterpreter { intp ⇒
    import intp._
    println(res)
  }

}

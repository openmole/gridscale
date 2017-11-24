package gridscale.slurm

import effectaside._
import gridscale._
import gridscale.cluster._
import gridscale.local._

object SlurmExampleLocal extends App {

  val headNode = LocalHost()

  val jobDescription = SLURMJobDescription(command = "/bin/echo hello from $(hostname)", workDirectory = "/homes/jpassera/test_gridscale", queue = Some("short"))

  def res(implicit system: Effect[System], ssh: Effect[Local]) = {
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

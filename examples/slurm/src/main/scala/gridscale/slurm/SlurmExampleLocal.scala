package gridscale.slurm

import gridscale._
import gridscale.cluster._
import gridscale.local._

object SlurmExampleLocal extends App {

  val headNode = LocalHeadNode()
  val jobDescription = SLURMJobDescription(command = "/bin/echo hello from $(hostname)", workDirectory = "/homes/jpassera/test_gridscale", partition = Some("short"))

  def res =
    val job = submit(headNode, jobDescription)
    val s = waitUntilEnded(() ⇒ state(headNode, job))
    val out = stdOut(headNode, job)
    clean(headNode, job)
    (s, out)

  println(res)

}

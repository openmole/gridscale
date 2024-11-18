package gridscale.slurm

import gridscale._
import gridscale.cluster._
import gridscale.local._

object SlurmExampleLocal extends App:

  val jobDescription = SLURMJobDescription(command = "/bin/echo hello from $(hostname)", workDirectory = "/homes/jpassera/test_gridscale", partition = Some("short"))

  def res =
    val job = submit(HeadNode.local, jobDescription)
    val s = waitUntilEnded(() ⇒ state(HeadNode.local, job))
    val out = stdOut(HeadNode.local, job)
    clean(HeadNode.local, job)
    (s, out)

  println(res)


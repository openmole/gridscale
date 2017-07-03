package gridscale.slurm

import freedsl.dsl._
import freedsl.errorhandler._
import freedsl.system._
import gridscale._
import gridscale.local._

object SlurmExampleLocal extends App {

  val context = merge(Local, System, ErrorHandler)

  import scala.language.reflectiveCalls
  import gridscale.slurm.slurmDSL._
  import context.M
  import context.implicits._

  val headNode = LocalHost()

  val jobDescription = SlurmJobDescription(executable = "/bin/echo", arguments = "hello from $(hostname)", workDirectory = "/homes/jpassera/test_gridscale", queue = Some("short"))

  val res = for {
    job ← submit[M, LocalHost](headNode, jobDescription)
    s ← waitUntilEnded[M](state[M, LocalHost](headNode, job))
    out ← stdOut[M, LocalHost](headNode, job)
    _ ← clean[M, LocalHost](headNode, job)
  } yield (s, out)

  val interpreter = merge(Local.interpreter, System.interpreter, ErrorHandler.interpreter)
  println(interpreter.run(res))

}

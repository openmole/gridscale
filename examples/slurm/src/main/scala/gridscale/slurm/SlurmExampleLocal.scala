package gridscale.slurm

import cats._
import cats.implicits._
import freedsl.errorhandler._
import freedsl.system._
import gridscale._
import gridscale.cluster.ClusterInterpreter
import gridscale.local._

object SlurmExampleLocal extends App {

  import gridscale.slurm._

  val headNode = LocalHost()

  val jobDescription = SlurmJobDescription(executable = "/bin/echo", arguments = "hello from $(hostname)", workDirectory = "/homes/jpassera/test_gridscale", queue = Some("short"))

  def res[M[_]: Local: System: ErrorHandler: Monad] = for {
    job ← submit[M, LocalHost](headNode, jobDescription)
    s ← waitUntilEnded[M](state[M, LocalHost](headNode, job))
    out ← stdOut[M, LocalHost](headNode, job)
    _ ← clean[M, LocalHost](headNode, job)
  } yield (s, out)

  ClusterInterpreter { intp ⇒
    import intp._
    println(res[util.Try])
  }

}

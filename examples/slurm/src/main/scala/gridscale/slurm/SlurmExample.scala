package gridscale.slurm

import cats._
import cats.implicits._
import freedsl.system._
import freedsl.errorhandler._
import gridscale._
import gridscale.authentication._
import gridscale.cluster.ClusterInterpreter
import gridscale.ssh._

object SlurmExample extends App {

  val authentication = PrivateKey(new java.io.File("/home/jopasserat/.ssh/id_rsa"), "", "jopasserat")
  val headNode = SSHServer("localhost", 22)(authentication)

  import scala.language.reflectiveCalls
  import gridscale.slurm._

  val jobDescription = SlurmJobDescription(executable = "/bin/echo", arguments = "hello from $(hostname)", workDirectory = "/homes/jpassera/test_gridscale", queue = Some("short"))

  def res[M[_]: SSH: System: ErrorHandler: Monad] = for {
    job ← submit[M, SSHServer](headNode, jobDescription)
    s ← waitUntilEnded[M](state[M, SSHServer](headNode, job))
    out ← stdOut[M, SSHServer](headNode, job)
    _ ← clean[M, SSHServer](headNode, job)
  } yield (s, out)

  ClusterInterpreter { intp ⇒
    import intp._
    println(res[util.Try])
  }

}

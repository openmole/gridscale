package gridscale.sge

import cats._
import cats.implicits._
import freedsl.dsl._
import freedsl.errorhandler._
import freedsl.system._
import gridscale._
import gridscale.authentication._
import gridscale.cluster.SSHClusterInterpreter
import gridscale.ssh._
import squants.time.TimeConversions._

import scala.language.postfixOps

object SGEExample extends App {

  val authentication = UserPassword("sgeuser", "sgeuser")
  val localhost = SSHServer("172.17.0.7", 22)(authentication)

  val jobDescription = SGEJobDescription("""echo "hello world from $(hostname)"""", "/tmp/test_gridscale", wallTime = Some(10 minutes))

  def res[M[_]: SSH: System: ErrorHandler: Monad] = for {
    job ← submit[M, SSHServer](localhost, jobDescription)
    s ← waitUntilEnded[M](state[M, SSHServer](localhost, job))
    out ← stdOut[M, SSHServer](localhost, job)
    _ ← clean[M, SSHServer](localhost, job)
  } yield (s, out)

  SSHClusterInterpreter { intp ⇒
    import intp._
    println(res[DSL].eval)
  }

}

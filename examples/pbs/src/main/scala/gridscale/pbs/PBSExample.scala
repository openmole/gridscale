package gridscale.pbs

import cats._
import cats.implicits._
import freedsl.system._
import freedsl.errorhandler._
import gridscale._
import gridscale.authentication._
import gridscale.ssh._
import gridscale.cluster.SSHClusterInterpreter
import squants.time.TimeConversions._

import scala.language.postfixOps

object PBSExample extends App {

  val authentication = UserPassword("testuser", "testuser")
  val localhost = SSHServer("localhost", 10022)(authentication)

  import gridscale.pbs._

  val jobDescription = PBSJobDescription("""echo "hello world from $(hostname)"""", "/work/jpassera/test_gridscale", wallTime = Some(10 minutes))

  def res[M[_]: SSH: System: ErrorHandler: Monad] = for {
    job ← submit[M, SSHServer](localhost, jobDescription)
    s ← waitUntilEnded[M](state[M, SSHServer](localhost, job))
    out ← stdOut[M, SSHServer](localhost, job)
    _ ← clean[M, SSHServer](localhost, job)
  } yield (s, out)

  SSHClusterInterpreter { intp ⇒
    import intp._
    println(res[util.Try])
  }

}

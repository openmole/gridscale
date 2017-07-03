package gridscale.pbs

import freedsl.system._
import freedsl.errorhandler._
import gridscale._
import gridscale.authentication._
import gridscale.ssh._
import freedsl.dsl._
import squants.time.TimeConversions._
import scala.language.postfixOps

object PBSExample extends App {

  val authentication = UserPassword("testuser", "testuser")
  val localhost = SSHServer("localhost", 10022)(authentication)

  val context = merge(SSH, System, ErrorHandler)

  import scala.language.reflectiveCalls
  import context.M
  import context.implicits._
  import gridscale.pbs.pbsDSL._

  val jobDescription = PBSJobDescription("""echo "hello world from $(hostname)"""", "/work/jpassera/test_gridscale", wallTime = Some(10 minutes))

  val res = for {
    job ← submit[M, SSHServer](localhost, jobDescription)
    s ← waitUntilEnded[M](state[M, SSHServer](localhost, job))
    out ← stdOut[M, SSHServer](localhost, job)
    _ ← clean[M, SSHServer](localhost, job)
  } yield (s, out)

  val interpreter = merge(SSH.interpreter, System.interpreter, ErrorHandler.interpreter)
  println(interpreter.run(res))

}

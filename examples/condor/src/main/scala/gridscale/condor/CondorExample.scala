package gridscale.condor

import cats._
import cats.implicits._
import freedsl.system._
import freedsl.errorhandler._
import gridscale._
import gridscale.ssh
import gridscale.authentication._
import gridscale.condor._
import gridscale.cluster.{ SSHClusterInterpreter }

object CondorExample extends App {

  val authentication = PrivateKey(new java.io.File("/home/jopasserat/.ssh/id_rsa"), "", "jopasserat")
  val headNode = ssh.SSHServer("myhost.co.uk")(authentication)

  import scala.language.reflectiveCalls
  import gridscale.condor._

  val jobDescription = CondorJobDescription(executable = "/bin/echo", arguments = "hello from $(hostname)", workDirectory = "/homes/jpassera/test_gridscale")

  def res[M[_]: System: ErrorHandler: ssh.SSH: Monad] = for {
    job ← submit[M, ssh.SSHServer](headNode, jobDescription)
    s ← waitUntilEnded[M](state[M, ssh.SSHServer](headNode, job))
    out ← stdOut[M, ssh.SSHServer](headNode, job)
    _ ← clean[M, ssh.SSHServer](headNode, job)
  } yield (s, out)

  SSHClusterInterpreter { intp ⇒
    import intp._
    println(res[util.Try])
  }

}

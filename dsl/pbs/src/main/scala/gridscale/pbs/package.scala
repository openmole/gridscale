package gridscale

import cats._
import cats.implicits._
import freedsl.system._
import freedsl.errorhandler._
import gridscale.cluster.HeadNode
import gridscale.tools._
import squants._
import scala.language.higherKinds

package object pbs {

  case class PBSJobDescription(
    command: String,
    workDirectory: String,
    queue: Option[String] = None,
    wallTime: Option[Time] = None,
    memory: Option[Int] = None,
    nodes: Option[Int] = None,
    coreByNode: Option[Int] = None)

  def toScript(description: PBSJobDescription, uniqId: String) = {
    import description._

    val buffer = new ScriptBuffer
    buffer += "#!/bin/bash"

    buffer += "#PBS -o " + output(uniqId)
    buffer += "#PBS -e " + error(uniqId)

    queue foreach { q ⇒ buffer += "#PBS -q " + q }
    memory foreach { m ⇒ buffer += "#PBS -lmem=" + m + "mb" }
    wallTime foreach { t ⇒ buffer += "#PBS -lwalltime=" + t.toHHmmss }

    nodes match {
      case Some(n) ⇒ buffer += "#PBS -lnodes=" + n + ":ppn=" + coreByNode.getOrElse(1)
      case None    ⇒ coreByNode foreach { c ⇒ buffer += "#PBS -lnodes=1:ppn=" + c }
    }

    buffer += "cd " + workDirectory

    buffer += command
    buffer.toString
  }

  case class PBSJob(uniquId: String, pbsId: String, workDirectory: String)

  def output(uniqId: String): String = uniqId + ".out"
  def error(uniqId: String): String = uniqId + ".err"

  def pbsScriptName(uniqId: String) = uniqId + ".pbs"
  def pbsScriptPath(workDirectory: String, uniqId: String) = workDirectory + "/" + pbsScriptName(uniqId)

  def translateStatus(retCode: Int, status: String) =
    status match {
      case "R" | "E" | "H" | "S" ⇒ Right(JobState.Running)
      case "Q" | "W" | "T"       ⇒ Right(JobState.Submitted)
      case _                     ⇒ Left(new RuntimeException("Unrecognized state " + status))
    }

  def submit[M[_]: Monad, S](server: S, description: PBSJobDescription)(implicit hn: HeadNode[S, M], system: System[M], errorHandler: ErrorHandler[M]) = for {
    _ ← hn.execute(server, s"mkdir -p ${description.workDirectory}")
    uniqId ← system.randomUUID.map(_.toString)
    script = toScript(description, uniqId)
    _ ← hn.write(server, script.getBytes, pbsScriptPath(description.workDirectory, uniqId))
    command = s"cd ${description.workDirectory} && qsub ${pbsScriptName(uniqId)}"
    cmdRet ← hn.execute(server, command)
    ExecutionResult(ret, out, error) = cmdRet
    _ ← if (ret != 0) errorHandler.errorMessage(ExecutionResult.error(command, cmdRet)) else ().pure[M]
    _ ← if (out == null) errorHandler.errorMessage("qsub did not return a JobID") else ().pure[M]
    pbsId = out.split("\n").head
  } yield PBSJob(uniqId, pbsId, description.workDirectory)

  def state[M[_]: Monad, S](server: S, job: PBSJob)(implicit hn: HeadNode[S, M], error: ErrorHandler[M]): M[JobState] = {
    val command = "qstat -f " + job.pbsId
    val jobStateAttribute = "JOB_STATE"

    def parseState(cmdRet: ExecutionResult): Either[RuntimeException, JobState] =
      cmdRet.returnCode match {
        case 153 ⇒ Right(JobState.Done)
        case 0 ⇒
          val lines = cmdRet.stdOut.split("\n").map(_.trim)
          val state = lines.filter(_.matches(".*=.*")).map {
            prop ⇒
              val splited = prop.split('=')
              splited(0).trim.toUpperCase -> splited(1).trim
          }.toMap.get(jobStateAttribute)

          state match {
            case Some(s) ⇒ translateStatus(cmdRet.returnCode, s)
            case None    ⇒ Left(new RuntimeException("State not found in qstat output: " + cmdRet.stdOut))
          }
        case _ ⇒ Left(new RuntimeException(ExecutionResult.error(command, cmdRet)))
      }

    for {
      cmdRet ← hn.execute(server, command)
      s ← error.get[JobState](parseState(cmdRet))
    } yield s
  }

  def stdOut[M[_], S](server: S, job: PBSJob)(implicit hn: HeadNode[S, M]) = hn.read(server, job.workDirectory + "/" + output(job.uniquId))
  def stdErr[M[_], S](server: S, job: PBSJob)(implicit hn: HeadNode[S, M]) = hn.read(server, job.workDirectory + "/" + error(job.uniquId))

  def clean[M[_]: Monad, S](server: S, job: PBSJob)(implicit hn: HeadNode[S, M]) = for {
    _ ← hn.execute(server, s"qdel ${job.pbsId}")
    _ ← hn.rm(server, pbsScriptPath(job.workDirectory, job.uniquId))
    _ ← hn.rm(server, job.workDirectory + "/" + output(job.uniquId))
    _ ← hn.rm(server, job.workDirectory + "/" + error(job.uniquId))
  } yield ()

}

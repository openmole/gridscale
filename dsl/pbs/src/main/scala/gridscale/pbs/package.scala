package gridscale

import cats._
import cats.implicits._
import freedsl.system._
import freedsl.io._
import gridscale.cluster.HeadNode
import gridscale.tools._
import squants._

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

  def translateStatus[M[_]](retCode: Int, status: String) =
    status match {
      case "R" | "E" | "H" | "S" ⇒ Right(JobState.Running)
      case "Q" | "W" | "T"       ⇒ Right(JobState.Submitted)
      case _                     ⇒ Left("Unrecognized state " + status)
    }

  def submit[M[_]: Monad](description: PBSJobDescription)(implicit hn: HeadNode[M], system: System[M], io: IO[M]) = for {
    _ ← hn.execute(s"mkdir -p ${description.workDirectory}")
    uniqId ← system.randomUUID.map(_.toString)
    script = toScript(description, uniqId)
    _ ← hn.write(script.getBytes, pbsScriptPath(description.workDirectory, uniqId))
    command = s"cd ${description.workDirectory} && qsub ${pbsScriptName(uniqId)}"
    cmdRet ← hn.execute(command)
    ExecutionResult(ret, out, error) = cmdRet
    _ ← if (ret != 0) io.errorMessage(ExecutionResult.error(command, cmdRet)) else ().pure[M]
    _ ← if (out == null) io.errorMessage("qsub did not return a JobID") else ().pure[M]
    pbsId = out.split("\n").head
  } yield PBSJob(uniqId, pbsId, description.workDirectory)

  def state[M[_]: Monad](job: PBSJob)(implicit hn: HeadNode[M], io: IO[M]): M[JobState] = {
    val command = "qstat -f " + job.pbsId
    val jobStateAttribute = "JOB_STATE"

    def parseState(cmdRet: ExecutionResult) =
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
            case None    ⇒ Left("State not found in qstat output: " + cmdRet.stdOut)
          }
        case r ⇒ Left(ExecutionResult.error(command, cmdRet))
      }

    for {
      cmdRet ← hn.execute(command)
      s ← io.errorMessageOrResult(parseState(cmdRet))
    } yield s
  }

  def stdOut[M[_]](job: PBSJob)(implicit hn: HeadNode[M]) = hn.read(job.workDirectory + "/" + output(job.uniquId))
  def stdErr[M[_]](job: PBSJob)(implicit hn: HeadNode[M]) = hn.read(job.workDirectory + "/" + error(job.uniquId))

  def clean[M[_]: Monad](job: PBSJob)(implicit hn: HeadNode[M]) = for {
    _ ← hn.execute(s"qdel ${job.pbsId}")
    _ ← hn.rm(pbsScriptPath(job.workDirectory, job.uniquId))
    _ ← hn.rm(job.workDirectory + "/" + output(job.uniquId))
    _ ← hn.rm(job.workDirectory + "/" + error(job.uniquId))
  } yield ()

}

package gridscale

import effectaside._
import gridscale.cluster.{ BatchScheduler, HeadNode }
import gridscale.tools._
import squants._
import monocle.macros._
import squants.information.Information

import scala.language.higherKinds

package object pbs {

  sealed trait PBSFlavour
  case object PBSPro extends PBSFlavour
  case object Torque extends PBSFlavour

  @Lenses case class PBSJobDescription(
    command: String,
    workDirectory: String,
    queue: Option[String] = None,
    wallTime: Option[Time] = None,
    memory: Option[Information] = None,
    nodes: Option[Int] = None,
    coreByNode: Option[Int] = None,
    flavour: PBSFlavour = Torque)

  object impl {

    def toScript(description: PBSJobDescription)(uniqId: String) = {
      import description._

      val buffer = new ScriptBuffer
      buffer += "#!/bin/bash"

      buffer += "#PBS -o " + BatchScheduler.output(uniqId)
      buffer += "#PBS -e " + BatchScheduler.error(uniqId)

      queue foreach { q ⇒ buffer += "#PBS -q " + q }

      wallTime foreach { t ⇒ buffer += "#PBS -lwalltime=" + t.toHHmmss }

      val nbNodes = nodes.getOrElse(1)
      val coresPerNode = coreByNode.getOrElse(1)

      val memoryString = memory map { m ⇒ s":mem=${m.toMBString}mb" } getOrElse ""

      flavour match {
        case Torque ⇒ buffer += s"#PBS -l nodes=$nbNodes:ppn=$coresPerNode$memoryString"
        case PBSPro ⇒ buffer += s"#PBS -l select=$nbNodes:ncpus=$coresPerNode$memoryString"
      }

      buffer += "cd " + workDirectory

      buffer += command
      buffer.toString
    }

    def retrieveJobID(out: String) = out.split("\n").head

    def translateStatus(retCode: Int, status: String) =
      status match {
        case "R" | "E" | "H" | "S" ⇒ JobState.Running
        case "Q" | "W" | "T"       ⇒ JobState.Submitted
        case "C"                   ⇒ JobState.Done
        case _                     ⇒ throw new RuntimeException("Unrecognized state " + status)
      }

    def parseState(cmdRet: ExecutionResult, command: String): JobState = {

      val jobStateAttribute = "JOB_STATE"

      cmdRet.returnCode match {
        case 153 ⇒ JobState.Done
        case 0 ⇒
          val lines = cmdRet.stdOut.split("\n").map(_.trim)
          val state = lines.filter(_.matches(".*=.*")).map {
            prop ⇒
              val splited = prop.split('=')
              splited(0).trim.toUpperCase -> splited(1).trim
          }.toMap.get(jobStateAttribute)

          state match {
            case Some(s) ⇒ translateStatus(cmdRet.returnCode, s)
            case None    ⇒ throw new RuntimeException("State not found in $command output: " + cmdRet.stdOut)
          }
        case _ ⇒ throw new RuntimeException(ExecutionResult.error(command, cmdRet))
      }

    }

    def pbsErrorWrapper(command: String, executionResult: ExecutionResult) =
      ExecutionResult.error("You might want to specify a different PBS flavour in your job description? flavour = PBSPro")(command, executionResult)
  }

  import impl._
  import BatchScheduler._

  val scriptSuffix = ".pbs"

  def submit[S](server: S, jobDescription: PBSJobDescription)(implicit hn: HeadNode[S], system: Effect[System]): BatchJob =
    BatchScheduler.submit[S](
      jobDescription.workDirectory,
      toScript(jobDescription),
      scriptSuffix,
      (f, _) ⇒ s"qsub $f",
      impl.retrieveJobID,
      server,
      impl.pbsErrorWrapper)

  def state[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): JobState =
    BatchScheduler.state[S](
      s"qstat -f ${job.jobId}",
      parseState)(server, job)

  def clean[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): Unit =
    BatchScheduler.clean[S](
      s"qdel ${job.jobId}",
      scriptSuffix)(server, job)

  def stdOut[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = BatchScheduler.stdOut[S](server, job)
  def stdErr[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = BatchScheduler.stdErr[S](server, job)

}

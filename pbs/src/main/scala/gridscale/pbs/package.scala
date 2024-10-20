package gridscale

import gridscale.cluster.{BatchScheduler, HeadNode, Requirement}
import gridscale.tools._
import squants._
import squants.information.Information

import scala.language.higherKinds

package object pbs {

  sealed trait PBSFlavour
  case object PBSPro extends PBSFlavour
  case object Torque extends PBSFlavour
  case class PBSJobDescription(
    command: String,
    workDirectory: String,
    queue: Option[String] = None,
    wallTime: Option[Time] = None,
    memory: Option[Information] = None,
    nodes: Option[Int] = None,
    coreByNode: Option[Int] = None,
    flavour: PBSFlavour = Torque)

  object impl {

    import Requirement._

    def memoryRequirements(separator: String)(memory: Option[Information]): String =
      memory map {
        m ⇒ s"${separator}mem=${m.toMBString}mb"
      } getOrElse ""

    def toScript(description: PBSJobDescription)(uniqId: String) =
      import description._

      val header = "#!/bin/bash\n"

      val nbNodes = nodes.getOrElse(1)
      val coresPerNode = coreByNode.getOrElse(1)

      val core = Seq(
        "-o " -> Some(BatchScheduler.output(uniqId)),
        "-e " -> Some(BatchScheduler.error(uniqId)),
        "-q " -> queue,
        "-lwalltime=" -> wallTime.map(_.toHHmmss),
      )

      val nodeSelection = flavour match
        case Torque ⇒
          val memoryString = memoryRequirements(",")(memory)
          s"#PBS -l nodes=$nbNodes:ppn=$coresPerNode$memoryString"
        case PBSPro ⇒
          val memoryString = memoryRequirements(":")(memory)
          s"#PBS -l select=$nbNodes:ncpus=$coresPerNode$memoryString"

      s"""$header
         |${requirementsString(core, "#PBS")}
         |$nodeSelection
         |
         |cd $workDirectory
         |$command
         |""".stripMargin

    def retrieveJobID(out: String) =
      out.split("\n").reverse.map(_.trim).dropWhile(_.isEmpty).head

    def translateStatus(retCode: Int, status: String) =
      status match
        case "R" | "E" | "H" | "S" ⇒ JobState.Running
        case "Q" | "W" | "T"       ⇒ JobState.Submitted
        case "C"                   ⇒ JobState.Done
        case _                     ⇒ throw new RuntimeException("Unrecognized state " + status)

    def parseState(cmdRet: ExecutionResult, command: String): JobState =
      val jobStateAttribute = "JOB_STATE"

      cmdRet.returnCode match {
        case 153 | 35 ⇒ JobState.Done
        case 0 =>
          val lines = cmdRet.stdOut.split("\n").map(_.trim)
          val state =
            lines.filter(_.matches(".*=.*")).map {
              prop ⇒
                val splited = prop.split('=')
                splited(0).trim.toUpperCase -> splited(1).trim
            }.toMap.get(jobStateAttribute)

          state match
            case Some(s) ⇒ translateStatus(cmdRet.returnCode, s)
            case None    ⇒ throw new RuntimeException("State not found in $command output: " + cmdRet.stdOut)

        case _ ⇒ throw new RuntimeException(ExecutionResult.error(command, cmdRet))
      }


    def pbsErrorWrapper(command: String, executionResult: ExecutionResult) =
      ExecutionResult.error("You might want to specify a different PBS flavour in your job description? flavour = PBSPro")(command, executionResult)
  }

  import impl._
  import BatchScheduler._

  val scriptSuffix = ".pbs"

  def submit(server: HeadNode, jobDescription: PBSJobDescription): BatchJob =
    BatchScheduler.submit(
      jobDescription.workDirectory,
      toScript(jobDescription),
      scriptSuffix,
      (f, _) ⇒ s"qsub $f",
      impl.retrieveJobID,
      server,
      impl.pbsErrorWrapper)

  def state(server: HeadNode, job: BatchJob): JobState =
    BatchScheduler.state(
      s"qstat -f ${job.jobId}",
      parseState)(server, job)

  def clean(server: HeadNode, job: BatchJob): Unit =
    BatchScheduler.clean(
      s"qdel ${job.jobId}",
      scriptSuffix)(server, job)

  def stdOut(server: HeadNode, job: BatchJob): String = BatchScheduler.stdOut(server, job)
  def stdErr(server: HeadNode, job: BatchJob): String = BatchScheduler.stdErr(server, job)

}

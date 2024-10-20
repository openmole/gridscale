package gridscale

import gridscale.cluster.{ BatchScheduler, HeadNode, Requirement }
import gridscale.tools._
import squants._
import squants.information._

import scala.language.higherKinds

package object slurm {

  case class Gres(gresName: String, gresValue: Int):
    override def toString = gresName + ":" + gresValue.toString

  case class SLURMJobDescription(
    command: String,
    workDirectory: String,
    partition: Option[String] = None,
    time: Option[Time] = None,
    memory: Option[Information] = None,
    nodes: Option[Int] = None,
    ntasks: Option[Int] = None,
    cpuPerTask: Option[Int] = Some(1),
    qos: Option[String] = None,
    gres: List[Gres] = List(),
    constraints: List[String] = List(),
    reservation: Option[String] = None,
    wckey: Option[String] = None)

  object impl {

    import Requirement._

    import BatchScheduler.{ BatchJob, output, error }

    def toScript(description: SLURMJobDescription)(uniqId: String) =
      import description._

      val header = "#!/bin/bash\n"

      val core = Seq(
        "-o " -> Some(output(uniqId)),
        "-e " -> Some(error(uniqId)),
        "-p " -> partition,
        "--mem=" -> memory.map(m ⇒ s"${m.toMBString}M"),
        "--nodes=" -> nodes.map(_.toString),
        "--ntasks=" -> ntasks.map(_.toString),
        "--cpus-per-task=" -> cpuPerTask.map(_.toString),
        "--time=" -> time.map(_.toHHmmss),
        "--qos=" -> qos,
        "-D " -> Some(workDirectory),
        "--reservation=" -> reservation,
        "--wckey" -> wckey)

      // must handle empty list separately since it is not done in mkString
      val gresList = gres match
        case List() ⇒ ""
        case _      ⇒ gres.mkString("#SBATCH --gres=", "--gres=", "")

      val constraintsList = constraints match
        case List() ⇒ ""
        case _      ⇒ constraints.mkString("#SBATCH --constraint=\"", "&", "\"")

      s"""$header
         |${requirementsString(core, "#SBATCH")}
         |$gresList
         |$constraintsList
         |
         |$command
         |""".stripMargin

      // TODO: handle several srun and split gres accordingly
      //    buffer += "srun "
      //    // must handle empty list separately since it is not done in mkString
      //    gres match {
      //      case List() ⇒
      //      case _ ⇒ buffer += gres.mkString("--gres=", "--gres=", "")
      //    }
      //    constraints match {
      //      case List() ⇒
      //      case _ ⇒ buffer += constraints.mkString("--constraint=\"", "&", "\"")
      //    }


    def retrieveJobID(submissionOutput: String) = submissionOutput.trim.reverse.takeWhile(_ != ' ').reverse

    def translateStatus(retCode: Int, status: String, command: String): JobState =
      import JobState._
      status match
        case "COMPLETED" ⇒ Done
        case "COMPLETED?" if 1 == retCode ⇒ Done
        case "COMPLETED?" if 1 != retCode ⇒ Failed
        case "RUNNING" | "COMPLETING" ⇒ Running
        case "CONFIGURING" | "PENDING" | "SUSPENDED" ⇒ Submitted
        case "CANCELLED" | "FAILED" | "NODE_FAIL" | "PREEMPTED" | "TIMEOUT" ⇒ Failed
        case _ ⇒ throw new RuntimeException(s"Unrecognized state $status returned by $command")

    def parseState(cmdRet: ExecutionResult, command: String): JobState =
      val jobStateAttribute = "JobState"
      val lines = cmdRet.stdOut.split("\n").map(_.trim)
      val state = lines.filter(_.matches(".*JobState=.*")).map {
        prop ⇒
          val splits = prop.split('=')
          splits(0).trim -> splits(1).trim.split(' ')(0)
        // consider job COMPLETED when scontrol returns 1: "Invalid job id specified"
        /** @see translateStatus(retCode: Int, status: String) */
      }.toMap.getOrElse(jobStateAttribute, "COMPLETED?")
      translateStatus(cmdRet.returnCode, state, command)

    // compiles thanks to a divine intervention
    def processCancel(cancelRet: ExecutionResult, job: BatchJob) = cancelRet match
      case ExecutionResult(0, _, _) ⇒
      case ExecutionResult(1, _, error) if error.matches(".*Invalid job id specified") ⇒ throw new RuntimeException(s"Slurm JobService: ${job.jobId} is an invalid job id")
      case _ ⇒ throw new RuntimeException(s"Slurm JobService could not cancel job ${job.jobId}")

  }

  import impl._
  import BatchScheduler._

  val scriptSuffix = ".slurm"

  def submit(server: HeadNode, jobDescription: SLURMJobDescription): BatchJob =
    BatchScheduler.submit(
      jobDescription.workDirectory,
      toScript(jobDescription),
      scriptSuffix,
      (f, _) ⇒ s"sbatch $f",
      retrieveJobID,
      server)

  def state(server: HeadNode, job: BatchJob): JobState =
    BatchScheduler.state(
      s"scontrol show job ${job.jobId}",
      parseState)(server, job)

  def clean(server: HeadNode, job: BatchJob): Unit =
    BatchScheduler.clean(
      s"scancel ${job.jobId}",
      scriptSuffix)(server, job)

  def stdOut(server: HeadNode, job: BatchJob): String = server.read(job.workDirectory + "/" + output(job.uniqId))
  def stdErr(server: HeadNode, job: BatchJob): String = server.read(job.workDirectory + "/" + error(job.uniqId))

}

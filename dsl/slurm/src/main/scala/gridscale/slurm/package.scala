package gridscale

import cats._
import cats.implicits._
import freedsl.system._
import freedsl.errorhandler._
import gridscale.cluster.{ BatchScheduler, HeadNode }
import gridscale.tools._
import monocle.macros._
import squants._

import scala.language.higherKinds

package object slurm {

  case class Gres(gresName: String, gresValue: Int) {
    override def toString = gresName + ":" + gresValue.toString
  }

  @Lenses case class SlurmJobDescription(
    executable: String,
    arguments: String,
    workDirectory: String,
    queue: Option[String] = None,
    wallTime: Option[Time] = None,
    memory: Option[Int] = None,
    nodes: Option[Int] = None,
    coresByNode: Option[Int] = Some(1),
    qos: Option[String] = None,
    gres: List[Gres] = List(),
    constraints: List[String] = List())

  object impl {

    import BatchScheduler.{ BatchJob, output, error }

    def pair2String(p: (String, Option[String])): String = p._1 + p._2.getOrElse("")

    def toScript(description: SlurmJobDescription, uniqId: String) = {
      import description._

      val header = "#!/bin/bash\n"

      val core = Seq(
        "-o " -> Some(output(uniqId)),
        "-e " -> Some(error(uniqId)),
        "-p " -> queue,
        "--mem=" -> memory.map(_.toString),
        "--nodes=" -> nodes.map(_.toString),
        "--cpus-per-task=" -> coresByNode.map(_.toString),
        "--time=" -> wallTime.map(_.toHHmmss),
        "--qos=" -> qos,
        "-D " -> Some(workDirectory)
      ).filter { case (k, v) ⇒ v.isDefined }.
        map(pair2String).
        mkString("#SBATCH ", "\n#SBATCH ", "\n")

      // must handle empty list separately since it is not done in mkString
      val gresList = gres match {
        case List() ⇒ ""
        case _      ⇒ gres.mkString("#SBATCH --gres=", "--gres=", "")
      }
      val constraintsList = constraints match {
        case List() ⇒ ""
        case _      ⇒ constraints.mkString("#SBATCH --constraint=\"", "&", "\"")
      }

      s"""$header
         |$core
         |$gresList
         |$constraintsList
         |
         |$executable $arguments
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

    }

    def retrieveJobID(submissionOutput: String) = submissionOutput.trim.reverse.takeWhile(_ != ' ').reverse

    def translateStatus(retCode: Int, status: String, command: BatchScheduler.Command): Either[RuntimeException, JobState] = {
      import JobState._
      status match {
        case "COMPLETED" ⇒ Right(Done)
        case "COMPLETED?" if 1 == retCode ⇒ Right(Done)
        case "COMPLETED?" if 1 != retCode ⇒ Right(Failed)
        case "RUNNING" | "COMPLETING" ⇒ Right(Running)
        case "CONFIGURING" | "PENDING" | "SUSPENDED" ⇒ Right(Submitted)
        case "CANCELLED" | "FAILED" | "NODE_FAIL" | "PREEMPTED" | "TIMEOUT" ⇒ Right(Failed)
        case _ ⇒ Left(new RuntimeException(s"Unrecognized state $status returned by $command"))
      }
    }

    def parseState(cmdRet: ExecutionResult, command: BatchScheduler.Command): Either[RuntimeException, JobState] = {
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
    }

    // compiles thanks to a divine intervention
    def processCancel[M[_]: Monad](cancelRet: ExecutionResult, job: BatchJob)(implicit errorHandler: ErrorHandler[M]) = cancelRet match {
      case ExecutionResult(0, _, _) ⇒ ().pure[M]
      case ExecutionResult(1, _, error) if error.matches(".*Invalid job id specified") ⇒ errorHandler.exception(new RuntimeException(s"Slurm JobService: ${job.jobId} is an invalid job id"))
      case _ ⇒ errorHandler.exception(new RuntimeException(s"Slurm JobService could not cancel job ${job.jobId}"))
    }

  }

  implicit val slurmDSL = new BatchScheduler[SlurmJobDescription] {

    import impl._
    import BatchScheduler._

    val scriptSuffix = ".slurm"

    override def submit[M[_]: Monad, S](server: S, jobDescription: SlurmJobDescription)(implicit hn: HeadNode[S, M], system: System[M], errorHandler: ErrorHandler[M]): M[BatchJob] =
      BatchScheduler.submit[M, S, SlurmJobDescription](
        SlurmJobDescription.workDirectory.get,
        toScript,
        scriptSuffix,
        f ⇒ s"sbatch $f",
        retrieveJobID
      )(server, jobDescription)

    override def state[M[_]: Monad, S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M], error: ErrorHandler[M]): M[JobState] =
      BatchScheduler.state[M, S](
        id ⇒ s"scontrol show job $id",
        parseState
      )(server, job)

    override def clean[M[_]: Monad, S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[Unit] =
      BatchScheduler.clean[M, S](
        id ⇒ s"scancel $id",
        scriptSuffix
      )(server, job)

  }
}

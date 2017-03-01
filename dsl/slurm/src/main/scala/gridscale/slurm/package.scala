package gridscale

import cats._
import cats.implicits._
import freedsl.system._
import freedsl.io._
import gridscale.cluster.HeadNode
import gridscale.tools._
import squants._

import scala.language.higherKinds

package object slurm {

  case class Gres(gresName: String, gresValue: Int) {
    override def toString = gresName + ":" + gresValue.toString
  }

  case class SlurmJobDescription(
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

  case class SlurmJob(uniqId: String, slurmId: String, workDirectory: String)

  def output(uniqId: String): String = uniqId + ".out"

  def error(uniqId: String): String = uniqId + ".err"

  def slurmScriptName(uniqId: String) = uniqId + ".slurm"

  def slurmScriptPath(workDirectory: String, uniqId: String) = workDirectory + "/" + slurmScriptName(uniqId)

  def translateStatus[M[_]](retCode: Int, status: String): Either[String, JobState] = {
    import JobState._
    status match {
      case "COMPLETED" ⇒ Right(Done)
      case "COMPLETED?" if 1 == retCode ⇒ Right(Done)
      case "COMPLETED?" if 1 != retCode ⇒ Right(Failed)
      case "RUNNING" | "COMPLETING" ⇒ Right(Running)
      case "CONFIGURING" | "PENDING" | "SUSPENDED" ⇒ Right(Submitted)
      case "CANCELLED" | "FAILED" | "NODE_FAIL" | "PREEMPTED" | "TIMEOUT" ⇒ Right(Failed)
      case _ ⇒ Left("Unrecognized state " + status)
    }
  }

  def retrieveJobID(submissionOutput: String) = submissionOutput.trim.reverse.takeWhile(_ != ' ').reverse

  def submit[M[_]: Monad](description: SlurmJobDescription)(implicit hn: HeadNode[M], system: System[M], io: IO[M]) = for {
    _ ← hn.execute(s"mkdir -p ${description.workDirectory}")
    uniqId ← system.randomUUID.map(_.toString)
    script = toScript(description, uniqId)
    _ ← hn.write(script.getBytes, slurmScriptPath(description.workDirectory, uniqId))
    command = s"cd ${description.workDirectory} && sbatch ${slurmScriptName(uniqId)}"
    cmdRet ← hn.execute(command)
    ExecutionResult(ret, out, error) = cmdRet
    slurmID = retrieveJobID(out)
    _ ← if (ret != 0 || slurmID.isEmpty) io.errorMessage(ExecutionResult.error(command, cmdRet)) else ().pure[M]
  } yield SlurmJob(uniqId, slurmID, description.workDirectory)

  lazy val jobStateAttribute = "JobState"

  def state[M[_]: Monad](job: SlurmJob)(implicit hn: HeadNode[M], io: IO[M]): M[JobState] = {
    val command = "scontrol show job " + job.slurmId

    def parseState(cmdRet: ExecutionResult) = {

      val lines = cmdRet.stdOut.split("\n").map(_.trim)
      val state = lines.filter(_.matches(".*JobState=.*")).map {
        prop ⇒
          val splits = prop.split('=')
          splits(0).trim -> splits(1).trim.split(' ')(0)
        // consider job COMPLETED when scontrol returns 1: "Invalid job id specified"
        /** @see translateStatus(retCode: Int, status: String) */
      }.toMap.getOrElse(jobStateAttribute, "COMPLETED?")
      translateStatus(cmdRet.returnCode, state)
    }

    for {
      cmdRet ← hn.execute(command)
      s ← io.errorMessageOrResult(parseState(cmdRet))
    } yield s
  }

  def stdOut[M[_]](job: SlurmJob)(implicit hn: HeadNode[M]) = hn.read(job.workDirectory + "/" + output(job.uniqId))

  def stdErr[M[_]](job: SlurmJob)(implicit hn: HeadNode[M]) = hn.read(job.workDirectory + "/" + error(job.uniqId))

  // compiles thanks to a divine intervention
  def processCancel[M[_]: Monad](cancelRet: ExecutionResult, job: SlurmJob)(implicit io: IO[M]) = cancelRet match {
    case ExecutionResult(0, _, _) ⇒ ().pure[M]
    case ExecutionResult(1, _, error) if error.matches(".*Invalid job id specified") ⇒ io.exception(new RuntimeException(s"Slurm JobService: ${job.slurmId} is an invalid job id"))
    case _ ⇒ io.exception(new RuntimeException(s"Slurm JobService could not cancel job ${job.slurmId}"))
  }

  def clean[M[_]: Monad](job: SlurmJob)(implicit hn: HeadNode[M], io: IO[M]) = for {
    cmdRet ← hn.execute(s"scancel ${job.slurmId}")
    _ ← hn.rm(slurmScriptPath(job.workDirectory, job.uniqId))
    _ ← hn.rm(job.workDirectory + "/" + output(job.uniqId))
    _ ← hn.rm(job.workDirectory + "/" + error(job.uniqId))
  } yield ()

}

package gridscale

import cats._
import freedsl.system._
import freedsl.errorhandler._
import gridscale.cluster.{ BatchScheduler, HeadNode }
import gridscale.tools._
import squants._
import monocle.macros.GenLens

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

  object impl {

    def toScript(description: PBSJobDescription, uniqId: String) = {
      import description._

      val buffer = new ScriptBuffer
      buffer += "#!/bin/bash"

      buffer += "#PBS -o " + BatchScheduler.output(uniqId)
      buffer += "#PBS -e " + BatchScheduler.error(uniqId)

      queue foreach { q ⇒ buffer += "#PBS -q " + q }
      // FIXME better way than setting default value?
      val mem = memory.getOrElse(2048)
      wallTime foreach { t ⇒ buffer += "#PBS -lwalltime=" + t.toHHmmss }

      val nbNodes = nodes.getOrElse(1)
      val coresPerNode = coreByNode.getOrElse(1)

      buffer += s"#PBS -l select=$nbNodes:ncpus=$coresPerNode:mem=${mem}MB"

      buffer += "cd " + workDirectory

      buffer += command
      buffer.toString
    }

    def retrieveJobID(out: String) = out.split("\n").head

    def translateStatus(retCode: Int, status: String) =
      status match {
        case "R" | "E" | "H" | "S" ⇒ Right(JobState.Running)
        case "Q" | "W" | "T"       ⇒ Right(JobState.Submitted)
        case _                     ⇒ Left(new RuntimeException("Unrecognized state " + status))
      }

    def parseState(cmdRet: ExecutionResult, command: BatchScheduler.Command): Either[RuntimeException, JobState] = {

      val jobStateAttribute = "JOB_STATE"

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
            case None    ⇒ Left(new RuntimeException("State not found in $command output: " + cmdRet.stdOut))
          }
        case _ ⇒ Left(new RuntimeException(ExecutionResult.error(command, cmdRet)))
      }

    }

  }

  implicit val pbsDSL = new BatchScheduler[PBSJobDescription] {

    import impl._
    import BatchScheduler._

    val scriptSuffix = ".pbs"

    override def submit[M[_]: Monad, S](server: S, jobDescription: PBSJobDescription)(implicit hn: HeadNode[S, M], system: System[M], errorHandler: ErrorHandler[M]): M[BatchJob] =
      BatchScheduler.submit[M, S, PBSJobDescription](
        GenLens[PBSJobDescription](_.workDirectory).get,
        toScript,
        scriptSuffix,
        "qsub",
        impl.retrieveJobID
      )(server, jobDescription)

    override def state[M[_]: Monad, S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M], error: ErrorHandler[M]): M[JobState] =
      BatchScheduler.state[M, S](
        "qstat -f ",
        parseState
      )(server, job)

    override def clean[M[_]: Monad, S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[Unit] =
      BatchScheduler.clean[M, S](
        "qdel",
        scriptSuffix
      )(server, job)
  }
}

package gridscale

import cats._
import cats.implicits._
import freedsl.system._
import freedsl.errorhandler._
import gridscale.cluster.BatchScheduler.BatchJob
import gridscale.cluster.{ BatchScheduler, HeadNode }
import squants._
import gridscale.tools._
import monocle.macros._

import scala.language.{ higherKinds, postfixOps }

package object condor {

  @Lenses case class CondorJobDescription(
    executable: String,
    arguments: String,
    workDirectory: String,
    memory: Option[Int] = None,
    nodes: Option[Int] = None,
    coreByNode: Option[Int] = None,
    requirements: Option[CondorRequirement] = None)

  object impl {

    import BatchScheduler.{ BatchJob, output, error }

    // TODO refactor duplicate
    def pair2String(p: (String, Option[String])): String = p._1 + p._2.getOrElse("")

    def toScript(description: CondorJobDescription, uniqId: String) = {

      import description._

      val header = "#!/bin/bash\n"

      val core = Seq(
        "output = " -> Some(output(uniqId)),
        "error = " -> Some(error(uniqId)),
        "request_memory = " -> memory.map(_.toString + " MB"),

        "initialdir = " -> Some(workDirectory),
        "executable = " -> Some(executable),
        "arguments = " -> Some(s""""$arguments"""")).filter { case (k, v) ⇒ v.isDefined }.
        map(pair2String).
        mkString("\n")

      // TODO: are these features available in Condor?
      //    queue match {
      //      case Some(q) ⇒ buffer += "#PBS -q " + q
      //      case None ⇒
      //    }
      //
      //    wallTime match {
      //      case Some(t) ⇒
      //        val df = new java.text.SimpleDateFormat("HH:mm:ss")
      //        df.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
      //        buffer += "#PBS -lwalltime=" + df.format(t * 60 * 1000)
      //      case None ⇒
      //    }

      val universe = nodes match {
        case Some(n) ⇒
          s"""universe = parallel
            |machine_count = $n
            |
            |request_cpus = ${coreByNode.getOrElse(1)}
            """.stripMargin
        case None ⇒ "universe = vanilla"
      }

      val reqAll = Seq(requirements, coreByNode.map(c ⇒ CondorRequirement(c.toString)))
      val reqList = reqAll.foldLeft(Seq.empty[CondorRequirement])(_ ++ _)

      // 'queue 1' actually submits N jobs (default to 1 in our case)
      s"""$header
           |$universe
           |$core
           |
           |${if (reqList.nonEmpty) "requirements = " + CondorRequirement(reqList.mkString).toCondor else ""}
           |
           |getenv = True
           |
           |queue 1
           |""".stripMargin
    }

    def retrieveJobId(out: String) = out.trim.reverse.tail.takeWhile(_ != ' ').reverse

    def translateStatus(status: String, command: String): Either[RuntimeException, JobState] =
      status match {
        case "3" | "4"       ⇒ Right(JobState.Done)
        case "2"             ⇒ Right(JobState.Running)
        // choice was made to characterize held jobs (status=5) as submitted instead of Running
        case "0" | "1" | "5" ⇒ Right(JobState.Submitted)
        case "6"             ⇒ Right(JobState.Failed)
        case _               ⇒ Left(new RuntimeException(s"Unrecognized state $status retrieved from $command"))
      }

    def formatError(command: String, cmdRet: ExecutionResult): RuntimeException = {
      new RuntimeException(s"Could not retrieve job state from $command [output: ${cmdRet.stdOut}] [error: ${cmdRet.stdErr}]")
    }

    /**
     * When split, the actual state is the last member of a 2-element array
     *
     * @param output State command output to parse
     * @return parse state to be translated
     */
    def parseStateInQueue(output: String) = output.split('=').map(_ trim).last

    /**
     * Can't match it with a regex from the ouput for some reason...
     * resulting in this ugly one-liner...
     *
     * @param output State command output to parse
     * @return parse state to be translated
     */
    def parseStateFinished(output: String) =
      output.split("\n").filter(_ matches "^JobStatus = .*").head.split('=').map(_ trim).last

    // FIXME fails to compile if second param list is implicit..
    def queryState[M[_]: Monad, S](server: S, job: BatchJob)(hn: HeadNode[S, M], error: ErrorHandler[M]): M[JobState] = {

      // if the job is still running, his state is returned by condor_q...
      val queryInQueueCommand = s"condor_q ${job.jobId} -long -attributes JobStatus"
      // ...but if the job is already completed, his state is returned by condor_history...
      val queryFinishedCommand = s"condor_history ${job.jobId} -long"

      for {
        cmdRet ← hn.execute(server, queryInQueueCommand)
        ExecutionResult(ret, stdOut, _) = cmdRet
        _ ← if (ret != 0) error.exception(formatError(queryInQueueCommand, cmdRet)) else ().pure[M]
        state ← if (stdOut.nonEmpty) {
          val state = parseStateInQueue(stdOut)
          error.get[JobState](translateStatus(state, queryInQueueCommand))
        } else for {
          cmdRet ← hn.execute(server, queryFinishedCommand)
          state = parseStateFinished(cmdRet.stdOut)
          s ← error.get[JobState](translateStatus(state, queryFinishedCommand))
        } yield s
      } yield state
    }

  }

  import impl._
  import BatchScheduler._
  import shell.BashShell._

  val scriptSuffix = ".condor"

  def submit[M[_]: Monad, S](server: S, jobDescription: CondorJobDescription)(implicit hn: HeadNode[S, M], system: System[M], errorHandler: ErrorHandler[M]): M[BatchJob] =
    BatchScheduler.submit[M, S, CondorJobDescription](
      CondorJobDescription.workDirectory.get,
      toScript,
      scriptSuffix,
      f ⇒ s"condor_submit $f",
      impl.retrieveJobId)(server, jobDescription)

  def state[M[_]: Monad, S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M], error: ErrorHandler[M]): M[JobState] = queryState(server, job)(hn, error)

  def clean[M[_]: Monad, S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[Unit] =
    BatchScheduler.clean[M, S](
      id ⇒ s"condor_rm $id",
      scriptSuffix)(server, job)

  def stdOut[M[_], S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[String] = BatchScheduler.stdOut[M, S](server, job)
  def stdErr[M[_], S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[String] = BatchScheduler.stdErr[M, S](server, job)
}

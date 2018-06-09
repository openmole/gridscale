package gridscale

import gridscale.cluster.BatchScheduler.BatchJob
import gridscale.cluster.{ BatchScheduler, HeadNode, Requirement }
import effectaside._
import squants._
import gridscale.tools._
import monocle.macros._
import squants.information._

import scala.language.{ higherKinds, postfixOps }

package object condor {

  @Lenses case class CondorJobDescription(
    executable: String,
    arguments: String,
    workDirectory: String,
    memory: Option[Information] = None,
    nodes: Option[Int] = None,
    coreByNode: Option[Int] = None,
    requirements: Option[CondorRequirement] = None)

  object impl {

    import Requirement._
    import BatchScheduler.{ BatchJob, output, error }

    def toScript(description: CondorJobDescription)(uniqId: String) = {

      import description._

      val header = "#!/bin/bash\n"

      val core = Seq(
        "output = " -> Some(output(uniqId)),
        "error = " -> Some(error(uniqId)),
        "request_memory = " -> memory.map(m ⇒ s"${m.toMBString} MB"),

        "initialdir = " -> Some(workDirectory),
        "executable = " -> Some(executable),
        "arguments = " -> Some(s""""$arguments""""))

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
           |${requirementsString(core)}
           |
           |${if (reqList.nonEmpty) "requirements = " + CondorRequirement(reqList.mkString).toCondor else ""}
           |
           |getenv = True
           |
           |queue 1
           |""".stripMargin
    }

    def retrieveJobId(out: String) = out.trim.reverse.tail.takeWhile(_ != ' ').reverse

    def translateStatus(status: String, command: String): JobState =
      status match {
        case "3" | "4"       ⇒ JobState.Done
        case "2"             ⇒ JobState.Running
        // choice was made to characterize held jobs (status=5) as submitted instead of Running
        case "0" | "1" | "5" ⇒ JobState.Submitted
        case "6"             ⇒ JobState.Failed
        case _               ⇒ throw new RuntimeException(s"Unrecognized state $status retrieved from $command")
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
    def queryState[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): JobState = {

      // if the job is still running, his state is returned by condor_q...
      val queryInQueueCommand = s"condor_q ${job.jobId} -long -attributes JobStatus"
      // ...but if the job is already completed, his state is returned by condor_history...
      val queryFinishedCommand = s"condor_history ${job.jobId} -long"

      val cmdRet = hn.execute(server, queryInQueueCommand)
      val ExecutionResult(ret, stdOut, _) = cmdRet
      if (ret != 0) throw formatError(queryInQueueCommand, cmdRet)

      val state: JobState = if (stdOut.nonEmpty) {
        val state = parseStateInQueue(stdOut)
        translateStatus(state, queryInQueueCommand)
      } else {
        val cmdRet = hn.execute(server, queryFinishedCommand)
        val state = parseStateFinished(cmdRet.stdOut)
        translateStatus(state, queryFinishedCommand)
      }
      state
    }

  }

  import impl._
  import BatchScheduler._
  import shell.BashShell._

  val scriptSuffix = ".condor"

  def submit[S](server: S, jobDescription: CondorJobDescription)(implicit hn: HeadNode[S], system: Effect[System]): BatchJob =
    BatchScheduler.submit[S](
      jobDescription.workDirectory,
      toScript(jobDescription),
      scriptSuffix,
      (f, _) ⇒ s"condor_submit $f",
      impl.retrieveJobId,
      server)

  def state[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): JobState = queryState(server, job)

  def clean[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): Unit =
    BatchScheduler.clean[S](
      s"condor_rm ${job.jobId}",
      scriptSuffix)(server, job)

  def stdOut[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = BatchScheduler.stdOut[S](server, job)
  def stdErr[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = BatchScheduler.stdErr[S](server, job)
}

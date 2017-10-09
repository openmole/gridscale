package gridscale

import cats.Monad
import freedsl.errorhandler.ErrorHandler
import freedsl.system.System
import gridscale.cluster.{ BatchScheduler, HeadNode }
import gridscale.cluster.BatchScheduler.BatchJob
import squants._

package object sge {

  case class SGEJobDescription(
    command: String,
    workDirectory: String,
    queue: Option[String] = None,
    memory: Option[Int] = None,
    wallTime: Option[Time] = None)

  object impl {

    def toSGE(jobDescription: SGEJobDescription)(uniqId: String) =
      s"""
        |#!/bin/bash
        |
        |#$$ -o ${BatchScheduler.output(uniqId)}
        |#$$ -e ${BatchScheduler.error(uniqId)}
        |
        |${jobDescription.queue.map(q ⇒ s"#$$ -q $q").getOrElse("")}
        |${jobDescription.memory.map(m ⇒ s"#$$ -l h_vmem=${m}M").getOrElse("")}
        |${jobDescription.wallTime.map(t ⇒ s"#$$ -l h_cpu=${t.toSeconds.toLong}").getOrElse("")}
        |
        |#$$ -cwd
        |
        |${jobDescription.command}
      """.stripMargin

    def retreiveJobId(out: String) = {
      val jobId = out.split(" ").drop(2).head
      if (!jobId.forall(_.isDigit)) throw new RuntimeException("qsub did not return a valid JobID in " + out)
      jobId
    }

    def parseState(executionResult: ExecutionResult) = translateStatus(executionResult.stdOut.dropRight(1))

    def translateStatus(status: String) =
      status match {
        case "qw" | "hqw" | "hRwq" | "Rs" | "Rts" | "RS" | "RtS" | "RT" | "RtT" ⇒ Right(JobState.Submitted)
        case "r" | "t" | "Rr" | "Rt" | "T" | "tT" | "s" | "ts" | "S" | "tS" ⇒ Right(JobState.Running)
        case "" | "dr" | "dt" | "dRr" | "dRt" | "ds" | "dS" | "dT" | "dRs" | "dRS" | "dRT" ⇒ Right(JobState.Done)
        case "Eqw" | "Ehqw" | "EhRqw" ⇒ Right(JobState.Failed)
        case _ ⇒ Left(throw new RuntimeException("Unrecognized state " + status))
      }
  }

  val scriptSuffix = ".sge"

  def submit[M[_]: Monad, S](server: S, jobDescription: SGEJobDescription)(implicit hn: HeadNode[S, M], system: System[M], errorHandler: ErrorHandler[M]): M[BatchJob] =
    BatchScheduler.submit[M, S](
      jobDescription.workDirectory,
      impl.toSGE(jobDescription),
      scriptSuffix,
      (f, _) ⇒ s"qsub $f",
      impl.retreiveJobId,
      server)

  def state[M[_]: Monad, S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M], error: ErrorHandler[M]): M[JobState] =
    BatchScheduler.state[M, S](
      id ⇒ s"""qstat | sed 's/^  *//g'  |  grep '^${id} ' | sed 's/  */ /g' | cut -d' ' -f5""",
      (res, _) ⇒ impl.parseState(res))(server, job)

  def clean[M[_]: Monad, S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[Unit] =
    BatchScheduler.clean[M, S](
      id ⇒ s"qdel $id",
      scriptSuffix)(server, job)

  def stdOut[M[_], S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[String] = hn.read(server, job.workDirectory + "/" + BatchScheduler.output(job.uniqId))
  def stdErr[M[_], S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[String] = hn.read(server, job.workDirectory + "/" + BatchScheduler.error(job.uniqId))

}

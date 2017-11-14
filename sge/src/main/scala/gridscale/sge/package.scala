package gridscale

import effectaside._
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
        case "qw" | "hqw" | "hRwq" | "Rs" | "Rts" | "RS" | "RtS" | "RT" | "RtT" ⇒ JobState.Submitted
        case "r" | "t" | "Rr" | "Rt" | "T" | "tT" | "s" | "ts" | "S" | "tS" ⇒ JobState.Running
        case "" | "dr" | "dt" | "dRr" | "dRt" | "ds" | "dS" | "dT" | "dRs" | "dRS" | "dRT" ⇒ JobState.Done
        case "Eqw" | "Ehqw" | "EhRqw" ⇒ JobState.Failed
        case _ ⇒ throw new RuntimeException("Unrecognized state " + status)
      }
  }

  val scriptSuffix = ".sge"

  def submit[S](server: S, jobDescription: SGEJobDescription)(implicit hn: HeadNode[S], system: Effect[System]): BatchJob =
    BatchScheduler.submit[S](
      jobDescription.workDirectory,
      impl.toSGE(jobDescription),
      scriptSuffix,
      (f, _) ⇒ s"qsub $f",
      impl.retreiveJobId,
      server)

  def state[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): JobState =
    BatchScheduler.state[S](
      s"""qstat | sed 's/^  *//g'  |  grep '^${job.jobId} ' | sed 's/  */ /g' | cut -d' ' -f5""",
      (res, _) ⇒ impl.parseState(res))(server, job)

  def clean[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): Unit =
    BatchScheduler.clean[S](
      s"qdel ${job.jobId}",
      scriptSuffix)(server, job)

  def stdOut[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = hn.read(server, job.workDirectory + "/" + BatchScheduler.output(job.uniqId))
  def stdErr[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = hn.read(server, job.workDirectory + "/" + BatchScheduler.error(job.uniqId))

}

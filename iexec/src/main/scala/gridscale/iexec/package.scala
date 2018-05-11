package gridscale

import effectaside._
import gridscale.cluster.BatchScheduler.BatchJob
import gridscale.cluster.{ BatchScheduler, HeadNode }
import gridscale.tools._
import monocle.macros._
import squants._
import squants.information._

package object iexec {

  case class IEXECJobDescription(
    workDirectory: String,
    walletPath: String,
    dappAddress: String,
    arguments: String,
    dappCost: Int)

  object impl {

    def toIexec(description: IEXECJobDescription) = // put path to iexec sdk in PATH variable
      s"""
         |#!/bin/bash
         |PATH=""
         |cd ${description.workDirectory}
         |iexec account login
         |iexec account allow ${description.dappCost}
         |DEBUG='iexec:submit' iexec submit ${description.arguments} --dapp ${description.dappAddress}
       """.stripMargin

    def retrieveJobID(out: String) = {
      val tokenised_out = out.split("\n")

      val linePrefix = "            \"transactionHash\""
      var hashLineIndex = 0
      while (hashLineIndex < tokenised_out.length && !tokenised_out(hashLineIndex).startsWith(linePrefix)) {
        hashLineIndex += 1
      }

      assert(hashLineIndex < tokenised_out.length, "Error: could not find txHash in " + out)

      val hashPrefix = "0x"
      val hashLine = tokenised_out(hashLineIndex)
      val hashIndex = hashLine indexOf hashPrefix
      val txHash = hashLine.substring(hashIndex, hashIndex + 66)
      txHash
    }

    def parseState(executionResult: ExecutionResult, command: String) = translateStatus(executionResult.stdOut.split(" ")(1).replace(".", "").replace(":", ""), command)

    def translateStatus(status: String, command: String) =
      status match {
        case "PENDING" ⇒ JobState.Submitted
        case "RUNNING" ⇒ JobState.Running
        case "Result"  ⇒ JobState.Done
        //case "......" ⇒ JobState.Failed -- TODO
        case _         ⇒ throw new RuntimeException("Unrecognized state " + status + "from command " + command)
      }
  }

  val scriptSuffix = ".sh"

  def submit[S](server: S, jobDescription: IEXECJobDescription)(implicit hn: HeadNode[S], system: Effect[System]): BatchJob =
    BatchScheduler.submit[S](
      jobDescription.workDirectory,
      _ ⇒ impl.toIexec(jobDescription),
      scriptSuffix,
      (f, _) ⇒ s"./$f",
      impl.retrieveJobID,
      server)

  def state[S](server: S, job: BatchJob, jobDescription: IEXECJobDescription)(implicit hn: HeadNode[S]): JobState =
    BatchScheduler.state[S](
      s"""iexec result ${job.jobId} --dapp ${jobDescription.dappAddress}""",
      impl.parseState)(server, job)

  def clean[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): Unit =
    BatchScheduler.clean[S](
      "", // no cancel necessary for iexec
      scriptSuffix)(server, job)

  def stdOut[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = BatchScheduler.stdOut[S](server, job)
  def stdErr[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = BatchScheduler.stdErr[S](server, job)
}

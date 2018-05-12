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
    IexecFilesPath: String,
    dappAddress: String,
    arguments: String,
    dappCost: Int)

  object impl {

    def toIexec(description: IEXECJobDescription) =
      s"""
         |#!/bin/bash
         |export PATH="${description.IexecFilesPath}"
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

    def parseState(executionResult: ExecutionResult, command: String) = {
      if (executionResult.stdOut == "") {
        JobState.Running
      } else {
        JobState.Done
      }
    }
  }

  val scriptSuffix = ".sh"
  val outSuffix = ".text"

  def submit[S](server: S, jobDescription: IEXECJobDescription)(implicit hn: HeadNode[S], system: Effect[System]): BatchJob =
    BatchScheduler.submit[S](
      jobDescription.workDirectory,
      _ ⇒ impl.toIexec(jobDescription),
      scriptSuffix,
      (f, _) ⇒ s"./$f",
      impl.retrieveJobID,
      server)

  def state[S](server: S, job: BatchJob, jobDescription: IEXECJobDescription)(implicit hn: HeadNode[S]): JobState = // need to set path to include iexec files again, needs fix
    BatchScheduler.state[S](
      s"""export PATH="${jobDescription.IexecFilesPath}" && cd ${jobDescription.workDirectory} && iexec result ${job.jobId} --dapp ${jobDescription.dappAddress} --save""",
      impl.parseState)(server, job)

  def clean[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): Unit = {
    hn.rmFile(server, job.workDirectory + "/" + job.uniqId + scriptSuffix)
    hn.rmFile(server, job.workDirectory + "/" + job.jobId + outSuffix)
  }

  def stdOut[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = hn.read(server, job.workDirectory + "/" + job.jobId + outSuffix)
  def stdErr[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = "" // no stdError file to get
}

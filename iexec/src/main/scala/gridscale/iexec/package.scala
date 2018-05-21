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
    IexecFilesPath: String,
    dappAddress: String,
    arguments: String,
    dappCost: Int)

  object impl {

    def toIexec[S](server: S, description: IEXECJobDescription)(implicit hn: HeadNode[S], system: Effect[System]) = {

      loginIexecAccount(server, description)
      verifyIexecAccountBalance(server, description)

      s"""
         |#!/bin/bash
         |export PATH="${description.IexecFilesPath}:$${PATH}"
         |cd ${description.workDirectory}
         |DEBUG='iexec:submit' iexec submit ${description.arguments} --dapp ${description.dappAddress}
       """.stripMargin
    }

    def loginIexecAccount[S](server: S, description: IEXECJobDescription)(implicit hn: HeadNode[S], system: Effect[System]) = {
      if (!new java.io.File(s"""/${description.workDirectory}/account.json""").exists) {
        hn.execute(server, s"""cd ${description.workDirectory} && export PATH="${description.IexecFilesPath}:$${PATH}" && iexec account login""")
      }
    }

    def populateIexecAccount[S](server: S, description: IEXECJobDescription, amount: Int)(implicit hn: HeadNode[S], system: Effect[System]) = {
      val cmdRet = hn.execute(server, s"""cd ${description.workDirectory} && export PATH="${description.IexecFilesPath}:$${PATH}" && iexec account allow ${amount}""")
    }

    def verifyIexecAccountBalance[S](server: S, description: IEXECJobDescription)(implicit hn: HeadNode[S], system: Effect[System]) = {
      val ExecutionResult(ret, out, error) = hn.execute(server, s"""cd ${description.workDirectory} && export PATH="${description.IexecFilesPath}:$${PATH}" && iexec account show""")

      var ropstenNetworkIndex = 2

      val ropstenBalanceLine = out.split("\n")(ropstenNetworkIndex)
      val rlcBalance = ropstenBalanceLine.substring(ropstenBalanceLine.indexOf(":")+1, ropstenBalanceLine.indexOf(" nRLC")).trim.toInt

      assert(rlcBalance >= description.dappCost, s"iExec account does not have enough tokens to execute this DApp, account needs at least ${description.dappCost} but has ${rlcBalance}")
    }

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
      if (executionResult.stdOut.isEmpty) {
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
      _ ⇒ impl.toIexec(server, jobDescription),
      scriptSuffix,
      (f, _) ⇒ s"./$f",
      impl.retrieveJobID,
      server)

  def state[S](server: S, job: BatchJob, jobDescription: IEXECJobDescription)(implicit hn: HeadNode[S]): JobState = // need to set path to include iexec files again, needs fix
    BatchScheduler.state[S](
      s"""export PATH="${jobDescription.IexecFilesPath}:$${PATH}" && cd ${jobDescription.workDirectory} && iexec result ${job.jobId} --dapp ${jobDescription.dappAddress} --save""",
      impl.parseState)(server, job)

  def clean[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): Unit = {
    hn.rmFile(server, job.workDirectory + "/" + job.uniqId + scriptSuffix)
    hn.rmFile(server, job.workDirectory + "/" + job.jobId + outSuffix)
  }

  def stdOut[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = hn.read(server, job.workDirectory + "/" + job.jobId + outSuffix)
  def stdErr[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = "" // no stdError file to get
}

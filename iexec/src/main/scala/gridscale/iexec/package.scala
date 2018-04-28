package gridscale

import effectaside._
import gridscale.cluster.BatchScheduler.BatchJob
import gridscale.cluster.{BatchScheduler, HeadNode}
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

    def toIexec(description: IEXECJobDescription) =
      s"""
         |#!/bin/bash
         |PATH="/Users/Karow/.nvm/versions/node/v8.9.4/bin:/Library/Frameworks/Python.framework/Versions/3.5/bin:/Library/Frameworks/Python.framework/Versions/3.5/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:/Library/TeX/texbin:/usr/local/git/bin"
         |cd ${description.workDirectory}
         |iexec account allow ${description.dappCost}
         |iexec account login
         |iexec submit ${description.arguments} --dapp ${description.dappAddress}
       """.stripMargin

    def retreiveJobID(out: String) = {
      println("string passed is :" + out + "\n")
      val linePrefix = "txHash"
      val hashPrefix = "0x"
      var txHash = out.split("\n").find(_.contains(linePrefix)).getOrElse(throw new RuntimeException("iexec output did not return a txHash in \n" + out))
        .split(" ").find(_.contains(hashPrefix)).getOrElse(throw new RuntimeException("iexec output did not return a valid txHash in \n" + out))
      txHash
    }

    def parseState(executionResult: ExecutionResult, command: String) = translateStatus(executionResult.stdOut.split(" ")(1).replace(".", "").replace(":",""), command)

    def translateStatus(status: String, command: String) =
      status match {
        case "PENDING" ⇒ JobState.Submitted
        case "RUNNING" ⇒ JobState.Running
        case "Result" ⇒ JobState.Done
        //case "......" ⇒ JobState.Failed -- TODO
        case _ ⇒ throw new RuntimeException("Unrecognized state " + status + "from command " + command)
      }
  }

  val scriptSuffix = ".sh" // what should it be?

  def submit[S](server: S, jobDescription: IEXECJobDescription)(implicit hn: HeadNode[S], system: Effect[System]): BatchJob =
    BatchScheduler.submit[S](
      jobDescription.workDirectory,
      _ ⇒ impl.toIexec(jobDescription),
      scriptSuffix,
      (f,_) ⇒ s"./${f}",
      impl.retreiveJobID,
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

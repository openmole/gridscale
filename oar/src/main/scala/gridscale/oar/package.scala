/*
 * Copyright (C) 2017 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package gridscale

import gridscale.effectaside._
import gridscale.cluster.{ BatchScheduler, HeadNode }
import squants._
import gridscale.tools._

package object oar {

  case class OARJobDescription(
    command: String,
    workDirectory: String,
    queue: Option[String] = None,
    cpu: Option[Int] = None,
    core: Option[Int] = None,
    wallTime: Option[Time] = None,
    bestEffort: Boolean = false)

  object impl {

    def toOAR(description: OARJobDescription) =
      s"""
         |#!/bin/bash
         |
         |${description.command}
       """.stripMargin

    def submissionCommand(description: OARJobDescription)(scriptName: String, uniqId: String) = {
      def commandLineResources(description: OARJobDescription) = {
        val attributes =
          List(description.cpu.map(c ⇒ s"cpu=$c"), description.core.map(c ⇒ s"core=$c")).flatten match {
            case Nil ⇒ ""
            case l   ⇒ "/" + l.mkString("/") + ","
          }
        attributes + description.wallTime.map(wt ⇒ s"walltime=" + wt.toHHmmss).getOrElse("")
      }

      def ressources = {
        val l = commandLineResources(description)
        if (!l.isEmpty) s"-l $l " else ""
      }

      s"oarsub -O${BatchScheduler.output(uniqId)} -E${BatchScheduler.error(uniqId)} " +
        s"${if (description.bestEffort) "-t besteffort " else ""}" +
        s"${description.queue.map(q ⇒ s"-q $q ").getOrElse("")}" +
        s"-d ${description.workDirectory} " +
        ressources +
        s"./${scriptName}"
    }

    def retrieveJobId(output: String) = {
      val oarJobId = "OAR_JOB_ID"
      val jobIdLine = output.split("\n").find(_.startsWith(oarJobId)).headOption.getOrElse(throw new RuntimeException("oarsub did not return a valid JobID in " + output))
      jobIdLine.split("=")(1)
    }

    def translateStatus(status: String) =
      status match {
        case "Waiting" | "toLaunch" | "Launching" | "toAckReservation" ⇒ JobState.Submitted
        case "Hold" | "Running" | "Finishing" | "Suspended" | "Resuming" ⇒ JobState.Running
        case "Terminated" ⇒ JobState.Done
        case "Error" | "toError" ⇒ JobState.Failed
        case _ ⇒ throw new RuntimeException("Unrecognized state " + status)
      }

    def parseState(executionResult: ExecutionResult, command: String): JobState = {
      executionResult.returnCode match {
        case 0 ⇒
          val status = executionResult.stdOut.split("\n").head.split(" ")(1)
          translateStatus(status)
        case r ⇒ throw new RuntimeException(ExecutionResult.error(command, executionResult))
      }
    }
  }

  import BatchScheduler._

  val scriptSuffix = ".oar"

  def submit[S](server: S, jobDescription: OARJobDescription)(implicit hn: HeadNode[S], system: Effect[System]): BatchJob =
    BatchScheduler.submit[S](
      jobDescription.workDirectory,
      _ ⇒ impl.toOAR(jobDescription),
      scriptSuffix,
      impl.submissionCommand(jobDescription),
      impl.retrieveJobId,
      server)

  def state[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): JobState =
    BatchScheduler.state[S](
      s"""oarstat -j ${job.jobId} -s""",
      impl.parseState)(server, job)

  def clean[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): Unit =
    BatchScheduler.clean[S](
      s"oardel ${job.jobId}",
      scriptSuffix)(server, job)

  def stdOut[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = BatchScheduler.stdOut[S](server, job)
  def stdErr[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = BatchScheduler.stdErr[S](server, job)

}

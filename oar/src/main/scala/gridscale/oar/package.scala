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

    def submissionCommand(description: OARJobDescription)(scriptName: String, uniqId: String) =
      def commandLineResources(description: OARJobDescription) =
        val attributes =
          List(description.cpu.map(c ⇒ s"cpu=$c"), description.core.map(c ⇒ s"core=$c")).flatten match
            case Nil ⇒ ""
            case l   ⇒ "/" + l.mkString("/") + ","

        attributes + description.wallTime.map(wt ⇒ s"walltime=" + wt.toHHmmss).getOrElse("")

      def ressources =
        val l = commandLineResources(description)
        if (!l.isEmpty) s"-l $l " else ""

      s"oarsub -O${BatchScheduler.output(uniqId)} -E${BatchScheduler.error(uniqId)} " +
        s"${if (description.bestEffort) "-t besteffort " else ""}" +
        s"${description.queue.map(q ⇒ s"-q $q ").getOrElse("")}" +
        s"-d ${description.workDirectory} " +
        ressources +
        s"./${scriptName}"

    def retrieveJobId(output: String) =
      val oarJobId = "OAR_JOB_ID"
      val jobIdLine = output.split("\n").find(_.startsWith(oarJobId)).headOption.getOrElse(throw new RuntimeException("oarsub did not return a valid JobID in " + output))
      jobIdLine.split("=")(1)

    def translateStatus(status: String) =
      status match
        case "Waiting" | "toLaunch" | "Launching" | "toAckReservation" ⇒ JobState.Submitted
        case "Hold" | "Running" | "Finishing" | "Suspended" | "Resuming" ⇒ JobState.Running
        case "Terminated" ⇒ JobState.Done
        case "Error" | "toError" ⇒ JobState.Failed
        case _ ⇒ throw new RuntimeException("Unrecognized state " + status)

    def parseState(executionResult: ExecutionResult, command: String): JobState =
      executionResult.returnCode match
        case 0 ⇒
          val status = executionResult.stdOut.split("\n").head.split(" ")(1)
          translateStatus(status)
        case r ⇒ throw new RuntimeException(ExecutionResult.error(command, executionResult))

  }

  import BatchScheduler._

  val scriptSuffix = ".oar"

  def submit(server: HeadNode, jobDescription: OARJobDescription): BatchJob =
    BatchScheduler.submit(
      jobDescription.workDirectory,
      _ ⇒ impl.toOAR(jobDescription),
      scriptSuffix,
      impl.submissionCommand(jobDescription),
      impl.retrieveJobId,
      server)

  def state(server: HeadNode, job: BatchJob): JobState =
    BatchScheduler.state(
      s"""oarstat -j ${job.jobId} -s""",
      impl.parseState)(server, job)

  def clean(server: HeadNode, job: BatchJob): Unit =
    BatchScheduler.clean(
      s"oardel ${job.jobId}",
      scriptSuffix)(server, job)

  def stdOut(server: HeadNode, job: BatchJob): String = BatchScheduler.stdOut(server, job)
  def stdErr(server: HeadNode, job: BatchJob): String = BatchScheduler.stdErr(server, job)

}

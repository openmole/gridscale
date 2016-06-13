/*
 * Copyright (C) 2012 Romain
 * Copyright (C) 2014 Jonathan Passerat-Palmbach
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

package fr.iscpif.gridscale.condor

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.ssh.SSHJobService._
import fr.iscpif.gridscale.ssh._
import fr.iscpif.gridscale.tools.shell.BashShell

import scala.concurrent.duration._
import scala.util.Try

object CondorJobService {

  def apply(host: String, port: Int = 22, timeout: Duration = 1 minute)(implicit credential: SSHAuthentication) = {
    val (_host, _port, _credential, _timeout) = (host, port, credential, timeout)
    new CondorJobService {
      override val credential = _credential
      override val host = _host
      override val port = _port
      override val timeout = _timeout
    }
  }

  class CondorJob(val description: CondorJobDescription, val condorId: String)

  object CondorJob {
    def apply(condorDescription: CondorJobDescription, condorId: String) = new CondorJob(condorDescription, condorId)
  }

  def translateStatus(status: String) =
    status match {
      case "3" | "4"       ⇒ Done
      case "2"             ⇒ Running
      // choice was made to characterize held jobs (status=5) as submitted instead of Running
      case "0" | "1" | "5" ⇒ Submitted
      case "6"             ⇒ Failed
      case _               ⇒ throw new RuntimeException("Unrecognized state " + status)
    }
}

import fr.iscpif.gridscale.condor.CondorJobService._

trait CondorJobService extends JobService with SSHHost with SSHStorage with BashShell {
  type J = CondorJob
  type D = CondorJobDescription

  def submit(description: D): J = withConnection { implicit connection ⇒
    exec("mkdir -p " + description.workDirectory)
    write(description.toCondor.getBytes, condorScriptPath(description))

    val command = "cd " + description.workDirectory + " && condor_submit " + description.uniqId + ".condor"

    val (ret, output, error) = execReturnCodeOutput(command)
    if (0 != ret) throw exception(ret, command, output, error)

    val jobId = output.trim.reverse.tail.takeWhile(_ != ' ').reverse
    if (jobId.isEmpty) throw exception(ret, command, output, error)

    CondorJob(description, jobId)
  }

  def state(job: J): JobState = withConnection { implicit connection ⇒
    // NOTE: submission sends only 1 process per cluster for the moment so no need to query the process id

    // if the job is still running, his state is returned by condor_q...
    val queryInQueue = "condor_q " + job.condorId + " -long -attributes JobStatus"

    val (retInQueue, outputInQueue, errorInQueue) = execReturnCodeOutput(queryInQueue)

    retInQueue.toInt match {
      case 0 if (!outputInQueue.isEmpty) ⇒ {
        // when split, the actual state is the last member of a 2-element array
        val state = outputInQueue.split('=').map(_ trim).last
        translateStatus(state)
      }
      case 0 ⇒ {
        // ...but if the job is already completed, his state is returned by condor_history...
        val queryFinished = "condor_history " + job.condorId + " -long"

        val (retFinished, outputFinished, errorFinished) = execReturnCodeOutput(queryFinished)

        retFinished.toInt match {
          case 0 if (!outputFinished.isEmpty) ⇒ {
            // can't match it with a regex from the ouput for some reason...
            // resulting in this ugly one-liner...
            val state = outputFinished.split("\n").filter(_ matches "^JobStatus = .*").head.split('=').map(_ trim).last
            translateStatus(state)
          }
          case _ ⇒ throw exception(retFinished, queryFinished, outputFinished, errorFinished)
        }
      }
      case _ ⇒ throw exception(retInQueue, queryInQueue, outputInQueue, errorInQueue)
    }

  }

  private def cancel(job: J) = withConnection { exec("condor_rm " + job.condorId)(_) }

  // Purge output, error and job script
  def delete(job: J) = Try {
    try cancel(job)
    finally withSftpClient { c ⇒
      rmFileWithClient(condorScriptPath(job.description))(c)
      rmFileWithClient(job.description.workDirectory + "/" + job.description.output)(c)
      rmFileWithClient(job.description.workDirectory + "/" + job.description.error)(c)
    }
  }

  def condorScriptPath(description: D) = description.workDirectory + "/" + description.uniqId + ".condor"
}

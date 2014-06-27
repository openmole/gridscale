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

import fr.iscpif.gridscale._
import fr.iscpif.gridscale.ssh._
import SSHJobService._

object CondorJobService {
  class CondorJob(val description: CondorJobDescription, val condorId: String)
}

import CondorJobService._

trait CondorJobService extends JobService with SSHHost with SSHStorage {
  type J = CondorJob
  type D = CondorJobDescription

  // assume bash will be available on most systems
  // bash -ci will fake an interactive shell (-i) in order to load the config files
  // as an interactive ssh shell would (~/.bashrc, /etc/bashrc)
  // and run the sequence of command without interaction (-c)
  def baseCommand = "bash -ci \""

  def submit(description: D)(implicit credential: A): J = withConnection { c ⇒
    withSession(c) { exec(_, "mkdir -p " + description.workDirectory) }
    val outputStream = openOutputStream(condorScriptPath(description))
    try outputStream.write(description.toCondor.getBytes)
    finally outputStream.close

    withSession(c) { session ⇒
      val command = baseCommand + "cd " + description.workDirectory + " && condor_submit " + description.uniqId + ".condor\""

      val (ret, output, error) = execReturnCodeOutput(session, command)
      if (0 != ret) throw exception(ret, command, output, error)

      val jobId = output.trim.reverse.tail.takeWhile(_ != ' ').reverse
      if (jobId.isEmpty) throw exception(ret, command, output, error)

      new CondorJob(description, jobId)
    }
  }

  def state(job: J)(implicit credential: A): JobState = withConnection { c ⇒
    // NOTE: submission sends only 1 process per cluster for the moment so no need to query the process id

    // if the job is still running, his state is returned by condor_q...
    val queryInQueue = baseCommand + "condor_q " + job.condorId + " -long -attributes JobStatus\""

    val (retInQueue, outputInQueue, errorInQueue) = withSession(c) {
      session ⇒
        execReturnCodeOutput(session, queryInQueue)
    }

    retInQueue.toInt match {
      case 0 if (!outputInQueue.isEmpty) ⇒ {
        // when split, the actual state is the last member of a 2-element array
        val state = outputInQueue.split('=').map(_ trim).last
        translateStatus(state)
      }
      case 0 ⇒ {
        // ...but if the job is already completed, his state is returned by condor_history...
        val queryFinished = baseCommand + "condor_history " + job.condorId + " -long\""

        val (retFinished, outputFinished, errorFinished) = withSession(c) {
          session ⇒ execReturnCodeOutput(session, queryFinished)
        }

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

  def cancel(job: J)(implicit credential: A) = withConnection(withSession(_) { exec(_, baseCommand + "condor_rm " + job.condorId + "\"") })

  // Purge output, error and job script
  def purge(job: J)(implicit credential: A) = withSftpClient { c ⇒
    rmFileWithClient(condorScriptPath(job.description))(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.output)(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.error)(c)
  }

  def condorScriptPath(description: D) = description.workDirectory + "/" + description.uniqId + ".condor"

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

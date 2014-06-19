/*
 * Copyright (C) 2012 Romain Reuillon
 * Copyright (C) 2012 Jonathan Passerat-Palmbach
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

package fr.iscpif.gridscale.slurm

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.ssh._
import SSHJobService._
import fr.iscpif.gridscale.tools.shell.BashShell

object SLURMJobService {
  class SLURMJob(val description: SLURMJobDescription, val slurmId: String)

  val jobStateAttribute = "JobState"
}

import SLURMJobService._

trait SLURMJobService extends JobService with SSHHost with SSHStorage with BashShell {
  type J = SLURMJob
  type D = SLURMJobDescription

  def submit(description: D): J = withConnection { c ⇒
    withSession(c) { s ⇒ exec(s, "mkdir -p " + description.workDirectory) }
    val outputStream = openOutputStream(slurmScriptPath(description))
    try outputStream.write(description.toSLURM.getBytes)
    finally outputStream.close

    withSession(c) { s ⇒
      val command = "cd " + description.workDirectory + " ; sbatch " + description.uniqId + ".slurm"
      val (ret, output, error) = execReturnCodeOutput(s, command)

      val jobId = output.trim.reverse.takeWhile(_ != ' ').reverse

      if (ret != 0 || jobId.isEmpty) throw exception(ret, command, output, error)
      new SLURMJob(description, jobId)
    }
  }

  def state(job: J): JobState = withConnection { c ⇒
    val command = "scontrol show job " + job.slurmId

    withSession(c) { s ⇒
      val (ret, output, error) = execReturnCodeOutput(s, command)
      val lines = output.split("\n").map(_.trim)
      val state = lines.filter(_.matches(".*JobState=.*")).map {
        prop ⇒
          val splits = prop.split('=')
          splits(0).trim -> splits(1).trim.split(' ')(0)
      }.toMap.getOrElse(jobStateAttribute, throw new RuntimeException(s"State not found in scontrol output, return=$ret, output=$output, error=$error."))
      translateStatus(ret, state)
    }
  }

  def cancel(job: J) = withConnection(withSession(_) { exec(_, "scancel " + job.slurmId) })

  //Purge output error job script
  def purge(job: J) = withSftpClient { c ⇒
    rmFileWithClient(slurmScriptPath(job.description))(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.output)(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.error)(c)
  }

  private def slurmScriptPath(description: D) = description.workDirectory + "/" + description.uniqId + ".slurm"

  private def translateStatus(retCode: Int, status: String) =
    status match {
      case "CANCELLED" | "COMPLETED" ⇒ Done
      case "RUNNING" | "COMPLETING" ⇒ Running
      case "CONFIGURING" | "PENDING" | "SUSPENDED" ⇒ Submitted
      case "FAILED" | "NODE_FAIL" | "PREEMPTED" | "TIMEOUT" ⇒ Failed
      case _ ⇒ throw new RuntimeException("Unrecognized state " + status)
    }

}

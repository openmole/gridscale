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

package fr.iscpif.gridscale.jobservice

import ch.ethz.ssh2.StreamGobbler
import fr.iscpif.gridscale.authentication._
import fr.iscpif.gridscale.tools._
import fr.iscpif.gridscale.storage._
import SSHJobService._
import java.io.BufferedReader
import java.io.InputStreamReader

object SLURMJobService {
  class SLURMJob(val description: SLURMJobDescription, val slurmId: String)

  val jobStateAttribute = "JobState"
}

import SLURMJobService._

trait SLURMJobService extends JobService with SSHHost with SSHStorage {
  type J = SLURMJob
  type D = SLURMJobDescription

  def submit(description: D)(implicit credential: A): J = withConnection { c ⇒
    withSession(c) { s ⇒ exec(s, "mkdir -p " + description.workDirectory) }
    val outputStream = openOutputStream(slurmScriptPath(description))
    try outputStream.write(description.toSLURM.getBytes)
    finally outputStream.close

    withSession(c) { s ⇒
      exec(s, "cd " + description.workDirectory + " ; sbatch " + description.uniqId + ".slurm")

      val stdout = new StreamGobbler(s.getStdout)
      val br = new BufferedReader(new InputStreamReader(stdout))
      val jobId = try {
        val r = ".* ([0-9]+)".r
        br.readLine match {
          case r(id) ⇒ id
          case _ ⇒ null
        }
      } finally br.close

      if (jobId == null) throw new RuntimeException("sbatch did not return a JobID")

      new SLURMJob(description, jobId, getNodeList(jobId))
    }
  }

  def state(job: J)(implicit credential: A): JobState = withConnection { c ⇒
    val command = "scontrol show job " + job.slurmId

    withSession(c) { s ⇒
      val ret = execReturnCode(s, command)

      val br = new BufferedReader(new InputStreamReader(new StreamGobbler(s.getStdout)))
      try {
        val lines = Iterator.continually(br.readLine).takeWhile(_ != null).map(_.trim)
        val state = lines.filter(_.matches(".*JobState=.*")).map {
          prop ⇒
            val splits = prop.split('=')
            splits(0).trim -> splits(1).trim.split(' ')(0)
        }.toMap.getOrElse(jobStateAttribute, throw new RuntimeException("State not found in scontrol output."))
        translateStatus(ret, state)
      } finally br.close
    }
  }

  def cancel(job: J)(implicit credential: A) = withConnection(withSession(_) { exec(_, "scancel " + job.slurmId) })

  //Purge output error job script
  def purge(job: J)(implicit credential: A) = withSftpClient { c ⇒
    rmFileWithClient(slurmScriptPath(job.description))(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.output)(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.error)(c)
  }

  private def slurmScriptPath(description: D) = description.workDirectory + "/" + description.uniqId + ".slurm"

  /**
   * Get node list by performing call to scontrol just after job
   * has been successfully submitted.
   * In scontrol output, nodes are comma-separated.
   * @param jobId Integer identifying the job in SLURM.
   * @return The list of nodes allocated to the job
   */

  private def translateStatus(retCode: Int, status: String) =
    status match {
      case "CANCELLED" | "COMPLETED" ⇒ Done
      case "RUNNING" | "COMPLETING" ⇒ Running
      case "CONFIGURING" | "PENDING" | "SUSPENDED" ⇒ Submitted
      case "FAILED" | "NODE_FAIL" | "PREEMPTED" | "TIMEOUT" ⇒ Failed
      case _ ⇒ throw new RuntimeException("Unrecognized state " + status)
    }

}

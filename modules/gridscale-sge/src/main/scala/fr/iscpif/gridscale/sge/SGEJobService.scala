/*
 * Copyright (C) 2012 Romain Reuillon
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

package fr.iscpif.gridscale.sge

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.ssh._
import SSHJobService._
import fr.iscpif.gridscale.tools.shell.BashShell

object SGEJobService {
  class SGEJob(val description: SGEJobDescription, val sgeId: String)

  object SGEJob {
    def apply(description: SGEJobDescription, sgeId: String) = new SGEJob(description, sgeId)
  }

  val jobStateAttribute = "JOB_STATE"

  /**
   * Match SGE's states to 1 of the 4 generic states available in GridScale
   * Arbitrary choices were made for SGE's Suspended states that either relate to
   * Running or Submitted in GridScale.
   * @param status The original state collected from the SGE job scheduler
   * @return Corresponding state in GridScale
   * @throws RuntimeException when the input state can't be recognized.
   */
  def translateStatus(status: String) =
    status match {
      case "qw" | "hqw" | "hRwq" | "Rs" | "Rts" | "RS" | "RtS" | "RT" | "RtT" ⇒ Submitted
      case "r" | "t" | "Rr" | "Rt" | "T" | "tT" | "s" | "ts" | "S" | "tS" ⇒ Running
      case "" | "dr" | "dt" | "dRr" | "dRt" | "ds" | "dS" | "dT" | "dRs" | "dRS" | "dRT" ⇒ Done
      case "Eqw" | "Ehqw" | "EhRqw" ⇒ Failed
      case _ ⇒ throw new RuntimeException("Unrecognized state " + status)
    }
}

import SGEJobService._

trait SGEJobService extends JobService with SSHHost with SSHStorage with BashShell {
  type J = SGEJob
  type D = SGEJobDescription

  def submit(description: D): J = withConnection { implicit connection ⇒
    exec("mkdir -p " + description.workDirectory)
    val outputStream = openOutputStream(sgeScriptPath(description))
    try outputStream.write(description.toSGE.getBytes)
    finally outputStream.close

    val command = "cd " + description.workDirectory + " && qsub " + sgeScriptName(description)
    val (ret, out, error) = execReturnCodeOutput(command)
    if (ret != 0) throw exception(ret, command, out, error)

    val jobId = out.split(" ").drop(2).head

    if (!jobId.forall(_.isDigit)) throw new RuntimeException("qsub did not return a valid JobID in " + out)
    SGEJob(description, jobId)
  }

  def state(job: J): JobState = withConnection { implicit connection ⇒
    val command = s"""qstat | sed 's/^  *//g'  |  grep '^${job.sgeId} ' | sed 's/  */ /g' | cut -d' ' -f5"""

    val (ret, output, error) = execReturnCodeOutput(command)

    ret.toInt match {
      case 0 ⇒
        val status = output.dropRight(1)
        translateStatus(status)
      case r ⇒ throw exception(ret, command, output, error)
    }
  }

  def cancel(job: J) = withConnection { exec("qdel " + job.sgeId)(_) }

  //Purge output error job script
  // TODO purge log as well
  def purge(job: J) = withSftpClient { implicit c ⇒
    rmFileWithClient(sgeScriptPath(job.description))
    rmFileWithClient(job.description.workDirectory + "/" + job.description.output)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.error)
  }

  def sgeScriptName(description: D) = "job" + description.uniqId + ".sge"
  def sgeScriptPath(description: D) = description.workDirectory + "/" + sgeScriptName(description)
}

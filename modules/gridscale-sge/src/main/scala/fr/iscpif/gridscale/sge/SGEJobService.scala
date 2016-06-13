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

import java.io.ByteArrayInputStream

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.ssh.SSHJobService._
import fr.iscpif.gridscale.ssh._
import fr.iscpif.gridscale.tools.shell.BashShell

import scala.concurrent.duration._
import scala.util.Try

object SGEJobService {

  def apply(host: String, port: Int = 22, timeout: Duration = 1 minute)(implicit credential: SSHAuthentication) = {
    val (_host, _port, _credential, _timeout) = (host, port, credential, timeout)
    new SGEJobService {
      override val credential = _credential
      override val host = _host
      override val port = _port
      override val timeout = _timeout
    }
  }

  class SGEJob(val description: SGEJobDescription, val sgeId: String)

  object SGEJob {
    def apply(description: SGEJobDescription, sgeId: String) = new SGEJob(description, sgeId)
  }

  val jobStateAttribute = "JOB_STATE"

  /**
   * Match SGE's states to 1 of the 4 generic states available in GridScale
   * Arbitrary choices were made for SGE's Suspended states that either relate to
   * Running or Submitted in GridScale.
   *
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

import fr.iscpif.gridscale.sge.SGEJobService._

trait SGEJobService extends JobService with SSHHost with SSHStorage with BashShell {
  type J = SGEJob
  type D = SGEJobDescription

  def submit(description: D): J = withConnection { implicit connection ⇒
    exec("mkdir -p " + description.workDirectory)
    write(description.toSGE.getBytes, sgeScriptPath(description))

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

  //FIXME should not throw exception if job does not exist
  def cancel(job: J) = withConnection { exec("qdel " + job.sgeId)(_) }

  // TODO purge log as well
  def delete(job: J) = Try {
    try cancel(job)
    finally withSftpClient { implicit c ⇒
      rmFileWithClient(sgeScriptPath(job.description))
      rmFileWithClient(job.description.workDirectory + "/" + job.description.output)
      Try(rmFileWithClient(job.description.workDirectory + "/" + job.description.error))
    }
  }

  def sgeScriptName(description: D) = "job" + description.uniqId + ".sge"
  def sgeScriptPath(description: D) = description.workDirectory + "/" + sgeScriptName(description)

}

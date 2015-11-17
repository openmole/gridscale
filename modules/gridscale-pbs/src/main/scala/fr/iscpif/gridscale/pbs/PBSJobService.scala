/*
 * Copyright (C) 2012 Romain Reuillon
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

package fr.iscpif.gridscale.pbs

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.ssh.SSHJobService._
import fr.iscpif.gridscale.ssh._
import fr.iscpif.gridscale.tools.shell.BashShell

object PBSJobService {

  def apply(host: String, port: Int = 22)(implicit credential: SSHAuthentication) = {
    val (_host, _port, _credential) = (host, port, credential)
    new PBSJobService {
      override def credential = _credential
      override def host = _host
      override def port = _port
    }
  }

  class PBSJob(val description: PBSJobDescription, val pbsId: String)

  val jobStateAttribute = "JOB_STATE"
}

import fr.iscpif.gridscale.pbs.PBSJobService._

trait PBSJobService extends JobService with SSHHost with SSHStorage with BashShell {
  type J = PBSJob
  type D = PBSJobDescription

  def submit(description: D): J = withConnection { implicit connection ⇒
    exec("mkdir -p " + description.workDirectory)
    val outputStream = openOutputStream(pbsScriptPath(description))
    try outputStream.write(description.toPBS.getBytes)
    finally outputStream.close

    val command = "cd " + description.workDirectory + " && qsub " + pbsScriptName(description)
    val (ret, jobId, error) = execReturnCodeOutput(command)
    if (ret != 0) throw exception(ret, command, jobId, error)
    if (jobId == null) throw new RuntimeException("qsub did not return a JobID")
    new PBSJob(description, jobId)

  }

  def state(job: J): JobState = withConnection { implicit connection ⇒
    val command = "qstat -f " + job.pbsId

    val (ret, output, error) = execReturnCodeOutput(command)

    ret.toInt match {
      case 153 ⇒ Done
      case 0 ⇒
        val lines = output.split("\n").map(_.trim)
        val state = lines.filter(_.matches(".*=.*")).map {
          prop ⇒
            val splited = prop.split('=')
            splited(0).trim.toUpperCase -> splited(1).trim
        }.toMap.getOrElse(jobStateAttribute, throw new RuntimeException("State not found in qstat output: " + output))
        translateStatus(ret, state)
      case r ⇒ throw exception(ret, command, output, error)
    }
  }

  def cancel(job: J) = withConnection { exec("qdel " + job.pbsId)(_) }

  //Purge output error job script
  def purge(job: J) = withSftpClient { c ⇒
    rmFileWithClient(pbsScriptPath(job.description))(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.output)(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.error)(c)
  }

  def pbsScriptName(description: D) = description.uniqId + ".pbs"
  def pbsScriptPath(description: D) = description.workDirectory + "/" + pbsScriptName(description)

  def translateStatus(retCode: Int, status: String) =
    status match {
      case "R" | "E" | "H" | "S" ⇒ Running
      case "Q" | "W" | "T"       ⇒ Submitted
      case _                     ⇒ throw new RuntimeException("Unrecognized state " + status)
    }

}

/*
 * Copyright (C) 2014 Romain Reuillon
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

package fr.iscpif.gridscale.oar

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.ssh.SSHJobService._
import fr.iscpif.gridscale.ssh.{ SSHAuthentication, SSHHost, SSHStorage }
import fr.iscpif.gridscale.tools.shell.BashShell
import net.schmizz.sshj.xfer.FilePermission

object OARJobService {

  def apply(host: String, port: Int = 22)(implicit credential: SSHAuthentication) = {
    val (_host, _port, _credential) = (host, port, credential)
    new OARJobService {
      override def credential = _credential
      override def host = _host
      override def port = _port
    }
  }

  case class OARJob(val description: OARJobDescription, val id: String)
  val oarJobId = "OAR_JOB_ID"

  def translateStatus(status: String) =
    status match {
      case "Waiting" | "toLaunch" | "Launching" | "toAckReservation" ⇒ Submitted
      case "Hold" | "Running" | "Finishing" | "Suspended" | "Resuming" ⇒ Running
      case "Terminated" ⇒ Done
      case "Error" | "toError" ⇒ Failed
      case _ ⇒ throw new RuntimeException("Unrecognized state " + status)
    }

}

import fr.iscpif.gridscale.oar.OARJobService._

trait OARJobService extends JobService with SSHHost with SSHStorage with BashShell {
  type J = OARJob
  type D = OARJobDescription

  def submit(description: D): J = withConnection { implicit connection ⇒
    exec("mkdir -p " + description.workDirectory)

    val script = oarScriptPath(description)

    val outputStream = openOutputStream(script)
    try outputStream.write(description.toOAR.getBytes)
    finally outputStream.close

    chmod(script, FilePermission.USR_RWX)

    def ressources = {
      val l = description.commandLineResources
      if (!l.isEmpty) s"-l $l " else ""
    }

    val command =
      s"oarsub -O${description.output} -E${description.error} " +
        s"${if (description.bestEffort) "-t besteffort " else ""}" +
        s"${description.queue.map(q ⇒ s"-q $q ").getOrElse("")}" +
        s"-d ${description.workDirectory} " +
        ressources +
        s"${description.workDirectory}/${oarScriptName(description)}"

    val (ret, out, error) = execReturnCodeOutput(command)
    if (ret != 0) throw exception(ret, command, out, error)

    val jobIdLine = out.split("\n").find(_.startsWith(oarJobId)).headOption.getOrElse(throw new RuntimeException("oarsub did not return a valid JobID in " + out))
    val jobId = jobIdLine.split("=")(1)
    OARJob(description, jobId)
  }

  def state(job: J): JobState = withConnection { implicit connection ⇒
    val command = s"""oarstat -j ${job.id} -s"""

    val (ret, output, error) = execReturnCodeOutput(command)

    ret.toInt match {
      case 0 ⇒
        val status = output.split("\n").head.split(" ")(1)
        translateStatus(status)
      case r ⇒ throw exception(ret, command, output, error)
    }
  }

  def cancel(job: J) = withConnection {
    exec("oar " + job.id)(_)
  }

  def purge(job: J) = withSftpClient { implicit c ⇒
    rmFileWithClient(oarScriptPath(job.description))
    rmFileWithClient(job.description.workDirectory + "/" + job.description.output)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.error)
  }

  def oarScriptName(description: D) = "job" + description.uniqId + ".oar"
  def oarScriptPath(description: D) = description.workDirectory + "/" + oarScriptName(description)
}

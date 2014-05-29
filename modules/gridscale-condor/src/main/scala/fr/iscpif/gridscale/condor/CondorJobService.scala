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

  val jobStateAttribute = "JOB_STATE"
}

import CondorJobService._

trait CondorJobService extends JobService with SSHHost with SSHStorage {
  type J = CondorJob
  type D = CondorJobDescription

  def sourceBashRC = "source ~/.bashrc ; "

  def submit(description: D)(implicit credential: A): J = withConnection { c ⇒
    withSession(c) { exec(_, "mkdir -p " + description.workDirectory) }
    val outputStream = openOutputStream(condorScriptPath(description))
    try outputStream.write(description.toCondor.getBytes)
    finally outputStream.close

    withSession(c) { session ⇒
      val command = "bash -c \"source ~/.bashrc && cd " + description.workDirectory + " && condor_submit " + description.uniqId + ".condor\""

      val (ret, output, error) = execReturnCodeOutput(session, command)
      if (0 != ret) throw exception(ret, command, output, error)

      val jobId = output.trim.reverse.tail.takeWhile(_ != ' ')
      if (jobId == null || jobId.isEmpty) throw exception(ret, command, output, error)

      new CondorJob(description, jobId)
    }
  }

  def state(job: J)(implicit credential: A): JobState = withConnection(withSession(_) { session ⇒
    val command = sourceBashRC + "qstat -f " + job.condorId

    val (ret, output, error) = execReturnCodeOutput(session, command)

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
  })

  def cancel(job: J)(implicit credential: A) = withConnection(withSession(_) { exec(_, sourceBashRC + "qdel " + job.condorId) })

  //Purge output error job script
  def purge(job: J)(implicit credential: A) = withSftpClient { c ⇒
    rmFileWithClient(condorScriptPath(job.description))(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.output)(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.error)(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.log)(c)
  }

  def condorScriptPath(description: D) = description.workDirectory + "/" + description.uniqId + ".condor"

  def translateStatus(retCode: Int, status: String) =
    status match {
      case "C" | "X" ⇒ Done
      case "R" ⇒ Running
      case "I" | "H" ⇒ Submitted
      case "U" ⇒ Failed
      case _ ⇒ throw new RuntimeException("Unrecognized state " + status)
    }

}

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

package fr.iscpif.gridscale.jobservice

import ch.ethz.ssh2.StreamGobbler
import fr.iscpif.gridscale.authentication._
import fr.iscpif.gridscale.tools._
import fr.iscpif.gridscale.storage._
import SSHJobService._
import java.io.BufferedReader
import java.io.InputStreamReader

object PBSJobService {
  class PBSJob(val description: PBSJobDescription, val pbsId: String)

  val jobStateAttribute = "JOB_STATE"
}

import PBSJobService._

trait PBSJobService extends JobService with SSHHost with SSHStorage {
  type J = PBSJob
  type D = PBSJobDescription

  def submit(description: D)(implicit credential: A): J = withConnection { c ⇒
    withSession(c) { exec(_, "mkdir -p " + description.workDirectory) }
    val outputStream = openOutputStream(pbsScriptPath(description))
    try outputStream.write(description.toPBS.getBytes)
    finally outputStream.close

    withSession(c) { session ⇒
      exec(session, "cd " + description.workDirectory + " ; qsub " + description.uniqId + ".pbs")
      val stdout = new StreamGobbler(session.getStdout)
      val br = new BufferedReader(new InputStreamReader(stdout))
      val jobId = try br.readLine finally br.close
      if (jobId == null) throw new RuntimeException("qsub did not return a JobID")
      new PBSJob(description, jobId)
    }
  }

  def state(job: J)(implicit credential: A): JobState = withConnection(withSession(_) { session ⇒
    val command = "qstat -f -1 " + job.pbsId

    val ret = execReturnCode(session, command)

    if (ret == 153) Done
    else {
      val br = new BufferedReader(new InputStreamReader(new StreamGobbler(session.getStdout)))
      try {
        val lines = Iterator.continually(br.readLine).takeWhile(_ != null).map(_.trim)

        val state = lines.filter(_.matches(".*=.*")).map {
          prop ⇒
            val splited = prop.split('=')
            splited(0).trim.toUpperCase -> splited(1).trim
        }.toMap.getOrElse(jobStateAttribute, throw new RuntimeException("State not found in qstat output."))
        translateStatus(ret, state)
      } finally br.close
    }
  })

  def cancel(job: J)(implicit credential: A) = withConnection(withSession(_) { exec(_, "qdel " + job.pbsId) })

  //Purge output error job script
  def purge(job: J)(implicit credential: A) = withSftpClient { c ⇒
    rmFileWithClient(pbsScriptPath(job.description))(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.output)(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.error)(c)
  }

  def pbsScriptPath(description: D) = description.workDirectory + "/" + description.uniqId + ".pbs"

  def translateStatus(retCode: Int, status: String) =
    status match {
      case "R" | "E" | "H" | "S" ⇒ Running
      case "Q" | "W" | "T" ⇒ Submitted
      case _ ⇒ throw new RuntimeException("Unrecognized state " + status)
    }

}

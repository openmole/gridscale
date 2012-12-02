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

import fr.iscpif.gridscale.tools._
import fr.iscpif.gridscale.storage.SSHStorage
import java.io.ByteArrayOutputStream
import java.util.UUID
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.common.IOUtils

object SSHJobService {

  val bufferSize = 65535
  val timeout = 120

  val rootDir = ".gridscale/ssh"

  def file(jobId: String, suffix: String) = rootDir + "/" + jobId + "." + suffix
  def pidFile(jobId: String) = file(jobId, "pid")
  def endCodeFile(jobId: String) = file(jobId, "end")
  def outFile(jobId: String) = file(jobId, "out")
  def errFile(jobId: String) = file(jobId, "err")

  val PROCESS_CANCELED = 143
  val COMMAND_NOT_FOUND = 127

  /*def exec (connection: Connection, cde: String): Unit = {
    val session = connection.openSession
    try {
      exec(session, cde) 
      if(session.getExitStatus != 0) throw new RuntimeException("Return code was no 0 but " + session.getExitStatus)
    } finally session.close
  } */

  def withSession[T](c: SSHClient)(f: Session ⇒ T): T = {
    val session = c.startSession
    try f(session)
    finally session.close
  }

  def execReturnCode(session: Session, cde: String) = {
    val cmd = session.exec(cde)
    try {
      cmd.join
      cmd.getExitStatus
    } finally cmd.close
  }

  def execReturnCodeOutput(session: Session, cde: String) = {
    val cmd = session.exec(cde)
    try {
      cmd.join
      (cmd.getExitStatus, IOUtils.readFully(cmd.getInputStream).toString)
    } finally cmd.close
  }

  def exec(session: Session, cde: String) = {
    val retCode = execReturnCode(session, cde)
    if (retCode != 0) throw new RuntimeException("Return code was no 0 but " + retCode)
  }

}

import SSHJobService._

trait SSHJobService extends JobService with SSHHost with SSHStorage { js ⇒
  type J = String
  type D = SSHJobDescription

  def submit(description: D)(implicit credential: A): J = {
    val jobId = UUID.randomUUID.toString
    val command = new ScriptBuffer

    def absolute(path: String) = "$HOME/" + path

    command += "mkdir -p " + absolute(rootDir)
    command += "mkdir -p " + description.workDirectory
    command += "cd " + description.workDirectory

    val executable = description.executable + " " + description.arguments

    val jobDir =
      command += "((" +
        executable +
        " > " + absolute(outFile(jobId)) + " 2> " + absolute(errFile(jobId)) + " ; " +
        " echo $? > " + absolute(endCodeFile(jobId)) + ") & " +
        "echo $! > " + absolute(pidFile(jobId)) + " )"

    withConnection(withSession(_) { exec(_, "bash -c '" + command.toString + "'") })
    jobId
  }

  def state(job: J)(implicit credential: A): JobState =
    if (exists(endCodeFile(job))) {
      val is = openInputStream(endCodeFile(job))
      val content =
        try getBytes(is, bufferSize, SSHJobService.timeout)
        finally is.close

      translateState(new String(content).takeWhile(_.isDigit).toInt)
    } else Running

  def cancel(jobId: J)(implicit credential: A) = withConnection(withSession(_) {
    s ⇒
      val cde = "kill `cat " + pidFile(jobId) + "`;"
      exec(s, cde)
  })

  def purge(jobId: String)(implicit credential: A) = withConnection(withSession(_) {
    s ⇒
      val cde = "rm -rf " + rootDir + "/" + jobId + "*"
      exec(s, cde)
  })

  private def translateState(retCode: Int) =
    if (retCode >= 0)
      if (retCode == PROCESS_CANCELED) Failed
      else if (retCode == COMMAND_NOT_FOUND) Failed
      else Done
    else Failed

}

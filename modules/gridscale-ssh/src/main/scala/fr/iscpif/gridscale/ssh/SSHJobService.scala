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

package fr.iscpif.gridscale.ssh

import java.util.UUID

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.tools._
import fr.iscpif.gridscale.tools.shell._
import net.schmizz.sshj.common.IOUtils

import scala.concurrent.duration._
import scala.util.Try

object SSHJobService {

  def apply(host: String, port: Int = 22, timeout: Duration = 1 minute)(implicit credential: SSHAuthentication) = {
    val (_port, _host, _credential, _timeout) = (port, host, credential, timeout)

    new SSHJobService {
      override val credential = _credential
      override val port = _port
      override val host = _host
      override val timeout = _timeout
    }
  }

  // val rootDir = ".gridscale/ssh"

  def file(dir: String, jobId: String, suffix: String) = dir + "/" + jobId + "." + suffix
  def pidFile(dir: String, jobId: String) = file(dir, jobId, "pid")
  def endCodeFile(dir: String, jobId: String) = file(dir, jobId, "end")
  def outFile(dir: String, jobId: String) = file(dir, jobId, "out")
  def errFile(dir: String, jobId: String) = file(dir, jobId, "err")

  //val PROCESS_CANCELED = 143
  //val COMMAND_NOT_FOUND = 127

  /*def exec (connection: Connection, cde: String): Unit = {
    val session = connection.openSession
    try {
      exec(session, cde) 
      if(session.getExitStatus != 0) throw new RuntimeException("Return code was no 0 but " + session.getExitStatus)
    } finally session.close
  } */

  def withSession[T](c: SSHClient)(f: SSHSession ⇒ T): T = {
    val session = c.startSession
    try f(session)
    finally session.close
  }

  def execReturnCode(cde: Command)(implicit client: SSHClient) = withSession(client) { session ⇒
    val cmd = session.exec(cde.toString)
    try {
      cmd.join
      cmd.getExitStatus
    } finally cmd.close
  }

  def execReturnCodeOutput(cde: Command)(implicit client: SSHClient) = withSession(client) { session ⇒
    val cmd = session.exec(cde.toString)
    try {
      cmd.join
      (cmd.getExitStatus, IOUtils.readFully(cmd.getInputStream).toString, IOUtils.readFully(cmd.getErrorStream).toString)
    } finally cmd.close
  }

  def exec(cde: Command)(implicit client: SSHClient) = withSession(client) { session ⇒
    val retCode = execReturnCode(cde)
    if (retCode != 0) throw new RuntimeException("Return code was no 0 but " + retCode + " while executing " + cde)
  }

  def launch(cde: Command)(implicit client: SSHClient) = withSession(client) {
    _.exec(cde.toString).close
  }

  def exception(ret: Int, command: String, output: String, error: String) = new RuntimeException(s"Unexpected return code $ret, when running $command (stdout=$output, stderr=$error")

  case class JobId(jobId: String, workDirectory: String)
}

import fr.iscpif.gridscale.ssh.SSHJobService._

trait SSHJobService extends JobService with SSHHost with SSHStorage with BashShell { js ⇒
  type J = JobId
  type D = SSHJobDescription

  def bufferSize = 65535
  def timeout: Duration

  def toScript(description: D, background: Boolean = true) = {
    val jobId = UUID.randomUUID.toString

    def absolute(path: String) = description.workDirectory + "/" + path

    def executable = description.executable + " " + description.arguments

    val command =
      s"""
         |mkdir -p ${description.workDirectory}
         |cd ${description.workDirectory}
         |($executable >${outFile(description.workDirectory, jobId)} 2>${errFile(description.workDirectory, jobId)} ; echo \\$$? >${endCodeFile(description.workDirectory, jobId)}) ${if (background) "&" else ""}
         |echo \\$$! >${pidFile(description.workDirectory, jobId)}
       """.stripMargin

    (command, jobId)
  }

  def execute(description: D) = withConnection { implicit c ⇒
    val (command, jobId) = toScript(description, background = false)
    val (ret, out, err) = execReturnCodeOutput(command)
    try if (ret != 0) throw exception(ret, command, out, err)
    finally { delete(JobId(jobId, description.workDirectory)) }
  }

  def submit(description: D): J = {
    val (command, jobId) = toScript(description)
    withConnection(launch(command)(_))
    JobId(jobId, description.workDirectory)
  }

  def state(job: J): JobState =
    if (jobIsRunning(job)) Running
    else {
      if (exists(endCodeFile(job.workDirectory, job.jobId))) {
        val is = _read(endCodeFile(job.workDirectory, job.jobId))
        val content =
          try getBytes(is, bufferSize, timeout)
          finally is.close

        translateState(new String(content).takeWhile(_.isDigit).toInt)
      } else Failed
    }

  def delete(job: J) = Try {
    withConnection { implicit connection ⇒
      val kill = s"kill -9 `cat ${pidFile(job.workDirectory, job.jobId)}`;"
      val rm = s"rm -rf ${job.workDirectory}/${job.jobId}*"
      try {
        val (ret, out, err) = execReturnCodeOutput(kill)
        ret match {
          case 0 | 1 ⇒
          case r     ⇒ throw exception(r, kill, out, err)
        }
      } finally exec(rm)
    }
  }

  private def jobIsRunning(job: J) = {
    val cde = s"ps -p `cat ${pidFile(job.workDirectory, job.jobId)}`"
    withConnection(execReturnCode(cde)(_) == 0)
  }

  private def translateState(retCode: Int) =
    retCode match {
      case 0 ⇒ Done
      case _ ⇒ Failed
    }

}

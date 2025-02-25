/**
 * Copyright (C) 2017 Jonathan Passerat-Palmbach
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
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

package gridscale.cluster

import gridscale.JobState
import gridscale.ExecutionResult

import java.util.UUID
import scala.language.higherKinds

/** Generic functions to be used as building blocks to implement batch schedulers */
object BatchScheduler:

  type BatchJobID = String
  case class BatchJob(uniqId: String, jobId: BatchJobID, workDirectory: String)

  def output(uniqId: String): String = uniqId + ".out"
  def error(uniqId: String): String = uniqId + ".err"

  def scriptName(suffix: String)(uniqId: String): String = uniqId + suffix
  def scriptPath(workDirectory: String, suffix: String)(uniqId: String): String = s"$workDirectory/${scriptName(suffix)(uniqId)}"

  def submit(
    workDirectory: String,
    buildScript: String ⇒ String,
    scriptSuffix: String,
    submitCommand: (String, String) ⇒ String,
    retrieveJobID: String ⇒ BatchJobID,
    server: HeadNode,
    errorWrapper: (String, ExecutionResult) ⇒ String = ExecutionResult.error): BatchJob =

    server.execute(s"mkdir -p '$workDirectory'")
    val uniqueId = s"job-${UUID.randomUUID().toString}"
    val script = buildScript(uniqueId)
    val sName = scriptName(scriptSuffix)(uniqueId)
    val sPath = scriptPath(workDirectory, scriptSuffix)(uniqueId)
    server.write(script.getBytes, sPath)
    server.execute(s"chmod +x '$sPath'")
    val command = s"cd '$workDirectory' && ${submitCommand(sName, uniqueId)}"
    val cmdRet = server.execute(command)
    val ExecutionResult(ret, out, error) = cmdRet
    if (ret != 0) throw new RuntimeException(errorWrapper(command, cmdRet))
    if (out == null) throw new RuntimeException(s"$submitCommand did not return a JobID")
    val jobId = retrieveJobID(out)
    BatchJob(uniqueId, jobId, workDirectory)

  def state(
    stateCommand: String,
    parseState: (ExecutionResult, String) ⇒ JobState)(server: HeadNode, job: BatchJob): JobState =

    val cmdRet = server.execute(stateCommand)
    parseState(cmdRet, stateCommand)

  def clean(
    cancelCommand: String,
    scriptSuffix: String)(server: HeadNode, job: BatchJob): Unit =

    def paths =
      Seq[String](
        scriptPath(job.workDirectory, scriptSuffix)(job.uniqId),
        job.workDirectory + "/" + output(job.uniqId),
        job.workDirectory + "/" + error(job.uniqId)
      )

    def rmCommand = s"rm -f ${paths.map(p => s"'$p'").mkString(" ")}"

    server.execute(cancelCommand)
    server.execute(rmCommand)

  def stdOut(server: HeadNode, job: BatchJob): String = server.read(job.workDirectory + "/" + output(job.uniqId))
  def stdErr(server: HeadNode, job: BatchJob): String = server.read(job.workDirectory + "/" + error(job.uniqId))

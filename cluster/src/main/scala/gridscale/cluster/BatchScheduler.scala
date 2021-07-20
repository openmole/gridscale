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

import gridscale.effectaside._
import gridscale.JobState
import gridscale.ExecutionResult

import scala.language.higherKinds

/** Generic functions to be used as building blocks to implement batch schedulers */
object BatchScheduler {

  type BatchJobID = String
  case class BatchJob(uniqId: String, jobId: BatchJobID, workDirectory: String)

  def output(uniqId: String): String = uniqId + ".out"
  def error(uniqId: String): String = uniqId + ".err"

  def scriptName(suffix: String)(uniqId: String): String = uniqId + suffix
  // FIXME fragile => order of params
  def scriptPath(workDirectory: String, suffix: String)(uniqId: String): String = s"$workDirectory/${scriptName(suffix)(uniqId)}"

  def submit[S](
    workDirectory: String,
    buildScript: String ⇒ String,
    scriptSuffix: String,
    submitCommand: (String, String) ⇒ String,
    retrieveJobID: String ⇒ BatchJobID,
    server: S,
    errorWrapper: ((String, ExecutionResult) ⇒ String) = ExecutionResult.error)(implicit hn: HeadNode[S], system: Effect[System]): BatchJob = {

    hn.execute(server, s"mkdir -p $workDirectory")
    val uniqId = s"job-${system().randomUUID().toString}"
    val script = buildScript(uniqId)
    val sName = scriptName(scriptSuffix)(uniqId)
    val sPath = scriptPath(workDirectory, scriptSuffix)(uniqId)
    hn.write(server, script.getBytes, sPath)
    hn.execute(server, s"chmod +x $sPath")
    val command = s"cd $workDirectory && ${submitCommand(sName, uniqId)}"
    val cmdRet = hn.execute(server, command)
    val ExecutionResult(ret, out, error) = cmdRet
    if (ret != 0) throw new RuntimeException(errorWrapper(command, cmdRet))
    if (out == null) throw new RuntimeException(s"$submitCommand did not return a JobID")
    val jobId = retrieveJobID(out)
    BatchJob(uniqId, jobId, workDirectory)
  }

  def state[S](
    stateCommand: String,
    parseState: (ExecutionResult, String) ⇒ JobState)(server: S, job: BatchJob)(implicit hn: HeadNode[S]): JobState = {

    val cmdRet = hn.execute(server, stateCommand)
    parseState(cmdRet, stateCommand)
  }

  def clean[S](
    cancelCommand: String,
    scriptSuffix: String)(server: S, job: BatchJob)(implicit hn: HeadNode[S]): Unit = {
    hn.execute(server, cancelCommand)
    hn.rmFile(server, scriptPath(job.workDirectory, scriptSuffix)(job.uniqId))
    hn.rmFile(server, job.workDirectory + "/" + output(job.uniqId))
    hn.rmFile(server, job.workDirectory + "/" + error(job.uniqId))
  }

  def stdOut[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = hn.read(server, job.workDirectory + "/" + output(job.uniqId))
  def stdErr[S](server: S, job: BatchJob)(implicit hn: HeadNode[S]): String = hn.read(server, job.workDirectory + "/" + error(job.uniqId))

}
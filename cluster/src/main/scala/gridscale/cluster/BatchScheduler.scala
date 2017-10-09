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

import cats._
import cats.implicits._
import freedsl.errorhandler.ErrorHandler
import freedsl.system._
import gridscale.JobState
import gridscale.ExecutionResult
import gridscale.cluster.BatchScheduler.BatchJob

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

  def submit[M[_]: Monad, S](
    workDirectory: String,
    buildScript: String ⇒ String,
    scriptSuffix: String,
    submitCommand: (String, String) ⇒ String,
    retrieveJobID: String ⇒ BatchJobID,
    server: S)(implicit hn: HeadNode[S, M], system: System[M], errorHandler: ErrorHandler[M]): M[BatchJob] = {

    for {
      _ ← hn.execute(server, s"mkdir -p $workDirectory")
      uniqId ← system.randomUUID.map(_.toString)
      script = buildScript(uniqId)
      sName = scriptName(scriptSuffix)(uniqId)
      sPath = scriptPath(workDirectory, scriptSuffix)(uniqId)
      _ ← hn.write(server, script.getBytes, sPath)
      _ ← hn.execute(server, s"chmod +x ${sPath}")
      command = s"cd $workDirectory && ${submitCommand(sName, uniqId)}"
      cmdRet ← hn.execute(server, command)
      ExecutionResult(ret, out, error) = cmdRet
      _ ← if (ret != 0) errorHandler.errorMessage(ExecutionResult.error(command, cmdRet)) else ().pure[M]
      _ ← if (out == null) errorHandler.errorMessage(s"$submitCommand did not return a JobID") else ().pure[M]
      jobId ← errorHandler.get(util.Try(retrieveJobID(out)))
    } yield BatchJob(uniqId, jobId, workDirectory)
  }

  def state[M[_]: Monad, S](
    stateCommand: String ⇒ String,
    parseState: (ExecutionResult, String) ⇒ Either[RuntimeException, JobState])(server: S, job: BatchJob)(implicit hn: HeadNode[S, M], error: ErrorHandler[M]): M[JobState] = {

    val command = stateCommand(job.jobId)

    for {
      cmdRet ← hn.execute(server, command)
      s ← error.get[JobState](parseState(cmdRet, command))
    } yield s
  }

  def clean[M[_]: Monad, S](
    cancelCommand: String ⇒ String,
    scriptSuffix: ⇒ String)(server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[Unit] = for {
    _ ← hn.execute(server, s"$cancelCommand ${job.jobId}")
    _ ← hn.rmFile(server, scriptPath(job.workDirectory, scriptSuffix)(job.uniqId))
    _ ← hn.rmFile(server, job.workDirectory + "/" + output(job.uniqId))
    _ ← hn.rmFile(server, job.workDirectory + "/" + error(job.uniqId))
  } yield ()

  def stdOut[M[_], S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[String] = hn.read(server, job.workDirectory + "/" + output(job.uniqId))
  def stdErr[M[_], S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[String] = hn.read(server, job.workDirectory + "/" + error(job.uniqId))

}
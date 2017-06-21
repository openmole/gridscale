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

/**
 * Typeclass reflecting the public API of a BatchScheduler
 *
 * @tparam D Generic JobDescription type
 */
trait BatchScheduler[D] {

  def submit[M[_]: Monad, S](server: S, jobDescription: D)(implicit hn: HeadNode[S, M], system: System[M], errorHandler: ErrorHandler[M]): M[BatchJob]

  def state[M[_]: Monad, S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M], error: ErrorHandler[M]): M[JobState]

  def clean[M[_]: Monad, S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[Unit]

  def stdOut[M[_], S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[String] = BatchScheduler.stdOut[M, S](server, job)
  def stdErr[M[_], S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[String] = BatchScheduler.stdErr[M, S](server, job)

}

/** Generic functions to be used as building blocks to implement batch schedulers */
object BatchScheduler {

  type BatchJobID = String
  case class BatchJob(uniqId: String, jobId: BatchJobID, workDirectory: String)

  def output(uniqId: String): String = uniqId + ".out"
  def error(uniqId: String): String = uniqId + ".err"

  def scriptName(suffix: String)(uniqId: String): String = uniqId + suffix
  // FIXME fragile => order of params
  def scriptPath(workDirectory: String, suffix: String)(uniqId: String): String = s"$workDirectory/${scriptName(suffix)(uniqId)}"

  def submit[M[_]: Monad, S, D](
    workDirectory: D ⇒ String,
    buildScript: (D, String) ⇒ String,
    scriptSuffix: ⇒ String,
    submitCommand: ⇒ String,
    retrieveJobID: String ⇒ BatchJobID)(
      server: S,
      jobDescription: D)(implicit hn: HeadNode[S, M], system: System[M], errorHandler: ErrorHandler[M]): M[BatchJob] = {

    val workDir = workDirectory(jobDescription)

    for {
      _ ← hn.execute(server, s"mkdir -p $workDir")
      uniqId ← system.randomUUID.map(_.toString)
      script = buildScript(jobDescription, uniqId)
      sName = scriptName(scriptSuffix)(uniqId)
      _ ← hn.write(server, script.getBytes, scriptPath(workDir, scriptSuffix)(uniqId))
      command = s"cd $workDir && $submitCommand $sName"
      cmdRet ← hn.execute(server, command)
      ExecutionResult(ret, out, error) = cmdRet
      _ ← if (ret != 0) errorHandler.errorMessage(ExecutionResult.error(command, cmdRet)) else ().pure[M]
      _ ← if (out == null) errorHandler.errorMessage(s"$submitCommand did not return a JobID") else ().pure[M]
      jobId = retrieveJobID(out)
    } yield BatchJob(uniqId, jobId, workDir)
  }

  type Command = String
  def state[M[_]: Monad, S](
    stateCommand: ⇒ String,
    parseState: (ExecutionResult, Command) ⇒ Either[RuntimeException, JobState])(server: S, job: BatchJob)(implicit hn: HeadNode[S, M], error: ErrorHandler[M]): M[JobState] = {

    // FIXME might not work for each batch scheduler
    val command = stateCommand + job.jobId

    for {
      cmdRet ← hn.execute(server, command)
      s ← error.get[JobState](parseState(cmdRet, command))
    } yield s
  }

  def clean[M[_]: Monad, S](cancelCommand: ⇒ String, scriptSuffix: ⇒ String)(server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[Unit] = for {
    _ ← hn.execute(server, s"$cancelCommand ${job.jobId}")
    _ ← hn.rm(server, scriptPath(job.workDirectory, scriptSuffix)(job.uniqId))
    _ ← hn.rm(server, job.workDirectory + "/" + output(job.uniqId))
    _ ← hn.rm(server, job.workDirectory + "/" + error(job.uniqId))
  } yield ()

  def stdOut[M[_], S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[String] = hn.read(server, job.workDirectory + "/" + output(job.uniqId))
  def stdErr[M[_], S](server: S, job: BatchJob)(implicit hn: HeadNode[S, M]): M[String] = hn.read(server, job.workDirectory + "/" + error(job.uniqId))

}
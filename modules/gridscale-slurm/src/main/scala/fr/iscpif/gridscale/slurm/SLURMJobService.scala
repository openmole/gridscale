/*
 * Copyright (C) 2012 Romain Reuillon
 * Copyright (C) 2012 Jonathan Passerat-Palmbach
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

package fr.iscpif.gridscale.slurm

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.ssh.SSHJobService._
import fr.iscpif.gridscale.ssh._
import fr.iscpif.gridscale.ssh.SSHHost._
import fr.iscpif.gridscale.tools.shell.BashShell
import net.schmizz.sshj.SSHClient

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object SLURMJobService {

  def apply(host: String, port: Int = 22, timeout: Duration = 1 minute)(implicit credential: SSHAuthentication) = {
    val (_host, _port, _credential, _timeout) = (host, port, credential, timeout)
    new SLURMJobService {
      override val credential = _credential
      override val host = _host
      override val port = _port
      override val timeout = _timeout
    }
  }

  class SLURMJob(val description: SLURMJobDescription, val slurmId: String)

  object SLURMJob {
    def apply(slurmDescription: SLURMJobDescription, slurmId: String) = new SLURMJob(slurmDescription, slurmId)
  }

  val jobStateAttribute = "JobState"

  def translateStatus(retCode: Int, status: String) =
    status match {
      case "COMPLETED" ⇒ Done
      case "COMPLETED?" if (1 == retCode) ⇒ Done
      case "COMPLETED?" if (1 != retCode) ⇒ Failed
      case "RUNNING" | "COMPLETING" ⇒ Running
      case "CONFIGURING" | "PENDING" | "SUSPENDED" ⇒ Submitted
      case "CANCELLED" | "FAILED" | "NODE_FAIL" | "PREEMPTED" | "TIMEOUT" ⇒ Failed
      case _ ⇒ throw new RuntimeException("Unrecognized state " + status)
    }
}

import fr.iscpif.gridscale.slurm.SLURMJobService._

trait SLURMJobService extends JobService with SSHHost with SSHStorage with BashShell {
  type J = SLURMJob
  type D = SLURMJobDescription

  def submit(description: D): J = withConnection { implicit connection ⇒
    exec("mkdir -p " + description.workDirectory)
    write(description.toSLURM.getBytes, slurmScriptPath(description))

    val command = "cd " + description.workDirectory + " ; sbatch " + description.uniqId + ".slurm"
    val (ret, output, error) = execReturnCodeOutput(command)

    val jobId = output.trim.reverse.takeWhile(_ != ' ').reverse

    if (ret != 0 || jobId.isEmpty) throw exception(ret, command, output, error)
    new SLURMJob(description, jobId)

  }

  def state(job: J): JobState = withConnection { implicit connection ⇒
    val command = "scontrol show job " + job.slurmId

    val (ret, output, error) = execReturnCodeOutput(command)
    val lines = output.split("\n").map(_.trim)
    val state = lines.filter(_.matches(".*JobState=.*")).map {
      prop ⇒
        val splits = prop.split('=')
        splits(0).trim -> splits(1).trim.split(' ')(0)
      // consider job COMPLETED when scontrol returns 1: "Invalid job id specified"
      /** @see translateStatus(retCode: Int, status: String) */
    }.toMap.getOrElse(jobStateAttribute, "COMPLETED?")
    translateStatus(ret, state)

  }

  def stateAsync(job: J) = withReusedConnection { implicit connection ⇒
    execReturnCodeOutputFuture("scontrol show job " + job.slurmId).map((job, _))
  }

  // FIXME factor out for synchronous state as well
  def processState(resState: (SLURMJob, ExecResult)) = {

    val (job, ExecResult(ret, output, _)) = resState
    val lines = output.split("\n").map(_.trim)
    val state = lines.filter(_.matches(".*JobState=.*")).map {
      prop ⇒
        val splits = prop.split('=')
        splits(0).trim -> splits(1).trim.split(' ')(0)
      // consider job COMPLETED when scontrol returns 1: "Invalid job id specified"
      /** @see translateStatus(retCode: Int, status: String) */
    }.toMap.getOrElse(jobStateAttribute, "COMPLETED?")
    (job, translateStatus(ret, state))
  }

  def cancel(job: J): Unit = withConnection { implicit connection ⇒
    execReturnCodeOutput("scancel " + job.slurmId) match {
      case (0, _, _) ⇒
      case (1, _, error) if (error.matches(".*Invalid job id specified")) ⇒ throw new RuntimeException(s"Slurm JobService: ${job.slurmId} is an invalid job id")
      case _ ⇒ throw new RuntimeException(s"Slurm JobService could not cancel job ${job.slurmId}")
    }
  }

  def cancel2(job: J) =
    withReusedConnection { implicit connection ⇒
      execReturnCodeOutput("scancel " + job.slurmId) match {
        case (0, _, _) ⇒
        case (1, _, error) if (error.matches(".*Invalid job id specified")) ⇒ throw new RuntimeException(s"Slurm JobService: ${job.slurmId} is an invalid job id")
        case _ ⇒ throw new RuntimeException(s"Slurm JobService could not cancel job ${job.slurmId}")
      }
    }

  def cancelAsync(job: J) =
    withReusedConnection { implicit connection ⇒
      execReturnCodeOutputFuture("scancel " + job.slurmId).map((job, _))
    }

  def processCancel(resCancel: (SLURMJob, ExecResult)) = {
    val (job, result) = resCancel
    result match {
      case ExecResult(0, _, _) ⇒ (job, result)
      case ExecResult(1, _, error) if error.matches(".*Invalid job id specified") ⇒ throw new RuntimeException(s"Slurm JobService: ${job.slurmId} is an invalid job id")
      case _ ⇒ throw new RuntimeException(s"Slurm JobService could not cancel job ${job.slurmId}")
    }
  }

  /**
   * Generic function creating the futures from the action applied to the jobs
   *
   * @param jobs list of Jobs / Job Description to apply an action on
   * @param process Function performing an action on a job through an implicit connection
   * @return A sequence containing the created futures
   */
  def processFutures[J, T](jobs: J*)(process: J ⇒ Reader[SSHClient, Future[(J, T)]]) = withReusedConnection { connection ⇒
    val futures = jobs.map(process).map(_.run(connection))
    futures
  }

  /**
   * Generic function retrieving the results of the action run in async futures
   *
   * @param futures Sequence of the futures containing the results to be retrieved
   * @param process Post-processing function to apply to the retrived results
   * @return A sequence of results retrieved from the asynchronous actions performed in the futures
   */
  def retrieveFutures[J, T, R](futures: Seq[Future[(J, T)]])(process: ((J, T)) ⇒ R) = {
    val res = Await.result(
      Future.sequence(futures),
      Duration.Inf)
    res.map(process)
  }

  //Purge output error job script
  def purge(job: J) = withSftpClient { c ⇒
    rmFileWithClient(slurmScriptPath(job.description))(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.output)(c)
    rmFileWithClient(job.description.workDirectory + "/" + job.description.error)(c)
  }

  private def slurmScriptPath(description: D) = description.workDirectory + "/" + description.uniqId + ".slurm"
}

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

package fr.iscpif.gridscale.jobservice

import ch.ethz.ssh2.StreamGobbler
import fr.iscpif.gridscale.authentication._
import fr.iscpif.gridscale.tools._
import fr.iscpif.gridscale.storage._
import SSHJobService._
import java.io.BufferedReader
import java.io.InputStreamReader

object SLURMJobService {
  class SLURMJob(val description: SLURMJobDescription, val slurmId: String)
}

import SLURMJobService._

trait SLURMJobService extends JobService with SSHHost with SSHStorage {
  type J = SLURMJob
  type D = SLURMJobDescription
  
  def submit(description: D)(implicit credential: A): J = withConnection { c =>
    exec(c, "mkdir -p " + description.workDirectory)
    val outputStream = openOutputStream(slurmScriptPath(description))
    try outputStream.write(description.toSLURM.getBytes)
    finally outputStream.close
    
    val session = c.openSession
    
    try {
      exec(session, "cd " +  description.workDirectory + " ; sbatch " + description.uniqId + ".slurm")
      val stdout = new StreamGobbler(session.getStdout)
      val br = new BufferedReader(new InputStreamReader(stdout))
      val jobId = try br.readLine finally br.close
      if (jobId == null) throw new RuntimeException("sbatch did not return a JobID")
      
      println (description.toSLURM.toString)
      
      new SLURMJob(description, jobId)
    } finally session.close
  }
  
  def state(job: J)(implicit credential: A): JobState = {
    val command = "squeue --jobs " + job.slurmId

    withConnection { 
      c =>
      val session = c.openSession
      try {
        val ret = exec(session, command)
      
        val br = new BufferedReader(new InputStreamReader(new StreamGobbler(session.getStdout)))
        try {
          val lines = Iterator.continually(br.readLine).takeWhile(_ != null).map(_.trim).drop(1)

          val state = lines.hasNext match {
            case true => 
            	val splitted = lines.next.split(' ')
            	splitted(4)
            case false => throw new RuntimeException("State not found in squeue output.")
          }
          translateStatus(ret, state)
        } finally br.close 
      } finally session.close
    } 
  }
  
  def cancel(job: J)(implicit credential: A) = withConnection { c => 
    exec(c, "scancel " + job.slurmId)
  }
  
  //Purge output error job script
  def purge(job: J)(implicit credential: A) = {
    rmFile(slurmScriptPath(job.description))
    rmFile(job.description.workDirectory + "/" + job.description.output)
    rmFile(job.description.workDirectory + "/" + job.description.error)
  }
  
  def slurmScriptPath(description: D) = description.workDirectory + "/" + description.uniqId + ".slurm"
  
  
  def translateStatus(retCode: Int, status: String) =
    status match {
      case "CA" | "CO" => Done
      case "R" | "CG"  => Running
      case "CF" | "PD" | "SU" => Submitted
      case "F" | "NF" | "PR" | "TO" => Failed
      case _ => throw new RuntimeException("Unreconized state "+ status)
    }

}

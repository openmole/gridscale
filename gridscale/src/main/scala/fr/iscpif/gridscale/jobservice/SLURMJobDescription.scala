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

import java.util.UUID
import fr.iscpif.gridscale.tools._

trait SLURMJobDescription extends JobDescription {
  val uniqId = UUID.randomUUID.toString
  def workDirectory: String
  def queue: Option[String] = None
  def cpuTime: Option[Int] = None
  def memory: Option[Int] = None
  def output: String = uniqId + ".out"
  def error: String = uniqId + ".err"
  
  def toSLURM =  {
    val buffer = new ScriptBuffer
    buffer += "#!/bin/bash"
    
    buffer += "#SBATCH -o " + output
    buffer += "#SBATCH -e " + error

    queue match {
      case Some(p) => buffer += "#SBATCH -p " + p
      case None =>
    }
    
    memory match {
      case Some(m) => buffer += "#SBATCH --mem-per-cpu=" + m
      case None =>
    }
    
    cpuTime match {
      case Some(t) => buffer += "#SBATCH --time=" + t * 60 
      case None => 
    }

    workDirectory match {
      case Some(w) => buffer += "#SBATCH -D " + workDirectory
      case None =>
    }
    
    buffer += "srun " + executable +  " " + arguments
    buffer.toString
  }
}

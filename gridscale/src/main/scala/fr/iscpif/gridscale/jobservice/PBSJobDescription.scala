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

import java.util.UUID
import fr.iscpif.gridscale.tools._

trait PBSJobDescription extends JobDescription {
  val uniqId = UUID.randomUUID.toString
  def workDirectory: String
  def queue: Option[String] = None
  def cpuTime: Option[Int] = None
  def memory: Option[Int] = None
  def output: String = uniqId + ".out"
  def error: String = uniqId + ".err"
  
  def toPBS =  {
    val buffer = new ScriptBuffer
    buffer += "#!/bin/bash"
    
    buffer += "#PBS -o " + output
    buffer += "#PBS -e " + error

    queue match {
      case Some(q) => buffer += "#PBS -q " + q
      case None =>
    }
    
    memory match {
      case Some(m) => buffer += "#PBS -lmem=" + m + "mb"
      case None =>
    }
    
    cpuTime match {
      case Some(t) => 
        val df = new java.text.SimpleDateFormat("HH:mm:ss")
        df.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
        buffer += "#PBS -lwalltime=" + df.format(t * 60 * 1000)
      case None => 
    }
    
    buffer += "cd " + workDirectory
    
    buffer += executable +  " " + arguments
    buffer.toString
  }
}

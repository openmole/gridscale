/*
 * Copyright (C) 2012 Romain Reuillon
 * Copyright (C) 2014 Jonathan Passerat-Palmbach
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

package fr.iscpif.gridscale.sge

import java.util.UUID

import fr.iscpif.gridscale.jobservice.JobDescription
import fr.iscpif.gridscale.tools.ScriptBuffer

import scala.concurrent.duration.Duration

trait SGEJobDescription extends JobDescription {

  val uniqId = UUID.randomUUID.toString
  def output: String = uniqId + ".out"
  def error: String = uniqId + ".err"
  def workDirectory: String
  def queue: Option[String] = None
  def memory: Option[Int] = None
  def wallTime: Option[Duration] = None

  def toSGE = {
    val buffer = new ScriptBuffer
    buffer += "#!/bin/bash"

    buffer += "#$ -o " + output
    buffer += "#$ -e " + error

    queue.foreach(q ⇒ buffer += "#$ -q " + q)
    memory.foreach(m ⇒ buffer += "-l h_vmem=" + m + "M")
    wallTime.foreach(t ⇒ buffer += "-l h_cpu=" + t.toSeconds)
    buffer += "#$ -cwd"

    buffer += executable + " " + arguments

    buffer.toString
  }
}

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
import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.tools.ScriptBuffer

import scala.concurrent.duration.Duration

trait SGEJobDescription extends JobDescription {
  val uniqId = UUID.randomUUID.toString
  def workDirectory: String
  def queue: Option[String] = None
  def wallTime: Option[Duration] = None
  def memory: Option[Int] = None
  def nodes: Option[Int] = None
  def coreByNode: Option[Int] = None
  def output: String = uniqId + ".out"
  def error: String = uniqId + ".err"

  def toSGE = {
    val buffer = new ScriptBuffer
    buffer += "#!/bin/bash"

    buffer += "#$ -o " + output
    buffer += "#$ -e " + error

    queue match {
      case Some(q) ⇒ buffer += "#$ -q " + q
      case None    ⇒
    }

    memory match {
      case Some(m) ⇒ buffer += "#$ -lmem=" + m + "mb"
      case None    ⇒
    }

    wallTime match {
      case Some(t) ⇒
        val df = new java.text.SimpleDateFormat("HH:mm:ss")
        df.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
        buffer += "#$ -lwalltime=" + df.format(t.toMillis)
      case None ⇒
    }

    nodes match {
      case Some(n) ⇒ buffer += "#$ -lnodes=" + n + ":ppn=" + coreByNode.getOrElse(1)
      case None ⇒
        coreByNode match {
          case Some(c) ⇒ buffer += "#$ -lnodes=1:ppn=" + c
          case None    ⇒
        }
    }

    buffer += "cd " + workDirectory

    buffer += executable + " " + arguments
    buffer.toString
  }
}

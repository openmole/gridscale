/*
 * Copyright (C) 2014 Romain Reuillon
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

package fr.iscpif.gridscale.oar

import java.util.UUID

import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.tools._

import scala.concurrent.duration.Duration

case class OARJobDescription(
    executable: String,
    arguments: String,
    workDirectory: String,
    queue: Option[String] = None,
    cpu: Option[Int] = None,
    core: Option[Int] = None,
    wallTime: Option[Duration] = None,
    bestEffort: Boolean = false) {

  val uniqId = UUID.randomUUID.toString

  def output: String = uniqId + ".out"
  def error: String = uniqId + ".err"

  def commandLineResources =
    (List(cpu.map(c ⇒ s"cpu=$c"), core.map(c ⇒ s"core=$c")).flatten match {
      case Nil ⇒ ""
      case l   ⇒ "/" + l.mkString("/") + ","
    }) + wallTime.map(wt ⇒ s"walltime=" + wt.toHHmmss).getOrElse("")

  def toOAR = {
    val buffer = new ScriptBuffer
    buffer += "#!/bin/bash"
    buffer += executable + " " + arguments
    buffer.toString
  }

}

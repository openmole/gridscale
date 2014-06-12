/*
 * Copyright (C) 2012 Romain
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

package fr.iscpif.gridscale.condor

import java.util.UUID
import fr.iscpif.gridscale._

/** Represent Requirements by extending Tuple2 in order to override toString */
class CondorRequirement(val requirementName: String,
  val requirementValue: String)
    extends Tuple2[String, String](requirementName, requirementValue) {
  override def toString = _1 + " == \"" + _2 + "\""
}

object CondorRequirement {
  def apply(inRequirementName: String, inRequirementValue: String) = {
    new CondorRequirement(inRequirementName, inRequirementValue)
  }
}

trait CondorJobDescription extends JobDescription {
  val uniqId = UUID.randomUUID.toString
  def workDirectory: String
  // not available yet
  //  def queue: Option[String] = None
  //  def wallTime: Option[Int] = None
  def memory: Option[Int] = None
  def nodes: Option[Int] = None
  def coreByNode: Option[Int] = None
  def output: String = uniqId + ".out"
  def error: String = uniqId + ".err"

  def requirements: List[CondorRequirement] = List()

  def toCondor = {
    val buffer = new ScriptBuffer
    val requirementsBuffer = new ScriptBuffer

    buffer += "output = " + output
    buffer += "error = " + error

    // TODO: are these features available in Condor?
    //    queue match {
    //      case Some(q) ⇒ buffer += "#PBS -q " + q
    //      case None ⇒
    //    }
    //
    //    wallTime match {
    //      case Some(t) ⇒
    //        val df = new java.text.SimpleDateFormat("HH:mm:ss")
    //        df.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
    //        buffer += "#PBS -lwalltime=" + df.format(t * 60 * 1000)
    //      case None ⇒
    //    }

    nodes match {
      case Some(n) ⇒ {
        buffer += "universe = parallel"
        buffer += "machine_count = " + n
        buffer += "request_cpus = " + coreByNode.getOrElse(1)
      }
      case None ⇒
        buffer += "universe = vanilla"
        coreByNode match {
          case Some(c) ⇒ requirementsBuffer += "TotalCpus >= " + c
          case None    ⇒
        }
    }

    memory match {
      case Some(m) ⇒ buffer += "request_memory = " + m + " MB"
      case None    ⇒
    }

    requirements match {
      case List() ⇒
      case _      ⇒ requirementsBuffer += requirements.mkString("( ", "&&", " )")
    }

    buffer += "requirements = " + requirementsBuffer
    buffer += "initialdir = " + workDirectory

    buffer += "executable = " + executable
    buffer += "arguments = " + arguments
    // queue actually submits N jobs (default to 1 in our case)
    buffer += "queue 1"

    buffer.toString
  }
}

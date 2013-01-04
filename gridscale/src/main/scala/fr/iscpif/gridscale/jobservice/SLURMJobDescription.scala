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

/** Represent Gres by extending Tuple2 in order to override toString */
class Gres(val gresName: String, val gresValue: Int) extends Tuple2[String, Int](gresName, gresValue) {
  override def toString = _1 + ":" + _2.toString
}

trait SLURMJobDescription extends JobDescription {

  val uniqId = UUID.randomUUID.toString
  def workDirectory: String
  def queue: Option[String] = None
  def wallTime: Option[Int] = None
  def memory: Option[Int] = None
  def output: String = uniqId + ".out"
  def error: String = uniqId + ".err"
  def gres: List[Gres] = List()
  def constraints: List[String] = List()

  def toSLURM = {
    val buffer = new ScriptBuffer
    buffer += "#!/bin/bash"

    buffer += "#SBATCH -o " + output
    buffer += "#SBATCH -e " + error

    queue match {
      case Some(p) ⇒ buffer += "#SBATCH -p " + p
      case None ⇒
    }

    memory match {
      case Some(m) ⇒ buffer += "#SBATCH --mem-per-cpu=" + m
      case None ⇒
    }

    wallTime match {
      case Some(t) ⇒ buffer += "#SBATCH --time=" + t * 60
      case None ⇒
    }

    // must handle empty list separately since it is not done in mkString
    gres match {
      case List() ⇒
      case _ ⇒ buffer += gres.mkString("#SBATCH --gres=", "--gres=", "")
    }
    constraints match {
      case List() ⇒
      case _ ⇒ buffer += constraints.mkString("#SBATCH --constraint=\"", "&", "\"")
    }

    buffer += "#SBATCH -D " + workDirectory + "\n"

    // TODO: handle several srun and split gres accordingly
    buffer += "srun "
    // must handle empty list separately since it is not done in mkString
    gres match {
      case List() ⇒
      case _ ⇒ buffer += gres.mkString("--gres=", "--gres=", "")
    }
    constraints match {
      case List() ⇒
      case _ ⇒ buffer += constraints.mkString("--constraint=\"", "&", "\"")
    }

    buffer += executable + " " + arguments

    buffer.toString
  }
}

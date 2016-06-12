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

import java.util.UUID
import fr.iscpif.gridscale.tools.{ ScriptBuffer, _ }

import scala.concurrent.duration.Duration

/** Represent Gres by extending Tuple2 in order to override toString */
class Gres(val gresName: String, val gresValue: Int) extends Tuple2[String, Int](gresName, gresValue) {
  override def toString = _1 + ":" + _2.toString
}

object Gres {
  def apply(inGresName: String, inGresValue: Int) = {
    new Gres(inGresName, inGresValue)
  }
}

case class SLURMJobDescription(
    executable: String,
    arguments: String,
    workDirectory: String,
    queue: Option[String] = None,
    wallTime: Option[Duration] = None,
    memory: Option[Int] = None,
    nodes: Option[Int] = None,
    coresByNode: Option[Int] = None,
    qos: Option[String] = None,
    gres: List[Gres] = List(),
    constraints: List[String] = List()) {

  val uniqId = UUID.randomUUID.toString

  def output: String = uniqId + ".out"
  def error: String = uniqId + ".err"

  def toSLURM = {
    val buffer = new ScriptBuffer
    buffer += "#!/bin/bash"

    buffer += "#SBATCH -o " + output
    buffer += "#SBATCH -e " + error

    queue match {
      case Some(p) ⇒ buffer += "#SBATCH -p " + p
      case None    ⇒
    }

    memory match {
      case Some(m) ⇒ buffer += "#SBATCH --mem=" + m
      case None    ⇒
    }

    nodes match {
      case Some(n) ⇒ buffer += s"#SBATCH --nodes=${n}"
      case None    ⇒
    }

    buffer += s"#SBATCH --cpus-per-task=${coresByNode.getOrElse(1)}"

    wallTime match {
      case Some(t) ⇒
        buffer += "#SBATCH --time=" + t.toHHmmss
      case None ⇒
    }

    qos match {
      case Some(q) ⇒ buffer += s"#SBATCH --qos=${q}"
      case None    ⇒
    }

    // must handle empty list separately since it is not done in mkString
    gres match {
      case List() ⇒
      case _      ⇒ buffer += gres.mkString("#SBATCH --gres=", "--gres=", "")
    }

    constraints match {
      case List() ⇒
      case _      ⇒ buffer += constraints.mkString("#SBATCH --constraint=\"", "&", "\"")
    }

    // FIXME workDirectory should be an option to force a value (can't be empty)
    buffer += "#SBATCH -D " + workDirectory + "\n"

    // TODO: handle several srun and split gres accordingly
    //    buffer += "srun "
    //    // must handle empty list separately since it is not done in mkString
    //    gres match {
    //      case List() ⇒
    //      case _ ⇒ buffer += gres.mkString("--gres=", "--gres=", "")
    //    }
    //    constraints match {
    //      case List() ⇒
    //      case _ ⇒ buffer += constraints.mkString("--constraint=\"", "&", "\"")
    //    }

    buffer += executable + " " + arguments

    buffer.toString
  }
}

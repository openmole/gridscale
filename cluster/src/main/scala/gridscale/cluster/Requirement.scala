/**
 * Copyright (C) 2018 Jonathan Passerat-Palmbach
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
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

package gridscale.cluster

import scala.language.implicitConversions

case class Requirement(flag: String, value: Option[String]) {
  override def toString: String = flag + value.getOrElse("")
}

object Requirement {

  def pair2Requirement(p: (String, Option[String])) = Requirement(p._1, p._2)
  implicit def pairs2Requirements(ps: Seq[(String, Option[String])]): Seq[Requirement] = ps.map(pair2Requirement)

  def requirementsString(requirements: Seq[Requirement], prefix: String = ""): String =
    requirements.filter { case Requirement(k, v) ⇒ v.isDefined }.
      map(_.toString).
      mkString(s"$prefix ", s"\n$prefix ", "")
}

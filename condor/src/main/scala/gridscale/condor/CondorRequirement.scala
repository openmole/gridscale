/*
 * Copyright (C) 2015 Jonathan Passerat-Palmbach
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

package gridscale.condor

class CondorRequirement(val requirement: String) {
  def ||(otherReq: CondorRequirement) = CondorRequirement(s"( ${requirement} ) || ( ${otherReq} )")
  def &&(otherReq: CondorRequirement) = CondorRequirement(s"( ${requirement} ) && ( ${otherReq} )")
  override def toString = s"${requirement}"
  def toCondor = s"( ${this} )"
}

object CondorRequirement {
  def apply(requirement: String) = new CondorRequirement(requirement)
}

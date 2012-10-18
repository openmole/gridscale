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

package fr.iscpif.gridscale.storage

trait RecursiveRmDir extends Storage {

  def rmDir(path: String)(implicit authentication: A) = {
    val subfiles = list(path)
    if(subfiles.isEmpty) rmEmptyDir(path)
    else subfiles.foreach {
      case(name, t) =>
        t match {
          case DirectoryType => rmDir(child(path, name))
          case _ => rmFile(child(path, name))
        }
    }
  }
  
  def rmEmptyDir(path: String)(implicit authentication: A)
}

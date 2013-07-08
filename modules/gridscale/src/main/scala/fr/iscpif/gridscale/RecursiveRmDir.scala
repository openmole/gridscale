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

package fr.iscpif.gridscale

import java.util.logging.{ Level, Logger }

trait RecursiveRmDir extends Storage {

  def _rmDir(path: String)(implicit authentication: A) = {
    val subfiles = _list(path)
    if (subfiles.isEmpty) rmEmptyDir(path)
    else subfiles.foreach {
      case (name, t) ⇒
        try {
          t match {
            case DirectoryType ⇒ _rmDir(child(path, name))
            case _ ⇒ _rmFile(child(path, name))
          }
        } catch {
          case t: Throwable ⇒ Logger.getLogger(classOf[RecursiveRmDir].getName).log(Level.FINE, "Error in recursive rm", t)
        }
    }
  }

  def rmEmptyDir(path: String)(implicit authentication: A)
}

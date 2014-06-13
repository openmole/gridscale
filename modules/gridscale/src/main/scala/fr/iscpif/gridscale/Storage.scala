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

import java.io._

object Storage {
  def child(parent: String, child: String) = if (parent.endsWith("/")) parent + child else parent + '/' + child
}

trait Storage <: Credential {

  def exists(path: String) =
    if (isRoot(path)) true
    else listNames(parent(path)).toSet.contains(name(path))

  def child(parent: String, child: String) = Storage.child(parent, child)

  def parent(path: String) =
    path.reverse.dropWhile(c ⇒ c == '/' || c == '\\').drop(name(path).length).reverse

  def name(path: String) = new File(path).getName
  def isRoot(path: String) = parent(path) == path

  def listNames(path: String) = _list(path).unzip._1

  def openInputStream(path: String): InputStream = new BufferedInputStream(_openInputStream(path))
  def openOutputStream(path: String): OutputStream = new BufferedOutputStream(_openOutputStream(path))

  def list(path: String): Seq[(String, FileType)] = wrapException(s"list $path")(_list(path))
  def makeDir(path: String) = wrapException(s"make dir $path")(_makeDir(path))
  def rmDir(path: String) = wrapException(s"rm dir $path")(_rmDir(path))
  def rmFile(path: String) = wrapException(s"rm file $path")(_rmFile(path))
  def mv(from: String, to: String) = wrapException(s"move $from to $to")(_mv(from, to))

  def _list(path: String): Seq[(String, FileType)]
  def _makeDir(path: String)
  def _rmDir(path: String)
  def _rmFile(path: String)
  def _mv(from: String, to: String)

  protected def _openInputStream(path: String): InputStream
  protected def _openOutputStream(path: String): OutputStream

  def wrapException[T](operation: String)(f: ⇒ T): T =
    try f
    catch {
      case e: Throwable ⇒ throw new IOException(s"Error $operation", e)
    }
}

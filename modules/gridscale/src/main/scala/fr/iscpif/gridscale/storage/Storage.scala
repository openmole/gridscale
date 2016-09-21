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

import java.io._
import java.nio.file.Path

object Storage {
  def child(parent: String, child: String) = if (parent.endsWith("/")) parent + child else parent + '/' + child
  def parent(path: String): Option[String] = {
    val cleaned = path.reverse.dropWhile(c ⇒ c == '/' || c == '\\').reverse
    cleaned match {
      case "" ⇒ None
      case _  ⇒ Some(cleaned.dropRight(name(path).length))
    }
  }
  def name(path: String) = new File(path).getName
}

case class ListEntry(name: String, `type`: FileType, modificationTime: Option[Long] = None)

trait Storage {

  def child(parent: String, child: String) = Storage.child(parent, child)
  def parent(path: String): Option[String] = Storage.parent(path)
  def name(path: String) = Storage.name(path)

  def listNames(path: String) = _list(path).map(_.name)
  def list(path: String): Seq[ListEntry] = wrapException(s"list $path on ${this}")(_list(path))
  def makeDir(path: String) = wrapException(s"make dir $path on ${this}")(_makeDir(path))
  def rmDir(path: String) = wrapException(s"rm dir $path on ${this}")(_rmDir(path))
  def rmFile(path: String) = wrapException(s"rm file $path on ${this}")(_rmFile(path))
  def mv(from: String, to: String) = wrapException(s"move $from to $to on ${this}")(_mv(from, to))
  def exists(path: String) = wrapException(s"exists $path ${this}")(_exists(path))
  def read(path: String) = wrapException(s"read $path on $this")(_read(path))
  def write(is: InputStream, path: String) = wrapException(s"write $path on $this")(_write(is, path))
  def write(bytes: Array[Byte], path: String): Unit = write(new ByteArrayInputStream(bytes), path)

  def _read(path: String): InputStream
  def _write(is: InputStream, path: String): Unit
  def _list(path: String): Seq[ListEntry]
  def _makeDir(path: String)
  def _rmDir(path: String)
  def _rmFile(path: String)
  def _mv(from: String, to: String)
  def _exists(path: String) =
    parent(path).
      map(parentPath ⇒ listNames(parentPath).exists(_ == name(path))).
      getOrElse(true)

  def errorWrapping(operation: String, t: Throwable): Throwable = new IOException(s"Error $operation", t)

  def wrapException[T](operation: String)(f: ⇒ T): T =
    try f
    catch {
      case e: Throwable ⇒ throw errorWrapping(operation, e)
    }
}

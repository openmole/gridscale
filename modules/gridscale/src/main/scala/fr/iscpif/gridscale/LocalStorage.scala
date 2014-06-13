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
import collection.JavaConversions._
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.Files

trait LocalStorage extends Storage {
  type A = Unit
  def credential = Unit

  override def child(parent: String, child: String) =
    new File(new File(parent), child).getAbsolutePath

  override def exists(path: String) =
    new File(path).exists

  def _list(path: String): Seq[(String, FileType)] =
    new File(path).listFiles.map {
      f ⇒
        val fs = FileSystems.getDefault
        val p = fs.getPath(f.getAbsolutePath)

        val ftype =
          if (Files.isSymbolicLink(p)) LinkType
          else if (f.isDirectory) DirectoryType
          else FileType

        f.getName -> ftype
    }

  def _makeDir(path: String) =
    new File(path).mkdirs

  def _rmDir(path: String) = {
    def delete(f: File): Unit = {
      if (f.isDirectory) f.listFiles.foreach(delete)
      f.delete
    }
    delete(new File(path))
  }

  def _rmFile(path: String) =
    new File(path).delete

  def _mv(from: String, to: String) =
    new File(from).renameTo(new File(to))

  protected def _openInputStream(path: String): InputStream =
    new FileInputStream(new File(path))

  protected def _openOutputStream(path: String): OutputStream =
    new FileOutputStream(new File(path))

}

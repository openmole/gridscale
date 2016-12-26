/**
 * Copyright (C) 2016 Jonathan Passerat-Palmbach
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

package fr.iscpif.gridscale.ssh.sshj

import fr.iscpif.gridscale._
import java.io.InputStream
import util.Try

trait SFTPClient {
  def ls(path: String)(predicate: String ⇒ Boolean): Try[Vector[ListEntry]]
  def chmod(path: String, perms: Int): Try[Unit]
  def close(): Try[Unit]
  def canonicalize(path: String): Try[String]
  def exists(path: String): Try[Boolean]
  def mkdir(path: String): Try[Unit]
  def rmdir(path: String): Try[Unit]
  def rm(path: String): Try[Unit]
  def rename(oldName: String, newName: String): Try[Unit]
  def readAheadFileInputStream(path: String): Try[InputStream]
  def writeFile(is: InputStream, path: String): Try[Unit]
}

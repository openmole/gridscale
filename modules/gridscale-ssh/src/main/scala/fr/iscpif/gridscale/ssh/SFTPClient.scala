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

package fr.iscpif.gridscale.ssh

import java.io.InputStream

import fr.iscpif.gridscale.storage.ListEntry

trait SFTPClient {

  def ls(path: String)(predicate: String ⇒ Boolean): List[ListEntry]

  def chmod(path: String, perms: Int)
  def close
  def canonicalize(path: String): String
  def exists(path: String): Boolean
  def mkdir(path: String)
  def rmdir(path: String)
  def rm(path: String)
  def rename(oldName: String, newName: String)

  def readAheadFileInputStream(path: String): InputStream

  def fileOutputStream(is: InputStream, path: String)
}

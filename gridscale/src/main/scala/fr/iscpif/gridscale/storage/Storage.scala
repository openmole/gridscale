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

trait Storage {
  type A
  
  def exists(path: String)(implicit authentication: A) = 
    if(isRoot(path)) true
    else listNames(parent(path)).toSet.contains(name(path))
    
  def child(parent: String, child: String) = if(parent.endsWith("/")) parent + child else parent + '/' + child  
  
  def parent(path: String) = new File(path).getParent
  def name(path: String) = new File(path).getName
  def isRoot(path: String) = parent(path) == path
  
  def listNames(path: String)(implicit authentication: A) = list(path).unzip._1
  def list(path: String)(implicit authentication: A): Seq[(String, FileType)]
  def makeDir(path: String)(implicit authentication: A)
  def rmDir(path: String)(implicit authentication: A)
  def rmFile(path: String)(implicit authentication: A)
  def openInputStream(path: String)(implicit authentication: A): InputStream = new BufferedInputStream(_openInputStream(path))
  def openOutputStream(path: String)(implicit authentication: A): OutputStream = new BufferedOutputStream(_openOutputStream(path))
  protected def _openInputStream(path: String)(implicit authentication: A): InputStream 
  protected def _openOutputStream(path: String)(implicit authentication: A): OutputStream
}

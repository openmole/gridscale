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
import collection.JavaConversions._
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.Files

trait LocalStorage extends Storage {
  type A = Unit
  
  override def exists(path: String)(implicit authentication: A) = 
    new File(path).exists
    
  def list(path: String)(implicit authentication: A): Seq[(String, FileType)] = 
    new File(path).listFiles.map{ 
      f => 
        val fs = FileSystems.getDefault
        val p = fs.getPath(f.getAbsolutePath)
      
        val ftype = 
          if(Files.isSymbolicLink(p)) LinkType
          else if(f.isDirectory) DirectoryType
          else FileType
          
        f.getName -> ftype
      }
  
  def makeDir(path: String)(implicit authentication: A) = 
    new File(path).mkdirs
  
  def rmDir(path: String)(implicit authentication: A) = {
    def delete(f: File): Unit = {
      if(f.isDirectory) f.listFiles.foreach(delete)
      f.delete
    }
    delete(new File(path))
  }
  
  def rmFile(path: String)(implicit authentication: A) = 
    new File(path).delete
  
  def openInputStream(path: String)(implicit authentication: A): InputStream = 
    new BufferedInputStream(new FileInputStream(new File(path)))
  
  def openOutputStream(path: String)(implicit authentication: A): OutputStream =
    new BufferedOutputStream(new FileOutputStream(new File(path)))
  
}

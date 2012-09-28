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

import fr.iscpif.gridscale.tools._
import ch.ethz.ssh2.Connection
import ch.ethz.ssh2.SFTPException
import ch.ethz.ssh2.SFTPInputStream
import ch.ethz.ssh2.SFTPOutputStream
import ch.ethz.ssh2.SFTPv3Client
import ch.ethz.ssh2.SFTPv3DirectoryEntry
import fr.iscpif.gridscale.authentication._
import java.io.InputStream
import java.io.OutputStream
import ch.ethz.ssh2.SFTPv3FileAttributes
import ch.ethz.ssh2.SFTPv3FileHandle
import ch.ethz.ssh2.sftp.AttribPermissions
import ch.ethz.ssh2.sftp.ErrorCodes
import collection.JavaConversions._

trait SSHStorage extends Storage with SSHHost { storage =>

  def home(implicit authentication: A) = withSftpClient {
    _.canonicalPath(".")
  }
  
  def makeDirs(parent: String, child: String)(implicit authentication: A) = {
    (parent + "/" + child).split("/").tail.filter(_.isEmpty).foldLeft("") {
      (c, r) => 
      val dir = c + "/" + r
      if(!exists(dir)) makeDir(dir)
      dir
    }
  }
  
  override def exists(path: String)(implicit authentication: A) = withSftpClient {  c =>
     try {
      c.stat(path)
      true
    } catch {
      case e: SFTPException => 
        if(e.getServerErrorCode == ErrorCodes.SSH_FX_NO_SUCH_FILE) false
        else throw e
    }
  }
  
  def list(path: String)(implicit authentication: A) = withSftpClient {
    listWithClient(path) _
  }
  
  private def listWithClient(path: String)(c: SFTPv3Client) = {
    c.ls(path).filterNot(e => {e.filename == "." || e.filename == ".."}).map {
      e =>
      val t = 
        if(e.attributes.isDirectory) DirectoryType 
      else if(e.attributes.isRegularFile) FileType
      else LinkType
      e.filename -> t
    }
  }
  
  def makeDir(path: String)(implicit authentication: A) = withSftpClient {
    import AttribPermissions._
    _.mkdir(path, S_IRUSR | S_IWUSR | S_IXUSR)
  }
  
  def rmDir(path: String)(implicit authentication: A) = withSftpClient {
    rmDirWithClient(path) _
  }
  
  private def rmDirWithClient(path: String)(c: SFTPv3Client): Unit = {
    listWithClient(path)(c).foreach {
      case(p, t) => 
        val child = path + "/" + p
        t match {
          case FileType => rmFileWithClient(child)(c)
          case LinkType => rmFileWithClient(child)(c)
          case DirectoryType => rmDirWithClient(child)(c)
        }
    }
    c.rmdir(path)
  }
  
  def rmFile(path: String)(implicit authentication: A) = withSftpClient {
    rmFileWithClient(path) _
  }
  
  private def rmFileWithClient(path: String)(c: SFTPv3Client) = {
    c.rm(path)
  }
  
  def openInputStream(path: String)(implicit authentication: A): InputStream = {
    val connection = connectionCache.cached(this)
    val sftpClient = new SFTPv3Client(connection)
    
    val fileHandle = 
      try sftpClient.createFile(path)
    catch {
      case e: Throwable => 
        try sftpClient.close
        finally connectionCache.release(this)
        throw e
    }
    
    def closeAll = {
      try sftpClient.closeFile(fileHandle)
      finally try sftpClient.close
      finally connectionCache.release(this)
    }
    
    new SFTPInputStream(fileHandle) {
      override def close = {
        try closeAll
        finally super.close
      }
    }                       
  }
    
    
  def openOutputStream(path: String)(implicit authentication: A): OutputStream = {
    val connection = connectionCache.cached(this)
    val sftpClient = new SFTPv3Client(connection)
    
    val fileHandle = 
      try sftpClient.createFileTruncate(path)
    catch {
      case e: Throwable => 
        try sftpClient.close
        finally connectionCache.release(this)
        throw e
    }
    
    def closeAll = {
      try sftpClient.closeFile(fileHandle)
      finally try sftpClient.close
      finally connectionCache.release(this)
    }
    
    new SFTPOutputStream(fileHandle) {
      override def close = {
        try closeAll
        finally super.close
      }
    }                       
  }
  

  
  
}

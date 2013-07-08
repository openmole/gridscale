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

package fr.iscpif.gridscale.ssh

import java.io._
import collection.JavaConversions._
import net.schmizz.sshj.sftp.{ OpenMode, FileAttributes, FileMode, SFTPClient }
import java.util
import fr.iscpif.gridscale._

trait SSHStorage extends Storage with SSHHost { storage ⇒

  def unconfirmedExchanges = 5

  def home(implicit authentication: A) = withSftpClient {
    _.canonicalize(".")
  }

  override def exists(path: String)(implicit authentication: A) = withSftpClient { c ⇒
    c.statExistence(path) != null
  }

  def _list(path: String)(implicit authentication: A) = withSftpClient {
    listWithClient(path) _
  }

  private def listWithClient(path: String)(c: SFTPClient) = {
    c.ls(path).filterNot(e ⇒ { e.getName == "." || e.getName == ".." }).map {
      e ⇒
        val t =
          e.getAttributes.getType match {
            case FileMode.Type.DIRECTORY ⇒ DirectoryType
            case FileMode.Type.SYMKLINK ⇒ LinkType
            case _ ⇒ FileType
          }
        e.getName -> t
    }
  }

  def _makeDir(path: String)(implicit authentication: A) = withSftpClient {
    _.mkdir(path)
  }

  def _rmDir(path: String)(implicit authentication: A) = withSftpClient {
    rmDirWithClient(path)(_)
  }

  private def rmDirWithClient(path: String)(c: SFTPClient): Unit = wrapException(s"rm dir $path") {
    listWithClient(path)(c).foreach {
      case (p, t) ⇒
        try {
          val child = path + "/" + p
          t match {
            case FileType ⇒ rmFileWithClient(child)(c)
            case LinkType ⇒ rmFileWithClient(child)(c)
            case DirectoryType ⇒ rmDirWithClient(child)(c)
          }
        } catch {
          case _: Throwable =>
        }
    }
    c.rmdir(path)
  }

  def _rmFile(path: String)(implicit authentication: A) = withSftpClient {
    rmFileWithClient(path)(_)
  }

  def _mv(from: String, to: String)(implicit authentication: A) = withSftpClient {
    _.rename(from, to)
  }

  protected def rmFileWithClient(path: String)(c: SFTPClient) = wrapException(s"rm $path"){
    c.rm(path)
  }

  protected def _openInputStream(path: String)(implicit authentication: A): InputStream = {
    val connection = getConnection

    def close = release(connection)

    val sftpClient =
      try connection.newSFTPClient
      catch {
        case e: Throwable ⇒
          close
          throw e
      }

    val fileHandle =
      try sftpClient.open(path, util.EnumSet.of(OpenMode.READ))
      catch {
        case e: Throwable ⇒
          try sftpClient.close
          finally close
          throw e
      }

    def closeAll = {
      try fileHandle.close
      finally try sftpClient.close
      finally close
    }

    new fileHandle.RemoteFileInputStream {
      override def close = {
        try closeAll
        finally super.close
      }
    }
  }

  protected def _openOutputStream(path: String)(implicit authentication: A): OutputStream = {
    val connection = getConnection

    def close = release(connection)

    val sftpClient =
      try connection.newSFTPClient
      catch {
        case e: Throwable ⇒
          close
          throw e
      }

    val fileHandle =
      try sftpClient.open(path, util.EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
      catch {
        case e: Throwable ⇒
          try sftpClient.close
          finally close
          throw e
      }

    def closeAll = {
      try fileHandle.close
      finally try sftpClient.close
      finally close
    }

    new fileHandle.RemoteFileOutputStream(0, unconfirmedExchanges) {
      override def close = {
        try closeAll
        finally super.close
      }
    }
  }

}

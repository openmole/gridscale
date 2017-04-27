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

import java.io.InputStream
import java.util.logging.{ Level, Logger }

import fr.iscpif.gridscale.storage._

import scala.collection.JavaConversions._
import scala.concurrent.duration._

object SSHStorage {

  def apply(host: String, port: Int = 22, timeout: Duration = 1 minute)(implicit credential: SSHAuthentication) = {
    val (_port, _host, _credential, _timeout) = (port, host, credential, timeout)

    new SSHStorage {
      override def credential: SSHAuthentication = _credential
      override def host: String = _host
      override def timeout: Duration = _timeout
      override def port: Int = _port
    }
  }

}

trait SSHStorage extends Storage with SSHHost { storage ⇒

  object FilePermission {

    sealed abstract class FilePermission

    case object USR_RWX extends FilePermission

    case object GRP_RWX extends FilePermission

    case object OTH_RWX extends FilePermission
    def toMask(fps: Set[FilePermission]): Int = {

      import net.schmizz.sshj.xfer.{ FilePermission ⇒ SSHJFilePermission }

      SSHJFilePermission.toMask(
        fps map {
          case USR_RWX ⇒ SSHJFilePermission.USR_RWX
          case GRP_RWX ⇒ SSHJFilePermission.GRP_RWX
          case OTH_RWX ⇒ SSHJFilePermission.OTH_RWX
        })
    }
  }

  def home = withSftpClient {
    _.canonicalize(".")
  }

  override def exists(path: String): Boolean = withSftpClient { c ⇒
    c.exists(path)
  }

  override def errorWrapping(operation: String, t: Throwable) =
    t match {
      case e: net.schmizz.sshj.sftp.SFTPException if e.getMessage == "No such file" ⇒
        new SSHException.NoSuchFileException(s"$operation: " + e.getMessage)
      case t: Throwable ⇒ super.errorWrapping(operation, t)
    }

  def chmod(path: String, perms: FilePermission.FilePermission*) = withSftpClient {
    _.chmod(path, FilePermission.toMask(perms.toSet[FilePermission.FilePermission]))
  }

  def _list(path: String) = withSftpClient {
    _.ls(path)(e ⇒ e != "." || e != "..")
  }

  def _makeDir(path: String) = withSftpClient {
    _.mkdir(path)
  }

  def _rmDir(path: String) = withSftpClient {
    rmDirWithClient(path)(_)
  }

  private def rmDirWithClient(path: String)(c: SFTPClient): Unit = wrapException(s"rm dir $path") {
    c.ls(path)(_ ⇒ true).foreach {
      entry ⇒
        val child = path + "/" + entry.name
        entry.`type` match {
          case FileType      ⇒ rmFileWithClient(child)(c)
          case LinkType      ⇒ rmFileWithClient(child)(c)
          case DirectoryType ⇒ rmDirWithClient(child)(c)
          case UnknownType   ⇒ rmFileWithClient(child)(c)
        }
    }
    c.rmdir(path)
  }

  def _rmFile(path: String) = withSftpClient {
    rmFileWithClient(path)(_)
  }

  def _mv(from: String, to: String) = withSftpClient {
    _.rename(from, to)
  }

  protected def rmFileWithClient(path: String)(implicit c: SFTPClient) = wrapException(s"rm $path") {
    c.rm(path)
  }


  // FIXME resource not released in normal case!
  def withNotClosedResource[R <: { def release(connection: SSHClient)}, T](f: SFTPClient => T): T = withConnection { connection =>
    val sftpClient =
      try connection.newSFTPClient
      catch {
        case e: Throwable ⇒
          release(connection)
          throw e
      }

      f(sftpClient)
  }

  override def _read(path: String): InputStream = withNotClosedResource(_.readAheadFileInputStream(path))

  // FIXME signature incoherent with read
  override def _write(is: InputStream, path: String): Unit = withSftpClient(_.fileOutputStream(is, path))
}

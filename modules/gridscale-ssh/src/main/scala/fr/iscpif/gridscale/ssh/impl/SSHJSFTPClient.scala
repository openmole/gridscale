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

package fr.iscpif.gridscale.ssh.impl

import java.io.InputStream
import java.util

import fr.iscpif.gridscale.storage.{ DirectoryType, FileType, LinkType, ListEntry }
import fr.iscpif.gridscale.tools._

object SSHJSFTPClient {

  import net.schmizz.sshj.sftp.{ FileMode, OpenMode, SFTPClient }
  import scala.collection.JavaConversions._

  def ls(path: String)(predicate: String ⇒ Boolean)(implicit sshjSFTPClient: SFTPClient): List[ListEntry] = {

    sshjSFTPClient.ls(path).filterNot(e ⇒ {
      predicate(e.getName)
    }).map {
      e ⇒
        val t =
          e.getAttributes.getType match {
            case FileMode.Type.DIRECTORY ⇒ DirectoryType
            case FileMode.Type.SYMKLINK  ⇒ LinkType
            case _                       ⇒ FileType
          }
        ListEntry(e.getName, t, Some(e.getAttributes.getMtime))
    }.toList // FIXME is it because the List<> gets converted to a buffer?
  }

  def chmod(path: String, perms: Int)(implicit sshjSFTPClient: SFTPClient) = sshjSFTPClient.chmod(path, perms)

  def close()(implicit sshjSFTPClient: SFTPClient) = sshjSFTPClient.close()

  def canonicalize(path: String)(implicit sshjSFTPClient: SFTPClient) = sshjSFTPClient.canonicalize(path)

  def exists(path: String)(implicit sshjSFTPClient: SFTPClient) = sshjSFTPClient.statExistence(path) != null

  def mkdir(path: String)(implicit sshjSFTPClient: SFTPClient) = sshjSFTPClient.mkdir(path)

  def rmdir(path: String)(implicit sshjSFTPClient: SFTPClient) = sshjSFTPClient.rmdir(path)

  def rm(path: String)(implicit sshjSFTPClient: SFTPClient) = sshjSFTPClient.rm(path)

  def rename(oldName: String, newName: String)(implicit sshjSFTPClient: SFTPClient) = sshjSFTPClient.rename(oldName, newName)

  lazy val unconfirmedExchanges = 32

  def readAheadFileInputStream(path: String)(implicit sshjSFTPClient: SFTPClient): InputStream = {

    val fileHandle =
      try sshjSFTPClient.open(path, util.EnumSet.of(OpenMode.READ))
      catch {
        case e: Throwable ⇒
          try sshjSFTPClient.close()
          finally close
          throw e
      }

    def closeAll() = {
      try fileHandle.close()
      finally try sshjSFTPClient.close()
      finally close
    }

    new fileHandle.ReadAheadRemoteFileInputStream(unconfirmedExchanges) {
      override def close() = {
        try closeAll()
        finally super.close()
      }
    }
  }

  def fileOutputStream(is: InputStream, path: String)(implicit sshjSFTPClient: SFTPClient) = {
    val fileHandle =
      try sshjSFTPClient.open(path, util.EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
      catch {
        case e: Throwable ⇒
          try sshjSFTPClient.close()
          finally close()
          throw e
      }

    def closeAll() = {
      try fileHandle.close()
      finally try sshjSFTPClient.close()
      finally close()
    }

    try {
      val os = new fileHandle.RemoteFileOutputStream(0, unconfirmedExchanges)
      try copyStream(is, os)
      finally os.close()
    } finally closeAll()

  }
}
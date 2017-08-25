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

package gridscale.ssh.sshj

import java.io.InputStream
import java.util
import gridscale._
import gridscale.tools._

object SSHJSFTPClient {

  import net.schmizz.sshj.sftp

  import collection.JavaConverters._

  def ls(sshjSFTPClient: sftp.SFTPClient)(path: String, accept: String ⇒ Boolean): Vector[ListEntry] =
    sshjSFTPClient.ls(path).asScala.filter { e ⇒ accept(e.getName) }.map {
      e ⇒
        val t =
          e.getAttributes.getType match {
            case sftp.FileMode.Type.DIRECTORY ⇒ FileType.Directory
            case sftp.FileMode.Type.SYMLINK   ⇒ FileType.Link
            case _                            ⇒ FileType.File
          }
        ListEntry(e.getName, t, Some(e.getAttributes.getMtime))
    }.toVector

  def chmod(sshjSFTPClient: sftp.SFTPClient)(path: String, perms: Int) = sshjSFTPClient.chmod(path, perms)

  def close(sshjSFTPClient: sftp.SFTPClient) = sshjSFTPClient.close()

  def canonicalize(sshjSFTPClient: sftp.SFTPClient)(path: String) = sshjSFTPClient.canonicalize(path)

  def exists(sshjSFTPClient: sftp.SFTPClient)(path: String) = sshjSFTPClient.statExistence(path) != null

  def mkdir(sshjSFTPClient: sftp.SFTPClient)(path: String) = sshjSFTPClient.mkdir(path)

  def rmdir(sshjSFTPClient: sftp.SFTPClient)(path: String) = sshjSFTPClient.rmdir(path)

  def rm(sshjSFTPClient: sftp.SFTPClient)(path: String) = sshjSFTPClient.rm(path)

  def rename(sshjSFTPClient: sftp.SFTPClient)(oldName: String, newName: String) = sshjSFTPClient.rename(oldName, newName)

  lazy val unconfirmedExchanges = 32

  def withClosable[C <: java.io.Closeable, T](sshjSFTPClient: sftp.SFTPClient, open: ⇒ C)(f: C ⇒ T): T = {
    val c = open
    try f(c)
    catch {
      case e: Throwable ⇒
        close(sshjSFTPClient)
        throw e
    } finally c.close()
  }

  // FIXME takes care of too much things => should not close the sftpclient for instance
  def readAheadFileInputStream(sshjSFTPClient: sftp.SFTPClient)(path: String): InputStream = {
    val fileHandle = sshjSFTPClient.open(path, util.EnumSet.of(sftp.OpenMode.READ))
    new fileHandle.ReadAheadRemoteFileInputStream(unconfirmedExchanges)
  }

  // FIXME this is badly named as it writes but does not return an output stream
  def fileOutputStream(sshjSFTPClient: sftp.SFTPClient)(is: InputStream, path: String): Unit =
    withClosable(sshjSFTPClient, sshjSFTPClient.open(path, util.EnumSet.of(sftp.OpenMode.WRITE, sftp.OpenMode.CREAT, sftp.OpenMode.TRUNC))) {
      fileHandle ⇒ withClosable(sshjSFTPClient, new fileHandle.RemoteFileOutputStream(0, unconfirmedExchanges)) { copyStream(is, _) }
    }

}

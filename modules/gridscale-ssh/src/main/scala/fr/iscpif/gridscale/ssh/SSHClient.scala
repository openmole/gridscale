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
import java.util

import fr.iscpif.gridscale.authentication.{ PrivateKey, AuthenticationException }
import fr.iscpif.gridscale.storage.{ FileType, LinkType, DirectoryType, ListEntry }
import fr.iscpif.gridscale.tools._

class SSHClient {

  import net.schmizz.sshj.{ SSHClient ⇒ SSHJSSHClient }
  import net.schmizz.sshj.transport.verification.PromiscuousVerifier
  import net.schmizz.sshj.DefaultConfig

  // instantiated only once and not for each sshj SSHClient
  // see https://groups.google.com/d/msg/sshj-users/p-cjao1MiHg/nFZ99-WEf6IJ
  private lazy val sshDefaultConfig = new DefaultConfig()
  private lazy val peer: SSHJSSHClient = new SSHJSSHClient(sshDefaultConfig)

  def startSession: SSHSession = {
    val sshjSession = peer.startSession
    new SSHSession {
      def close = sshjSession.close()

      def exec(command: String): Command = {
        val sshjCommand = sshjSession.exec(command)
        new Command {
          def join = sshjCommand.join()
          def close = sshjCommand.close()
          def getExitStatus: Int = sshjCommand.getExitStatus
          def getInputStream: InputStream = sshjSession.getInputStream
          def getErrorStream: InputStream = sshjCommand.getErrorStream
        }
      }
    }
  }

  def close = peer.close()
  def connect(host: String, port: Int) = peer.connect(host, port)
  def disconnect = peer.disconnect()

  def setConnectTimeout(timeout: Int) = peer.setConnectTimeout(timeout)
  def setTimeout(timeout: Int) = peer.setTimeout(timeout)
  def disableHostChecking() = peer.getTransport.addHostKeyVerifier(new PromiscuousVerifier)
  def useCompression() = peer.useCompression()

  def authPassword(username: String, password: String) = {
    try peer.authPassword(username, password)
    catch {
      case e: Throwable ⇒ throw AuthenticationException("Error during ssh login/password authentication", e)
    }
  }
  def authPrivateKey(privateKey: PrivateKey) = {
    try {
      val kp = peer.loadKeys(privateKey.privateKey.getAbsolutePath, privateKey.password)
      peer.authPublickey(privateKey.user, kp)
    } catch {
      case e: Throwable ⇒ throw AuthenticationException("Error during ssh key authentication", e)
    }
  }

  def isAuthenticated: Boolean = peer.isAuthenticated
  def isConnected: Boolean = peer.isConnected

  def newSFTPClient: SFTPClient = {
    val sshjSFTPClient = peer.newSFTPClient()
    new SFTPClient {
      import net.schmizz.sshj.sftp.{ FileMode, OpenMode }
      import scala.collection.JavaConversions._

      def ls(path: String)(predicate: String ⇒ Boolean): List[ListEntry] = {

        sshjSFTPClient.ls(path).filterNot(e ⇒ { predicate(e.getName) }).map {
          e ⇒
            val t =
              e.getAttributes.getType match {
                case FileMode.Type.DIRECTORY ⇒ DirectoryType
                case FileMode.Type.SYMKLINK  ⇒ LinkType
                case _                       ⇒ FileType
              }
            ListEntry(e.getName, t, Some(e.getAttributes.getMtime))
        } toList // FIXME is it because the List<> gets converted to a buffer?
      }

      def chmod(path: String, perms: Int) = sshjSFTPClient.chmod(path, perms)
      def close = sshjSFTPClient.close()
      def canonicalize(path: String) = sshjSFTPClient.canonicalize(path)
      def exists(path: String) = sshjSFTPClient.statExistence(path) != null
      def mkdir(path: String) = sshjSFTPClient.mkdir(path)
      def rmdir(path: String) = sshjSFTPClient.rmdir(path)
      def rm(path: String) = sshjSFTPClient.rm(path)
      def rename(oldName: String, newName: String) = sshjSFTPClient.rename(oldName, newName)

      lazy val unconfirmedExchanges = 32

      def readAheadFileInputStream(path: String): InputStream = {

        val fileHandle =
          try sshjSFTPClient.open(path, util.EnumSet.of(OpenMode.READ))
          catch {
            case e: Throwable ⇒
              try sshjSFTPClient.close
              finally close
              throw e
          }

        def closeAll = {
          try fileHandle.close
          finally try sshjSFTPClient.close
          finally close
        }

        new fileHandle.ReadAheadRemoteFileInputStream(unconfirmedExchanges) {
          override def close = {
            try closeAll
            finally super.close
          }
        }
      }

      def fileOutputStream(is: InputStream, path: String) = {
        val fileHandle =
          try sshjSFTPClient.open(path, util.EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
          catch {
            case e: Throwable ⇒
              try sshjSFTPClient.close
              finally close
              throw e
          }

        def closeAll = {
          try fileHandle.close
          finally try sshjSFTPClient.close
          finally close
        }

        try {
          val os = new fileHandle.RemoteFileOutputStream(0, unconfirmedExchanges)
          try copyStream(is, os)
          finally os.close
        } finally closeAll

      }
    }
  }
}

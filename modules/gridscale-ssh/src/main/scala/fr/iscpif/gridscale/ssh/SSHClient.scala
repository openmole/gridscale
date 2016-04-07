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

import fr.iscpif.gridscale.authentication.{ PrivateKey, AuthenticationException }

class SSHClient {

  import net.schmizz.sshj.{ SSHClient ⇒ SSHJSSHClient }
  import net.schmizz.sshj.transport.verification.PromiscuousVerifier
  import net.schmizz.sshj.DefaultConfig

  // instantiated only once and not for each sshj SSHClient
  // see https://groups.google.com/d/msg/sshj-users/p-cjao1MiHg/nFZ99-WEf6IJ
  private lazy val sshDefaultConfig = new DefaultConfig()
  private lazy val peer: SSHJSSHClient = new SSHJSSHClient(sshDefaultConfig)

  def startSession: SSHSession = new SSHSession {

    implicit val peerSession = peer.startSession
    import impl.SSHJSession

    override def close() = SSHJSession.close()
    override def exec(command: String) = SSHJSession.exec(command)
  }

  def close() = peer.close()
  def connect(host: String, port: Int) = peer.connect(host, port)
  def disconnect() = peer.disconnect()

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

  def newSFTPClient = new SFTPClient {

    import impl.SSHJSFTPClient
    implicit val peerSFTPClient = peer.newSFTPClient()

    override def fileOutputStream(is: InputStream, path: String) = SSHJSFTPClient.fileOutputStream(is, path)
    override def rename(oldName: String, newName: String) = SSHJSFTPClient.rename(oldName, newName)
    override def canonicalize(path: String) = SSHJSFTPClient.canonicalize(path)
    override def rmdir(path: String) = SSHJSFTPClient.rmdir(path)
    override def chmod(path: String, perms: Int) = SSHJSFTPClient.chmod(path, perms)
    override def readAheadFileInputStream(path: String) = SSHJSFTPClient.readAheadFileInputStream(path)
    override def close() = SSHJSFTPClient.close()
    override def rm(path: String) = SSHJSFTPClient.rm(path)
    override def mkdir(path: String) = SSHJSFTPClient.mkdir(path)
    override def exists(path: String) = SSHJSFTPClient.exists(path)
    override def ls(path: String)(predicate: String ⇒ Boolean) = SSHJSFTPClient.ls(path)(predicate)
  }
}

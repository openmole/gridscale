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

package fr.iscpif.gridscale.ssh.sshj

import fr.iscpif.gridscale.authentication._
import fr.iscpif.gridscale.tools._
import java.io.InputStream

import net.schmizz.sshj.common.IOUtils

object SSHClient {

  def withSession[T](c: SSHClient)(f: SSHSession ⇒ T): util.Try[T] = util.Try {
    val session = c.startSession
    try f(session)
    finally session.close
  }

  //  def execReturnCode(client: SSHClient)(cde: String) = withSession(client) { session ⇒
  //    val cmd = session.exec(cde.toString)
  //    try {
  //      cmd.join
  //      cmd.getExitStatus
  //    } finally cmd.close
  //  }

  def exec(client: SSHClient)(cde: String) = withSession(client) { session ⇒
    val cmd = session.exec(cde.toString)
    try {
      cmd.join
      fr.iscpif.gridscale.ssh.ExecutionResult(cmd.getExitStatus, IOUtils.readFully(cmd.getInputStream).toString, IOUtils.readFully(cmd.getErrorStream).toString)
    } finally cmd.close
  }

  //  def exec(client: SSHClient)(cde: String) = withSession(client) { session ⇒
  //    val retCode = execReturnCode(client)(cde)
  //    if (retCode != 0) throw new RuntimeException("Return code was no 0 but " + retCode + " while executing " + cde)
  //  }

}

class SSHClient {

  import net.schmizz.sshj.transport.verification.PromiscuousVerifier
  import net.schmizz.sshj.{ DefaultConfig, SSHClient ⇒ SSHJSSHClient }

  // instantiated only once and not for each sshj SSHClient
  // see https://groups.google.com/d/msg/sshj-users/p-cjao1MiHg/nFZ99-WEf6IJ
  private lazy val sshDefaultConfig = new DefaultConfig()
  private lazy val peer: SSHJSSHClient = new SSHJSSHClient(sshDefaultConfig)

  def startSession: SSHSession = new SSHSession {
    implicit val peerSession = peer.startSession
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

  def authPassword(username: String, password: String): util.Try[Unit] =
    util.Try(peer.authPassword(username, password)) mapFailure {
      e ⇒ AuthenticationException("Error during ssh login/password authentication", e)
    }

  def authPrivateKey(privateKey: PrivateKey): util.Try[Unit] =
    util.Try {
      val kp = peer.loadKeys(privateKey.privateKey.getAbsolutePath, privateKey.password)
      peer.authPublickey(privateKey.user, kp)
    } mapFailure {
      e ⇒ AuthenticationException("Error during ssh key authentication", e)
    }

  def isAuthenticated: Boolean = peer.isAuthenticated
  def isConnected: Boolean = peer.isConnected

  def newSFTPClient = new SFTPClient {
    lazy val peerSFTPClient = peer.newSFTPClient()
    override def fileOutputStream(is: InputStream, path: String) = SSHJSFTPClient.fileOutputStream(peerSFTPClient)(is, path)
    override def rename(oldName: String, newName: String) = SSHJSFTPClient.rename(peerSFTPClient)(oldName, newName)
    override def canonicalize(path: String) = SSHJSFTPClient.canonicalize(peerSFTPClient)(path)
    override def rmdir(path: String) = SSHJSFTPClient.rmdir(peerSFTPClient)(path)
    override def chmod(path: String, perms: Int) = SSHJSFTPClient.chmod(peerSFTPClient)(path, perms)
    override def readAheadFileInputStream(path: String) = SSHJSFTPClient.readAheadFileInputStream(peerSFTPClient)(path)
    override def close() = SSHJSFTPClient.close(peerSFTPClient)
    override def rm(path: String) = SSHJSFTPClient.rm(peerSFTPClient)(path)
    override def mkdir(path: String) = SSHJSFTPClient.mkdir(peerSFTPClient)(path)
    override def exists(path: String) = SSHJSFTPClient.exists(peerSFTPClient)(path)
    override def ls(path: String)(predicate: String ⇒ Boolean) = SSHJSFTPClient.ls(peerSFTPClient)(path, predicate)
  }
}

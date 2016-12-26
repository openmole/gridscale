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

import gridscale.authentication._
import gridscale.tools._
import java.io.InputStream

import net.schmizz.sshj.common.IOUtils

object SSHClient {

  def withSession[T](c: SSHClient)(f: SSHSession ⇒ T): util.Try[T] = util.Try {
    val session = c.startSession
    try f(session)
    finally session.close
  }

  def exec(client: SSHClient)(cde: String) = withSession(client) { session ⇒
    val cmd = session.exec(cde.toString)
    try {
      cmd.join
      gridscale.ssh.ExecutionResult(cmd.getExitStatus, IOUtils.readFully(cmd.getInputStream).toString, IOUtils.readFully(cmd.getErrorStream).toString)
    } finally cmd.close
  }

  case class NoSuchFileException(msg: String, cause: Throwable = null) extends Throwable(msg, cause)
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
    def wrap[T](f: ⇒ T) =
      util.Try(f) match {
        case s @ util.Success(_) ⇒ s
        case util.Failure(e: net.schmizz.sshj.sftp.SFTPException) if e.getMessage == "No such file" ⇒
          util.Failure(new SSHClient.NoSuchFileException(e.getMessage))
        case e ⇒ e
      }

    private lazy val peerSFTPClient = wrap(peer.newSFTPClient())

    private def withClient[T](f: net.schmizz.sshj.sftp.SFTPClient ⇒ T) =
      for {
        c ← peerSFTPClient
        r ← wrap(f(c))
      } yield r

    override def writeFile(is: InputStream, path: String) =
      withClient { c ⇒ SSHJSFTPClient.fileOutputStream(c)(is, path) }

    override def rename(oldName: String, newName: String) =
      withClient { c ⇒ SSHJSFTPClient.rename(c)(oldName, newName) }

    override def canonicalize(path: String) =
      withClient { c ⇒ SSHJSFTPClient.canonicalize(c)(path) }

    override def rmdir(path: String) =
      withClient { c ⇒ SSHJSFTPClient.rmdir(c)(path) }

    override def chmod(path: String, perms: Int) =
      withClient { c ⇒ SSHJSFTPClient.chmod(c)(path, perms) }

    override def readAheadFileInputStream(path: String) =
      withClient { c ⇒ SSHJSFTPClient.readAheadFileInputStream(c)(path) }

    override def close() =
      withClient { c ⇒ SSHJSFTPClient.close(c) }

    override def rm(path: String) =
      withClient { c ⇒ SSHJSFTPClient.rm(c)(path) }

    override def mkdir(path: String) =
      withClient { c ⇒ SSHJSFTPClient.mkdir(c)(path) }

    override def exists(path: String) =
      withClient { c ⇒ SSHJSFTPClient.exists(c)(path) }

    override def ls(path: String)(predicate: String ⇒ Boolean) =
      withClient { c ⇒ SSHJSFTPClient.ls(c)(path, predicate) }
  }
}

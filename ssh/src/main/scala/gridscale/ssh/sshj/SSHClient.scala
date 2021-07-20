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
import java.io.InputStream
import java.util.concurrent.TimeUnit

import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.common.IOUtils
import squants.time.Time

object SSHClient {

  def connect(ssh: SSHClient) = {
    ssh.disableHostChecking()
    ssh.useCompression()
    ssh.setConnectTimeout(ssh.timeout.millis.toInt)
    ssh.setTimeout(ssh.timeout.millis.toInt)
    ssh.connect(ssh.host, ssh.port)
    ssh
  }

  def withSession[T](c: SSHClient)(f: SSHSession ⇒ T): util.Try[T] = util.Try {
    val session = c.startSession()
    try f(session)
    finally session.close()
  }

  def withSFTP[T](c: SSHClient, f: SFTPClient ⇒ T) = {
    val sftp = c.newSFTPClient
    try f(sftp)
    finally sftp.close()
  }

  def launchInBackground(client: SSHClient, cde: String): Unit = withSession(client) { session ⇒
    val cmd = session.exec(s"$cde")
    cmd.close()
  }

  def run(client: SSHClient, cde: String) = withSession(client) { session ⇒
    val cmd = session.exec(cde.toString)
    try {
      cmd.join()
      val output = IOUtils.readFully(cmd.getInputStream()).toString
      val error = IOUtils.readFully(cmd.getErrorStream()).toString
      gridscale.ExecutionResult(cmd.getExitStatus(), output, error)
    } finally cmd.close()
  }

  case class NoSuchFileException(msg: String, cause: Throwable = null) extends Throwable(msg, cause)
}

class SSHClient(val host: String, val port: Int, val timeout: Time, val keepAlive: Option[Time] = None) {

  import net.schmizz.sshj.transport.verification.PromiscuousVerifier
  import net.schmizz.sshj.{ DefaultConfig, SSHClient ⇒ SSHJSSHClient }

  // instantiated only once and not for each sshj SSHClient
  // see https://groups.google.com/d/msg/sshj-users/p-cjao1MiHg/nFZ99-WEf6IJ
  private lazy val sshDefaultConfig = {
    val config = new DefaultConfig()
    if (keepAlive.isDefined) config.setKeepAliveProvider(KeepAliveProvider.KEEP_ALIVE)
    config
  }

  private lazy val peer: SSHJSSHClient = new SSHJSSHClient(sshDefaultConfig)

  def startSession(): SSHSession = {
    val session = peer.startSession
    new SSHSession {
      override def close() = SSHJSession.close(session)
      override def exec(command: String) = SSHJSession.exec(session, command)
    }
  }

  def close() = peer.close()

  def connect(host: String, port: Int) = {
    peer.connect(host, port)
    keepAlive.foreach(k ⇒ peer.getConnection.getKeepAlive().setKeepAliveInterval(k.toSeconds.toInt))
  }

  def disconnect() = peer.disconnect()

  def setConnectTimeout(timeout: Int) = peer.setConnectTimeout(timeout)
  def setTimeout(timeout: Int) = peer.setTimeout(timeout)
  def disableHostChecking() = peer.getTransport.addHostKeyVerifier(new PromiscuousVerifier)
  def useCompression() = peer.useCompression()

  def authPassword(username: String, password: String): Unit =
    try peer.authPassword(username, password)
    catch {
      case e: Throwable ⇒ throw AuthenticationException("Error during ssh login/password authentication", e)
    }

  def authPrivateKey(privateKey: PrivateKey): Unit =
    try {
      val kp = peer.loadKeys(privateKey.privateKey.getAbsolutePath, privateKey.password)
      peer.authPublickey(privateKey.user, kp)
    } catch {
      case e: Throwable ⇒ throw AuthenticationException("Error during ssh key authentication", e)
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

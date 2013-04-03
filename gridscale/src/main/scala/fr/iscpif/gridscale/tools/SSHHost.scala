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

package fr.iscpif.gridscale.tools

import fr.iscpif.gridscale.authentication._

import net.schmizz.sshj._
import net.schmizz.sshj.sftp._
import transport.verification.HostKeyVerifier
import java.security.PublicKey

trait SSHHost {
  type A = SSHAuthentication

  def user: String
  def host: String
  def port: Int = 22
  def timeout = 30 * 1000

  def connectionCache = ConnectionCache.default

  def withConnection[T](f: SSHClient ⇒ T)(implicit authentication: SSHAuthentication) = {
    val connection = connectionCache.cached(this)
    try f(connection.get)
    finally connectionCache.release(connection)
  }

  def connect(implicit authentication: SSHAuthentication) = {
    val ssh = new SSHClient
    ssh.setConnectTimeout(timeout)
    ssh.setTimeout(timeout)
    ssh.addHostKeyVerifier(new HostKeyVerifier {
      def verify(p1: String, p2: Int, p3: PublicKey) = true
    })
    ssh.connect(host, port)
    authentication.authenticate(ssh)
    ssh
  }

  def withSftpClient[T](f: SFTPClient ⇒ T)(implicit authentication: SSHAuthentication): T = withConnection {
    connection ⇒
      val sftpClient = connection.newSFTPClient
      try f(sftpClient) finally sftpClient.close
  }
}

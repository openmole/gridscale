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

import fr.iscpif.gridscale.tools.DefaultTimeout
import net.schmizz.sshj._
import net.schmizz.sshj.sftp._
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

trait SSHHost <: DefaultTimeout {

  def credential: SSHAuthentication
  def host: String
  def port: Int = 22
  lazy val sshDefaultConfigH = new DefaultConfig()

  def withConnection[T](f: SSHClient ⇒ T) = {
    val connection = getConnection
    connection.setConnectTimeout(timeout.toMillis.toInt)
    connection.setTimeout(timeout.toMillis.toInt)
    try f(connection)
    finally release(connection)
  }

  def getConnection = connect
  def release(c: SSHClient) = c.close

  def connect = {
    val ssh = new SSHClient(sshDefaultConfigH)
    ssh.setConnectTimeout(timeout.toMillis.toInt)
    ssh.setTimeout(timeout.toMillis.toInt)
    // disable strict host key checking
    ssh.getTransport.addHostKeyVerifier(new PromiscuousVerifier)

    ssh.connect(host, port)
    credential.authenticate(ssh)
    ssh
  }

  def withSftpClient[T](f: SFTPClient ⇒ T): T = withConnection {
    connection ⇒
      val sftpClient = connection.newSFTPClient
      try f(sftpClient) finally sftpClient.close
  }
}

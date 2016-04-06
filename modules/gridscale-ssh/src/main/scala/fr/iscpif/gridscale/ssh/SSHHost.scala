/*
 * Copyright (C) 2012 Romain Reuillon
 * Copyright (C) 2015 Jonathan Passerat-Palmbach
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

import scala.concurrent.duration.Duration

trait SSHHost {

  def credential: SSHAuthentication
  def host: String
  def port: Int
  def timeout: Duration

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
    val ssh = credential.connect(host, port)
    ssh.setConnectTimeout(timeout.toMillis.toInt)
    ssh.setTimeout(timeout.toMillis.toInt)
    ssh
  }

  def withSftpClient[T](f: SFTPClient ⇒ T): T = withConnection {
    connection ⇒
      val sftpClient = connection.newSFTPClient
      try f(sftpClient) finally sftpClient.close
  }
}

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

import ch.ethz.ssh2.SFTPv3Client
import fr.iscpif.gridscale.authentication._
import ch.ethz.ssh2.{ Connection, Session }

trait SSHHost {
  type A = SSHAuthentication

  def user: String
  def host: String
  def port: Int = 22

  def connectionCache = ConnectionCache.default

  /*def withSession[T](f: Session ⇒ T)(implicit authentication: SSHAuthentication) = withConnection { c ⇒
    val s = c.openSession
    try f(s)
    finally s.close
  }  */

  def withConnection[T](f: Connection ⇒ T)(implicit authentication: SSHAuthentication) = {
    val connection = connectionCache.cached(this)
    try f(connection)
    finally connectionCache.release(this)
  }

  def connect(implicit authentication: SSHAuthentication) = {
    val c = new Connection(host, port)
    c.connect
    authentication.authenticate(c)
    c
  }

  def withSftpClient[T](f: SFTPv3Client ⇒ T)(implicit authentication: SSHAuthentication): T = withConnection {
    connection ⇒
      val sftpClient = new SFTPv3Client(connection)
      try f(sftpClient) finally sftpClient.close
  }
}

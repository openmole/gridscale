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

import net.schmizz.sshj.connection.{ ConnectionException, Connection }
import net.schmizz.sshj.SSHClient

trait SSHConnectionCache <: SSHHost {

  def connectionKeepAlive: Long

  case class CacheLine(client: SSHClient, lastUsed: Long = System.currentTimeMillis)

  @transient private var connections = List.empty[CacheLine]

  override def withConnection[T](f: SSHClient ⇒ T)(implicit authentication: SSHAuthentication): T = {
    val c = getConnection
    try f(c)
    finally release(c)
  }

  override def getConnection(implicit authentication: SSHAuthentication) = synchronized {
    connections match {
      case h :: t => connections = t; h.client
      case Nil => connect
    }
  }

  override def release(c: SSHClient) = synchronized {
    clean
    if (c.isAuthenticated && c.isConnected) connections = CacheLine(c) :: connections
  }

  def clean = synchronized {
    val (keep, discard) = connections.partition(_.lastUsed + connectionKeepAlive * 1000 > System.currentTimeMillis)
    discard.foreach(_.client.close)
    connections = keep
  }

  override def finalize = connections.foreach(_.client.close)

}

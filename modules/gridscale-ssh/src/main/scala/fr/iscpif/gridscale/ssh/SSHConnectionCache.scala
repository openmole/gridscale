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

import net.schmizz.sshj.SSHClient
import java.util.concurrent.Executors

trait SSHConnectionCache <: SSHHost {
  @transient private var connection: Option[SSHClient] = None

  override def withConnection[T](f: SSHClient ⇒ T)(implicit authentication: SSHAuthentication): T = {
    val c = getConnection
    try f(c)
    finally release(c)
  }

  override def getConnection(implicit authentication: SSHAuthentication) = synchronized {
    def updateConnection = {
      val newC = connect
      connection = Some(newC)
      newC
    }

    connection match {
      case Some(c) ⇒
        if (c.isAuthenticated && c.isConnected) c
        else updateConnection
      case None ⇒ updateConnection
    }
  }

  override def release(c: SSHClient) = {}

  override def finalize = {
    Executors.newSingleThreadExecutor.submit(new Runnable {
      def run = connection.foreach(_.close)
    })
  }

}

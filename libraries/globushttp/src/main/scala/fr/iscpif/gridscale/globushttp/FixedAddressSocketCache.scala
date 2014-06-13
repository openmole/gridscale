/*
 * Copyright (C) 2014 Romain Reuillon
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

package fr.iscpif.gridscale.globushttp

import java.net.{ SocketTimeoutException, Socket }
import java.util.concurrent.Executors
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory

trait FixedAddressSocketCache <: GlobusHttpClient {

  @transient private var connection: Option[Socket] = None

  override def finalize = {
    Executors.newSingleThreadExecutor.submit(new Runnable {
      def run = connection.foreach(_.close)
    })
  }

  @transient override abstract def socket(host: String, port: Int) = synchronized {
    def updateConnection = {
      val newC = super.socket(host, port)
      newC.setKeepAlive(true)
      connection = Some(newC)
      newC
    }

    connection match {
      case Some(c: Socket) ⇒
        if (c.isConnected && !c.isClosed && !c.isInputShutdown && !c.isOutputShutdown) c
        else updateConnection
      case None ⇒ updateConnection
    }
  }

  override def post(in: String, address: java.net.URI, headers: Map[String, String]): String =
    try super.post(in, address, headers)
    catch {
      case e: SocketTimeoutException ⇒
        connection.foreach(_.close)
        connection = None
        throw e
    }

}

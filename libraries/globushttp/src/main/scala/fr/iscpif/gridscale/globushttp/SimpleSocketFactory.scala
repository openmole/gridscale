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

import org.apache.commons.httpclient.protocol.ProtocolSocketFactory
import org.globus.gsi.gssapi.net.{ GssSocketFactory, GssSocket }
import java.net.{ Socket, InetAddress }
import org.apache.commons.httpclient.params.HttpConnectionParams
import org.gridforum.jgss.ExtendedGSSManager
import org.globus.gsi.gssapi.GSSConstants
import org.ietf.jgss.GSSContext

import scala.concurrent.duration.Duration

trait SimpleSocketFactory <: SocketFactory {

  def proxyBytes: Array[Byte]
  def timeout: Duration

  def sslContext(proxy: Array[Byte]) = {
    val manager = ExtendedGSSManager.getInstance

    manager.createContext(null,
      GSSConstants.MECH_OID,
      GlobusHttpClient.credential(proxy),
      GSSContext.DEFAULT_LIFETIME)
  }

  def initialize(socket: GssSocket) = {
    socket.setAuthorization(null)
    socket.setUseClientMode(true)
    socket.setAuthorization(null)
    socket.setSoTimeout(timeout.toMillis.toInt)
    socket
  }

  def socket(host: String, port: Int): Socket =
    initialize(GssSocketFactory.getDefault.createSocket(host, port, sslContext(proxyBytes)).asInstanceOf[GssSocket])

}

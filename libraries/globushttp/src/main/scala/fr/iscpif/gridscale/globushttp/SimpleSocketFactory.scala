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

import org.globus.gsi.gssapi.net.{ GssSocketFactory, GssSocket }
import java.net.{ InetSocketAddress, SocketAddress, Socket, InetAddress }
import org.gridforum.jgss.ExtendedGSSManager
import org.globus.gsi.gssapi.GSSConstants
import org.ietf.jgss.GSSContext

trait SimpleSocketFactory <: SocketFactory {

  def proxyBytes: Array[Byte]

  def sslContext(proxy: Array[Byte]) = {
    val manager = ExtendedGSSManager.getInstance

    manager.createContext(null,
      GSSConstants.MECH_OID,
      GlobusHttpClient.credential(proxy),
      GSSContext.DEFAULT_LIFETIME)
  }

  def socket(host: String, port: Int): Socket = connect(host, port, newSocket)

  def newSocket = {
    val socket = new Socket()
    socket.setSoTimeout(timeout.toMillis.toInt)
    socket
  }

  def connect(host: String, port: Int, socket: Socket) = {
    socket.connect(new InetSocketAddress(host, port), timeout.toMillis.toInt)
    val gssSocket = GssSocketFactory.getDefault.createSocket(socket, host, port, sslContext(proxyBytes)).asInstanceOf[GssSocket]
    gssSocket.setUseClientMode(true)
    gssSocket.setAuthorization(null)
    gssSocket.setSoTimeout(timeout.toMillis.toInt)
    //gssSocket.connect(new InetSocketAddress(host, port), timeout.toMillis.toInt)
    gssSocket
  }

}

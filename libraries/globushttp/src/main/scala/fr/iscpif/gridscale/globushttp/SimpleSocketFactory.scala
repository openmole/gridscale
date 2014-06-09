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
import org.globus.gsi.gssapi.net.{GssSocketFactory, GssSocket}
import java.net.{Socket, InetAddress}
import org.apache.commons.httpclient.params.HttpConnectionParams
import org.gridforum.jgss.ExtendedGSSManager
import org.globus.gsi.gssapi.GSSConstants
import org.ietf.jgss.GSSContext

trait SimpleSocketFactory <: SocketFactory {

  def proxyBytes: Array[Byte]
  def timeout: Int

  def factory = new  ProtocolSocketFactory {

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
      socket.setSoTimeout(timeout)
      socket
    }

    def createSocket(host: String, port: Int): java.net.Socket =
      initialize(GssSocketFactory.getDefault.createSocket(host, port, sslContext(proxyBytes)).asInstanceOf[GssSocket])


    def createSocket(host: String, port: Int, localAddress: InetAddress, localPort: Int, params: HttpConnectionParams): java.net.Socket = {
      val socket = javax.net.SocketFactory.getDefault.createSocket(host,
        port,
        localAddress,
        localPort)
      initialize(GssSocketFactory.getDefault.createSocket(socket, host, port, sslContext(proxyBytes)).asInstanceOf[GssSocket])
    }

    def createSocket(host: String, port: Int, localAddress: java.net.InetAddress, localPort: Int): Socket = {
      val socket = javax.net.SocketFactory.getDefault.createSocket(host,
        port,
        localAddress,
        localPort)
      initialize(GssSocketFactory.getDefault.createSocket(socket, host, port, sslContext(proxyBytes)).asInstanceOf[GssSocket])
    }
  }

}

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

import java.io.{ FileInputStream, File }
import org.gridforum.jgss.{ ExtendedGSSCredential, ExtendedGSSManager }
import org.ietf.jgss.{ GSSContext, GSSCredential }
import org.globus.gsi.gssapi.{ GSSConstants, GlobusGSSCredentialImpl }
import org.apache.commons.httpclient.protocol.{ Protocol, ProtocolSocketFactory }
import org.globus.gsi.gssapi.net.{ GssSocket, GssSocketFactory }
import java.net.{ Socket, InetAddress }
import org.apache.commons.httpclient.params.HttpConnectionParams
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.{ PostMethod, StringRequestEntity }

object GlobusHttpClient {
  def credential(proxy: File) = {
    val proxyBytes = Array.ofDim[Byte](proxy.length.toInt)
    val in = new FileInputStream(proxy)
    try in.read(proxyBytes)
    finally in.close

    ExtendedGSSManager.getInstance.asInstanceOf[ExtendedGSSManager].createCredential(
      proxyBytes,
      ExtendedGSSCredential.IMPEXP_OPAQUE,
      GSSCredential.DEFAULT_LIFETIME,
      null, // use default mechanism: GSI
      GSSCredential.INITIATE_AND_ACCEPT).asInstanceOf[GlobusGSSCredentialImpl]
  }

  def sslContext(proxy: File) = {
    val manager = ExtendedGSSManager.getInstance

    manager.createContext(null,
      GSSConstants.MECH_OID,
      credential(proxy),
      GSSContext.DEFAULT_LIFETIME)
  }

  def protocolFactory(proxy: File, timeout: Int) = new ProtocolSocketFactory {
    def createSocket(host: String, port: Int): java.net.Socket = {
      val factory = GssSocketFactory.getDefault
      val socket = factory.createSocket(host, port, sslContext(proxy)).asInstanceOf[GssSocket]
      socket.setAuthorization(null)
      socket.setUseClientMode(true)
      socket.setSoTimeout(timeout)
      socket
    }

    def createSocket(host: String, port: Int, localAddress: InetAddress, localPort: Int, params: HttpConnectionParams): java.net.Socket =
      createSocket(host, port)
    def createSocket(host: String, port: Int, localAddress: java.net.InetAddress, localPort: Int): Socket =
      createSocket(host, port)
  }

}

import GlobusHttpClient._

trait GlobusHttpClient {
  def proxy: File
  def timeout: Int
  @transient private lazy val factory = protocolFactory(proxy, timeout)

  def request(in: String, address: java.net.URI, headers: Map[String, String]): String = {
    val myHttpg = new Protocol("https", factory, 8446)
    val httpclient = new org.apache.commons.httpclient.HttpClient
    httpclient.getHostConfiguration.setHost(address.getHost, address.getPort, myHttpg)
    val entity = new StringRequestEntity(in, "text/xml", null)
    val post = new PostMethod(address.getPath)
    post.setRequestEntity(entity)
    headers.foreach { case (k, v) => post.setRequestHeader(k, v) }
    httpclient.executeMethod(post)
    post.getResponseBodyAsString
  }
}

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

import org.gridforum.jgss.{ExtendedGSSContext, ExtendedGSSCredential, ExtendedGSSManager}
import org.ietf.jgss.{ GSSContext, GSSCredential }
import org.globus.gsi.gssapi.{ GSSConstants, GlobusGSSCredentialImpl }
import org.apache.commons.httpclient.protocol.{ Protocol, ProtocolSocketFactory }
import org.apache.commons.httpclient.methods.{PutMethod, GetMethod, PostMethod, StringRequestEntity}

object GlobusHttpClient {
  def credential(proxy: Array[Byte]) =
    ExtendedGSSManager.getInstance.asInstanceOf[ExtendedGSSManager].createCredential(
      proxy,
      ExtendedGSSCredential.IMPEXP_OPAQUE,
      GSSCredential.DEFAULT_LIFETIME,
      null, // use default mechanism: GSI
      GSSCredential.INITIATE_AND_ACCEPT).asInstanceOf[GlobusGSSCredentialImpl]
}

import GlobusHttpClient._

trait GlobusHttpClient <: SocketFactory {
  def proxyBytes: Array[Byte]
  def timeout: Int

  def httpclient(address: java.net.URI) = {
    val myHttpg = new Protocol("https", factory, 8446)
    val httpclient = new org.apache.commons.httpclient.HttpClient
    httpclient.getHostConfiguration.setHost(address.getHost, address.getPort, myHttpg)
     httpclient
  }

  def get(address: java.net.URI, headers: Map[String, String] = Map.empty): String = {
    val get = new GetMethod(address.getPath)
    headers.foreach { case (k, v) => get.setRequestHeader(k, v) }
    httpclient(address).executeMethod(get)
    get.getResponseBodyAsString
  }


  def post(in: String, address: java.net.URI, headers: Map[String, String]): String = {
    val entity = new StringRequestEntity(in, "text/xml", null)
    val post = new PostMethod(address.getPath)
    post.setRequestEntity(entity)
    headers.foreach { case (k, v) => post.setRequestHeader(k, v) }
    httpclient(address).executeMethod(post)
    post.getResponseBodyAsString
  }

   def put(in: String, address: java.net.URI, headers: Map[String, String]): String = {
    val entity = new StringRequestEntity(in, "text/xml", null)
    val put = new PutMethod(address.getPath)
    put.setRequestEntity(entity)
    headers.foreach { case (k, v) => put.setRequestHeader(k, v) }
    httpclient(address).executeMethod(put)
    put.getResponseBodyAsString
  }

}

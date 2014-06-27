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

import java.net.SocketTimeoutException
import java.util.concurrent.{ ConcurrentLinkedQueue, Executors }

import org.apache.commons.httpclient.{ HttpClient, MultiThreadedHttpConnectionManager }
import org.gridforum.jgss.{ ExtendedGSSContext, ExtendedGSSCredential, ExtendedGSSManager }
import org.ietf.jgss.{ GSSContext, GSSCredential }
import org.globus.gsi.gssapi.{ GSSConstants, GlobusGSSCredentialImpl }
import org.apache.commons.httpclient.protocol.{ Protocol, ProtocolSocketFactory }
import org.apache.commons.httpclient.methods.{ PutMethod, GetMethod, PostMethod, StringRequestEntity }

import collection.JavaConversions._

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
  def address: java.net.URI

  @transient lazy val manager = new MultiThreadedHttpConnectionManager()

  @transient lazy val httpclient = {
    val myHttpg = new Protocol("https", factory, 8446)
    val httpclient = new HttpClient(manager)
    httpclient.getHostConfiguration.setHost(address.getHost, address.getPort, myHttpg)
    httpclient
  }

  def post(in: String, address: java.net.URI, headers: Map[String, String]): String = {
    val entity = new StringRequestEntity(in, "text/xml", null)
    val post = new PostMethod(address.getPath)
    try {
      post.setRequestEntity(entity)
      headers.foreach { case (k, v) ⇒ post.setRequestHeader(k, v) }
      httpclient.executeMethod(post)
      post.getResponseBodyAsString
    } finally {
      post.releaseConnection
    }
  }

  override def finalize =
    Executors.newSingleThreadExecutor().submit(new Runnable {
      override def run(): Unit = manager.shutdown()
    })

}

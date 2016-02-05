/*
 * Copyright (C) 2015 Romain Reuillon
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
package fr.iscpif.gridscale.http

import com.github.sardine.impl.SardineRedirectStrategy
import com.github.sardine.impl.methods.HttpPropFind
import org.apache.http.client.methods.{ RequestBuilder, HttpPut, HttpUriRequest }
import org.apache.http.params.CoreConnectionPNames
import org.apache.http.protocol.HttpContext
import org.apache.http.{ HttpStatus, HttpEntityEnclosingRequest, HttpResponse, HttpRequest }
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.{ SocketConfig, RegistryBuilder }
import org.apache.http.conn.socket._
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client._
import org.apache.http.impl.conn.{ BasicHttpClientConnectionManager, PoolingHttpClientConnectionManager }
import org.apache.http.client._
import scala.concurrent.duration._

object HTTPSClient {

  def isResponseOk(response: HttpResponse) =
    response.getStatusLine.getStatusCode >= HttpStatus.SC_OK &&
      response.getStatusLine.getStatusCode < HttpStatus.SC_MULTIPLE_CHOICES

  def redirectStrategy = new LaxRedirectStrategy {

    override def getRedirect(request: HttpRequest, response: HttpResponse, context: HttpContext): HttpUriRequest = {
      assert(response.getStatusLine.getStatusCode < HttpStatus.SC_BAD_REQUEST, "Error while redirecting request")
      super.getRedirect(request, response, context)
    }

    override protected def isRedirectable(method: String) =
      method match {
        case HttpPropFind.METHOD_NAME ⇒ true
        case HttpPut.METHOD_NAME      ⇒ true
        case _                        ⇒ super.isRedirectable(method)
      }
  }
}

trait HTTPSClient {

  def timeout: Duration
  def factory: Duration ⇒ SSLConnectionSocketFactory

  def connectionManager = {
    val registry = RegistryBuilder.create[ConnectionSocketFactory]().register("https", factory(timeout)).build()
    val client = new BasicHttpClientConnectionManager(registry)
    val socketConfig = SocketConfig.custom().setSoTimeout(timeout.toMillis.toInt).build()
    client.setSocketConfig(socketConfig)
    client
  }

  def requestConfig = {
    RequestConfig.custom()
      .setSocketTimeout(timeout.toMillis.toInt)
      .setConnectTimeout(timeout.toMillis.toInt)
      .setConnectionRequestTimeout(timeout.toMillis.toInt)
      .build()
  }

  def newClient =
    HttpClients.custom().
      setRedirectStrategy(HTTPSClient.redirectStrategy).
      setConnectionManager(connectionManager).
      setDefaultRequestConfig(requestConfig).build()

  def withClient[T](f: CloseableHttpClient ⇒ T): T = {
    val httpClient = newClient
    try f(httpClient)
    finally httpClient.close
  }

}

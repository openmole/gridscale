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
import org.apache.http.client.methods.{ RequestBuilder, HttpPut, HttpUriRequest }
import org.apache.http.protocol.HttpContext
import org.apache.http.{ HttpEntityEnclosingRequest, HttpResponse, HttpRequest }
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket._
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client._
import org.apache.http.impl.conn.{ BasicHttpClientConnectionManager, PoolingHttpClientConnectionManager }
import org.apache.http.client._
import scala.concurrent.duration._

object HTTPSClient {
  def redirectStrategy = new SardineRedirectStrategy {
    override def getRedirect(request: HttpRequest, response: HttpResponse, context: HttpContext): HttpUriRequest = {
      val method = request.getRequestLine().getMethod()
      if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) {
        val uri = getLocationURI(request, response, context)
        RequestBuilder.put(uri).setEntity(request.asInstanceOf[HttpEntityEnclosingRequest].getEntity).build()
      } else super.getRedirect(request, response, context)
    }
    override protected def isRedirectable(method: String) =
      if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) true
      else super.isRedirectable(method)
  }
}

trait HTTPSClient {

  def timeout: Duration
  def factory: Duration ⇒ SSLConnectionSocketFactory

  def connectionManager = {
    val registry = RegistryBuilder.create[ConnectionSocketFactory]().register("https", factory(timeout)).build()
    new BasicHttpClientConnectionManager(registry)
  }

  def requestConfig = {
    RequestConfig.custom()
      .setSocketTimeout(timeout.toMillis.toInt)
      .setConnectTimeout(timeout.toMillis.toInt)
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

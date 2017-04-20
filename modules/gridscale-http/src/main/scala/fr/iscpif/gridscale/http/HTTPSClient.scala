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

import org.apache.http.config.{ SocketConfig, RegistryBuilder }
import org.apache.http.conn.socket._
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client._
import org.apache.http.impl.conn.{ BasicHttpClientConnectionManager, PoolingHttpClientConnectionManager }
import org.apache.http.client._
import scala.concurrent.duration._

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

  def newClient =
    HttpClients.custom().
      setRedirectStrategy(HTTPStorage.redirectStrategy).
      setConnectionManager(connectionManager).
      setDefaultRequestConfig(HTTPStorage.requestConfig(timeout)).build()

  def withClient[T](f: CloseableHttpClient ⇒ T): T = {
    val httpClient = newClient
    try f(httpClient)
    finally httpClient.close
  }

}

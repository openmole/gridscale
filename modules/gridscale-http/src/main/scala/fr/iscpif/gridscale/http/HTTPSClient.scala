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

import org.apache.http.client.config.RequestConfig
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket._
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager

import scala.concurrent.duration._

trait HTTPSClient {

  def pool: Option[Int]
  def timeout: Duration
  def factory: Duration ⇒ SSLConnectionSocketFactory

  @transient lazy val connectionPool =
    pool.map { connections ⇒
      val registry = RegistryBuilder.create[ConnectionSocketFactory]().register("https", factory(timeout)).build()
      val p = new PoolingHttpClientConnectionManager(registry)
      p.setDefaultMaxPerRoute(connections)
      p
    }

  @transient lazy val httpContext = HttpClientContext.create

  def requestConfig = {
    RequestConfig.custom()
      .setSocketTimeout(timeout.toMillis.toInt)
      .setConnectTimeout(timeout.toMillis.toInt)
      .build()
  }

  def newClient =
    connectionPool match {
      case Some(p) ⇒ HttpClients.custom().setConnectionManager(p)
      case None    ⇒ HttpClients.custom().setSSLSocketFactory(factory(timeout))
    }

}

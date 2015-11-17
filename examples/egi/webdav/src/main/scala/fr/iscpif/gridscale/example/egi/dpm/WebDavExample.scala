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
package fr.iscpif.gridscale.example.egi.dpm

import java.io.{ FileInputStream, InputStream, FileOutputStream, File }
import java.net.{ URL, URI }
import java.security.{ KeyStore, Security }
import javax.net.ssl.{ HttpsURLConnection, SSLSocket, KeyManagerFactory, SSLSocketFactory }

import fr.iscpif.gridscale.authentication.P12Authentication
import fr.iscpif.gridscale.egi._
import fr.iscpif.gridscale.egi.https.P12HTTPSAuthentication
import fr.iscpif.gridscale.egi.services.GlobusHttpRequest
import fr.iscpif.gridscale.globushttp.SimpleSocketFactory
import org.apache.commons.logging.impl.SimpleLog
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{ HttpGet, HttpRequestBase }
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.utils.URIBuilder
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.{ HttpRequest, HttpHost }
import concurrent.duration._
import fr.iscpif.gridscale._

import scala.io.Source
import scala.util.Try

object WebDavExample extends App {

  val p12 = P12Authentication(new File("/path/to/certificate.p12"), "password")
  val authentication = P12VOMSAuthentication(p12, 24 hours, "voms://voms.hellasgrid.gr:15160/C=GR/O=HellasGrid/OU=hellasgrid.gr/CN=voms.hellasgrid.gr", "vo.complex-systems.eu")
  val dav = EGIWebdav(service = "https://grid05.lal.in2p3.fr:443", basePath = "/dpm/lal.in2p3.fr/home/vo.complex-systems.eu/")(authentication)

  def dir = "blalalalal"
  def list = dav.list("/").map(_.name).mkString("\n")

  Try(dav.rmDir(dir))
  println(list)
  dav.makeDir(dir)
  println(list)
  dav.rmDir(dir)

}

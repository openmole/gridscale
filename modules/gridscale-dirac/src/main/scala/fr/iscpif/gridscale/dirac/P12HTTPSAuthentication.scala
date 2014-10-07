/*
 * Copyright (C) 04/06/13 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.iscpif.gridscale.dirac

import javax.net.ssl._
import java.net.{ Socket, URL }
import java.security.cert.X509Certificate
import java.io.{ FileInputStream, File }
import java.security.KeyStore
import fr.iscpif.gridscale.authentication.{ Credential, P12Authentication }
import fr.iscpif.gridscale.tools.DefaultTimeout
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.protocol.HttpContext

trait P12HTTPSAuthentication extends P12Authentication with Credential with DefaultTimeout {

  type A >: P12HTTPSAuthentication
  def credential = this

  @transient lazy val factory =
    new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER) {
      override protected def prepareSocket(socket: SSLSocket) = {
        socket.setSoTimeout(timeout.toMillis.toInt)
      }
    }

  @transient lazy val sslContext = {
    val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
    val ks = KeyStore.getInstance("pkcs12")
    val in = new FileInputStream(certificate)
    try ks.load(in, password.toCharArray)
    finally in.close
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, password.toCharArray)
    sslContext.init(kmf.getKeyManagers, tm, null)
    sslContext
  }

  @transient lazy val tm = Array[TrustManager](
    new X509TrustManager {
      def getAcceptedIssuers = Array.empty[X509Certificate]
      def checkClientTrusted(certs: Array[X509Certificate], authType: String) {}
      def checkServerTrusted(certs: Array[X509Certificate], authType: String) {}
    })

  @transient lazy val hv = new HostnameVerifier {
    def verify(hostname: String, session: SSLSession) = true
  }
}

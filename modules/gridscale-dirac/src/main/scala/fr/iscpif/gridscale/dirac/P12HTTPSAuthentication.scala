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
import java.net.URL
import java.security.cert.X509Certificate
import java.io.{ FileInputStream, File }
import java.security.KeyStore
import fr.iscpif.gridscale.{P12Authentication, HTTPSAuthentication}

trait P12HTTPSAuthentication extends HTTPSAuthentication with P12Authentication {

  def connect(scon: HttpsURLConnection) = {
    //val scon = url.openConnection.asInstanceOf[HttpsURLConnection]
    scon.setSSLSocketFactory(sslContext.getSocketFactory)
    scon.setHostnameVerifier(hv)
    //scon
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

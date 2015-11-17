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
package fr.iscpif.gridscale.egi.https

import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl._

import fr.iscpif.gridscale.authentication.{ P12Authentication }
import fr.iscpif.gridscale.tools.DefaultTimeout
import org.apache.http.conn.ssl.SSLConnectionSocketFactory

trait P12HTTPSAuthentication {

  def authentication: P12Authentication

  @transient lazy val sslContext = {
    val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
    val ks = KeyStore.getInstance("pkcs12")

    val in = new FileInputStream(authentication.certificate)
    try ks.load(in, authentication.password.toCharArray)
    finally in.close

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, authentication.password.toCharArray)
    sslContext.init(kmf.getKeyManagers, trustManager, null)
    sslContext
  }

}

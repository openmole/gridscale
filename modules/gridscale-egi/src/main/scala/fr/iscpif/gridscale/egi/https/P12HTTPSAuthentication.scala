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

import java.io.{File, IOException, FileInputStream}
import java.security.KeyStore
import javax.net.ssl._

import fr.iscpif.gridscale.authentication.{ AuthenticationException, P12Authentication }


object P12HTTPSAuthentication {

  def sslContext(certificate: File, password: String) = {
    val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
    val ks = KeyStore.getInstance("pkcs12")

    val in = new FileInputStream(certificate)
    try ks.load(in, password.toCharArray)
    catch {
      case e: IOException ⇒ throw new AuthenticationException(s"A wrong password has been provided for certificate $certificate", e)
    } finally in.close

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, password.toCharArray)
    sslContext.init(kmf.getKeyManagers, trustManager, null)
    sslContext
  }

}

trait P12HTTPSAuthentication {

  def authentication: P12Authentication

  @transient lazy val sslContext = P12HTTPSAuthentication.sslContext(authentication.certificate, authentication.password)

}

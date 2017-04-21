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

package gridscale.authentication

import java.io.{ IOException, FileInputStream, File }
import java.security.KeyStore

object P12Authentication {

  def apply(certificate: File, password: String) = {
    val (_certificate, _password) = (certificate, password)

    new P12Authentication {
      override val certificate: File = _certificate
      override val password: String = _password
    }
  }

  def loadKeyStore(a: P12Authentication) = {
    val ks = KeyStore.getInstance("pkcs12")

    val in = new FileInputStream(a.certificate)
    try ks.load(in, a.password.toCharArray)
    catch {
      case e: IOException ⇒ throw new AuthenticationException(s"A wrong password has been provided for certificate ${a.certificate}", e)
    } finally in.close
    ks
  }

}

trait P12Authentication {
  def certificate: File
  def password: String
}

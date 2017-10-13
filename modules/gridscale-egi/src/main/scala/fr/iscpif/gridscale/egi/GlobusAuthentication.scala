/*
 * Copyright (C) 10/06/13 Romain Reuillon
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

package fr.iscpif.gridscale.egi

import org.globus.gsi.gssapi.GlobusGSSCredentialImpl

object GlobusAuthenticationProvider {

  implicit val functionGlobusProvider = new GlobusAuthenticationProvider[() ⇒ GlobusAuthentication.Proxy] {
    override def apply(t: () ⇒ GlobusAuthentication.Proxy): GlobusAuthentication.Proxy = t()
  }

  implicit val p12VOMSGlobusProvider = new GlobusAuthenticationProvider[P12VOMSAuthentication] {
    override def apply(t: P12VOMSAuthentication) = t()
  }

}

trait GlobusAuthenticationProvider[-T] {
  def apply(t: T): GlobusAuthentication.Proxy
}

object GlobusAuthentication {

  object Proxy {
    def apply(
      credential: GlobusGSSCredentialImpl,
      proxyBytes: Array[Byte],
      delegationID: String,
      gt2Credential: GlobusGSSCredentialImpl) = new Proxy(credential, proxyBytes, delegationID, gt2Credential)
  }

  class Proxy(
      val credential: GlobusGSSCredentialImpl,
      val proxyBytes: Array[Byte],
      val delegationID: String,
      val gt2Credential: GlobusGSSCredentialImpl) {
    @transient lazy val proxyString = new String(proxyBytes)
  }

}


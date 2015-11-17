/*
 * Copyright (C) 2012 Romain Reuillon
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
package fr.iscpif.gridscale.egi

import java.io.File

import fr.iscpif.gridscale.authentication.P12Authentication
import org.glite.voms.contact.VOMSProxyInit

import scala.concurrent.duration.Duration

object P12VOMSAuthentication {

  def apply(p12Authentication: P12Authentication, lifeTime: Duration, serverURL: String, voName: String) = {
    val (_lifeTime, _serverURL, _voName) = (lifeTime, serverURL, voName)

    new P12VOMSAuthentication {
      override def certificate: File = p12Authentication.certificate
      override def password: String = p12Authentication.password
      override def lifeTime: Duration = _lifeTime
      override def voName: String = _voName
      override def serverURL: String = _serverURL
    }
  }

}

trait P12VOMSAuthentication extends VOMSAuthentication with P12Authentication {
  def proxyInit = VOMSProxyInit.instance(certificate, password)
}

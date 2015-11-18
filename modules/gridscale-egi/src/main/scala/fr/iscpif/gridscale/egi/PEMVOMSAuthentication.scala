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

import fr.iscpif.gridscale.authentication.PEMAuthentication
import org.glite.voms.contact.VOMSProxyInit

import scala.concurrent.duration.Duration

object PEMVOMSAuthentication {

  def apply(
    pem: PEMAuthentication,
    lifeTime: Duration,
    serverURL: String,
    voName: String,
    renewRatio: Double = 0.2,
    fqan: Option[String] = None,
    proxySize: Int = 1024) = {
    val (_lifeTime, _serverURL, _voName, _renewRatio, _fqan, _proxySize) =
      (lifeTime, serverURL, voName, renewRatio, fqan, proxySize)

    new PEMVOMSAuthentication {
      override def key: File = pem.key
      override def certificate: File = pem.certificate
      override def password: String = pem.password
      override def renewRation: Double = _renewRatio
      override def lifeTime: Duration = _lifeTime
      override def voName: String = _voName
      override def serverURL: String = _serverURL
      override def fqan = _fqan
      override def proxySize = _proxySize
    }
  }

}

trait PEMVOMSAuthentication extends VOMSAuthentication with PEMAuthentication {
  def proxyInit = VOMSProxyInit.instance(certificate.getPath, key.getPath, password)
}

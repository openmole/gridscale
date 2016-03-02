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

import java.io.{ ByteArrayInputStream, File }
import java.net.{ MalformedURLException, URI }

import fr.iscpif.gridscale.authentication.P12Authentication
import fr.iscpif.gridscale.egi.voms.VOMSRestAPI
import org.glite.voms.contact.{ VOMSRequestOptions, VOMSServerInfo, UserCredentials, VOMSProxyInit }
import org.globus.gsi.GSIConstants.CertificateType

import scala.concurrent.duration.Duration

object P12VOMSAuthentication {

  def apply(
    p12: P12Authentication,
    lifeTime: Duration,
    serverURLs: Seq[String],
    voName: String,
    renewRatio: Double = 0.2,
    fqan: Option[String] = None,
    proxySize: Int = 1024) = {
    val (_lifeTime, _serverURLs, _voName, _renewRatio, _fqan, _proxySize) =
      (lifeTime, serverURLs, voName, renewRatio, fqan, proxySize)

    new P12VOMSAuthentication {
      override val p12Authentication = p12
      override val lifeTime: Duration = _lifeTime
      override val voName: String = _voName
      override val serverURLs: Seq[String] = _serverURLs
      override val renewRation: Double = _renewRatio
      override val fqan = _fqan
      override val proxySize = _proxySize
    }
  }

}

trait P12VOMSAuthentication extends VOMSAuthentication {
  def p12Authentication: P12Authentication
  def proxy(serverURL: String, proxyType: CertificateType) = {

    //    val uri = new URI(serverURL.replaceAll(" ", "%20"))
    //    if (uri.getHost == null)
    //      throw new MalformedURLException("Attribute Server has no host name: " + uri.toString)
    //
    //    val server = new VOMSServerInfo
    //    server.setHostName(uri.getHost)
    //    server.setPort(uri.getPort)
    //    server.setHostDn(uri.getPath)
    //    server.setVoName(voName)
    //
    //    val requestOption = new VOMSRequestOptions
    //    requestOption.setVoName(voName)
    //
    //    fqan match {
    //      case Some(s) ⇒ requestOption.addFQAN(s)
    //      case None    ⇒
    //    }
    //
    //    val proxy = VOMSProxyInit.instance(p12Authentication.certificate, p12Authentication.password)
    //
    //    proxy.setProxyLifetime(lifeTime.toSeconds.toInt)
    //    proxy.setProxySize(proxySize)
    //    proxy.setProxyType(proxyType)
    //    requestOption.setLifetime(lifeTime.toSeconds.toInt)
    //
    //    proxy.getVomsProxy(server, requestOption)

    import fr.iscpif.gridscale.egi._
    def userCredential = UserCredentials.instance(p12Authentication.certificate, p12Authentication.password)
    val url = new URI(serverURL)
    val credential = VOMSRestAPI.query(url.getHost, url.getPort, lifetime = Some(this.lifeTime.toSeconds.toInt))(p12Authentication)
    credential.getCredential(userCredential, proxyType)
  }

}

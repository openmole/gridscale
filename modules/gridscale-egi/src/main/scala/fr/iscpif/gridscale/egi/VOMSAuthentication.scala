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

import java.io.{ ByteArrayOutputStream, File, FileOutputStream }
import java.net.{ MalformedURLException, URI }
import java.util.UUID

import fr.iscpif.gridscale.authentication.AuthenticationException
import fr.iscpif.gridscale.cache._
import org.glite.voms.contact.{ VOMSProxyBuilder, VOMSProxyInit, VOMSRequestOptions, VOMSServerInfo }
import org.globus.gsi.GSIConstants.CertificateType
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl
import org.ietf.jgss.GSSCredential

import scala.collection.JavaConversions._
import scala.concurrent.duration.Duration

object VOMSAuthentication {

  def setCARepository(directory: File) = {
    System.setProperty("X509_CERT_DIR", directory.getCanonicalPath)
    System.setProperty("CADIR", directory.getCanonicalPath)
  }

}

trait VOMSAuthentication {

  def serverURL: String
  def voName: String
  def proxyInit: VOMSProxyInit
  def fqan: Option[String]
  def lifeTime: Duration
  def renewRation: Double
  def proxySize: Int

  @transient lazy val delegationID = UUID.randomUUID

  def apply() = cached()

  @transient lazy val cached = cache(() ⇒ generateProxy)(lifeTime * renewRation)

  def generateProxy =
    try {
      val gt4Proxy = proxy(VOMSProxyBuilder.GT4_PROXY)

      val os = new ByteArrayOutputStream()
      VOMSProxyBuilder.saveProxy(gt4Proxy, os)

      GlobusAuthentication.Proxy(
        new GlobusGSSCredentialImpl(gt4Proxy, GSSCredential.INITIATE_AND_ACCEPT),
        os.toByteArray,
        delegationID.toString,
        new GlobusGSSCredentialImpl(proxy(VOMSProxyBuilder.GT2_PROXY), GSSCredential.INITIATE_AND_ACCEPT))
    } catch {
      case e: Throwable ⇒ throw AuthenticationException("Error during VOMS authentication", e)
    }

  def generate(file: File) = {
    val os = new FileOutputStream(file)
    try VOMSProxyBuilder.saveProxy(proxy(VOMSProxyBuilder.GT4_PROXY), os)
    finally os.close
  }

  def proxy(proxyType: CertificateType) = synchronized {
    val uri = new URI(serverURL.replaceAll(" ", "%20"))
    if (uri.getHost == null)
      throw new MalformedURLException("Attribute Server has no host name: " + uri.toString)

    val server = new VOMSServerInfo
    server.setHostName(uri.getHost)
    server.setPort(uri.getPort)
    server.setHostDn(uri.getPath)
    server.setVoName(voName)

    val proxy = proxyInit
    proxy.addVomsServer(server)

    val requestOption = new VOMSRequestOptions
    requestOption.setVoName(voName)

    fqan match {
      case Some(s) ⇒ requestOption.addFQAN(s)
      case None    ⇒
    }

    proxy.setProxyLifetime(lifeTime.toSeconds.toInt)
    proxy.setProxySize(proxySize)
    proxy.setProxyType(proxyType)
    requestOption.setLifetime(lifeTime.toSeconds.toInt)

    proxy.getVomsProxy(List(requestOption))
  }
}

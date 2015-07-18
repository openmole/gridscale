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

import java.io.{ ByteArrayOutputStream, File }
import java.net.MalformedURLException
import java.net.URI
import fr.iscpif.gridscale.authentication.AuthenticationException
import org.glite.voms.contact.{ VOMSProxyBuilder, VOMSProxyInit, VOMSRequestOptions, VOMSServerInfo }
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl
import collection.JavaConversions._
import org.ietf.jgss.GSSCredential

import scala.concurrent.duration.Duration

object VOMSAuthentication {

  def setCARepository(directory: File) = {
    System.setProperty("X509_CERT_DIR", directory.getCanonicalPath)
    System.setProperty("CADIR", directory.getCanonicalPath)
  }

}

trait VOMSAuthentication extends GlobusAuthentication {

  def serverURL: String
  def voName: String
  def proxyInit: VOMSProxyInit
  def fqan: Option[String] = None
  def lifeTime: Duration
  def proxySize = 1024

  def apply() = try {
    synchronized {
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
      requestOption.setLifetime(lifeTime.toSeconds.toInt)

      val globusProxy = proxy.getVomsProxy(List(requestOption))

      val os = new ByteArrayOutputStream()
      VOMSProxyBuilder.saveProxy(globusProxy, os)

      GlobusAuthentication.Proxy(new GlobusGSSCredentialImpl(globusProxy, GSSCredential.INITIATE_AND_ACCEPT), os.toByteArray, delegationID.toString)
    }
  } catch {
    case e: Throwable ⇒ throw AuthenticationException("Error during VOMS authentication", e)
  }
}
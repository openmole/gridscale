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
import org.globus.gsi.X509Credential
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl
import org.ietf.jgss.GSSCredential

import scala.collection.JavaConversions._
import scala.concurrent.duration.Duration
import scala.util._

object VOMSAuthentication {

  def setCARepository(directory: File) = {
    System.setProperty("X509_CERT_DIR", directory.getCanonicalPath)
    System.setProperty("CADIR", directory.getCanonicalPath)
  }

  def findWorking[T](servers: Seq[String], f: String ⇒ T): T = {
    def findWorking0(servers: List[String]): Try[T] =
      servers match {
        case Nil      ⇒ Failure(new RuntimeException("Server list is empty"))
        case h :: Nil ⇒ Try(f(h))
        case h :: tail ⇒
          Try(f(h)) match {
            case Failure(_) ⇒ findWorking0(tail)
            case s          ⇒ s
          }
      }

    findWorking0(servers.toList) match {
      case Failure(t) ⇒ throw new RuntimeException(s"No server is working among $servers", t)
      case Success(t) ⇒ t
    }
  }

}

trait VOMSAuthentication {

  def serverURLs: Seq[String]
  def voName: String

  def fqan: Option[String]
  def lifeTime: Duration
  def renewRation: Double
  def proxySize: Int

  @transient lazy val delegationID = UUID.randomUUID

  def apply() = cached()

  @transient lazy val cached = ValueAsyncCache(lifeTime * renewRation)(() ⇒ generateProxy)

  def generateProxy =
    try {
      val GlobusProxies(gt2Proxy, gt4Proxy) = proxy()

      val os = new ByteArrayOutputStream()
      VOMSProxyBuilder.saveProxy(gt4Proxy, os)

      GlobusAuthentication.Proxy(
        new GlobusGSSCredentialImpl(gt4Proxy, GSSCredential.INITIATE_AND_ACCEPT),
        os.toByteArray,
        delegationID.toString,
        new GlobusGSSCredentialImpl(gt2Proxy, GSSCredential.INITIATE_AND_ACCEPT))
    } catch {
      case e: Throwable ⇒ throw AuthenticationException("Error during VOMS authentication", e)
    }

  def proxy(): GlobusProxies =
    VOMSAuthentication.findWorking(serverURLs, s ⇒ proxy(s))

  case class GlobusProxies(gt2: X509Credential, gt4: X509Credential)

  def proxy(serverURL: String): GlobusProxies

  /*def proxy(serverURL: String, proxyType: CertificateType): X509Credential = synchronized {
    val uri = new URI(serverURL.replaceAll(" ", "%20"))
    if (uri.getHost == null)
      throw new MalformedURLException("Attribute Server has no host name: " + uri.toString)

    val server = new VOMSServerInfo
    server.setHostName(uri.getHost)
    server.setPort(uri.getPort)
    server.setHostDn(uri.getPath)
    server.setVoName(voName)

    val requestOption = new VOMSRequestOptions
    requestOption.setVoName(voName)

    fqan match {
      case Some(s) ⇒ requestOption.addFQAN(s)
      case None    ⇒
    }

    val proxy = proxyInit()
    proxy.setProxyLifetime(lifeTime.toSeconds.toInt)
    proxy.setProxySize(proxySize)
    proxy.setProxyType(proxyType)
    requestOption.setLifetime(lifeTime.toSeconds.toInt)

    proxy.getVomsProxy(server, requestOption)
  }*/

}

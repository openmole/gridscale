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

import java.security.KeyStore
import javax.net.ssl.SSLContext
import fr.iscpif.gridscale.egi._
import org.globus.gsi.jsse.SSLConfigurator
import org.globus.gsi.stores.Stores

trait VOMSProxyHTTPSAuthentication {

  def proxy(): GlobusAuthentication.Proxy

  def sslContext: SSLContext = {
    val keyStore = KeyStore.getInstance("JKS")
    keyStore.load(null, null)
    val x509 = proxy().credential.getX509Credential

    keyStore.setKeyEntry("default", x509.getPrivateKey(), "".toCharArray(), x509.getCertificateChain().toArray[java.security.cert.Certificate])

    val sslConfigurator = new SSLConfigurator()
    sslConfigurator.setCredentialStore(keyStore)
    sslConfigurator.setCredentialStorePassword("")

    val trustStore = Stores.getDefaultTrustStore
    sslConfigurator.setTrustAnchorStore(trustStore)

    val crlStore = Stores.getDefaultCRLStore()
    sslConfigurator.setCrlStore(crlStore)

    val sigPolStore = Stores.getDefaultSigningPolicyStore()
    sslConfigurator.setPolicyStore(sigPolStore)

    sslConfigurator.getSSLContext
  }

}

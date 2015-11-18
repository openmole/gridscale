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

import fr.iscpif.gridscale.authentication.P12Authentication
import fr.iscpif.gridscale.egi.GlobusAuthentication.Proxy
import fr.iscpif.gridscale.egi.GlobusAuthenticationProvider
import org.apache.http.conn.ssl.SSLConnectionSocketFactory

import scala.concurrent.duration.Duration

object HTTPSAuthentication {

  implicit val functionAuthentication = new HTTPSAuthentication[Duration ⇒ SSLConnectionSocketFactory] {
    override def factory(t: (Duration) ⇒ SSLConnectionSocketFactory): (Duration) ⇒ SSLConnectionSocketFactory = t
  }

  implicit val p12Authentication = new HTTPSAuthentication[P12Authentication] {
    override def factory(t: P12Authentication) = {
      val auth = new P12HTTPSAuthentication {
        override def authentication: P12Authentication = t
      }
      socketFactory(auth.sslContext)
    }
  }

  implicit def VOMSHTTSAuthentication[T: GlobusAuthenticationProvider] = new HTTPSAuthentication[T] {
    override def factory(t: T) = {
      val auth = new VOMSProxyHTTPSAuthentication {
        override def proxy(): Proxy = implicitly[GlobusAuthenticationProvider[T]].apply(t)
      }
      socketFactory(auth.sslContext)
    }
  }

}

trait HTTPSAuthentication[-T] {
  def factory(t: T): Duration ⇒ SSLConnectionSocketFactory
}

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
package fr.iscpif.gridscale.http

import fr.iscpif.gridscale.authentication.P12Authentication
import org.apache.http.conn.ssl.SSLConnectionSocketFactory

import scala.concurrent.duration.Duration

object HTTPSAuthentication {

  implicit val functionAuthentication = new HTTPSAuthentication[Duration ⇒ SSLConnectionSocketFactory] {
    override def factory(t: (Duration) ⇒ SSLConnectionSocketFactory): (Duration) ⇒ SSLConnectionSocketFactory = t
  }

}

trait HTTPSAuthentication[-T] {
  def factory(t: T): Duration ⇒ SSLConnectionSocketFactory
}

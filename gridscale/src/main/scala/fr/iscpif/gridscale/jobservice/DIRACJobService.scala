/*
 * Copyright (C) 05/06/13 Romain Reuillon
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

package fr.iscpif.gridscale.jobservice

import fr.iscpif.gridscale.authentication.HTTPSAuthentication
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import spray.json._
import DefaultJsonProtocol._
import fr.iscpif.gridscale.tools.HttpURLConnectionUtil._

trait DIRACJobService {

  private implicit def strToURL(s: String) = new URL(s)

  def service: String
  def group: String
  def setup = "Dirac-Production"
  def auth2Auth = service + "/oauth2/auth"

  def token(implicit credential: HTTPSAuthentication) =
    get(auth2Auth + s"?response_type=client_credentials&group=$group&setup=$setup") getString

  def get(url: URL)(implicit credential: HTTPSAuthentication): HttpsURLConnection = {
    val c = credential.connect(url)
    c.setRequestMethod("GET")
    c
  }
}

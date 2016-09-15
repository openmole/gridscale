/**
 * Created by Romain Reuillon on 28/01/16.
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
 *
 */
package fr.iscpif.gridscale.egi.voms

import java.net.URI

import fr.iscpif.gridscale.authentication.AuthenticationException
import fr.iscpif.gridscale.http.{ HTTPSAuthentication, HTTPSClient }
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet

import scala.concurrent.duration._

object VOMSRestAPI {

  def query[A: HTTPSAuthentication, T](
    host: String,
    port: Int,
    lifetime: Option[Int] = None,
    fquan: Option[String] = None,
    timeout: Duration = 1 minutes)(authentication: A)(f: RESTVOMSResponse ⇒ T): T = {
    val _timeout = timeout
    val client = new HTTPSClient {
      override val timeout = _timeout
      override val factory = implicitly[HTTPSAuthentication[A]].factory(authentication)
    }

    client.withClient { c ⇒

      val options =
        List(
          lifetime.map("lifetime=" + _),
          fquan.map("fquans=" + _)
        ).flatten.mkString("&")
      val uri = new URI(s"https://$host:$port/generate-ac${if (!options.isEmpty) "?" + options else ""}")
      val get = new HttpGet(uri)
      val r = c.execute(get)

      if (r.getStatusLine.getStatusCode != HttpStatus.SC_OK)
        throw new AuthenticationException("VOMS server returned " + r.getStatusLine.toString)

      val parse = new RESTVOMSResponseParsingStrategy()
      val parsed = parse.parse(r.getEntity.getContent)

      f(parsed)
    }
  }
}

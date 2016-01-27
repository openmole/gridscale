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

import org.apache.directory.ldap.client.api.LdapNetworkConnection
import org.apache.directory.shared.ldap.model.message.SearchScope
import scala.concurrent.duration._
import collection.JavaConversions._

object BDIIQuery {

  def withBDIIQuery[T](host: String, port: Int, timeout: Duration)(f: BDIIQuery ⇒ T) = {
    val q = new BDIIQuery(host, port, timeout)
    try f(q)
    finally q.close
  }

}

class BDIIQuery(host: String, port: Int, timeout: Duration) {

  lazy val connection = {
    val connection = new LdapNetworkConnection(host, port)
    connection.setTimeOut(timeout.toMillis)
    connection.anonymousBind()
    connection
  }

  /**
   * This method queries the bdii set in the constructor
   *
   * @param searchPhrase the search phrase
   * @return an array list of SearchResult objects.
   */
  def query(searchPhrase: String, attributeList: List[String] = List.empty, bindDN: String = "o=grid") = {
    // Perform the search
    val results = connection.search(
      bindDN,
      searchPhrase,
      SearchScope.SUBTREE)
    results.iterator().toList
    // java.util.Collections.list(results)
  }

  def close = connection.close

}

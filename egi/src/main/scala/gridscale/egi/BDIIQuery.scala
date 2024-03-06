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

package gridscale.egi

import java.util.Hashtable
import javax.naming.Context
import javax.naming.directory.{ InitialDirContext, SearchControls }

import scala.concurrent.duration.Duration
import collection.JavaConverters._

object BDIIQuery:

  def withBDIIQuery[T](host: String, port: Int, timeout: Duration)(f: BDIIQuery ⇒ T) =
    val q = new BDIIQuery(host, port, timeout)
    try f(q)
    finally q.close


class BDIIQuery(val host: String, port: Int, timeOut: Duration) {

  lazy val dirContext = {
    val env = new Hashtable[String, String]

    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
    env.put(Context.PROVIDER_URL, s"ldap://$host:$port")

    /* get a handle to an Initial DirContext */
    val dirContext = new InitialDirContext(env)
    dirContext
  }

  /**
   * This method queries the bdii set in the constructor
   *
   * @param searchPhrase the search phrase
   * @return an array list of SearchResult objects.
   */
  def query(searchPhrase: String, attributeList: List[String] = List.empty, bindDN: String = "o=grid") =
    //boolean hasError= false;

    /* specify search constraints to search subtree */
    val constraints = new SearchControls
    constraints.setTimeLimit(timeOut.toMillis.toInt)
    constraints.setSearchScope(SearchControls.SUBTREE_SCOPE)

    // specify the elements to return
    if attributeList.nonEmpty
    then constraints.setReturningAttributes(attributeList.toArray)


    // Perform the search
    val results = dirContext.search(
      bindDN,
      searchPhrase,
      constraints)
    
    java.util.Collections.list(results).asScala

  def close = dirContext.close
}
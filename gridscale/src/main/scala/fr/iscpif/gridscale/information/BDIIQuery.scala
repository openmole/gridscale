/*
 * Copyright (C) 2012 reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
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

package fr.iscpif.gridscale.information

import java.util.ArrayList;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

class BDIIQuery(val bdii: String, attributeList: List[String] = List.empty) {

  //private ArrayList<String> attributeList = new ArrayList<String>();
  /**
   * @param bdii the end poing of the bdii to query
   */
   //  public BDIIQuery(final String bdii)
   //  {
   //    this.bdii = bdii;
   //  }

   /**
    * Sets the specific attributes of the element that we want to return
    * @param attribute a valid
    */
   //  public void setAttribute(final String attribute)
   //  {
   //    if (attribute!=null && attribute.length() > 0 )
   //    {
   //      this.attributeList.add( attribute );
   //    }
   //  }

   //  private ArrayList<String> getAttributes()
   //  {
   //    return this.attributeList;
   //  }
   /**
    * This method queries the bdii set in the constructor
    * @param searchPhrase the search phrase
    * @return an array list of SearchResult objects.
    * @throws InternalProcessingError
    */
   def query(searchPhrase: String, timeOut: Int) = {
      //boolean hasError= false;
      var resultsList = new ArrayList[SearchResult]
      val bindDN = "o=grid"
      val env = new Hashtable[String, String]

      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
      env.put(Context.PROVIDER_URL, this.bdii)

  
      /* get a handle to an Initial DirContext */
      val dirContext = new InitialDirContext(env)

      /* specify search constraints to search subtree */
      val constraints = new SearchControls
      constraints.setTimeLimit(timeOut)
      constraints.setSearchScope(SearchControls.SUBTREE_SCOPE)

      // specify the elements to return
      if (attributeList.size > 0)
        constraints.setReturningAttributes(attributeList.toArray)

      // Perform the search
      val results = dirContext.search(bindDN,
                                      searchPhrase,
                                      constraints)
      java.util.Collections.list(results)


    }
   }

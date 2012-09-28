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

package fr.iscpif.gridscale.information

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.NamingException;

import javax.naming.directory.SearchResult;

import collection.JavaConversions._

class BDII(location: String) {

  val srmServiceType = Array("srm", "SRM" /*, "srm_v1"*/ )
  val wmsServiceType = Array("org.glite.wms.WMProxy" /*, "org.glite.wms"*/ )

  def querySRMURIs(vo: String, timeOut: Int) = {

    val srmURIs = new HashSet[URI]

    val q = new BDIIQuery(location)

    var res = q.query("(&(objectClass=GlueSA)(GlueSAAccessControlBaseRule=VO:" + vo + "))", timeOut)

    val ids = new TreeMap[String, String]

    for (r ← res) {

      try {
        // GlueSEAccessProtocol glueSEAccessProtocol = new GlueSEAccessProtocol();
        // this.setRetrievalTime( sdf.format(cal.getTime()) );
        //Attribute a = r.getAttributes().get("GlueChunkKey");
        var id = r.getAttributes().get("GlueChunkKey").get().toString(); //$NON-NLS-1$;
        id = id.substring(id.indexOf('=') + 1);
        val resForPath = q.query("(&(GlueChunkKey=GlueSEUniqueID=" + id + ")(GlueVOInfoAccessControlBaseRule=VO:" + vo + "))", timeOut);
        if (!resForPath.isEmpty()) {
          val path = resForPath.get(0).getAttributes().get("GlueVOInfoPath").get().toString();
          ids.put(id, path);
          //Logger.getLogger(BDII.class.getName()).log(Level.FINE, "Found {0} for {1}", new Object[]{path, id});
        }

      } catch {
        case ex: NamingException ⇒ Logger.getLogger(classOf[BDII].getName()).log(Level.FINE, "Error when quering BDII", ex);
      }

    }

    var searchPhrase =
      "(&(objectClass=GlueService)(GlueServiceUniqueID=*)(GlueServiceAccessControlRule=" + vo + ")"; //$NON-NLS-1$

    searchPhrase += "(|"; //$NON-NLS-1$
    for (t ← srmServiceType) {
      searchPhrase += "(GlueServiceType=" + t + ")"; //$NON-NLS-1$ //$NON-NLS-2$
    }
    searchPhrase += "))";

    //System.out.println(searchPhrase);

    res = q.query(searchPhrase, timeOut);

    //Set<String> srmIds = new TreeSet<String>();

    for (r ← res) {

      try {
        val serviceEndPoint = r.getAttributes().get("GlueServiceEndpoint").get().toString();

        val httpgURI = new URI(serviceEndPoint);

        // System.out.println(httpgURI.getHost());
        if (ids.containsKey(httpgURI.getHost())) {
          //Logger.getLogger(BDII.class.getName()).log(Level.FINE, "Constructing url for host {0}", httpgURI.getHost());
          val srmURI = new StringBuilder();

          srmURI.append("srm");
          srmURI.append("://");
          srmURI.append(httpgURI.getHost());
          if (httpgURI.getPort() != -1) {
            srmURI.append(':');
            srmURI.append(httpgURI.getPort());
          }

          //System.outt.println();
          srmURI.append(ids.get(httpgURI.getHost()));

          val srmURIString = srmURI.toString();
          srmURIs.add(URI.create(srmURIString));

          //srm  srmIds.add(httpgURI.getHost());
        } else {
          Logger.getLogger(classOf[BDII].getName()).log(Level.FINE, "No path found in BDII for host {0}", httpgURI.getHost());
        }

      } catch {
        case ex: NamingException ⇒ Logger.getLogger(classOf[BDII].getName()).log(Level.FINE, "Error interrogating the BDII.", ex);
        case e: URISyntaxException ⇒ Logger.getLogger(classOf[BDII].getName()).log(Level.FINE, "Error creating URI for a storge element.", e);
      }
    }

    /*	searchPhrase = "(&(objectClass=GlueSEAccessProtocol)(|";
        
        for(String id : ids.keySet()) {
        searchPhrase += "(GlueChunkKey=GlueSEUniqueID=" + id + ")";
        }
        searchPhrase += "))";
        res = q.query(searchPhrase);*/

    /*	for (SearchResult r : res) {
        Attributes attributes = r.getAttributes();
        //System.out.println(r.toString());
        //final String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss"; //$NON-NLS-1$
        // Calendar cal = Calendar.getInstance();
        //SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
        
        String id = GlueUtility.getStringAttribute( "GlueChunkKey", attributes ); //$NON-NLS-1$
        id = id.substring( id.indexOf( '=' ) + 1 );
        
        //String Endpoint = GlueUtility.getStringAttribute( "GlueSEAccessProtocolEndpoint", attributes ); //$NON-NLS-1$
        Long port = GlueUtility.getLongAttribute( "GlueSEAccessProtocolPort", attributes ); //$NON-NLS-1$
        String type = GlueUtility.getStringAttribute( "GlueSEAccessProtocolType", attributes ); //$NON-NLS-1$
        //  String Version = GlueUtility.getStringAttribute( "GlueSEAccessProtocolVersion", attributes ); //$NON-NLS-1$
        
        if(!srmIds.contains(id)) {
        
        StringBuilder url = new StringBuilder();
        
        url.append(type);
        url.append("://");
        url.append(id);
        if(port != -1) {
        url.append(':');
        url.append(port);
        }
        url.append(ids.get(id));
        
        url.append('/');
        
        URI uri = URI.create(url.toString());
        
        if(uri.getScheme().equals("gsiftp")) {
        srmURIs.add(uri);
        }
        }
        
        }*/

    /*for(URI uri: srmURIs) {
        System.out.println("bdii " + uri.toString());
        }*/

    srmURIs.toSeq
  }

  def queryWMSURIs(vo: String, timeOut: Int) = {
    val q = new BDIIQuery(location.toString())

    var searchPhrase =
      "(&(objectClass=GlueService)(GlueServiceUniqueID=*)(GlueServiceAccessControlRule=" + vo + ")";
    searchPhrase += "(|"; //$NON-NLS-1$
    for (t ← wmsServiceType) {
      searchPhrase += "(GlueServiceType=" + t + ")"; //$NON-NLS-1$ //$NON-NLS-2$
    }
    searchPhrase += "))";

    val res = q.query(searchPhrase, timeOut)

    val wmsURIs = new LinkedList[URI]

    for (r ← res) {

      try {
        val wmsURI = new URI(r.getAttributes().get("GlueServiceEndpoint").get().toString());
        wmsURIs.add(wmsURI)
      } catch {
        case ex: NamingException ⇒ Logger.getLogger(classOf[BDII].getName()).log(Level.WARNING, "Error creating URI for WMS.", ex);
        case e: URISyntaxException ⇒ Logger.getLogger(classOf[BDII].getName()).log(Level.WARNING, "Error creating URI for WMS.", e);
      }
    }

    wmsURIs.toSeq
  }
}

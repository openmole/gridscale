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

package fr.iscpif.gridscale.glite

import java.net.URI
import java.net.URISyntaxException
import java.util.TreeMap
import java.util.logging.Level
import java.util.logging.Logger
import javax.naming.NamingException
import collection.mutable
import collection.JavaConversions._
import scala.concurrent.duration.Duration
import scala.util._

class BDII(location: String) {

  val srmServiceType = Array("srm", "SRM" /*, "srm_v1"*/ )
  val wmsServiceType = Array("org.glite.wms.WMProxy" /*, "org.glite.wms"*/ )

  case class SRMLocation(host: String, port: Int, basePath: String) { self ⇒
    def toSRM(implicit auth: SRMStorage#A) =
      new SRMStorage {
        val host = self.host
        val port = self.port
        val basePath = self.basePath
        val credential = auth
      }
  }

  def querySRMLocations(vo: String, timeOut: Duration) = BDIIQuery.withBDIIQuery(location) { q ⇒
    var res = q.query("(&(objectClass=GlueSA)(GlueSAAccessControlBaseRule=VO:" + vo + "))", timeOut)

    val basePaths = new TreeMap[String, String]

    for (r ← res) {

      try {
        var id = r.getAttributes().get("GlueChunkKey").get().toString
        id = id.substring(id.indexOf('=') + 1)
        val resForPath = q.query("(&(GlueChunkKey=GlueSEUniqueID=" + id + ")(GlueVOInfoAccessControlBaseRule=VO:" + vo + "))", timeOut)
        if (!resForPath.isEmpty) {
          val path = resForPath.get(0).getAttributes().get("GlueVOInfoPath").get().toString
          basePaths.put(id, path)
        }
      } catch {
        case ex: NamingException ⇒ Logger.getLogger(classOf[BDII].getName()).log(Level.FINE, "Error when quering BDII", ex);
      }

    }

    var searchPhrase =
      "(&(objectClass=GlueService)(GlueServiceUniqueID=*)(GlueServiceAccessControlRule=" + vo + ")"

    searchPhrase += "(|"
    for (t ← srmServiceType) {
      searchPhrase += "(GlueServiceType=" + t + ")"
    }
    searchPhrase += "))"

    res = q.query(searchPhrase, timeOut)

    val srms = new mutable.HashMap[(String, Int), SRMLocation]

    for (r ← res) {

      try {
        val serviceEndPoint = r.getAttributes().get("GlueServiceEndpoint").get().toString();

        val httpgURI = new URI(serviceEndPoint)

        if (basePaths.containsKey(httpgURI.getHost)) {
          val host = httpgURI.getHost
          val port = httpgURI.getPort
          val basePath = basePaths.get(host)

          srms += ((host, port) -> SRMLocation(host, port, basePath))

          //          val srmURI = new StringBuilder();
          //
          //          
          //          
          //          srmURI.append("srm");
          //          srmURI.append("://");
          //          srmURI.append(httpgURI.getHost());
          //          if (httpgURI.getPort() != -1) {
          //            srmURI.append(':');
          //            srmURI.append(httpgURI.getPort());
          //          }
          //
          //          //System.outt.println();
          //          srmURI.append(ids.get(httpgURI.getHost()));
          //
          //          val srmURIString = srmURI.toString();
          //          srmURIs.add(URI.create(srmURIString));

          //srm  srmIds.add(httpgURI.getHost());
        } else {
          Logger.getLogger(classOf[BDII].getName()).log(Level.FINE, "No path found in BDII for host {0}", httpgURI.getHost());
        }

      } catch {
        case ex: NamingException   ⇒ Logger.getLogger(classOf[BDII].getName()).log(Level.FINE, "Error interrogating the BDII.", ex);
        case e: URISyntaxException ⇒ Logger.getLogger(classOf[BDII].getName()).log(Level.FINE, "Error creating URI for a storge element.", e);
      }
    }

    srms.values.toSeq
  }

  def querySRMs(vo: String, timeOut: Duration)(implicit auth: SRMStorage#A) =
    querySRMLocations(vo, timeOut).map(_.toSRM(auth))

  case class WMSLocation(url: URI) { self ⇒
    def toWMS(auth: WMSJobService#A) =
      new WMSJobService {
        val url = self.url
        val credential = auth
      }
  }

  def queryWMSLocations(vo: String, timeOut: Duration) = BDIIQuery.withBDIIQuery(location) { q ⇒

    var searchPhrase =
      "(&(objectClass=GlueService)(GlueServiceUniqueID=*)(GlueServiceAccessControlRule=" + vo + ")"
    searchPhrase += "(|"
    for (t ← wmsServiceType) {
      searchPhrase += "(GlueServiceType=" + t + ")"
    }
    searchPhrase += "))"

    val res = q.query(searchPhrase, timeOut)

    val wmsURIs = new mutable.HashSet[URI]

    for (r ← res) {

      try {
        val wmsURI = new URI(r.getAttributes.get("GlueServiceEndpoint").get().toString)
        wmsURIs += wmsURI
      } catch {
        case ex: NamingException   ⇒ Logger.getLogger(classOf[BDII].getName()).log(Level.WARNING, "Error creating URI for WMS.", ex);
        case e: URISyntaxException ⇒ Logger.getLogger(classOf[BDII].getName()).log(Level.WARNING, "Error creating URI for WMS.", e);
      }
    }

    wmsURIs.toSeq.map { WMSLocation(_) }
  }

  def queryWMS(vo: String, timeOut: Duration)(implicit auth: WMSJobService#A) = queryWMSLocations(vo, timeOut).map(_.toWMS(auth))
}

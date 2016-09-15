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

import java.net.{ URI, URISyntaxException }
import java.util.logging.{ Level, Logger }
import javax.naming.NamingException

import fr.iscpif.gridscale.http.WebDAVLocation

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util._

object BDII {
  def apply(host: String, port: Int, timeout: Duration = 1 minute) = new BDII(host, port, timeout)
}

class BDII(host: String, port: Int, timeout: Duration = 1 minute) {

  val creamCEServiceType = "org.glite.ce.CREAM"

  def queryWebDAVLocations(vo: String) = BDIIQuery.withBDIIQuery(host, port, timeout) { q ⇒
    def searchPhrase = "(GLUE2EndpointInterfaceName=webdav)"

    val services =
      for {
        webdavService ← q.query(searchPhrase, bindDN = "o=glue").toSeq
        id = webdavService.getAttributes.get("GLUE2EndpointID").get.toString
        url = webdavService.getAttributes.get("GLUE2EndpointURL").get.toString
      } yield (id, url)

    for {
      (id, url) ← services
      urlObject = new URI(url)
      host = urlObject.getHost
      pathQuery ← q.query(s"(&(GlueChunkKey=GlueSEUniqueID=$host)(GlueVOInfoAccessControlBaseRule=VO:$vo))")
      path = pathQuery.getAttributes.get("GlueVOInfoPath").get.toString
    } yield WebDAVLocation(urlObject.getHost, path, urlObject.getPort)
  }

  case class CREAMCELocation(hostingCluster: String, port: Int, uniqueId: String, contact: String, memory: Int, maxWallTime: Int, maxCPUTime: Int, status: String)

  def queryCREAMCELocations(vo: String) = BDIIQuery.withBDIIQuery(host, port, timeout) { q ⇒
    val res = q.query(s"(&(GlueCEAccessControlBaseRule=VO:$vo)(GlueCEImplementationName=CREAM))")

    case class Machine(memory: Int)
    def machineInfo(host: String) = {
      val info = q.query(s"(GlueChunkKey=GlueClusterUniqueID=$host)").get(0)
      Machine(memory = info.getAttributes.get("GlueHostMainMemoryRAMSize").get().toString.toInt)
    }

    for {
      info ← res
      maxWallTime = info.getAttributes.get("GlueCEPolicyMaxWallClockTime").get.toString.toInt
      maxCpuTime = info.getAttributes.get("GlueCEPolicyMaxCPUTime").get.toString.toInt
      port = info.getAttributes.get("GlueCEInfoGatekeeperPort").get.toString.toInt
      uniqueId = info.getAttributes.get("GlueCEUniqueID").get.toString
      contact = info.getAttributes.get("GlueCEInfoContactString").get.toString
      status = info.getAttributes.get("GlueCEStateStatus").get.toString
      hostingCluster = info.getAttributes.get("GlueCEHostingCluster").get.toString
      memory = machineInfo(hostingCluster).memory
    } yield {
      CREAMCELocation(
        hostingCluster = hostingCluster,
        port = port,
        uniqueId = uniqueId,
        contact = contact,
        memory = memory,
        maxCPUTime = maxCpuTime,
        maxWallTime = maxWallTime,
        status = status
      )
    }
  }

  def searchService(vo: String, serviceType: String) = {
    def serviceTypeQuery = s"(GlueServiceType=$serviceType)"
    s"(&(objectClass=GlueService)(GlueServiceUniqueID=*)(GlueServiceAccessControlRule=$vo)($serviceTypeQuery))"
  }
}

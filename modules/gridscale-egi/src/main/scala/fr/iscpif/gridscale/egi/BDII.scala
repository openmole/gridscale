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

  val srmServiceType = "SRM"
  val wmsServiceType = "org.glite.wms.WMProxy"
  val creamCEServiceType = "org.glite.ce.CREAM"

  case class DPMLocation(host: String, port: Int, basePath: String)

  def queryGridFTPLocations(vo: String, timeOut: Duration) = BDIIQuery.withBDIIQuery(location) { q ⇒
    def searchPhrase = "(&(objectclass=GlueSEAccessProtocol)(|(GlueSEAccessProtocolType=webdav)(GlueSEAccessProtocolType=https)))" // searchService(vo, "https") //"(&(objectClass=GlueSE)(GlueSEImplementationName=DPM))"
    //def searchPhrase = "(&(objectclass=GlueSEAccessProtocol)(GlueSEAccessProtocolType=https))" // searchService(vo, "https") //"(&(objectClass=GlueSE)(GlueSEImplementationName=DPM))"
    //def searchPhrase = "(&(objectclass=GlueSEAccessProtocol)(GlueSEAccessProtocolType=gsiftp))" // searchService(vo, "https") //"(&(objectClass=GlueSE)(GlueSEImplementationName=DPM))"

    for {
      webdavService ← q.query(searchPhrase, timeOut).toSeq
      //t ← webdavService.getAttributes.getAll
      id = webdavService.getAttributes.get("GlueChunkKey").get()
      //t ← q.query(s"($id)", timeOut)
      // id = webdavService.getAttributes.get("GlueSEUniqueID").get().toString
      //id ← Option(dpm.getAttributes().get("GlueSEUniqueID")).map(_.get().toString())
      //val resForPath = q.query(s"(&(GlueChunkKey=GlueSEUniqueID=$host)(GlueVOInfoAccessControlBaseRule=VO:$vo))", timeOut)

      //t = println(s"(&($id)(GlueVOInfoAccessControlBaseRule=VO:$vo))")
      //accessible ← q.query(s"(&($id))", timeOut) //(GlueVOInfoAccessControlBaseRule=VO:$vo))", timeOut) //(GlueVOInfoAccessControlBaseRule=VO:$vo))", timeOut)
    } yield id

    /*def searchService(vo: String, serviceType: String) = {
      def serviceTypeQuery = s"(GlueSEAccessProtocolType=$serviceType)"
      s"(&(objectClass=GlueSEAccessProtocol)($serviceTypeQuery))"
    }

    for {
      gsi ← q.query(searchService(vo, "gsiftp"), timeOut).toSeq
      chunk = gsi.getAttributes.get("GlueChunkKey").get()
      accessible ← q.query(s"(&(GlueChunkKey=$chunk)(GlueVOInfoAccessControlBaseRule=VO:$vo))", timeOut)
      path = accessible.getAttributes().get("GlueVOInfoPath").get().toString
    } yield new GridFTPStorage {
      override def host: String = ???
      override def basePath: String = ???
    }*/

  }

  def querySRMLocations(vo: String, timeOut: Duration): Seq[SRMLocation] = BDIIQuery.withBDIIQuery(location) { q ⇒
    def searchPhrase = searchService(vo, srmServiceType)
    val res = q.query(searchPhrase, timeOut)

    val srms =
      for {
        r ← res
        attributes = r.getAttributes()
        if attributes.get("GlueServiceVersion").get().toString.takeWhile(_ != '.').toInt >= 2
      } yield {
        val serviceEndPoint = attributes.get("GlueServiceEndpoint").get().toString()
        val httpgURI = new URI(serviceEndPoint)
        val host = httpgURI.getHost
        val port = httpgURI.getPort

        Try {
          val resForPath = q.query(s"(&(GlueChunkKey=GlueSEUniqueID=$host)(GlueVOInfoAccessControlBaseRule=VO:$vo))", timeOut)
          val path = resForPath.get(0).getAttributes().get("GlueVOInfoPath").get().toString
          SRMLocation(host, port, path)
        } match {
          case Success(s) ⇒ Some(s)
          case Failure(ex) ⇒
            Logger.getLogger(classOf[BDII].getName()).log(Level.FINE, "Error interrogating the BDII.", ex)
            None
        }
      }

    srms.flatten.toSeq
  }

  def queryWMSLocations(vo: String, timeOut: Duration) = BDIIQuery.withBDIIQuery(location) { q ⇒

    def searchPhrase = searchService(vo, wmsServiceType)
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

  case class CREAMCELocation(hostingCluster: String, port: Int, uniqueId: String, contact: String, memory: Int, maxWallTime: Int, maxCPUTime: Int, status: String)

  def queryCREAMCELocations(vo: String, timeOut: Duration) = BDIIQuery.withBDIIQuery(location) { q ⇒
    val res = q.query(s"(&(GlueCEAccessControlBaseRule=VO:$vo)(GlueCEImplementationName=CREAM))", timeOut)

    case class Machine(memory: Int)
    def machineInfo(host: String) = {
      val info = q.query(s"(GlueChunkKey=GlueClusterUniqueID=$host)", timeOut).get(0)
      Machine(memory = info.getAttributes().get("GlueHostMainMemoryRAMSize").get().toString.toInt)
    }

    for {
      info ← res
      attributes = info.getAttributes()
      maxWallTime = attributes.get("GlueCEPolicyMaxWallClockTime").get.toString.toInt
      maxCpuTime = attributes.get("GlueCEPolicyMaxCPUTime").get.toString.toInt
      port = attributes.get("GlueCEInfoGatekeeperPort").get.toString.toInt
      uniqueId = attributes.get("GlueCEUniqueID").get.toString
      contact = attributes.get("GlueCEInfoContactString").get.toString
      status = attributes.get("GlueCEStateStatus").get.toString
      hostingCluster = attributes.get("GlueCEHostingCluster").get.toString
      memory = machineInfo(hostingCluster).memory
    } yield {
      //println(uniqueId -> attributes.get("GlueCECapability"))

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

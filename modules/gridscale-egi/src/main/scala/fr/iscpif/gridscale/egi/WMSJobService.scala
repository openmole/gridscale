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

import java.io.{ ByteArrayInputStream, File }
import java.net.{ URI, URL }
import java.security.cert.{ CertificateFactory, X509Certificate }
import java.util.Date

import fr.iscpif.gridscale.cache.{ Cache, SingleValueCache }
import fr.iscpif.gridscale.egi.services._
import fr.iscpif.gridscale.jobservice._
import fr.iscpif.gridscale.libraries.lbstub._
import fr.iscpif.gridscale.tools._
import org.glite.security.delegation.GrDProxyGenerator
import org.globus.io.streams.{ GridFTPInputStream, GridFTPOutputStream }

import scala.concurrent.duration._

case class WMSLocation(url: URI)

object WMSJobService {

  def apply[P: GlobusAuthenticationProvider](location: WMSLocation, connections: Int = 5)(proxy: P): WMSJobService = {
    val (_connections, _proxy) = (connections, proxy)

    new WMSJobService {
      override def proxy(): GlobusAuthentication.Proxy = implicitly[GlobusAuthenticationProvider[P]].apply(_proxy)
      def url: URI = location.url
      override def connections = _connections
    }
  }

}

trait WMSJobService extends JobService with DefaultTimeout {
  type J = WMSJobId
  type D = WMSJobDescription

  def url: URI
  def connections = 5

  def copyBufferSize = 64 * 1000
  def delegationRenewal: Duration = 1 -> HOURS

  def proxy(): GlobusAuthentication.Proxy

  lazy val delegationCache =
    new SingleValueCache[String] {
      def compute = _delegate
      def expiresIn(s: String) = delegationRenewal
    }

  def delegationId = delegationCache()

  def delegate = delegationCache.forceRenewal

  private def _delegate: String = {
    val p = proxy()
    val req = delegationService.getProxyReq(p.delegationID).get
    delegationService.putProxy(p.delegationID, createProxyfromCertReq(req, p)).get
    p.delegationID
  }

  def submit(desc: WMSJobDescription) = {
    val cred = proxy()
    val j = wmsService.jobRegister(desc.toJDL, delegationId).get
    fillInputSandbox(desc, j.id)
    wmsService.jobStart(j.id).get
    new WMSJobId {
      val id = j.id
    }
  }

  def cancel(jobId: J) = wmsService.jobCancel(jobId.id).get

  def purge(jobId: J) = wmsService.jobPurge(jobId.id).get

  def state(jobId: J) = translateState(rawState(jobId).state)

  def downloadOutputSandbox(desc: D, jobId: J) = {
    val indexed = desc.outputSandbox.groupBy(_._1).map { case (k, v) ⇒ k -> v.head }

    wmsService.getOutputFileList(jobId.id, "gsiftp").get.file.foreach {
      from ⇒
        val url = new URI(from.name)
        val file = indexed(new File(url.getPath).getName)._2
        val is = new GridFTPInputStream(proxy().credential, url.getHost, SRMStorage.gridFtpPort(url.getPort), url.getPath)
        try copy(is, file, copyBufferSize, timeout)
        finally is.close
    }
  }

  private def fillInputSandbox(desc: WMSJobDescription, jobId: String) = {
    val inputSandboxURL = new URI(wmsService.getSandboxDestURI(jobId, "gsiftp").get.Item(0))
    desc.inputSandbox.foreach {
      file ⇒
        val os = new GridFTPOutputStream(proxy().credential, inputSandboxURL.getHost, SRMStorage.gridFtpPort(inputSandboxURL.getPort), inputSandboxURL.getPath + "/" + file.getName, false)
        try copy(file, os, copyBufferSize, timeout)
        finally os.close
    }
  }

  @transient lazy val delegationService = DelegationService(url, proxy, timeout, connections)
  @transient lazy val wmsService = WMSService(url, proxy, timeout, connections)

  @transient lazy val lbServiceCache = new Cache[String, LoggingAndBookkeepingPortType] {
    override def compute(k: String) = {
      val lbServiceURL = new URL(k)
      LBService(lbServiceURL.toURI, proxy, timeout, connections)
    }
    override def cacheTime(t: LoggingAndBookkeepingPortType) = None
  }

  private def translateState(s: StatName) =
    s match {
      case ABORTED        ⇒ Failed
      case CANCELLEDValue ⇒ Failed
      case CLEARED        ⇒ Done
      case DONEValue4     ⇒ Done
      case PURGED         ⇒ Done
      case READY          ⇒ Submitted
      case RUNNING        ⇒ Running
      case SCHEDULED      ⇒ Submitted
      case SUBMITTED      ⇒ Submitted
      case UNKNOWNValue3  ⇒ Failed
      case WAITING        ⇒ Submitted
    }

  def rawState(jobId: WMSJobId) = {
    val jobUrl = new URL(jobId.id)
    val lbServiceURL = new URL(jobUrl.getProtocol, jobUrl.getHost, jobUrl.getPort, "")
    lbServiceCache(lbServiceURL.toString).jobStatus(jobId.id, flags).get
  }

  private lazy val flags = JobFlags(CLASSADS, CHILDREN, CHILDSTAT)

  private def createProxyfromCertReq(certReq: String, proxy: GlobusAuthentication.Proxy) = {
    val proxyStream = proxy.proxyString

    // generator object
    val generator = new GrDProxyGenerator

    // gets the local proxy as array of byte
    //proxy = GrDPX509Util.getFileBytes( File );
    // reads the proxy time-left
    val stream = new ByteArrayInputStream(proxyStream.getBytes)
    val cf = CertificateFactory.getInstance("X.509")
    val cert = cf.generateCertificate(stream).asInstanceOf[X509Certificate]
    stream.close
    val now = new Date()
    val lifetime = (cert.getNotAfter.getTime - now.getTime()) / 3600000 // in hour ! (TBC in secs)

    // checks if the proxy is still valid
    if (lifetime < 0) throw new RuntimeException("The local proxy has expired")
    // sets the lifetime
    generator.setLifetime(lifetime.toInt)
    // creates the new proxy and converts the proxy from byte[] to String
    new String(generator.x509MakeProxyCert(certReq.getBytes, proxyStream.getBytes, ""))
  }

}

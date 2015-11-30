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
import org.glite.security.delegation.{ GrDPConstants, GrDPX509Util, GrDProxyGenerator }
import org.globus.gsi.GSIConstants.CertificateType
import org.globus.gsi.bc.BouncyCastleCertProcessingFactory
import org.globus.io.streams.{ GridFTPInputStream, GridFTPOutputStream }

import scala.concurrent.duration._

case class WMSLocation(url: URI)

object WMSJobService {

  def apply[P: GlobusAuthenticationProvider](
    location: WMSLocation,
    connections: Int = 5,
    timeout: Duration = 1 minute,
    delegationRenewal: Duration = 1 hour)(proxy: P): WMSJobService = {
    val (_connections, _proxy, _timeout, _delegationRenewal) = (connections, proxy, timeout, delegationRenewal)

    new WMSJobService {
      override def proxy(): GlobusAuthentication.Proxy = implicitly[GlobusAuthenticationProvider[P]].apply(_proxy)
      def url: URI = location.url
      override def connections = _connections
      override def delegationRenewal = _delegationRenewal
      override def timeout = _timeout
    }
  }

}

trait WMSJobService extends JobService {
  type J = WMSJobId
  type D = WMSJobDescription

  def url: URI
  def connections: Int
  def delegationRenewal: Duration
  def timeout: Duration
  def copyBufferSize = 64 * 1024

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
        val is = new GridFTPInputStream(proxy().gt2Credential, url.getHost, SRMStorage.gridFtpPort(url.getPort), url.getPath)
        try copy(is, file, copyBufferSize, timeout)
        finally is.close
    }
  }

  private def fillInputSandbox(desc: WMSJobDescription, jobId: String) = {
    val inputSandboxURL = new URI(wmsService.getSandboxDestURI(jobId, "gsiftp").get.Item(0))
    desc.inputSandbox.foreach {
      file ⇒
        val os = new GridFTPOutputStream(proxy().gt2Credential, inputSandboxURL.getHost, SRMStorage.gridFtpPort(inputSandboxURL.getPort), inputSandboxURL.getPath + "/" + file.getName, false)
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
    val globusCred = proxy.credential.getX509Credential

    val userCerts = globusCred.getCertificateChain
    val key = globusCred.getPrivateKey

    val factory = BouncyCastleCertProcessingFactory.getDefault

    val proxyType = globusCred.getProxyType
    val lifetime = proxy.credential.getRemainingLifetime

    val certificate =
      factory.createCertificate(
        new ByteArrayInputStream(
          GrDPX509Util.readPEM(
            new ByteArrayInputStream(certReq.getBytes()),
            GrDPConstants.CRH,
            GrDPConstants.CRF)), userCerts(0),
        key,
        lifetime,
        proxyType) //12 hours proxy

    val finalCerts = new Array[X509Certificate](userCerts.length + 1);
    finalCerts(0) = certificate
    for {
      (c, i) ← userCerts.zipWithIndex
    } finalCerts(i + 1) = c

    new String(GrDPX509Util.certChainToByte(finalCerts))
  }

}

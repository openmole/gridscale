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

import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.UUID
import org.apache.axis.SimpleTargetedChain
import org.apache.axis.client.Stub
import org.apache.axis.configuration.SimpleProvider
import org.apache.axis.transport.http.HTTPSender
import org.glite.security.delegation.GrDProxyGenerator
import org.glite.wms.wmproxy.JdlType
import org.glite.wms.wmproxy.WMProxyLocator
import org.globus.axis.transport.HTTPSSender
import org.glite.wsdl.services.lb.LoggingAndBookkeepingLocator
import org.glite.wsdl.services.lb.LoggingAndBookkeepingPortType
import org.glite.wsdl.types.lb.JobFlags
import org.glite.wsdl.types.lb.JobFlagsValue
import org.glite.wsdl.types.lb.StatName
import org.globus.axis.gsi.{ GSIConstants ⇒ AGSIConstants }
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl
import org.globus.io.streams.GridFTPInputStream
import org.globus.io.streams.GridFTPOutputStream
import scala.io.Source
import fr.iscpif.gridscale._
import scala.Some
import scala.Some
import scala.Some
import fr.iscpif.gridscale.{ JobService, Cache, DefaultTimeout }

object WMSJobService {

  private val flags = new JobFlags
  flags.setFlag(Array(JobFlagsValue.CLASSADS, JobFlagsValue.CHILDREN, JobFlagsValue.CHILDSTAT))

  def state(jobId: WMSJobService#J, timeout: Int = 60)(implicit credential: WMSJobService#A) = translateState(rawState(jobId, timeout))

  private def translateState(s: StatName) =
    s.getValue match {
      case StatName._ABORTED   ⇒ Failed
      case StatName._CANCELLED ⇒ Failed
      case StatName._CLEARED   ⇒ Done
      case StatName._DONE      ⇒ Done
      case StatName._PURGED    ⇒ Done
      case StatName._READY     ⇒ Submitted
      case StatName._RUNNING   ⇒ Running
      case StatName._SCHEDULED ⇒ Submitted
      case StatName._SUBMITTED ⇒ Submitted
      case StatName._UNKNOWN   ⇒ Failed
      case StatName._WAITING   ⇒ Submitted
    }

  private def rawState(jobId: WMSJobId, timeout: Int)(implicit credential: WMSJobService#A) = {
    val jobUrl = new URL(jobId.id)
    val lbServiceURL = new URL(jobUrl.getProtocol, jobUrl.getHost, 9003, "")
    lbService(lbServiceURL, timeout)(credential()).jobStatus(jobId.id, flags).getState
  }
  private def lbService(url: URL, timeout: Int)(credential: GlobusAuthentication.Proxy) = {
    val locator = new LoggingAndBookkeepingLocator(provider)
    val lbService = locator.getLoggingAndBookkeeping(url)
    lbService.asInstanceOf[Stub]._setProperty(AGSIConstants.GSI_CREDENTIALS, credential.credential)
    lbService.asInstanceOf[Stub].setTimeout(timeout * 1000)
    lbService
  }

  private def provider = {
    val provider = new SimpleProvider
    provider.deployTransport("https", new SimpleTargetedChain(new HTTPSSender))
    provider.deployTransport("http", new SimpleTargetedChain(new HTTPSSender))
    provider
  }

}

import WMSJobService._

trait WMSJobService extends JobService with DefaultTimeout {
  type J = WMSJobId
  type A = () ⇒ GlobusAuthentication.Proxy
  type D = WMSJobDescription

  def url: URI

  def copyBufferSize = 64 * 1000
  def delegationRenewal = 60 * 3600

  lazy val delegationCache =
    new Cache[A, String] {
      def compute(k: A) = _delegate(k())
      def cacheTime(s: String) = Some(delegationRenewal * 1000)
    }

  def delegationId(implicit credential: A) = delegationCache(credential)

  def delegate(credential: A) = delegationCache.forceRenewal(credential)

  private def _delegate(credential: GlobusAuthentication.Proxy): String = {
    val req = grstStub(credential).getProxyReq(credential.delegationID)
    serviceStub(credential).putProxy(credential.delegationID, createProxyfromCertReq(req, proxyString(credential.proxy)))
    credential.delegationID
  }

  def submit(desc: WMSJobDescription)(implicit credential: A) = {
    val cred = credential()
    val j = register(desc.toJDL)(cred)
    fillInputSandbox(desc, j.getId)(cred)
    serviceStub(cred).jobStart(j.getId)
    new WMSJobId {
      val id = j.getId
    }
  }

  def cancel(jobId: J)(implicit credential: A) = serviceStub(credential()).jobCancel(jobId.id)

  def purge(jobId: J)(implicit credential: A) = serviceStub(credential()).jobPurge(jobId.id)

  def state(jobId: J)(implicit credential: A) = WMSJobService.state(jobId, timeout)

  def downloadOutputSandbox(desc: D, jobId: J)(implicit credential: A) = {
    val cred = credential()
    val indexed = desc.outputSandbox.groupBy(_._1).map { case (k, v) ⇒ k -> v.head }

    serviceStub(cred).getOutputFileList(jobId.id, "gsiftp").getFile.foreach {
      from ⇒
        val url = new URI(from.getName)
        val file = indexed(new File(url.getPath).getName)._2

        val is = new GridFTPInputStream(cred.credential, url.getHost, SRMStorage.gridFtpPort(url.getPort), url.getPath)
        try copy(is, file, copyBufferSize, timeout)
        finally is.close
    }
  }

  private def fillInputSandbox(desc: WMSJobDescription, jobId: String)(credential: GlobusAuthentication.Proxy) = {
    val inputSandboxURL = new URI(serviceStub(credential).getSandboxDestURI(jobId, "gsiftp").getItem(0))
    desc.inputSandbox.foreach {
      file ⇒
        val os = new GridFTPOutputStream(credential.credential, inputSandboxURL.getHost, SRMStorage.gridFtpPort(inputSandboxURL.getPort), inputSandboxURL.getPath + "/" + file.getName, false)
        try copy(file, os, copyBufferSize, timeout)
        finally os.close
    }
  }

  private def register(jdl: String)(credential: GlobusAuthentication.Proxy) = serviceStub(credential).jobRegister(jdl, credential.delegationID)

  @transient private lazy val serviceLocator = new WMProxyLocator(provider)

  private def serviceStub(credential: GlobusAuthentication.Proxy) = {
    val serviceStub = serviceLocator.getWMProxy_PortType(url.toURL)
    serviceStub.asInstanceOf[Stub]._setProperty(AGSIConstants.GSI_CREDENTIALS, credential.credential)
    serviceStub.asInstanceOf[Stub].setTimeout(timeout * 1000)
    serviceStub
  }

  private def grstStub(credential: GlobusAuthentication.Proxy) = {
    val grstStub = serviceLocator.getWMProxyDelegation2_PortType(url.toURL)
    grstStub.asInstanceOf[Stub]._setProperty(AGSIConstants.GSI_CREDENTIALS, credential.credential)
    grstStub.asInstanceOf[Stub].setTimeout(timeout * 1000)
    grstStub
  }

  private def proxyString(proxyFile: File) = {
    val s = Source.fromFile(proxyFile)
    try s.mkString
    finally s.close
  }

  private def createProxyfromCertReq(certReq: String, proxyStream: String) = {
    // generator object
    val generator = new GrDProxyGenerator

    // gets the local proxy as array of byte
    //proxy = GrDPX509Util.getFileBytes( File );
    // reads the proxy time-left
    val stream = new ByteArrayInputStream(proxyStream.getBytes)
    val cf = CertificateFactory.getInstance("X.509")
    val cert = cf.generateCertificate(stream).asInstanceOf[X509Certificate]
    stream.close
    val now = new Date
    val lifetime = (cert.getNotAfter.getTime - now.getTime()) / 3600000; // in hour ! (TBC in secs)

    // checks if the proxy is still valid
    if (lifetime < 0) throw new org.glite.wms.wmproxy.CredentialException("the local proxy has expired")
    // sets the lifetime
    generator.setLifetime(lifetime.toInt)
    // creates the new proxy
    val proxy = generator.x509MakeProxyCert(certReq.getBytes, proxyStream.getBytes, "");
    // converts the proxy from byte[] to String
    new String(proxy)
  }

}

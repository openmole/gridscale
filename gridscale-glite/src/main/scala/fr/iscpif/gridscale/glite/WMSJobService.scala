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
      def compute(k: A) = _delegate(k, get(k).getOrElse(UUID.randomUUID.toString))
      def cacheTime(s: String) = Some(delegationRenewal * 1000)
    }

  def delegationId(implicit credential: A) = delegationCache(credential)

  def delegate(credential: A) = delegationCache.forceRenewal(credential)

  private def _delegate(credential: A, delegationId: String): String = {
    val req = grstStub(credential).getProxyReq(delegationId)
    serviceStub(credential).putProxy(delegationId, createProxyfromCertReq(req, proxyString(credential()._2)))
    delegationId
  }

  def submit(desc: WMSJobDescription)(implicit credential: A) = {
    val j = register(desc.toJDL)
    fillInputSandbox(desc, j.getId)
    serviceStub.jobStart(j.getId)
    new WMSJobId {
      val id = j.getId
    }
  }

  def cancel(jobId: J)(implicit credential: A) = serviceStub.jobCancel(jobId.id)

  def purge(jobId: J)(implicit credential: A) = serviceStub.jobPurge(jobId.id)

  def state(jobId: J)(implicit credential: A) = translateState(rawState(jobId))

  def rawState(jobId: J)(implicit credential: A) = {
    val jobUrl = new URL(jobId.id)
    val lbServiceURL = new URL(jobUrl.getProtocol, jobUrl.getHost, 9003, "")
    lbService(lbServiceURL).jobStatus(jobId.id, flags).getState
  }

  def downloadOutputSandbox(desc: D, jobId: J)(implicit credential: A) = {
    val indexed = desc.outputSandbox.groupBy(_._1).map { case (k, v) ⇒ k -> v.head }

    serviceStub.getOutputFileList(jobId.id, "gsiftp").getFile.foreach {
      from ⇒
        val url = new URI(from.getName)
        val file = indexed(new File(url.getPath).getName)._2

        val is = new GridFTPInputStream(credential()._1, url.getHost, SRMStorage.gridFtpPort(url.getPort), url.getPath)
        try copy(is, file, copyBufferSize, timeout)
        finally is.close
    }
  }

  private def translateState(s: StatName) =
    s.getValue match {
      case StatName._ABORTED ⇒ Failed
      case StatName._CANCELLED ⇒ Failed
      case StatName._CLEARED ⇒ Done
      case StatName._DONE ⇒ Done
      case StatName._PURGED ⇒ Done
      case StatName._READY ⇒ Submitted
      case StatName._RUNNING ⇒ Running
      case StatName._SCHEDULED ⇒ Submitted
      case StatName._SUBMITTED ⇒ Submitted
      case StatName._UNKNOWN ⇒ Failed
      case StatName._WAITING ⇒ Submitted
    }

  private def fillInputSandbox(desc: WMSJobDescription, jobId: String)(implicit credential: A) = {
    val inputSandboxURL = new URI(serviceStub.getSandboxDestURI(jobId, "gsiftp").getItem(0))
    desc.inputSandbox.foreach {
      file ⇒
        val os = new GridFTPOutputStream(credential()._1, inputSandboxURL.getHost, SRMStorage.gridFtpPort(inputSandboxURL.getPort), inputSandboxURL.getPath + "/" + file.getName, false)
        try copy(file, os, copyBufferSize, timeout)
        finally os.close
    }
  }

  private def register(jdl: String)(implicit credential: A) = serviceStub.jobRegister(jdl, delegationId)

  private def lbService(url: URL)(implicit credential: A) = {
    val locator = new LoggingAndBookkeepingLocator(provider)
    val lbService = locator.getLoggingAndBookkeeping(url)
    lbService.asInstanceOf[Stub]._setProperty(AGSIConstants.GSI_CREDENTIALS, credential()._1)
    lbService.asInstanceOf[Stub].setTimeout(timeout * 1000)
    lbService
  }

  private def provider = {
    val provider = new SimpleProvider
    provider.deployTransport("https", new SimpleTargetedChain(new HTTPSSender))
    provider.deployTransport("http", new SimpleTargetedChain(new HTTPSSender))
    provider
  }

  @transient private lazy val serviceLocator = new WMProxyLocator(provider)

  private def serviceStub(implicit credential: A) = {
    val serviceStub = serviceLocator.getWMProxy_PortType(url.toURL)
    serviceStub.asInstanceOf[Stub]._setProperty(AGSIConstants.GSI_CREDENTIALS, credential()._1)
    serviceStub.asInstanceOf[Stub].setTimeout(timeout * 1000)
    serviceStub
  }

  private def grstStub(implicit credential: A) = {
    val grstStub = serviceLocator.getWMProxyDelegation2_PortType(url.toURL)
    grstStub.asInstanceOf[Stub]._setProperty(AGSIConstants.GSI_CREDENTIALS, credential()._1)
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

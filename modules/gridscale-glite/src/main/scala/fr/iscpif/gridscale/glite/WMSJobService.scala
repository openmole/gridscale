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
import org.glite.security.delegation.GrDProxyGenerator
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl
import org.globus.io.streams.GridFTPInputStream
import org.globus.io.streams.GridFTPOutputStream
import scala.io.Source
import fr.iscpif.gridscale._
import scala.Some
import scala.Some
import scala.Some
import fr.iscpif.gridscale.{ JobService, Cache, DefaultTimeout }
import services._
import scala.io.Source
import fr.iscpif.gridscale.libraries.lbstub._

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
    val service = delegationService(credential)
    val req = service.getProxyReq(credential.delegationID).get
    service.putProxy(credential.delegationID, createProxyfromCertReq(req, credential)).get
    credential.delegationID
  }

  def submit(desc: WMSJobDescription)(implicit credential: A) = {
    val cred = credential()
    val j = wmsService(cred).jobRegister(desc.toJDL, cred.delegationID).get
    fillInputSandbox(desc, j.id)(cred)
    wmsService(cred).jobStart(j.id).get
    new WMSJobId {
      val id = j.id
    }
  }

  def cancel(jobId: J)(implicit credential: A) = wmsService(credential()).jobCancel(jobId.id).get

  def purge(jobId: J)(implicit credential: A) = wmsService(credential()).jobPurge(jobId.id).get

  def state(jobId: J)(implicit credential: A) = translateState(rawState(jobId))

  def downloadOutputSandbox(desc: D, jobId: J)(implicit credential: A) = {
    val cred = credential()
    val indexed = desc.outputSandbox.groupBy(_._1).map { case (k, v) ⇒ k -> v.head }

    wmsService(cred).getOutputFileList(jobId.id, "gsiftp").get.file.foreach {
      from ⇒
        val url = new URI(from.name)
        val file = indexed(new File(url.getPath).getName)._2

        val is = new GridFTPInputStream(cred.credential, url.getHost, SRMStorage.gridFtpPort(url.getPort), url.getPath)
        try copy(is, file, copyBufferSize, timeout)
        finally is.close
    }
  }

  private def fillInputSandbox(desc: WMSJobDescription, jobId: String)(credential: GlobusAuthentication.Proxy) = {
    val inputSandboxURL = new URI(wmsService(credential).getSandboxDestURI(jobId, "gsiftp").get.Item(0))
    desc.inputSandbox.foreach {
      file ⇒
        val os = new GridFTPOutputStream(credential.credential, inputSandboxURL.getHost, SRMStorage.gridFtpPort(inputSandboxURL.getPort), inputSandboxURL.getPath + "/" + file.getName, false)
        try copy(file, os, copyBufferSize, timeout)
        finally os.close
    }
  }

  private def delegationService(credential: GlobusAuthentication.Proxy) = DelegationService(url, credential, timeout * 1000)
  private def wmsService(credential: GlobusAuthentication.Proxy) = WMSService(url, credential, timeout * 1000)
  private def lbService(jobId: WMSJobId, credential: GlobusAuthentication.Proxy) = {
    val jobUrl = new URL(jobId.id)
    val lbServiceURL = new URL(jobUrl.getProtocol, jobUrl.getHost, 9003, "").toURI
    LBService(lbServiceURL, credential, timeout * 1000)
  }

   private def translateState(s: StatName) =
    s match {
      case ABORTED ⇒ Failed
      case CANCELLEDValue ⇒ Failed
      case CLEARED ⇒ Done
      case DONEValue4 ⇒ Done
      case PURGED ⇒ Done
      case READY ⇒ Submitted
      case RUNNING ⇒ Running
      case SCHEDULED ⇒ Submitted
      case SUBMITTED ⇒ Submitted
      case UNKNOWNValue3 ⇒ Failed
      case WAITING ⇒ Submitted
    }


  private def rawState(jobId: WMSJobId)(implicit credential: WMSJobService#A) =
    lbService(jobId, credential()).jobStatus(jobId.id, flags).get.state

  private lazy val flags = JobFlags(CLASSADS, CHILDREN, CHILDSTAT)


  private def createProxyfromCertReq(certReq: String, proxy: GlobusAuthentication.Proxy) = {
    def proxyString(proxyFile: File) = {
      val s = Source.fromFile(proxyFile)
      try s.mkString
      finally s.close
    }

    val proxyStream = proxyString(proxy.proxy)

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
    val lifetime = (cert.getNotAfter.getTime - now.getTime()) / 3600000; // in hour ! (TBC in secs)

    // checks if the proxy is still valid
    if (lifetime < 0) throw new RuntimeException("the local proxy has expired")
    // sets the lifetime
    generator.setLifetime(lifetime.toInt)
    // creates the new proxy and converts the proxy from byte[] to String
    new String(generator.x509MakeProxyCert(certReq.getBytes, proxyStream.getBytes, ""))
  }

}

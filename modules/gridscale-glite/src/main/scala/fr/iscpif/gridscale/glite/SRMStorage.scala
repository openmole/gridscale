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

import java.io.File
import java.net.MalformedURLException
import java.net.URI
import org.apache.axis.client.Call
import org.glite.voms.contact.VOMSProxyInit
import org.glite.voms.contact.VOMSRequestOptions
import org.glite.voms.contact.VOMSServerInfo
import org.globus.gsi.GSIConstants.DelegationType
import org.globus.ftp.GridFTPClient
import org.globus.gsi.GSIConstants.CertificateType
import org.globus.axis.gsi.GSIConstants
import org.ietf.jgss.GSSCredential
import collection.JavaConversions._
import org.globus.util.Util
import org.globus.gsi.X509Credential
import org.globus.gsi.gssapi._
import org.ogf.srm22._
import org.globus.axis.transport._
import org.apache.axis.configuration.SimpleProvider
import org.apache.axis.SimpleTargetedChain
import org.apache.axis.client.Stub
import TStatusCode._
import collection.JavaConversions._
import java.util.concurrent.TimeoutException
import org.globus.gsi.gssapi.auth.HostAuthorization
import org.globus.io.streams._
import com.sun.org.apache.xalan.internal.templates.Constants
import fr.iscpif.gridscale._

object SRMStorage {

  Call.setTransportForProtocol("httpg", classOf[GSIHTTPTransport])

  def init = Unit

  def gridFtpPort(p: Int) =
    if (p == -1) 2811 else p

  type RequestStatus = {
    def getReturnStatus(): TReturnStatus
  }

  type RequestStatusWithToken = RequestStatus {
    def getRequestToken(): String
  }

}

import SRMStorage._

trait SRMStorage extends Storage with RecursiveRmDir {

  SRMStorage.init

  type A = () ⇒ GlobusAuthentication.Proxy

  def host: String
  def port: Int
  def basePath: String

  def timeout = 60
  def sleepTime = 1
  def lsSizeMax = 500
  def SERVICE_PROTOCOL = "httpg"
  def SERVICE_PATH = "/srm/managerv2"
  def transferProtocols = Array("gsiftp")
  def permissive = false

  def version(implicit credential: A) = stub.srmPing(new SrmPingRequest).getVersionInfo

  def _list(absolutePath: String)(implicit credential: A): Seq[(String, FileType)] = {
    def recList(offset: Int, res: List[Seq[(String, FileType)]] = List.empty): Seq[(String, FileType)] = {
      val ls = list(absolutePath, offset, lsSizeMax)
      if (ls.size < lsSizeMax) (ls :: res).reverse.flatten
      else recList(offset + lsSizeMax, ls :: res)
    }
    recList(0)
  }

  def list(absolutePath: String, offset: Int, size: Int)(implicit credential: A) = {
    val uri = toSrmURI(absolutePath)
    println("uri " + uri)
    val request = new SrmLsRequest
    request.setArrayOfSURLs(new ArrayOfAnyURI(Array(uri)))
    request.setAllLevelRecursive(false)
    request.setFullDetailedList(false)
    request.setOffset(offset)
    request.setCount(size)

    type SRMLsRS = RequestStatus {
      def getDetails(): ArrayOfTMetaDataPathDetail
    }

    def requestStatus(implicit credential: A) = stub.srmLs(request)

    val childs = complete[SRMLsRS](requestStatus) {
      token ⇒
        val status = new SrmStatusOfLsRequestRequest
        status.setRequestToken(token)
        status.setOffset(offset)
        status.setCount(size)
        stub.srmStatusOfLsRequest(status)
    }.getDetails.getPathDetailArray

    (for {
      child ← childs
      if (child.getArrayOfSubPaths != null && child.getArrayOfSubPaths.getPathDetailArray != null)
      pd ← child.getArrayOfSubPaths.getPathDetailArray
    } yield {
      val t = pd.getType match {
        case TFileType.DIRECTORY ⇒ DirectoryType
        case TFileType.FILE ⇒ FileType
        case TFileType.LINK ⇒ LinkType
      }
      new File(pd.getPath).getName -> t
    }).toSeq
  }

  def _makeDir(path: String)(implicit credential: A) = {
    val uri = this.toSrmURI(path)
    val request = new SrmMkdirRequest
    request.setSURL(uri)
    val requestStatus = stub.srmMkdir(request)
    if (requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
    if (permissive) allowAllPermissions(path)
  }

  def rmEmptyDir(path: String)(implicit credential: A) = {
    val uri = this.toSrmURI(path)
    val request = new SrmRmdirRequest
    request.setSURL(uri)
    //Doesn't work
    //request.setRecursive(java.lang.Boolean.TRUE)
    val requestStatus = stub.srmRmdir(request)
    if (requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  def _rmFile(path: String)(implicit credential: A) = {
    val uri = this.toSrmURI(path)
    val request = new SrmRmRequest
    request.setArrayOfSURLs(new ArrayOfAnyURI(Array(uri)))
    val requestStatus = stub.srmRm(request)
    if (requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  def _mv(from: String, to: String)(implicit authentication: A) = {
    val fromURI = this.toSrmURI(from)
    val toURI = this.toSrmURI(to)
    val request = new SrmMvRequest
    request.setFromSURL(fromURI)
    request.setToSURL(toURI)
    val requestStatus = stub.srmMv(request)
    if (requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
    if (permissive) allowAllPermissions(to)
  }

  def allowAllPermissions(path: String)(implicit authentication: A) = {
    val uri = this.toSrmURI(path)
    val request = new SrmSetPermissionRequest

    request.setSURL(uri)

    request.setPermissionType(TPermissionType.CHANGE)
    request.setOwnerPermission(TPermissionMode.RWX)
    request.setOtherPermission(TPermissionMode.RWX)

    val requestStatus = stub.srmSetPermission(request)
    if (requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  protected def _openInputStream(path: String)(implicit credential: A) = {
    val (token, url) = prepareToGet(path)

    new GridFTPInputStream(credential().credential, url.getHost, gridFtpPort(url.getPort), url.getPath) {
      override def close = {
        try freeInputStream(token, path)
        finally super.close
      }
    }
  }

  protected def _openOutputStream(path: String)(implicit credential: A) = {
    val (token, url) = prepareToPut(path)

    new GridFTPOutputStream(credential().credential, url.getHost, gridFtpPort(url.getPort), url.getPath, false) {
      override def close = {
        try freeOutputStream(token, path)
        finally super.close
        if (permissive) allowAllPermissions(path)
      }
    }
  }

  private def freeInputStream(token: String, absolutePath: String)(implicit credential: A) = {
    val logicalUri = toSrmURI(absolutePath)
    val request = new SrmReleaseFilesRequest
    request.setRequestToken(token)
    request.setArrayOfSURLs(new ArrayOfAnyURI(Array(logicalUri)))
    val requestStatus = stub.srmReleaseFiles(request)

    if (requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  private def freeOutputStream(token: String, absolutePath: String)(implicit credential: A) = {
    val logicalUri = toSrmURI(absolutePath)
    val request = new SrmPutDoneRequest
    request.setRequestToken(token)
    request.setArrayOfSURLs(new ArrayOfAnyURI(Array(logicalUri)))
    val requestStatus = stub.srmPutDone(request)
    if (requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  private def prepareToPut(absolutePath: String)(implicit credential: A) = {
    val logicalUri = toSrmURI(absolutePath)
    val request = new SrmPrepareToPutRequest
    request.setArrayOfFileRequests(new ArrayOfTPutFileRequest(Array(new TPutFileRequest(logicalUri, null))))
    request.setTransferParameters(new TTransferParameters(TAccessPattern.TRANSFER_MODE, TConnectionType.WAN, null, new ArrayOfString(transferProtocols)))
    val requestStatus = stub.srmPrepareToPut(request)

    type SRMPrepareToPutRS = RequestStatus {
      def getArrayOfFileStatuses(): ArrayOfTPutRequestFileStatus
    }

    val url = complete[SRMPrepareToPutRS](requestStatus) {
      token ⇒
        val status = new SrmStatusOfPutRequestRequest
        status.setRequestToken(token)
        status.setArrayOfTargetSURLs(new ArrayOfAnyURI(Array(logicalUri)))
        stub.srmStatusOfPutRequest(status)
    }.getArrayOfFileStatuses.getStatusArray.head.getTransferURL

    (requestStatus.getRequestToken, url)
  }

  private def prepareToGet(absolutePath: String)(implicit credential: A) = {
    val logicalUri = toSrmURI(absolutePath)

    val request = new SrmPrepareToGetRequest

    request.setArrayOfFileRequests(new ArrayOfTGetFileRequest(Array(new TGetFileRequest(logicalUri, null))))
    request.setTransferParameters(new TTransferParameters(TAccessPattern.TRANSFER_MODE, TConnectionType.WAN, null, new ArrayOfString(transferProtocols)))

    val requestStatus = stub.srmPrepareToGet(request)

    type SRMPrepareToGetRS = RequestStatus {
      def getArrayOfFileStatuses(): ArrayOfTGetRequestFileStatus
    }

    val url = complete[SRMPrepareToGetRS](requestStatus) {
      token ⇒
        val status = new SrmStatusOfGetRequestRequest
        status.setRequestToken(token)
        status.setArrayOfSourceSURLs(new ArrayOfAnyURI(Array(logicalUri)))
        stub.srmStatusOfGetRequest(status)
    }.getArrayOfFileStatuses.getStatusArray.head.getTransferURL
    (requestStatus.getRequestToken, url)
  }

  private def status[R <: RequestStatusWithToken](request: R) = {
    import TStatusCode._

    if (request.getReturnStatus.getStatusCode == SRM_SUCCESS) Left(request)
    else if (request.getReturnStatus.getStatusCode == SRM_REQUEST_QUEUED || request.getReturnStatus.getStatusCode == SRM_REQUEST_INPROGRESS) Right(request.getRequestToken)
    else throw new RuntimeException("Error interrogating the SRM server " + host + ", response was " + request.getReturnStatus.getStatusCode)
  }

  private def complete[R <: RequestStatus](request: R with RequestStatusWithToken)(requestRequest: String ⇒ R)(implicit credential: A) =
    status(request) match {
      case Left(r) ⇒ r
      case Right(token) ⇒
        try waitSuccess(() ⇒ requestRequest(token))
        catch {
          case t: Throwable ⇒
            abortRequest(token)
            throw t
        }
    }

  private def waiting[R <: RequestStatus](r: R) =
    r.getReturnStatus.getStatusCode == SRM_REQUEST_QUEUED || r.getReturnStatus.getStatusCode == SRM_REQUEST_INPROGRESS

  private def waitSuccess[R <: RequestStatus](f: () ⇒ R, deadLine: Long = System.currentTimeMillis + timeout * 1000, sleep: Long = sleepTime * 1000): R = {
    val request = f()
    if (request.getReturnStatus.getStatusCode == SRM_SUCCESS) request
    else if (waiting(request)) {
      if (System.currentTimeMillis > deadLine) throw new TimeoutException("Waiting for request to complete, status is " + request.getReturnStatus.getStatusCode)
      Thread.sleep(sleepTime)
      val newSleepTime = if (System.currentTimeMillis + sleepTime * 2 < deadLine) sleepTime * 2 else deadLine - System.currentTimeMillis
      waitSuccess(f, deadLine, newSleepTime)
    } else throwError(request)
  }

  private def abortRequest(token: String)(implicit credential: A) = {
    val r = new SrmAbortRequestRequest
    r.setRequestToken(token)
    stub.srmAbortRequest(r)
  }

  private def throwError[R <: RequestStatus](r: R) = throw new RuntimeException("Error interrogating the SRM server " + host + ", response was " + r.getReturnStatus.getStatusCode + " " + r.getReturnStatus.getExplanation)

  private def toSrmURI(absolutePath: String) =
    new org.apache.axis.types.URI("srm", null, host, port, SERVICE_PATH, "SFN=" + basePath + absolutePath, null)

  @transient lazy val serviceUrl = new java.net.URL(SERVICE_PROTOCOL, host, port, SERVICE_PATH, new org.globus.net.protocol.httpg.Handler)

  private def stub(implicit credential: A) = {
    val stub = locator.getsrm(serviceUrl)
    stub.asInstanceOf[Stub]._setProperty(GSIConstants.GSI_CREDENTIALS, credential().credential)
    stub.asInstanceOf[Stub].setTimeout(timeout * 1000)
    stub
  }

  @transient private lazy val locator = {
    val provider = new SimpleProvider
    val c = new SimpleTargetedChain(new HTTPGHandler)
    provider.deployTransport("httpg", c)
    new SRMServiceLocator(provider)
  }

}

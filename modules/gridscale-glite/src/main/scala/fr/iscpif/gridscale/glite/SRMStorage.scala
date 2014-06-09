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
import org.glite.voms.contact.VOMSProxyInit
import org.glite.voms.contact.VOMSRequestOptions
import org.glite.voms.contact.VOMSServerInfo
import org.globus.gsi.GSIConstants.DelegationType
import org.globus.gsi.GSIConstants.CertificateType
import org.ietf.jgss.GSSCredential
import collection.JavaConversions._
import org.globus.util.Util
import org.globus.gsi.X509Credential
import org.globus.gsi.gssapi._
import collection.JavaConversions._
import java.util.concurrent.TimeoutException
import org.globus.gsi.gssapi.auth.HostAuthorization
import org.globus.io.streams._
import fr.iscpif.gridscale._
import fr.iscpif.gridscale.glite.services._
import fr.iscpif.gridscale.libraries.srmstub._

object SRMStorage {

  def gridFtpPort(p: Int) =
    if (p == -1) 2811 else p

  type RequestStatus = {
    def returnStatus: TReturnStatus
  }

  type RequestStatusWithToken = RequestStatus {
    def requestToken: Option[Option[String]]
  }

}

import SRMStorage._

trait SRMStorage <: Storage with RecursiveRmDir {

  type A = () ⇒ GlobusAuthentication.Proxy

  override def toString = s"srm://$host:$port$basePath"

  def host: String
  def port: Int
  def basePath: String

  def timeout = 60
  def sleepTime = 1
  def lsSizeMax = 500
  def SERVICE_PROTOCOL = "httpg"
  def SERVICE_PATH = "/srm/managerv2"
  def transferProtocols = ArrayOfString(Some("gsiftp"))
  @transient lazy val transferParameters =
    TTransferParameters(
      accessPattern = Some(Some(TRANSFER_MODE)),
      connectionType = Some(Some(WAN)),
      arrayOfTransferProtocols = Some(Some(transferProtocols))
    )

  def version(implicit credential: A) = stub.srmPing(new SrmPingRequest).get.versionInfo

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
    val request =
      SrmLsRequest(
        arrayOfSURLs = ArrayOfAnyURI(Some(uri)),
        allLevelRecursive = Some(Some(false)),
        fullDetailedList = Some(Some(false)),
        offset = Some(Some(offset)),
        count = Some(Some(size))
      )

    type SRMLsRS = RequestStatus {
      def details: Option[Option[ArrayOfTMetaDataPathDetail]]
    }

    def requestStatus(implicit credential: A) = stub.srmLs(request).get

    val childs = complete[SRMLsRS](requestStatus) {
      token ⇒
        val status =
          SrmStatusOfLsRequestRequest (
            requestToken = token,
            offset = Some(offset),
            count = Some(Some(size))
          )
         stub.srmStatusOfLsRequest(status).get
    }.details.get.get.pathDetailArray

    (for {
      pd: TMetaDataPathDetail ← childs.flatMap(_.map(_.arrayOfSubPaths.map(_.map(_.pathDetailArray)))).flatten.flatten.flatten.flatten
    } yield {
      val t = pd.typeValue match {
        case Some(Some(DIRECTORY)) ⇒ DirectoryType
        case Some(Some(FILE)) ⇒ FileType
        case Some(Some(LINK)) ⇒ LinkType
        case _ ⇒ UnknownType
      }
      new File(pd.path).getName -> t
    }).toSeq
  }

  def _makeDir(path: String)(implicit credential: A) = {
    val uri = this.toSrmURI(path)
    val request = SrmMkdirRequest(SURL = uri)
    val requestStatus = stub.srmMkdir(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }



  def rmEmptyDir(path: String)(implicit credential: A) = {
    val uri = this.toSrmURI(path)
    val request = SrmRmdirRequest(SURL = uri)
    //Doesn't work
    //request.setRecursive(java.lang.Boolean.TRUE)
    val requestStatus = stub.srmRmdir(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  def _rmFile(path: String)(implicit credential: A) = {
    val uri = this.toSrmURI(path)
    val request = SrmRmRequest(arrayOfSURLs = ArrayOfAnyURI(Some(uri)))
    val requestStatus = stub.srmRm(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  def _mv(from: String, to: String)(implicit authentication: A) = {
    val fromURI = this.toSrmURI(from)
    val toURI = this.toSrmURI(to)
    val request = SrmMvRequest(fromSURL = fromURI, toSURL = toURI)
    val requestStatus = stub.srmMv(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  def allowAllPermissions(path: String)(implicit authentication: A) = {
    val uri = this.toSrmURI(path)
    val request =
      SrmSetPermissionRequest(
        SURL = uri,
        permissionType = CHANGE,
        ownerPermission = Some(Some(RWX)),
        otherPermission = Some(Some(RWX))
      )

    val requestStatus = stub.srmSetPermission(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
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
      }
    }
  }
  private def freeInputStream(token: String, absolutePath: String)(implicit credential: A) = {
    val logicalUri = toSrmURI(absolutePath)
    val request =
      SrmReleaseFilesRequest (
        requestToken = Some(Some(token)),
        arrayOfSURLs = Some(Some(ArrayOfAnyURI(Some(logicalUri))))
      )
    val requestStatus = stub.srmReleaseFiles(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  private def freeOutputStream(token: String, absolutePath: String)(implicit credential: A) = {
    val logicalUri = toSrmURI(absolutePath)
    val request =
      SrmPutDoneRequest (
        requestToken = token,
        arrayOfSURLs = ArrayOfAnyURI(Some(logicalUri))
      )
    val requestStatus = stub.srmPutDone(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  private def prepareToPut(absolutePath: String)(implicit credential: A) = {
    val logicalUri = toSrmURI(absolutePath)
    val request =
      SrmPrepareToPutRequest (
        arrayOfFileRequests = Some(Some(ArrayOfTPutFileRequest(Some(TPutFileRequest(Some(Some(logicalUri))))))),
        transferParameters = Some(Some(transferParameters))
      )

    val requestStatus = stub.srmPrepareToPut(request).get

    type SRMPrepareToPutRS = RequestStatus {
      def arrayOfFileStatuses: Option[Option[ArrayOfTPutRequestFileStatus]]
    }

    val url = complete[SRMPrepareToPutRS](requestStatus) {
      token ⇒
        val status = SrmStatusOfPutRequestRequest(
          requestToken = token,
          arrayOfTargetSURLs = Some(Some(ArrayOfAnyURI(Some(logicalUri))))
        )
        stub.srmStatusOfPutRequest(status).get
    }.arrayOfFileStatuses.get.get.statusArray.head.transferURL

    (requestStatus.requestToken.get.get, url.get.get)
  }

  private def prepareToGet(absolutePath: String)(implicit credential: A) = {
    val logicalUri = toSrmURI(absolutePath)

    val request =
      SrmPrepareToGetRequest (
        arrayOfFileRequests = ArrayOfTGetFileRequest(Some(TGetFileRequest(logicalUri))),
        transferParameters = Some(Some(transferParameters))
      )

    val requestStatus = stub.srmPrepareToGet(request).get

    type SRMPrepareToGetRS = RequestStatus {
      def arrayOfFileStatuses: Option[Option[ArrayOfTGetRequestFileStatus]]
    }

    val url = complete[SRMPrepareToGetRS](requestStatus) {
      token ⇒
        val status = SrmStatusOfGetRequestRequest(
          requestToken = token,
          arrayOfSourceSURLs = Some(Some(ArrayOfAnyURI(Some(logicalUri))))
        )
        stub.srmStatusOfGetRequest(status).get
    }.arrayOfFileStatuses.get.get.statusArray.head.get.transferURL
    (requestStatus.requestToken.get.get, url.get.get)
  }

  private def status[R <: RequestStatusWithToken](request: R) = {
    import TStatusCode._

    if (request.returnStatus.statusCode == SRM_SUCCESS) Left(request)
    else if (request.returnStatus.statusCode == SRM_REQUEST_QUEUED || request.returnStatus.statusCode == SRM_REQUEST_INPROGRESS) Right(request.requestToken)
    else throw new RuntimeException("Error interrogating the SRM server " + host + ", response was " + request.returnStatus.statusCode)
  }

  private def complete[R <: RequestStatus](request: R with RequestStatusWithToken)(requestRequest: String ⇒ R)(implicit credential: A) =
    status(request) match {
      case Left(r) ⇒ r
      case Right(token) ⇒
        try waitSuccess(() ⇒ requestRequest(token.get.get))
        catch {
          case t: Throwable ⇒
            abortRequest(token.get.get)
            throw t
        }
    }

  private def waiting[R <: RequestStatus](r: R) =
    r.returnStatus.statusCode == SRM_REQUEST_QUEUED || r.returnStatus.statusCode == SRM_REQUEST_INPROGRESS

  private def waitSuccess[R <: RequestStatus](f: () ⇒ R, deadLine: Long = System.currentTimeMillis + timeout * 1000, sleep: Long = sleepTime * 1000): R = {
    val request = f()
    if (request.returnStatus.statusCode == SRM_SUCCESS) request
    else if (waiting(request)) {
      if (System.currentTimeMillis > deadLine) throw new TimeoutException("Waiting for request to complete, status is " + request.returnStatus.statusCode)
      Thread.sleep(sleepTime)
      val newSleepTime = if (System.currentTimeMillis + sleepTime * 2 < deadLine) sleepTime * 2 else deadLine - System.currentTimeMillis
      waitSuccess(f, deadLine, newSleepTime)
    } else throwError(request)
  }

  private def abortRequest(token: String)(implicit credential: A) = stub.srmAbortRequest(SrmAbortRequestRequest(token))

  private def throwError[R <: RequestStatus](r: R) =
    throw new RuntimeException("Error interrogating the SRM server " + host + ", response was " + r.returnStatus.statusCode + " " + r.returnStatus.explanation.getOrElse(None).getOrElse(""))

  private def toSrmURI(absolutePath: String) =
    new URI("srm", null, host, port, SERVICE_PATH, "SFN=" + basePath + absolutePath, null)

  @transient lazy val serviceUrl = new java.net.URL(SERVICE_PROTOCOL, host, port, SERVICE_PATH, new org.globus.net.protocol.httpg.Handler).toURI

  private def stub(implicit credential: A) = SRMService(serviceUrl, credential(), timeout * 1000)


}

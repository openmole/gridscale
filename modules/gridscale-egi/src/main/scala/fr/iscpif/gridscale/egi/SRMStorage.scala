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

import java.io.File
import java.net.URI
import java.util.concurrent.TimeoutException

import fr.iscpif.gridscale.egi.services._
import fr.iscpif.gridscale.libraries.srmstub._
import fr.iscpif.gridscale.storage._
import fr.iscpif.gridscale.tools.DefaultTimeout
import org.globus.io.streams._

case class SRMLocation(host: String, port: Int, basePath: String)

object SRMStorage {

  def apply[P: GlobusAuthenticationProvider](location: SRMLocation, connections: Int = 5)(proxy: P): SRMStorage = {
    val (_connections, _proxy) = (connections, proxy)

    new SRMStorage {
      override def proxy(): GlobusAuthentication.Proxy = implicitly[GlobusAuthenticationProvider[P]].apply(_proxy)
      override def host = location.host
      override def basePath = location.basePath
      override def port = location.port
      override def connections = _connections
    }
  }

  def SERVICE_PATH = "/srm/managerv2"

  def gridFtpPort(p: Int) =
    if (p == -1) 2811 else p

  type RequestStatus = {
    def returnStatus: TReturnStatus
  }

  type RequestStatusWithToken = RequestStatus {
    def requestToken: Option[Option[String]]
  }

  def fullEndpoint(host: String, port: Int, absolutePath: String) =
    new URI("srm", null, host, port, SERVICE_PATH, "SFN=" + absolutePath, null)

}

import fr.iscpif.gridscale.egi.SRMStorage._

trait SRMStorage <: Storage with RecursiveRmDir with DefaultTimeout {

  def proxy(): GlobusAuthentication.Proxy

  override def toString = s"srm://$host:$port$basePath"

  def host: String
  def port: Int
  def basePath: String

  def connections: Int
  def sleepTime = 1
  def lsSizeMax = 500
  def SERVICE_PROTOCOL = "httpg"
  def transferProtocols = ArrayOfString(Some("gsiftp"))

  @transient lazy val transferParameters =
    TTransferParameters(
      accessPattern = Some(Some(TRANSFER_MODE)),
      connectionType = Some(Some(WAN)),
      arrayOfTransferProtocols = Some(Some(transferProtocols))
    )

  def version = stub.srmPing(new SrmPingRequest).get.versionInfo

  def _list(absolutePath: String) = {
    def recList(offset: Int, res: List[Seq[ListEntry]] = List.empty): Seq[ListEntry] = {
      val ls = list(absolutePath, offset, lsSizeMax)
      if (ls.size < lsSizeMax) (ls :: res).reverse.flatten
      else recList(offset + lsSizeMax, ls :: res)
    }
    recList(0)
  }

  def list(absolutePath: String, offset: Int, size: Int) = {
    val uri = fullEndPoint(absolutePath)
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

    def requestStatus = stub.srmLs(request).get

    val childs = complete[SRMLsRS](requestStatus) {
      token ⇒
        val status =
          SrmStatusOfLsRequestRequest(
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
        case Some(Some(FILE))      ⇒ FileType
        case Some(Some(LINK))      ⇒ LinkType
        case _                     ⇒ UnknownType
      }
      ListEntry(
        new File(pd.path).getName,
        t,
        pd.lastModificationTime.flatten.map(_.toGregorianCalendar.getTimeInMillis))
    }).toSeq
  }

  def _makeDir(path: String) = {
    val uri = this.fullEndPoint(path)
    val request = SrmMkdirRequest(SURL = uri)
    val requestStatus = stub.srmMkdir(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  def rmEmptyDir(path: String) = {
    val uri = this.fullEndPoint(path)
    val request = SrmRmdirRequest(SURL = uri)
    //Doesn't work
    //request.setRecursive(java.lang.Boolean.TRUE)
    val requestStatus = stub.srmRmdir(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  def _rmFile(path: String) = {
    val uri = this.fullEndPoint(path)
    val request = SrmRmRequest(arrayOfSURLs = ArrayOfAnyURI(Some(uri)))
    val requestStatus = stub.srmRm(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  def _mv(from: String, to: String) = {
    val fromURI = this.fullEndPoint(from)
    val toURI = this.fullEndPoint(to)
    val request = SrmMvRequest(fromSURL = fromURI, toSURL = toURI)
    val requestStatus = stub.srmMv(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  def allowAllPermissions(path: String) = {
    val uri = this.fullEndPoint(path)
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

  protected def _openInputStream(path: String) = {
    val (token, url) = prepareToGet(path)

    new GridFTPInputStream(proxy().credential, url.getHost, gridFtpPort(url.getPort), url.getPath) {
      override def close = {
        try freeInputStream(token, path)
        finally super.close
      }
    }
  }

  protected def _openOutputStream(path: String) = {
    val (token, url) = prepareToPut(path)

    new GridFTPOutputStream(proxy().credential, url.getHost, gridFtpPort(url.getPort), url.getPath, false) {
      override def close = {
        try freeOutputStream(token, path)
        finally super.close
      }
    }
  }
  private def freeInputStream(token: String, absolutePath: String) = {
    val logicalUri = fullEndPoint(absolutePath)
    val request =
      SrmReleaseFilesRequest(
        requestToken = Some(Some(token)),
        arrayOfSURLs = Some(Some(ArrayOfAnyURI(Some(logicalUri))))
      )
    val requestStatus = stub.srmReleaseFiles(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  private def freeOutputStream(token: String, absolutePath: String) = {
    val logicalUri = fullEndPoint(absolutePath)
    val request =
      SrmPutDoneRequest(
        requestToken = token,
        arrayOfSURLs = ArrayOfAnyURI(Some(logicalUri))
      )
    val requestStatus = stub.srmPutDone(request).get
    if (requestStatus.returnStatus.statusCode != SRM_SUCCESS) throwError(requestStatus)
  }

  private def prepareToPut(absolutePath: String) = {
    val logicalUri = fullEndPoint(absolutePath)
    val request =
      SrmPrepareToPutRequest(
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

  private def prepareToGet(absolutePath: String) = {
    val logicalUri = fullEndPoint(absolutePath)

    val request =
      SrmPrepareToGetRequest(
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

    if (request.returnStatus.statusCode == SRM_SUCCESS) Left(request)
    else if (request.returnStatus.statusCode == SRM_REQUEST_QUEUED || request.returnStatus.statusCode == SRM_REQUEST_INPROGRESS) Right(request.requestToken)
    else throw new RuntimeException("Error interrogating the SRM server " + host + ", response was " + request.returnStatus.statusCode)
  }

  private def complete[R <: RequestStatus](request: R with RequestStatusWithToken)(requestRequest: String ⇒ R) =
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

  private def waitSuccess[R <: RequestStatus](f: () ⇒ R, deadLine: Long = System.currentTimeMillis + timeout.toMillis, sleep: Long = sleepTime * 1000): R = {
    val request = f()
    if (request.returnStatus.statusCode == SRM_SUCCESS) request
    else if (waiting(request)) {
      if (System.currentTimeMillis > deadLine) throw new TimeoutException("Waiting for request to complete, status is " + request.returnStatus.statusCode)
      Thread.sleep(sleepTime)
      val newSleepTime = if (System.currentTimeMillis + sleepTime * 2 < deadLine) sleepTime * 2 else deadLine - System.currentTimeMillis
      waitSuccess(f, deadLine, newSleepTime)
    } else throwError(request)
  }

  private def abortRequest(token: String) = stub.srmAbortRequest(SrmAbortRequestRequest(token))

  private def throwError[R <: RequestStatus](r: R) =
    throw new RuntimeException("Error interrogating the SRM server " + host + ", response was " + r.returnStatus.statusCode + " " + r.returnStatus.explanation.getOrElse(None).getOrElse(""))

  def fullEndPoint(absolutePath: String) = fullEndpoint(host, port, basePath + absolutePath)

  @transient lazy val serviceUrl = new java.net.URL(SERVICE_PROTOCOL, host, port, SERVICE_PATH, new org.globus.net.protocol.httpg.Handler).toURI
  @transient lazy val stub = SRMService(serviceUrl, proxy, timeout, connections)

}

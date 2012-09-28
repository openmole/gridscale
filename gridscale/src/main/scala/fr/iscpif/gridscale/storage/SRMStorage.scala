/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.storage

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

object SRMStorage {
  
  Call.setTransportForProtocol("httpg", classOf[GSIHTTPTransport])
  
  def gridFtpPort(p: Int) = 
    if(p == -1) 2811 else p
 
  type RequestStatus = { 
    def getReturnStatus(): TReturnStatus
  }
   
  type RequestStatusWithToken = RequestStatus {
    def getRequestToken(): String
  }
    
}

import SRMStorage._

trait SRMStorage extends Storage {
  
  type A <: GlobusGSSCredentialImpl
  
  def url: URI
  def timeout = 120 * 1000
  def sleepTime = 10000
  def lsSizeMax = 1000
  def SERVICE_PROTOCOL = "httpg"
  def SERVICE_PATH = "/srm/managerv2" 
  def transferProtocols = Array("gsiftp")
  
  def version(implicit credential: GlobusGSSCredentialImpl) = stub.srmPing(new SrmPingRequest).getVersionInfo
  
  def list(absolutePath: String)(implicit credential: GlobusGSSCredentialImpl): Seq[(String, FileType)] = {
    def recList(offset: Int, res: List[Seq[(String, FileType)]] = List.empty): Seq[(String, FileType)] = {
      val ls = list(absolutePath, offset, lsSizeMax)
      if(ls.size < lsSizeMax) (ls :: res).reverse.flatten
      else recList(offset + lsSizeMax, ls :: res)
    }
    recList(0)
  }
  
  def list(absolutePath: String, offset: Int, size: Int)(implicit credential: GlobusGSSCredentialImpl) = {
    val uri = toSrmURI(absolutePath)
    val request = new SrmLsRequest
    request.setArrayOfSURLs(new ArrayOfAnyURI(Array(uri)))
    request.setAllLevelRecursive(false)
    request.setFullDetailedList(false)
    request.setOffset(offset)
    request.setCount(size)
    
    type SRMLsRS = RequestStatus {
      def getDetails(): ArrayOfTMetaDataPathDetail
    }
    
    def requestStatus(implicit credential: GlobusGSSCredentialImpl) = stub.srmLs(request)
    
    complete[SRMLsRS](requestStatus) { 
      token => 
      val status = new SrmStatusOfLsRequestRequest
      status.setRequestToken(token)
      stub.srmStatusOfLsRequest(status)
    }.getDetails.getPathDetailArray.map(_.getArrayOfSubPaths.getPathDetailArray.map{
        pd => 
          val t = pd.getType match {
            case TFileType.DIRECTORY => DirectoryType
            case TFileType.FILE => FileType
            case TFileType.LINK => LinkType
          }
          pd.getPath -> t
      }
    ).flatten.toSeq
  }
  
  
  def makeDir(path: String)(implicit credential: GlobusGSSCredentialImpl) = {
    val uri = this.toSrmURI(path)
    val request = new SrmMkdirRequest
    request.setSURL(uri)
    val requestStatus = stub.srmMkdir(request)
    if(requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
  }
  
  def rmDir(path: String)(implicit credential: GlobusGSSCredentialImpl) = {
    val uri = this.toSrmURI(path)
    val request = new SrmRmdirRequest
    request.setSURL(uri)
    request.setRecursive(true)
    val requestStatus = stub.srmRmdir(request)
    if(requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
  }
  
  def rmFile(path: String)(implicit credential: GlobusGSSCredentialImpl)  = {
    val uri = this.toSrmURI(path)
    val request = new SrmRmRequest
    request.setArrayOfSURLs(new ArrayOfAnyURI(Array(uri)))
    val requestStatus = stub.srmRm(request)
    if(requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
  }
  
  def openInputStream(path: String)(implicit credential: GlobusGSSCredentialImpl) = {
    val (token, url) = prepareToGet(path)

    new GridFTPInputStream(credential, url.getHost, gridFtpPort(url.getPort), url.getPath) {
      override def close = {
        try freeInputStream(token, path)
        finally super.close
      }
    }
  }
  
  def openOutputStream(path: String)(implicit credential: GlobusGSSCredentialImpl) = {
    val (token, url) = prepareToPut(path)
        
    new GridFTPOutputStream(credential, url.getHost, gridFtpPort(url.getPort), url.getPath, false) {
      override def close = {
        try freeOutputStream(token, path)
        finally super.close
      }
    }
  }
  
  private def freeInputStream(token: String, absolutePath: String)(implicit credential: GlobusGSSCredentialImpl) = {
    val logicalUri = toSrmURI(absolutePath)
    val request = new SrmReleaseFilesRequest
    request.setRequestToken(token)
    request.setArrayOfSURLs(new ArrayOfAnyURI(Array(logicalUri)))
    val requestStatus = stub.srmReleaseFiles(request)
    
    if(requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
  }
  
  private def freeOutputStream(token: String, absolutePath: String)(implicit credential: GlobusGSSCredentialImpl) = {
    val logicalUri = toSrmURI(absolutePath)
    val request = new SrmPutDoneRequest
    request.setRequestToken(token);
    request.setArrayOfSURLs(new ArrayOfAnyURI(Array(logicalUri)))
    val requestStatus = stub.srmPutDone(request)
    if(requestStatus.getReturnStatus.getStatusCode != SRM_SUCCESS) throwError(requestStatus)
  }
  
  private def prepareToPut(absolutePath: String)(implicit credential: GlobusGSSCredentialImpl) = {
    val logicalUri = toSrmURI(absolutePath)
    val request = new SrmPrepareToPutRequest
    request.setArrayOfFileRequests(new ArrayOfTPutFileRequest(Array(new TPutFileRequest(logicalUri, null))))
    request.setTransferParameters(new TTransferParameters(TAccessPattern.TRANSFER_MODE, TConnectionType.WAN, null, new ArrayOfString(transferProtocols)))
    val requestStatus = stub.srmPrepareToPut(request)
        
    type SRMPrepareToPutRS = RequestStatus {
      def getArrayOfFileStatuses(): ArrayOfTPutRequestFileStatus
    }
          
    val url = complete[SRMPrepareToPutRS](requestStatus) { 
      token => 
      val status = new SrmStatusOfPutRequestRequest
      status.setRequestToken(token)
      stub.srmStatusOfPutRequest(status)
    }.getArrayOfFileStatuses.getStatusArray.head.getTransferURL
    
    (requestStatus.getRequestToken, url)
  }

  private def prepareToGet(absolutePath: String)(implicit credential: GlobusGSSCredentialImpl) = {
    val logicalUri = toSrmURI(absolutePath)
       
    val request = new SrmPrepareToGetRequest
        
    request.setArrayOfFileRequests(new ArrayOfTGetFileRequest(Array(new TGetFileRequest(logicalUri, null))))
    request.setTransferParameters(new TTransferParameters(TAccessPattern.TRANSFER_MODE, TConnectionType.WAN, null, new ArrayOfString(transferProtocols)))
    
    val requestStatus = stub.srmPrepareToGet(request)
    
    type SRMPrepareToGetRS = RequestStatus {
      def getArrayOfFileStatuses(): ArrayOfTGetRequestFileStatus
    }
    
    val url = complete[SRMPrepareToGetRS](requestStatus) { 
      token => 
      val status = new SrmStatusOfGetRequestRequest
      status.setRequestToken(token)
      stub.srmStatusOfGetRequest(status)
    }.getArrayOfFileStatuses.getStatusArray.head.getTransferURL
    (requestStatus.getRequestToken, url)
  }
  
  
  private def status[ R <: RequestStatusWithToken ](request: R)  = {
    import TStatusCode._
      
    if(request.getReturnStatus.getStatusCode == SRM_SUCCESS) Left(request)
    else if(request.getReturnStatus.getStatusCode == SRM_REQUEST_QUEUED || request.getReturnStatus.getStatusCode == SRM_REQUEST_INPROGRESS) Right(request.getRequestToken) 
    else throw new RuntimeException("Error interrogating the SRM server " + url + ", response was " + request.getReturnStatus.getStatusCode)
  } 
  
  private def complete[ R <: RequestStatus ](request: R with RequestStatusWithToken)(requestRequest: String => R) = 
    status(request) match {
      case Left(r) => r
      case Right(token) => 
        val rr = requestRequest(token)
        waitSuccess(rr)
    }
  
  private def waiting[ R <: RequestStatus ](r: R) =
    r.getReturnStatus.getStatusCode == SRM_REQUEST_QUEUED || r.getReturnStatus.getStatusCode == SRM_REQUEST_INPROGRESS
  
  
  private def waitSuccess[ R <: RequestStatus ](f: => R, deadLine: Long = System.currentTimeMillis + timeout): R = {
    val request = f
    if(request.getReturnStatus.getStatusCode == SRM_SUCCESS) request
    else if(waiting(request)) {
      if(System.currentTimeMillis > deadLine) throw new TimeoutException("Waiting for request to complete")
      Thread.sleep(sleepTime)
      waitSuccess(f, deadLine)
    } else throwError(request)
  }
  
  private def throwError[ R <: RequestStatus ](r: R) = throw new RuntimeException("Error interrogating the SRM server " + url + ", response was " + r.getReturnStatus.getStatusCode + " " + r.getReturnStatus.getExplanation)
  
  private def toSrmURI(absolutePath: String) =
    new org.apache.axis.types.URI("srm", null, url.getHost, url.getPort, SERVICE_PATH, "SFN="+ absolutePath, null)

  
  @transient lazy val serviceUrl = new java.net.URL(SERVICE_PROTOCOL, url.getHost, url.getPort, SERVICE_PATH, new org.globus.net.protocol.httpg.Handler) 

  private def stub(implicit credential: GlobusGSSCredentialImpl) = {
    val stub = locator.getsrm(serviceUrl)
    stub.asInstanceOf[Stub]._setProperty(GSIConstants.GSI_CREDENTIALS, credential)
    stub.asInstanceOf[Stub].setTimeout(timeout)
    stub
  }
  
  @transient private lazy val locator = {
    val provider = new SimpleProvider
    val c = new SimpleTargetedChain(new HTTPGHandler)
    provider.deployTransport("httpg", c)
    new SRMServiceLocator(provider)
  }
  
  
}

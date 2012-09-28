/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.authentication


import java.io.File
import java.net.MalformedURLException
import java.net.URI
import org.glite.voms.contact.VOMSProxyInit
import org.glite.voms.contact.VOMSRequestOptions
import org.glite.voms.contact.VOMSServerInfo
import org.glite.voms.contact.cli.VomsProxyInitClient
import org.globus.gsi.X509Credential
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl
import org.globus.util.Util
import collection.JavaConversions._
import org.ietf.jgss.GSSCredential


object VOMSAuthentication {
  
  def setCARepository(directory: File) = {
    System.setProperty("X509_CERT_DIR", directory.getAbsolutePath)
    System.setProperty("CADIR", directory.getAbsolutePath)
  }
  
}

trait VOMSAuthentication {
  
  def serverURL: String
  def voName: String
  def proxyInit(passphrase: String): VOMSProxyInit
  def proxyFile: File
  def fquan: Option[String]
  def lifeTime: Int
  
  def init(password: String) = {
    val uri = new URI(serverURL.replaceAll(" ", "%20"))
    if (uri.getHost == null)
      throw new MalformedURLException("Attribute Server has no host name: " + uri.toString)
  
    val server = new VOMSServerInfo
    server.setHostName(uri.getHost)
    server.setPort(uri.getPort)
    server.setHostDn(uri.getPath)
    server.setVoName(voName)
  
    val proxy = proxyInit(if(password.isEmpty) null else password)
    proxy.addVomsServer(server)
    proxy.setProxyOutputFile(proxyFile.getAbsolutePath)
  
    val requestOption = new VOMSRequestOptions
    requestOption.setVoName(voName)

    fquan match {
      case Some(s) => requestOption.addFQAN(s)
      case None =>
    }
 
    proxy.setProxyLifetime(lifeTime)
    requestOption.setLifetime(lifeTime)
  
    val globusProxy = proxy.getVomsProxy(List(requestOption))   
    Util.setFilePermissions(proxy.getProxyOutputFile, 600)
        
    new GlobusGSSCredentialImpl(globusProxy, GSSCredential.INITIATE_AND_ACCEPT)
  }
}

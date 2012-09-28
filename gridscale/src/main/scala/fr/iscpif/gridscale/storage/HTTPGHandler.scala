/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.iscpif.gridscale.storage

import org.apache.axis.AxisFault
import org.apache.axis.MessageContext
import org.apache.axis.components.net.BooleanHolder
import org.apache.axis.transport.http.SocketHolder
import org.globus.axis.transport.GSIHTTPSender

import java.io.IOException

/**
 * This class allow to bypass the need of declaring the URL handler for the HTTPG protocol.
 * This is really useful if you use JSAGA within tomcat or an OGSi container.
 * 
 * @author JÃ©rÃ´me Revillard
 *
 */
class HTTPGHandler extends GSIHTTPSender {

  /* (non-Javadoc)
   * @see org.apache.axis.transport.http.HTTPSender#invoke(org.apache.axis.MessageContext)
   */
  override def invoke( msgContext: MessageContext) = {
    val url = msgContext.getStrProp(MessageContext.TRANS_URL)
    if (!(url.startsWith("httpg://"))) {
      throw new AxisFault(classOf[GSIHTTPSender].getCanonicalName + " can only be used with the httpg protocol!",
                          new IOException("Invalid protocol"));
    }
    msgContext.setProperty(MessageContext.TRANS_URL,
                           msgContext.getStrProp(MessageContext.TRANS_URL).replaceFirst("httpg", "http"))
    super.invoke(msgContext)
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.globus.axis.transport.GSIHTTPSender#getSocket(org.apache.axis.transport.http.SocketHolder,
   * org.apache.axis.MessageContext, java.lang.String, java.lang.String, int, int, java.lang.StringBuffer,
   * org.apache.axis.components.net.BooleanHolder)
   */
  override protected def getSocket(
    sockHolder: SocketHolder,  
    msgContext: MessageContext, 
    protocol: String, 
    host: String,
    port: Int,
    timeout: Int,
    otherHeaders: StringBuffer, 
    useFullURL: BooleanHolder) = {
    super.getSocket(sockHolder, msgContext, "httpg", host, port, timeout, otherHeaders, useFullURL)
  }
}

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
  override def invoke(msgContext: MessageContext) = {
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

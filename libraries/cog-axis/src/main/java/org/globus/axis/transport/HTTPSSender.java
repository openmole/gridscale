/*
 * Copyright 1999-2006 University of Chicago
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.globus.axis.transport;

import java.io.IOException;
import org.apache.axis.MessageContext;
import org.apache.axis.components.net.BooleanHolder;
import org.apache.axis.transport.http.SocketHolder;
import org.apache.axis.transport.http.HTTPSender;

import org.globus.gsi.gssapi.net.GssSocket;

/**
 * This is meant to be used on a SOAP Client to call a SOAP server.
 * <BR><I>This code is based on Axis HTTPSender.java code.</I>
 */
public class HTTPSSender extends HTTPSender {

    protected void getSocket(SocketHolder sockHolder,
                             MessageContext msgContext,
                             String protocol,
                             String host, int port, int timeout, 
                             StringBuffer otherHeaders, 
                             BooleanHolder useFullURL)
        throws Exception {

        if (!protocol.equalsIgnoreCase("https")) {
	    throw new IOException("Invalid protocol");
	}

        int lport = (port == -1) ? 8443 : port;

        SSLContextHelper helper = new SSLContextHelper(msgContext,
                                                       host,
                                                       lport);
                                                       
        super.getSocket(sockHolder, msgContext, "http", host,
                        lport, timeout, otherHeaders, useFullURL);

        
        GssSocket gsiSocket = helper.wrapSocket(sockHolder.getSocket());

        sockHolder.setSocket(gsiSocket);
    }
    
}

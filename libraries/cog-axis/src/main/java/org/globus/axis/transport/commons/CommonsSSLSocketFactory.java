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
package org.globus.axis.transport.commons;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSCredential;

import org.globus.net.SocketFactory;

import org.globus.gsi.GSIConstants;
import org.globus.gsi.TrustedCertificates;
import org.globus.gsi.gssapi.auth.Authorization;
import org.globus.axis.transport.GSIHTTPTransport;
import org.globus.axis.transport.SSLContextHelper;
import org.globus.common.ChainedIOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CommonsSSLSocketFactory implements ProtocolSocketFactory {
    
    private SocketFactory defaultSocketFactory =
        SocketFactory.getDefault();
    
    public Socket createSocket(String host, 
                               int port, 
                               InetAddress localAddress, 
                               int localPort)
        throws IOException, UnknownHostException {
        throw new IOException("not supported");
    }
    
    public Socket createSocket(String host, 
                               int port) 
        throws IOException, UnknownHostException {
        throw new IOException("not supported");
    }
    
    public Socket createSocket(String host, 
                               int port, 
                               InetAddress localAddress, 
                               int localPort,
                               HttpConnectionParams params)
        throws IOException, UnknownHostException, ConnectTimeoutException {
        SSLContextHelper helper = null;
        Authorization authz = null;
        Boolean anonymous = null;
        GSSCredential cred = null;
        Integer protection = null;
        TrustedCertificates trustedCerts = null;

        if (params != null) {
            authz = (Authorization)params.getParameter(
                                      GSIHTTPTransport.GSI_AUTHORIZATION);

            anonymous = (Boolean)params.getParameter(
                                      GSIHTTPTransport.GSI_ANONYMOUS);

            cred = (GSSCredential)params.getParameter(
                                      GSIHTTPTransport.GSI_CREDENTIALS);

            protection = (Integer)params.getParameter(
                                      GSIConstants.GSI_TRANSPORT);

            trustedCerts = (TrustedCertificates)params
                .getParameter(GSIHTTPTransport.TRUSTED_CERTIFICATES);
        }

        try {
            helper = new SSLContextHelper(host, port,
                                          authz, anonymous, cred, protection,
                                          trustedCerts);
        } catch (GSSException e) {
            throw new ChainedIOException(
                  "Failed to initialize security context", e);
        }

        Socket socket = defaultSocketFactory.createSocket(host, 
                                                          port,
                                                          localAddress,
                                                          localPort);
        return helper.wrapSocket(socket);
    }
    
}

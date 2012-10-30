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

import java.net.Socket;

import org.apache.axis.MessageContext;

import org.gridforum.jgss.ExtendedGSSContext;
import org.gridforum.jgss.ExtendedGSSManager;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import org.globus.axis.util.Util;
import org.globus.gsi.GSIConstants;
import org.globus.gsi.TrustedCertificates;
import org.globus.gsi.gssapi.GSSConstants;
import org.globus.gsi.gssapi.auth.Authorization;
import org.globus.gsi.gssapi.auth.HostOrSelfAuthorization;
import org.globus.gsi.gssapi.net.GssSocket;
import org.globus.gsi.gssapi.net.GssSocketFactory;

/**
 * This is meant to be used on a SOAP Client to call a SOAP server.
 * <BR><I>This code is based on Axis HTTPSender.java code.</I>
 */
public class SSLContextHelper {

    private String host;
    private int port;

    private Authorization myAuth;
    private ExtendedGSSContext myContext;

    public SSLContextHelper(MessageContext msgContext, 
                            String host, int port) 
        throws GSSException {
        Authorization auth = (Authorization)Util.getProperty(msgContext, 
                                         GSIHTTPTransport.GSI_AUTHORIZATION);
        Boolean anonymous =  (Boolean)Util.getProperty(msgContext, 
                                         GSIHTTPTransport.GSI_ANONYMOUS);
        GSSCredential cred = (GSSCredential)Util.getProperty(msgContext, 
                                         GSIHTTPTransport.GSI_CREDENTIALS);
        Integer protection = (Integer)Util.getProperty(msgContext,
                                         GSIConstants.GSI_TRANSPORT);
	TrustedCertificates trustedCerts = 
            (TrustedCertificates)Util.getProperty(msgContext, 
                                                  GSIHTTPTransport
                                                  .TRUSTED_CERTIFICATES);
        init(host, port,
             auth, anonymous, cred, protection, trustedCerts);
    }

    public SSLContextHelper(String host, int port,
                            Authorization auth,
                            Boolean anonymous,
                            GSSCredential cred,
                            Integer protection,
                            TrustedCertificates trustedCerts)
        throws GSSException {

        init(host, port, 
             auth, anonymous, cred, protection, trustedCerts);
    }

    protected void init(String host, int port,
                        Authorization auth,
                        Boolean anonymous,
                        GSSCredential cred,
                        Integer protection,
                        TrustedCertificates trustedCerts)
        throws GSSException {

        this.host = host;
        this.port = port;
        
        if (auth == null) {
            auth = HostOrSelfAuthorization.getInstance();
        }

        GSSManager manager = ExtendedGSSManager.getInstance();
        
        boolean anon = false;
        
        if (anonymous != null && anonymous.equals(Boolean.TRUE)) {
            anon = true;
        }

        if (anon) {
            GSSName name = manager.createName((String)null,
                                              (Oid)null);
            cred = manager.createCredential(
                name,
                GSSCredential.DEFAULT_LIFETIME,
                (Oid)null,
                GSSCredential.INITIATE_ONLY);
        }
        
        // Expected name is null since delegation is never
        // done. Custom authorization is invoked after handshake is finished.
        ExtendedGSSContext context =(ExtendedGSSContext)manager
            .createContext(null,
                           GSSConstants.MECH_OID,
                           cred,
                           GSSContext.DEFAULT_LIFETIME);

        if (anon) {
            context.requestAnonymity(true);
        }
                        
        context.setOption(GSSConstants.GSS_MODE, GSIConstants.MODE_SSL);

        if (GSIConstants.ENCRYPTION.equals(protection)) {
            context.requestConf(true);
        } else {
            context.requestConf(false);
        }
        
        if (trustedCerts != null) {
            context.setOption(GSSConstants.TRUSTED_CERTIFICATES, trustedCerts);
        }

        this.myContext = context;
        this.myAuth = auth;
    }

        
    public GssSocket wrapSocket(Socket socket) {
        
        GssSocketFactory factory = GssSocketFactory.getDefault();
        
        GssSocket gsiSocket =
            (GssSocket)factory.createSocket(socket, 
                                            this.host,
                                            this.port,
                                            this.myContext);
        
        gsiSocket.setAuthorization(this.myAuth);
        
        return gsiSocket;
    }
    
}

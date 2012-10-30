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

import org.gridforum.jgss.ExtendedGSSContext;
import org.gridforum.jgss.ExtendedGSSManager;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import org.globus.axis.util.Util;
import org.globus.gsi.GSIConstants;
import org.globus.gsi.TrustedCertificates;
import org.globus.gsi.gssapi.GSSConstants;
import org.globus.gsi.gssapi.net.GssSocketFactory;
import org.globus.gsi.gssapi.net.GssSocket;
import org.globus.gsi.gssapi.auth.Authorization;
import org.globus.gsi.gssapi.auth.GSSAuthorization;
import org.globus.gsi.gssapi.auth.HostAuthorization;

/**
 * This is meant to be used on a SOAP Client to call a SOAP server.
 * <BR><I>This code is based on Axis HTTPSender.java code.</I>
 */
public class GSIHTTPSender extends HTTPSender {

    protected void getSocket(SocketHolder sockHolder,
                             MessageContext msgContext,
                             String protocol,
                             String host, int port, int timeout, 
                             StringBuffer otherHeaders, 
                             BooleanHolder useFullURL)
        throws Exception {

        if (!protocol.equalsIgnoreCase("httpg")) {
	    throw new IOException("Invalid protocol");
	}

	GSSCredential cred = null;
	Authorization auth = null;
	String mode = null;

	auth = (Authorization)Util.getProperty(msgContext, 
					       GSIHTTPTransport.GSI_AUTHORIZATION);

	mode = (String)Util.getProperty(msgContext, 
					GSIHTTPTransport.GSI_MODE);


	
	if (auth == null) {
	    auth = HostAuthorization.getInstance();
	}
	
	if (mode == null) {
	    mode = GSIHTTPTransport.GSI_MODE_NO_DELEG;
	}
        
	GSSManager manager = ExtendedGSSManager.getInstance();
	
	ExtendedGSSContext context = null;
	
        Boolean anonymous = (Boolean) Util.getProperty(
            msgContext, GSIHTTPTransport.GSI_ANONYMOUS);
        
        if (anonymous != null && anonymous.equals(Boolean.TRUE)) {
            GSSName name = manager.createName((String)null,
                                              (Oid)null);
            cred = manager.createCredential(
                name,
                GSSCredential.DEFAULT_LIFETIME,
                (Oid)null,
                GSSCredential.INITIATE_ONLY);
        } else {            
            cred = (GSSCredential)Util.getProperty(
                msgContext, GSIHTTPTransport.GSI_CREDENTIALS);
        }
        
        GSSName expectedName = null;
        if (auth instanceof GSSAuthorization) {
            GSSAuthorization gssAuth = (GSSAuthorization)auth;
            expectedName = gssAuth.getExpectedName(cred, host);
        }
                                                   
	context = 
	    (ExtendedGSSContext)manager.createContext(expectedName, 
						      GSSConstants.MECH_OID,
						      cred,
						      GSSContext.DEFAULT_LIFETIME);
	
	if (mode.equalsIgnoreCase(GSIHTTPTransport.GSI_MODE_LIMITED_DELEG)) {
	    context.requestCredDeleg(true);
	    context.setOption(GSSConstants.DELEGATION_TYPE,
			      GSIConstants.DELEGATION_TYPE_LIMITED);
	} else if (mode.equalsIgnoreCase(GSIHTTPTransport.GSI_MODE_FULL_DELEG)) {
	    context.requestCredDeleg(true);
	    context.setOption(GSSConstants.DELEGATION_TYPE,
			      GSIConstants.DELEGATION_TYPE_FULL);
	} else if (mode.equalsIgnoreCase(GSIHTTPTransport.GSI_MODE_NO_DELEG)) {
	    context.requestCredDeleg(false);
	} else if (mode.equalsIgnoreCase(GSIHTTPTransport.GSI_MODE_SSL)) {
	    context.setOption(GSSConstants.GSS_MODE,
			      GSIConstants.MODE_SSL);
	} else {
	    throw new Exception("Invalid GSI MODE: " + mode);
	}

	TrustedCertificates trustedCerts = 
            (TrustedCertificates)Util.getProperty(msgContext, 
                                                  GSIHTTPTransport
                                                  .TRUSTED_CERTIFICATES);
        if (trustedCerts != null) {
            context.setOption(GSSConstants.TRUSTED_CERTIFICATES, 
                              trustedCerts);
        }

        Boolean authzRequiredWithDelegation = 
            (Boolean)Util.getProperty(msgContext, 
                                      GSIConstants
                                      .AUTHZ_REQUIRED_WITH_DELEGATION);
        if (authzRequiredWithDelegation != null) {
            context.setOption(GSSConstants.AUTHZ_REQUIRED_WITH_DELEGATION,
                              authzRequiredWithDelegation);
        }

	GssSocketFactory factory = GssSocketFactory.getDefault();
        
        int lport = (port == -1) ? 8443 : port;
        super.getSocket(sockHolder, msgContext, "http", host,
                        lport, timeout, otherHeaders, useFullURL);

	GssSocket gsiSocket = 
	    (GssSocket)factory.createSocket(sockHolder.getSocket(), 
                                            host, lport, context);
        
	gsiSocket.setAuthorization(auth);
	
        sockHolder.setSocket(gsiSocket);
    }
    
}

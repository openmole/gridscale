/*
Copyright (c) Members of the EGEE Collaboration. 2004. 
See http://www.eu-egee.org/partners/ for details on the copyright
holders.  

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 

    http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
*/
package org.glite.security.util.axis;

import org.apache.axis.InternalException;
import org.apache.axis.MessageContext;
import org.apache.axis.transport.http.HTTPConstants;

import org.apache.log4j.Logger;

import org.glite.security.SecurityContext;

import java.security.cert.X509Certificate;
import javax.servlet.ServletRequest;



/**
 * DOCUMENT ME!
 *
 * @author mulmo
 *
 * Created on Oct 11, 2004
 */
public class InitSecurityContext {
    /** DOCUMENT ME! */
    protected static Logger log = Logger.getLogger(InitSecurityContext.class);

    /**
     * Initializes the SecurityContext from a {@link
     * org.apache.axis.MessageContext}.
     *
     * @see org.apache.axis.Handler#invoke(MessageContext)
     */
    public static void init() {
        // Try to initialize the client's certificate chain from the servlet
        // container's (e.g. tomcat) context.
        MessageContext messageContext = MessageContext.getCurrentContext();

        if (messageContext == null) {
            throw new InternalException(
                "No MessageContext found, method probably not called inside a web service");
        }

        ServletRequest req = (ServletRequest) messageContext.getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);

        if (req == null) {
            log.warn("SOAP Authorization: MC_HTTP_SERVLETREQUEST is null");
            initClearSC();
        } else {
            initSC(req);
        }
    }

    /**
     * Sets up the client's credentials. This method sets the current 
     * org.glite.java.security.SecurityContext to a new instance and
     * initializes it from the client's certificate. 
     *
     * <p>
     * If the certificate is invalid, or there is some other problem with the
     * client's credentials, then the distinguished name and CA will be set to
     * <code>null</code>.
     * @param req The servlet request to load the certificate chain from.
     */
    public static void initSC(ServletRequest req) {
        log.debug("Creating a new security context");

        SecurityContext sc = new SecurityContext();
        SecurityContext.setCurrentContext(sc);

        try {
            // Interpret the client's certificate.
            X509Certificate[] cert = (X509Certificate[]) req.getAttribute(
                    "javax.servlet.request.X509Certificate");

            /* Client certificate found. */
            sc.setClientCertChain(cert);

            // get and store the IP address of the other party
            String remote = req.getRemoteAddr();
            sc.setRemoteAddr(remote);

            // trigger the initialization of the certificate stuff in request.
            req.getAttribute("javax.servlet.request.key_size");

            // get the session id
            String sslId = (String) req.getAttribute("javax.servlet.request.ssl_session");
            sc.setSessionId(sslId);

            log.info("Connection from \"" + remote + "\" by " + sc.getClientDN());
        } catch (Exception e) {
            log.warn("Exception during certificate chain retrieval: " + e);

            // We swallow the exception and continue processing.
        }
    }

    /**
     * Initialize a clear security context, which will fail on all security
     * checks. It is intended for non-authenticated requests.
     */
    public static void initClearSC() {
        log.info("Clearing the security context");

        SecurityContext sc = new SecurityContext();
        SecurityContext.setCurrentContext(sc);
    }
}

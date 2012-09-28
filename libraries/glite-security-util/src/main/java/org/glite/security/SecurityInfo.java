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
package org.glite.security;

import java.security.cert.X509Certificate;

import org.glite.security.util.X500Principal;


/**
 * An interface from which an external application can get information from
 * underlying authentication and authorization processes.
 *
 * @see SecurityInfoContainer
 *
 * @author mulmo
 */
public interface SecurityInfo {
    /**
     * @return X509Certificate The identity certificate of the authenticated client
     */
    public X509Certificate getClientCert();

    /**
     * @return X509Certificate[] The client's certificate chain
     */
    public X509Certificate[] getClientCertChain();

    /**
     * Returns the name of the authenticated client. Typically, this
     * is the Subject Distinguished Name of the client certificate.
     *
     * @return String The name of the authenticated client.
     */
    public String getClientName();

    /**
     * Returns the name of the authenticated client in X500 format. Typically, this
     * is the Subject Distinguished Name of the client certificate.
     *
     * @return String The name of the authenticated client.
     */
    public String getClientX500Name();

    /**
     * Returns the Principal of the authenticated client in X500 format. Typically, this
     * is the Subject Distinguished Name of the client certificate.
     *
     * @return X500Principal The Principal of the authenticated client.
     */
    public X500Principal getClientX500Principal();

    /**
     * Returns the Issuer Distinguished Name of the client certificate.
     * @return The issuer name as a String.
     *
     */
    public String getIssuerName();
    
    /**
     * Returns the IP address of the other party.
     * @return The remote address as a String.
     *
     */
    public String getRemoteAddr();
    
    /**
     * Returns the SSL session ID used for this connection.
     * @return The session id as a String.
     *
     */
    public String getSessionId();
}

/*
 * Copyright (c) Members of the EGEE Collaboration. 2004. See
 * http://www.eu-egee.org/partners/ for details on the copyright holders.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.glite.security;

import org.apache.log4j.Logger;

import org.glite.security.util.CertUtil;
import org.glite.security.util.DNHandler;
import org.glite.security.util.DN;
import org.glite.security.util.X500Principal;
import java.security.Principal;
import java.security.cert.X509Certificate;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * A context class in which security-related information from the authentication
 * and authorization process is collected. <br>
 * SecurityContexts can be stored on a per-thread basis using the static method
 * <code>setCurrentContext()</code>, and retrieved with
 * <code>getCurrentContext()</code>. <br>
 * This class is for internal use. External users should make use of the
 * <code>SecurityInfo</class> rendering of this class.
 * <br>
 * 
 * @see SecurityInfo
 * @see SecurityInfoContainer
 * @author mulmo
 * 
 */
@SuppressWarnings("deprecation")
public class SecurityContext extends Properties implements SecurityInfo {
    /**
	 * UID
	 */
	private static final long serialVersionUID = -7396219279112154202L;

	/**
	 * The logging facility
	 */
	private static final Logger LOGGER = Logger.getLogger(SecurityContext.class);

    /** Thread local storage for locating the active security context. */
    private static ThreadLocal<SecurityContext> theSecurityContexts = new ThreadLocal<SecurityContext>();

    /**
     * @see #getClientCertChain()
     */
    public static final String CERT_CHAIN = "org.glite.security.certchain";

     /**
     * The label for the client identity certificate.
     * 
     * @see #getClientCert()
     * @see #setClientCert(X509Certificate)
     */
    public static final String CLIENT_CERT = "org.glite.security.clientcert";

    /**
     * The label for the client name.
     * 
     * @see #getClientName()
     * @see #setClientName(String)
     */
    public static final String CLIENT_NAME = "org.glite.security.clientname";

    /**
     * The label for the client name.
     * 
     * @see #getClientDN()
     * @see #setClientDN(DN)
     */
    public static final String CLIENT_DN = "org.glite.security.clientdn";

    /**
     * The label for the client name.
     * 
     * @see #getClientX500Name()
     * @see #setClientX500Name(String)
     */
    public static final String CLIENT_X500_NAME = "org.glite.security.clientX500name";

    /**
     * The label for the client name.
     * 
     * @see #getClientX500Principal()
     * @see #setClientX500Principal(X500Principal)
     */
    public static final String CLIENT_X500_PRINCIPAL = "org.glite.security.clientX500Principal";

    /**
     * The label for the issuer name.
     * 
     * @see #getIssuerName()
     * @see #setIssuerName(String)
     */
    public static final String ISSUER_NAME = "org.glite.security.issuername";

    /**
     * The label for the issuer name.
     * 
     * @see #getIssuerName()
     * @see #setIssuerName(String)
     */
    public static final String ISSUER_DN = "org.glite.security.issuerdn";

    /**
     * The label for UnverifiedCertChain.
     * 
     * @see #getUnverifiedCertChain
     * @see #setUnverifiedCertChain
     */
    public static final String UNVERIFIED_CERT_CHAIN = "org.glite.security.trustmanager.unverifiedchain";

    /**
     * The label for peer CA Principal list.
     * 
     * @see #getPeerCas
     * @see #setPeerCas
     */
    public static final String PEER_CAS = "org.glite.security.trustmanager.peercas";

    /**
     * The label for the ip address of the other party.
     */
    public static final String REMOTE_ADDR = "org.glite.security.trustmanager.remoteaddr";

    /**
     * The label for the SSL session Id for this connection.
     */
    public static final String SESSION_ID = "org.glite.security.trustmanager.sessionid";

    /**
     * The constructor.
     * 
     * @see java.util.Properties#Properties()
     */
    public SecurityContext() {
        super();
    }

    /**
     * @return SecurityContext the SecurityContext associated with the current
     *         thread.
     * @see #setCurrentContext(SecurityContext)
     */
    public static SecurityContext getCurrentContext() {
        return theSecurityContexts.get();
    }

    /**
     * @param sc the SecurityContext associated with the current thread.
     * @see #getCurrentContext()
     */
    public static void setCurrentContext(SecurityContext sc) {
        theSecurityContexts.set(sc);
    }

    /**
     * Clears any set SecurityContext associated with the current thread. This
     * is identical to <code>SecurityContext.setCurrentContext(null)</code>.
     */
    public static void clearCurrentContext() {
        theSecurityContexts.set(null);
    }

    /**
     * This method also automatically sets the client name, the issuer name, and
     * validity period.
     * 
     * @param clientCert The identity certificate of the authenticated client
     * 
     * @see #CLIENT_CERT
     * @see #getClientCert()
     * @see #setClientName(String)
     * @see #setIssuerName(String)
     */
    public void setClientCert(X509Certificate clientCert) {
        put(CLIENT_CERT, clientCert);
        DN issuerDN = DNHandler.getIssuer(clientCert);
        setIssuerDN(issuerDN);
        setIssuerName(issuerDN.getRFC2253());
        DN clientDN = DNHandler.getSubject(clientCert);
        setClientDN(clientDN);
        setClientName(clientDN.getRFC2253());        
        setClientX500Name(clientDN.getX500());
        X500Principal principal = new X500Principal();
        principal.setName(clientDN);
        setClientX500Principal(principal);
    }

    /**
     * @return X509Certificate The identity certificate of the authenticated
     *         client
     * @see #CLIENT_NAME
     * @see #setClientCert(X509Certificate)
     */
    public X509Certificate getClientCert() {
        return (X509Certificate) get(CLIENT_CERT);
    }

    /**
     * @param clientName The name of the authenticated client
     * @see #CLIENT_NAME
     * @see #getClientName()
     * @deprecated produces DN in wrong order, rather use DN methods.
     */
    public void setClientName(String clientName) {
        put(CLIENT_NAME, clientName);
    }

    /**
     * @return String The name of the authenticated client
     * @see #CLIENT_NAME
     * @see #setClientName(String)
     * @deprecated produces DN in wrong order, rather use DN methods.
     */
    public String getClientName() {
        return getProperty(CLIENT_NAME);
    }

    /**
     * @param clientDN The name of the authenticated client
     * @see #CLIENT_DN
     * @see #getClientDN()
     */
    public void setClientDN(DN clientDN) {
        put(CLIENT_DN, clientDN);
    }

    /**
     * @return String The name of the authenticated client
     * @see #CLIENT_DN
     * @see #setClientDN(DN)
     */
    public DN getClientDN() {
        return (DN) get(CLIENT_DN);
    }

    /**
     * @param clientName The name of the authenticated client
     * @see #CLIENT_X500_NAME
     * @see #getClientX500Name()
     */
    public void setClientX500Name(String clientName) {
        put(CLIENT_X500_NAME, clientName);
    }

    /**
     * @return String The name of the authenticated client
     * @see #CLIENT_X500_NAME
     * @see #setClientX500Name(String)
     */
    public String getClientX500Name() {
        return getProperty(CLIENT_X500_NAME);
    }

    /**
     * @param clientPrincipal The name of the authenticated client
     * @see #CLIENT_X500_NAME
     * @see #getClientX500Name()
     */
    public void setClientX500Principal(X500Principal clientPrincipal) {
        put(CLIENT_X500_PRINCIPAL, clientPrincipal);
    }

    /**
     * @return X500Principal The Principal of the authenticated client
     * @see #CLIENT_X500_PRINCIPAL
     * @see #setClientX500Principal(X500Principal)
     */
    public X500Principal getClientX500Principal() {
        return (X500Principal) get(CLIENT_X500_PRINCIPAL);
    }

    /**
     * @param issuerName The name of the authenticated client
     * @see #ISSUER_NAME
     * @see #getIssuerName()
     * @deprecated produces DN in wrong order, rather use DN methods.
     */
    public void setIssuerName(String issuerName) {
        put(ISSUER_NAME, issuerName);
    }

    /**
     * @return String The issuer name
     * @see #ISSUER_NAME
     * @see #setIssuerName(String)
     * @deprecated produces DN in wrong order, rather use DN methods.
     */
    public String getIssuerName() {
        return getProperty(ISSUER_NAME);
    }

    /**
     * @param issuerDN The name of the authenticated client
     * @see #ISSUER_NAME
     * @see #getIssuerName()
     */
    public void setIssuerDN(DN issuerDN) {
        put(ISSUER_DN, issuerDN);
    }

    /**
     * @return String The issuer name
     * @see #ISSUER_NAME
     * @see #setIssuerName(String)
     */
    public DN getIssuerDN() {
        return (DN)get(ISSUER_DN);
    }

    /**
     * This method also automatically sets the client name, the issuer name,
     * validity period.
     * 
     * @param certChain The client's certificate chain
     * @see #CERT_CHAIN
     * @see #getClientCertChain()
     * @see #setClientCert(X509Certificate)
     */
    public void setClientCertChain(X509Certificate[] certChain) {
        put(CERT_CHAIN, certChain);

        int i = CertUtil.findClientCert(certChain);

        if (i < 0) {
            LOGGER.warn("SecurityContext: No client certificate found in the supplied certificate chain");

            return;
        }

        setClientCert(certChain[i]);
    }

    /**
     * @return X509Certificate[] The client's certificate chain
     * @see #CERT_CHAIN
     * @see #setClientCertChain(X509Certificate[])
     */
    public X509Certificate[] getClientCertChain() {
        return (X509Certificate[]) get(CERT_CHAIN);
    }

    /**
     * 
     * @param certChain The unverified certificate chain
     * @see #UNVERIFIED_CERT_CHAIN
     */
    public void setUnverifiedCertChain(X509Certificate[] certChain) {
        put(UNVERIFIED_CERT_CHAIN, certChain);
    }

    /**
     * @return X509Certificate[] The unverified certificate chain
     * @see #UNVERIFIED_CERT_CHAIN
     * @see #setUnverifiedCertChain(X509Certificate[])
     */
    public X509Certificate[] getUnverifiedCertChain() {
        return (X509Certificate[]) get(UNVERIFIED_CERT_CHAIN);
    }

    /**
     * 
     * @param principals The list of accepted CAs from the peer
     * @see #PEER_CAS
     */
    public void setPeerCas(Principal[] principals) {
        put(PEER_CAS, principals);
    }

    /**
     * @return Principal[] The list of accepted CAs from the peer
     * @see #PEER_CAS
     * @see #setPeerCas(Principal[])
     */
    public Principal[] getPeerCas() {
        return (Principal[]) get(PEER_CAS);
    }

    /**
     * Sets the IP address of the other party.
     * 
     * @param remoteAddr the IP address of the other party to save
     */
    public void setRemoteAddr(String remoteAddr) {
        put(REMOTE_ADDR, remoteAddr);
    }

    /**
     * @return the IP address of the other party.
     * 
     */
    public String getRemoteAddr() {
        return getProperty(REMOTE_ADDR);
    }

    /**
     * Sets the IP address of the other party.
     * 
     * @param sessionId the IP address of the other party to save
     */
    public void setSessionId(String sessionId) {
        put(SESSION_ID, sessionId);
    }

    /**
     * @return the SSL session ID used for this connection.
     * 
     */
    public String getSessionId() {
        return getProperty(SESSION_ID);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.Hashtable#toString()
     */
    @SuppressWarnings("unchecked")
	public synchronized String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("SecurityContext:\n");

        for (Iterator i = entrySet().iterator(); i.hasNext();) {
            Map.Entry entry = (Map.Entry) i.next();
            sb.append("  " + entry.getKey() + " : " + entry.getValue() + "\n");
        }

        return sb.toString();
    }
}

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

package org.glite.security.trustmanager;

import org.apache.log4j.Logger;

import org.glite.security.SecurityContext;

import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.X509Certificate;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Vector;


/** The CRLFileTrustManager meks the decisions whether the
 * certificate chain is trusted or not. Before use
 * the instance of this calss has to be initialized by using
 * either of the init methods.
 *
 * @author  Joni Hahkala
 * Created on December 3, 2001, 2:57 PM
 */
public class CRLFileTrustManager implements javax.net.ssl.X509TrustManager {
    /**
     * The logging facility.
     */
    private static final Logger LOGGER = Logger.getLogger(CRLFileTrustManager.class.getName());
    /**
     * The validator to be used in the validation of the certificates.
     */
    ProxyCertPathValidator validator = null;

    /**
     * The string to use for finding the CRL files.
     */
    String crlFiles = null;
    /**
     * The vector of trust anchors.
     */
    Vector trustAnchors = null;
    /**
     * The properties used for configuring.
     */
    Properties config = null;

    /** Creates new CRLTrustManager
     * @param trustAnchors The trustanchors used for ceritificate path checking.
     * @throws CertificateException if certificate handling fails.
     * @throws NoSuchProviderException if bouncycastle provider was not found.
     */
    public CRLFileTrustManager(Vector trustAnchors) throws CertificateException, NoSuchProviderException {
        //        LOGGER.setLevel(Level.INFO);
        // create the validator
        validator = new ProxyCertPathValidator(trustAnchors);
    }

    /**
     * Sets the checker to use for the check against CRLs.
     *
     * @param cheker the crl checker to use.
     */
    public void setChecker(PKIXCertPathChecker cheker) {
        validator.setCRLChecker((CRLCertChecker) cheker);
    }

    /**
     * This method checks that the certificate path is a valid client certificate path. Currently the signatures and
     * subject lines are checked so that the path is valid and leads to one of the CA certs given in the constructor.
     * The certs are also checked against the CRLs and that they have not expired. This method behaves identically to
     * the server version of this method. No checks are made that this is a client cert. If the cert path fails the
     * check an exception is thrown.
     * 
     * @param x509Certificate The certificate path to check. It may contain the CA cert or not. If it contains the CA
     *            cert, the CA cert is discarded and the one given in the constructor is used. The array has the actual
     *            certificate in the index 0 and the CA or the CA signed cert as the last cert.
     * @param authType Defines the authentication type, but is not used.
     * @throws CertificateException Thrown if the certificate path is invalid.
     */
    public void checkClientTrusted(java.security.cert.X509Certificate[] x509Certificate, String authType) throws java.security.cert.CertificateException {
        LOGGER.debug("CRLFileTrustManager::checkClientTrusted");
        
        if(x509Certificate == null){
            throw new CertificateException("No certificae given, unable to verify!");
        }

        if (validator == null) {
            LOGGER.fatal("Trying to use uninitialized TrustManager");
            throw new CertificateException("Trying to use uninitialized TrustManager");
        }

        SecurityContext sc = SecurityContext.getCurrentContext();

        if (sc == null) {
            sc = new SecurityContext();
            SecurityContext.setCurrentContext(sc);
        }

        sc.setUnverifiedCertChain(x509Certificate);

        List<X509Certificate> certs = Arrays.asList(x509Certificate);
        Iterator<X509Certificate> iter = certs.iterator();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("The user cert chain is:");

            while (iter.hasNext()) {
                LOGGER.debug(iter.next());
            }
        }
        
        try {
            // CertPath path = certFactory.generateCertPath(certs);
            validator.check(x509Certificate);

            // have a side effect: set the valid chain in the security context
            sc.setClientCertChain(x509Certificate);

            // sc.setTrustManager(this);
            LOGGER.info("Client " + x509Certificate[0].getSubjectDN() + " accepted");
        } catch (Exception e) {
            LOGGER.info("Client certificate validation failed for " + x509Certificate[0].getSubjectDN() + " reason: "
                    + e.getMessage());
            LOGGER.debug(certs);

            // throw the certificate exception as is, others change into cert exception.
            if (e instanceof CertificateException) {
                throw (CertificateException) e;
            }

            CertificateException newExp = new java.security.cert.CertificateException(e.getMessage(), e);
            throw newExp;
        }
    }

    /**
     * This method checks that the certificate path is a valid server certificate path. Currently the signatures and
     * subject lines are checked so that the path is valid and leads to one of the CA certs given in the constructor.
     * The certs are also checked against the CRLs and that they have not expired. This method behaves identically to
     * the client version of this method. No checks are made that this is a server cert. If the cert path fails the
     * check an exception is thrown.
     * 
     * @param x509Certificate The certificate path to check. It may contain the CA cert or not. If it contains the CA
     *            cert, the CA cert is discarded and the one given in the constructor is used. The array has the actual
     *            certificate in the index 0 and the CA or the CA signed cert as the last cert.
     * @param authType Defines the authentication type, but is not used.
     * @throws CertificateException Thrown if the certificate path is invalid.
     */
    public void checkServerTrusted(java.security.cert.X509Certificate[] x509Certificate, String authType) throws CertificateException {
        LOGGER.debug("Trustmanager is validating a server");
        LOGGER.debug("The cert chain is: ");

        checkClientTrusted(x509Certificate, authType);
    }

    /** This method returns an array containing all the CA
     * certs.
     * @return An array containig all the CA certs is reaurned.
     */
    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        LOGGER.debug("getAcceptedIssuers");

        java.security.cert.X509Certificate[] cas = validator.getCACerts();

        LOGGER.debug("returning " + cas.length + " ca certs");

        return cas;
    }
}

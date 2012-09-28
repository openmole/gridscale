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

import java.math.BigInteger;

import java.security.Principal;
import java.security.cert.CertPathValidatorException;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;

import java.util.Iterator;
import java.util.Vector;


/** The CRLCertChecker is used to check a certificate agaisnt Certificate Revocation Lists (CRLs) and
 * thus determine if the certificate is revoked or not.
 *
 * @author  Joni Hahkala
 * Created on April 11, 2002, 4:24 PM
 */
public class CRLCertChecker extends java.security.cert.PKIXCertPathChecker {
    /** the logger to use when logging. */
    private static final Logger LOGGER = Logger.getLogger(CRLCertChecker.class.getName());

    /** the list of crls. */
    private Vector m_crls;

    /** switch that indicates if CRLs are required or just used when available.
     *  when they are required and a CA doesn't have a CRL all certs from that CA are
     *  refused, when they are not required and a CA doesn't have a CRL all othewice
     *  valid certs are accepted. */
    private boolean m_crlRequired = false;

    /** Creates a new instance of CRLCertChecker and sets the CRLs to use.
     * @param crls the Vector of CRLs to use for checking.
     * @param crlRequired defines if the crls are required or just used if available.
     */
    public CRLCertChecker(Vector crls, boolean crlRequired) {
        m_crls = crls;
        m_crlRequired = crlRequired;

        //        LOGGER.setLevel(Level.DEBUG);
    }

    /** Checks that the certificate is not revoked.
     *
     * @param certificate The certificate to check.
     * @param collection The collection of unresolved certificate extensions. Not used.
     * @throws CertPathValidatorException Thrown if the certificate is revoked or otherwise malformed or unreadable.
     */
    public void check(java.security.cert.Certificate certificate, java.util.Collection collection)
        throws CertPathValidatorException {
        //        LOGGER.debug("CRLCertChecker.check");
        Iterator iter = m_crls.iterator();

        if (!(certificate instanceof X509Certificate)) { // accept only X509Certificates
            LOGGER.error("Error: non-X509 certificate given as an argument");
            throw new CertPathValidatorException("Error: non-X509 certificate given as an argument");
        }

        X509Certificate cert = (X509Certificate) certificate;

        Principal certIssuer = cert.getIssuerDN();
        BigInteger serial = cert.getSerialNumber();

        try {
            LOGGER.debug("Checking certificate " + cert.getSubjectDN().getName() + " with serial "
                + serial);

            while (iter.hasNext()) { // go throught the CRLs

                X509CRL crl = (X509CRL) iter.next();

                if (crl.getIssuerDN().equals(certIssuer)) { // if there is a CRL from the issuer in the CRL list
                    LOGGER.debug("CRL found from " + certIssuer.getName());

                    X509CRLEntry crlEntry = crl.getRevokedCertificate(serial); // check it the serial number is in the list

                    if (crlEntry != null) { // the certificate is in the CRL list
                        LOGGER.info("The certificate is revoked by " + certIssuer.getName());
                        throw new CertPathValidatorException("The certificate "
                            + cert.getSubjectDN().getName() + " is revoked by "
                            + certIssuer.getName());
                    }

                    // do not check other CRLs, as we already found the issuer and the certificate was not revoked
                    // comment the following return out if it is possible to have several CRLs from one issuer
                    LOGGER.debug("CRLCertChecker.check: certificate OK, cheked against CRL");

                    return;
                }
            }

            if (m_crlRequired) {
                LOGGER.warn("No crl (even though it is required) found for the CA "
                    + certIssuer.toString());
                throw new CertPathValidatorException(
                    "No crl (even though it is required) found for the CA " + certIssuer.toString());
            }
        } catch (Exception e) {
            LOGGER.debug("Certificate revocation checking failed: " + e.getMessage());

            //            e.printStackTrace();
            throw new java.security.cert.CertPathValidatorException(e.getMessage());
        }

        LOGGER.debug("CRLCertChecker.check: certificate OK");
    }

    /** Returns the Set of supported extensions.
     * @return returns null, as this checker does not hadle extensions.
     */
    public java.util.Set getSupportedExtensions() {
        //        LOGGER.debug("CRLCertChecker.getSupportedExtensions");
        return null; // we don't handle extensions
    }

    /** This method is used to initialize the checker and to set the direction of checking (forward or reverse).
     * As the CRL checking does not care about the direction, this method does nothing.
     * @param param the direction, not used.
     * @throws CertPathValidatorException newer thrown.
     */
    public void init(boolean param) throws CertPathValidatorException {
        //        LOGGER.debug("CRLCertChecker.init");
        return; // we don't care about the direction of the checking
    }

    /** This method returns true if forward checking is supported. As CRL checking does not care about the
     * direction, true is alwaus returned.
     * @return returns true.
     */
    public boolean isForwardCheckingSupported() {
        // forward: check from client cert to CA cert
        // reverse: check from CA cert to client cert
        // maybe should return false, because crls involve usually the CA and those are in the CA end of the chain
        // and thus would be reached faster in reverse direction if the chain is long. Than again if the chain is
        // just CA and client, the forward cheking is faster...
        return true;
    }

    /**
     * Returns the verctor of crls.
     *
     * @return Returns the crls.
     */
    public Vector getCrls() {
        return m_crls;
    }

    /**
     * Sets the vector of crls to use for crl checking.
     *
     * @param crls The crls to set.
     */
    public void setCrls(Vector crls) {
        m_crls = crls;
    }
}

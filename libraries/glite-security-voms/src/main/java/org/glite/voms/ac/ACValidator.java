/*
 * Copyright (c) Members of the EGEE Collaboration. 2004.
 * See http://eu-egee.org/partners/ for details on the copyright holders.
 * For license conditions see the license file or http://eu-egee.org/license.html
 */

package org.glite.voms.ac;

import java.io.IOException;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.log4j.Logger;
import org.glite.voms.PKIVerifier;
import org.glite.voms.BasicVOMSTrustStore;

/**
 * Validator class capable of validating an Attribute Certificate
 * and verify its signature against a trust store of Attribute
 * Authority certificates.
 *
 * @author mulmo
 */
public class ACValidator {
    protected static Logger log = Logger.getLogger(ACValidator.class);
    private static ACValidator theInstance = null;
    protected ACTrustStore myTrustStore;
    protected VOMSTrustStore myVOMSStore;
    protected PKIVerifier    theVerifier;

    public ACValidator(ACTrustStore trustStore) {
        if (trustStore == null) {
            throw new IllegalArgumentException("ACValidator: constructor must have an ACTrustStore");
        }

        myTrustStore = trustStore;
    }

    public ACValidator(VOMSTrustStore theStore) {
        if (theStore == null)
            throw new IllegalArgumentException("ACValidator: constructor must have a VOMSTrustStore");

        myVOMSStore = theStore;
        try {
            theVerifier = new PKIVerifier(myVOMSStore);
        }
        catch(IOException e) {
            log.error("Problems while initializing the verifier: " + e.getMessage());
            throw new IllegalArgumentException("Problems with the passed store: " + e.getMessage());
        }
        catch (CertificateException e) {
            log.error("Problems while initializing the verifier: " + e.getMessage());
            throw new IllegalArgumentException("Problems with the passed store: " + e.getMessage());
        }
        catch (CRLException e) {
            log.error("Problems while initializing the verifier: " + e.getMessage());
            throw new IllegalArgumentException("Problems with the passed store: " + e.getMessage());
        }
    }

    public static ACValidator getInstance() {
        return getInstance((VOMSTrustStore)null);
    }

    public static ACValidator getInstance(ACTrustStore trustStore) throws IllegalArgumentException {
        return (theInstance = (theInstance != null) ? theInstance : new ACValidator(trustStore));
    }

    public static ACValidator getInstance(VOMSTrustStore trustStore) throws IllegalArgumentException {
        return (theInstance = (theInstance != null) ? theInstance : new ACValidator(trustStore));
    }


    public void cleanup() {
        if (myTrustStore != null)
            if (myTrustStore instanceof BasicVOMSTrustStore)
                ((BasicVOMSTrustStore)myTrustStore).stopRefresh();

        if (myVOMSStore != null)
            myVOMSStore.stopRefresh();

        if (theVerifier != null)
            theVerifier.cleanup();
    }

    public boolean validate(AttributeCertificate ac) {
        if (ac == null) {
            return false;
        }

        if (theVerifier != null) {
            return theVerifier.verify(ac);
        }

        X509Certificate[] candidates;

        if (!ac.isValid()) {
            if (log.isDebugEnabled()) {
                log.debug("AC expired or not yet valid. Issuer : " + ac.getIssuer().getName());
            }

            return false;
        }

        candidates = myTrustStore.getAACandidate(ac.getIssuer());

        if ((candidates == null) || (candidates.length == 0)) {
            if (log.isDebugEnabled()) {
                log.debug("AC not valid (no such trusted issuer) : " + ac.getIssuer().getName());
            }

            return false;
        }

        for (int i = 0; i < candidates.length; i++) {
            if (ac.verify(candidates[i].getPublicKey())) {
                if (log.isDebugEnabled()) {
                    log.debug("AC signature verified OK by issuer : " +
                        candidates[i].getSubjectX500Principal().getName());
                }

                // Is issuer valid?
                return true;
            }

            if (log.isDebugEnabled()) {
                log.debug("AC from signature did not verify OK by issuer : " +
                    candidates[i].getSubjectX500Principal().getName());
            }
        }

        return false;
    }
}

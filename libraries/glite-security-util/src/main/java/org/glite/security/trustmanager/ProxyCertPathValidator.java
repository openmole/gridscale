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

import org.glite.security.util.DN;
import org.glite.security.util.DNHandler;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.cert.CRLException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;

import java.util.Iterator;
import java.util.Vector;

/**
 * ProxyCertificatePathValidator validates certificate paths.
 * 
 * A certificate path is an array of certificates where the first certificate is
 * signed by the public key of the second, the second certificate is signed by
 * the public key of the third and so on. The certificate path might contain a
 * certificate authority (CA) certificate as the last element or it may not. If
 * the path ends in CA certificate, the CA certificate is ignored. To validate
 * the last non-CA certificate the trust anchors given in the constructor are
 * searched and if a CA that issued the certificate is found the non-CA
 * certificate is checked against the CA certificate. The last non-CA
 * certificate is checked against the optional certificate revocation lists
 * (CRL) given in the setCRLs method. If all the certificates in the array are
 * valid and there is a CA that signed the last non-CA certificate, the path is
 * valid.
 * 
 * The certificates have to be arranged in correct order. The have to be ordered
 * from index 0 being the actual end certificate, 0 or more intermediate
 * certificates. The last item in the array can be the end certificate if it is
 * signed by a CA, an intermediate certificate that is signed by a CA or a CA
 * certificate, which is ignored and the previous certificate is used as the
 * last of the array.
 * 
 * Notice: a certificate path consisting of only a CA certificate is considered
 * invalid certificate path.
 * 
 * The certificates are also checked for: - Date (the cert has to be valid for
 * the time of check)
 * 
 * @author Joni Hahkala Created on May 7, 2002, 6:23 PM
 */
public class ProxyCertPathValidator {
    /**
     * The logging facility.
     */
    private static final Logger LOGGER = Logger.getLogger(ProxyCertPathValidator.class.getName());
    /**
     * The vector of trust anchors.
     */
    Vector trustAnchors;
    /**
     * The common certificate factory, to avoid creating one each time certs are read.
     */
    CertificateFactory certFact;

    /**
     * The CRL checker to use within.
     */
    // does this need to be synchronized to make it threadsafe?
    CRLCertChecker crlChecker = null;

    /**
     * Creates a new instance of MyCertPathValidator
     * 
     * @param trustAnchors A vector or TrustAnchors (Certificate Authority certificates with additional info and
     *            wrapping) that are considered trusted.
     * @throws CertificateException thrown by certificate factory in some cases.
     * @throws NoSuchProviderException thrown if bouncycastle provider is not available. 
     */
    public ProxyCertPathValidator(Vector trustAnchors) throws CertificateException, NoSuchProviderException {
        certFact = CertificateFactory.getInstance("X.509", "BC");

        this.trustAnchors = trustAnchors;
    }

    /**
     * The setCRLChecker sets the CRLCehcker to use for the Chekcing of cert chains
     * 
     * @param checker The Checker instance to use to check the CRLs
     */
    public void setCRLChecker(CRLCertChecker checker) {
        crlChecker = checker;
    }

    /**
     * Checks that a certificate path is valid. Look above the class description
     * for better explanation.
     * 
     * @param inpath The certificate path to check.
     * @throws CertPathValidatorException thrown if there was a problem linking two certificates.
     * @throws CertificateException thrown if there was a problem with a single certificate.
     */
    public void check(X509Certificate[] inpath) throws CertPathValidatorException, CertificateException {
        // transform the certs into BC certs
        Vector<Certificate> pathVect = new Vector<Certificate>();

        for (int i = 0; i < inpath.length; i++) {
            byte[] bytes = inpath[i].getEncoded();
            BufferedInputStream certIS = new BufferedInputStream(new ByteArrayInputStream(bytes));

            pathVect.add(certFact.generateCertificate(certIS));
        }

        X509Certificate[] path = pathVect.toArray(new X509Certificate[] {});

        if (LOGGER.isDebugEnabled()) {
            for (int i = 0; i < path.length; i++) {
                LOGGER.debug("input path cert type: " + path[i].getClass().getName() + " DN [" + path[i].getSubjectDN()
                        + "]");
            }
        }

        int len = path.length;

        LOGGER.debug("path len is " + len);

        if (len == 0) {
            LOGGER.error("No certificate given to check");
            throw new CertPathValidatorException("No certificate given to check");
        }

        // check if the CA certificate is in the path
        X509Certificate caCert = path[len - 1];
        DN caIssuer = DNHandler.getIssuer(caCert);
        DN caSubject = DNHandler.getSubject(caCert);

        // check if the last cert is selfsigned cert. If so then it is a CA
        // cert.
        if (caIssuer.equals(caSubject)) {
            // the CA certificate is in the path
            // do not use that CA, use the one in trustanchors
            len--;

            LOGGER.debug("cert " + caSubject + " considered as CA cert (came with the chain from client)");

            // check if the cacert was the only cert in the chain, accept only
            // if it was a accepted self signed ca cert.
            if (len == 0) {
                TrustAnchor[] anchor = findCA(caSubject);
                TrustAnchor acceptedAnchor = null;

                for (int n = 0; n < anchor.length; n++) {
                    if (anchor[n].getTrustedCert().equals(caCert) == true) {
                        acceptedAnchor = anchor[n];

                        break;
                    }
                }

                if (acceptedAnchor == null) {
                    LOGGER.error("A self signed cert [" + caSubject + "] given, but not found among CAs, rejecting");
                    throw new CertPathValidatorException("A self signed cert [" + caSubject
                            + "] given, but not found among CAs, rejecting");
                }

                // check the certificate date
                try {
                    caCert.checkValidity();
                } catch (CertificateException e) {
                    LOGGER.info("the CA Certificate " + caSubject + " expired on " + caCert.getNotAfter());
                    throw new CertificateExpiredException("the CA Certificate " + DNHandler.getSubject(caCert)
                            + " expired on " + caCert.getNotAfter());
                }

                LOGGER.info("certificate path for " + caSubject + " is valid");

                return;
            }

            // otherwise the CA cert is just ignored (len-- above)
        }

        LOGGER.debug("Checking for expiration in the chain");

        // check the certificate dates for the whole chain
        for (int n = 0; n < path.length; n++) {
            try {
                path[n].checkValidity();
            } catch (CertificateExpiredException e) {
                LOGGER.info("the Certificate for " + DNHandler.getSubject(path[n]) + " expired on "
                        + path[n].getNotAfter());
                throw new CertificateExpiredException("the Certificate for " + DNHandler.getSubject(path[n])
                        + " expired on " + path[n].getNotAfter());
            } catch (CertificateNotYetValidException e) {
                LOGGER.info("the Certificate for " + DNHandler.getSubject(path[n]) + " will only be valid after "
                        + path[n].getNotBefore());
                throw new CertificateExpiredException("the Certificate for " + DNHandler.getSubject(path[n])
                        + " will only be valid after " + path[n].getNotBefore());
            }
        }

        // check the last certificate and CA certificate.
        X509Certificate last = path[len - 1]; // get the last cert (after the

        boolean namingConstraint = false;

        DN caDN = DNHandler.getIssuer(last);
        DN lastDN = DNHandler.getSubject(last);

        // find the CA certs for this certpath, throws exception if not found
        TrustAnchor[] anchor = findCA(caDN);

        LOGGER.debug("found " + anchor.length + " CAs that match, cheking which to use");

        // find the valid anchor from the found anchors with correct dn
        boolean acceptAnchor = false;
        Exception thrown = null;

        try {
            for (int n = 0; n < anchor.length; n++) {
                try {
                    namingConstraint = checkLastAnchor(last, anchor[n]);
                } catch (Exception e) {
                    if (e instanceof CRLException) {
                        throw (CRLException) e;
                    }

                    thrown = e;

                    continue;
                }

                acceptAnchor = true;
            }
        } catch (CRLException e) {
            LOGGER.info("Certificate for [" + lastDN + "] revoked by [" + caDN + "], rejecting it");
            throw new CertPathValidatorException(e.getMessage());
        }

        if (acceptAnchor == false) {
            if (thrown != null) {
                LOGGER.error("While checking against CA [" + caDN + "] got exception" + thrown);
                throw new CertPathValidatorException("While checking against CA [" + caDN + "] got exception " + thrown
                        + " " + thrown.getMessage());
            }

            LOGGER.info("CA cert [" + caDN + "] not found, rejecting certificate for [" + lastDN + "]");
            throw new CertPathValidatorException("CA cert [" + caDN + "] not found, rejecting certificate for ["
                    + lastDN + "]");
        }

        LOGGER.debug("checking the rest of the chain");

        // check the path pair by pair
        int n;

        try {
            for (n = len - 1; n > 0; n--) {
                namingConstraint = checkCertificatePair(path[n - 1], path[n], namingConstraint);
            }
        } catch (CertPathValidatorException e) {
            LOGGER.info(e.getMessage());
            throw e;
        } catch (CertificateException e) {
            LOGGER.info(e.getMessage());
            throw e;
        }

        LOGGER.debug("certificate path for " + DNHandler.getSubject(path[0]) + " is valid");
    }

    /**
     * Checks that the sub certificate is signed by the signer.
     * 
     * @param sub The sub certificate, the certificate appearing before signer in the certificate path array.
     * @param signer The signer certificate, the certificate appearing after the sub in the certificate path array.
     * @throws CertPathValidatorException Thrown if the signature checking fails.
     * @throws CertificateException Thrown if a problem occurs when accessing either certificate.
     */
    public void checkSignature(X509Certificate sub, X509Certificate signer) throws CertPathValidatorException,
            CertificateException {
        LOGGER.debug("Checking the signature");

        // get the public key of the signer
        PublicKey signKey = signer.getPublicKey();

        LOGGER.debug("Sub cert is " + sub.getClass().getName());

        // check that the sub cert was signed by the signer key
        try {
            sub.verify(signKey);
        } catch (java.security.NoSuchAlgorithmException e) {
            LOGGER.info("Invalid signature algorithm in \"" + sub.getSubjectDN().toString() + "\" error was "
                    + e.getClass().getName() + ":" + e.getMessage());
            throw new CertificateException("Invalid signature algorithm in \"" + sub.getSubjectDN().toString()
                    + "\" error was " + e.getClass().getName() + ":" + e.getMessage());
        } catch (java.security.InvalidKeyException e) {
            LOGGER.info("Invalid public key in \"" + signer.getSubjectDN().toString() + "\" error was "
                    + e.getClass().getName() + ":" + e.getMessage());
            throw new CertificateException("Invalid public key in \"" + signer.getSubjectDN().toString()
                    + "\" error was " + e.getClass().getName() + ":" + e.getMessage());
        } catch (java.security.NoSuchProviderException e) {
            LOGGER.error("Internal error, no crypto provider found. Error was " + e.getClass().getName() + ":"
                    + e.getMessage());
            throw new CertificateException("Internal error, no crypto provider found. Error was " + e.getMessage());
        } catch (java.security.SignatureException e) {
            LOGGER.info("invalid signature in " + sub.getSubjectDN().toString());
            throw new CertPathValidatorException("invalid signature in " + sub.getSubjectDN().toString());
        }
    }

    /**
     * Checks that the sub certificate is signed and issued by signer
     * 
     * @param sub the sub certificate
     * @param signer the signer certificate. The certificate for the issuer of the sub certificate.
     * @param namingConstraint true the cert pair is subject to naming constrain from above
     * @return true if the following certs are subject to naming restriction, meaning that the sub can only sign certs
     *         that contain the sub DN
     * @throws CertPathValidatorException Thrown if the signeture in sub is invalid or the certificate is not issued by
     *             signer.
     * @throws CertificateException Thrown if there is a problem accessing data from either of the certificates
     */
    public boolean checkCertificatePair(X509Certificate sub, X509Certificate signer, boolean namingConstraint)
            throws CertPathValidatorException, CertificateException {
        LOGGER.debug("Checking a cert pair");

        // check that the sub is signed by the signer
        checkSignature(sub, signer);

        /*
         * // do not accept any critical extensions Set critSet =
         * sub.getCriticalExtensionOIDs(); if (critSet != null &&
         * !critSet.isEmpty()) { LOGGER.debug("Set of critical extensions:");
         * for (Iterator i = critSet.iterator(); i.hasNext();) { String oid =
         * (String)i.next(); LOGGER.debug(oid); }
         * 
         * throw new CertificateException("Unsupported critical extension in " +
         * sub.getIssuerDN().toString()); }
         */

        // check that the issuer DN of the sub and the subject DN of the signer
        // match
        DN subIssuer = DNHandler.getIssuer(sub);
        DN signerSubject = DNHandler.getSubject(signer);

        LOGGER.debug("Checking DN match");

        if (subIssuer.equals(signerSubject)) {
            // LOGGER.debug("issuer OK");
        } else {
            LOGGER.info("cert issuer DN (" + subIssuer + ") - Issuer subject DN (" + signerSubject
                    + ") mismatch subject was ");

            // System.out.println("Issuer DN - Issuer surbject DN mismatch");
            throw new CertPathValidatorException("cert issuer DN (" + subIssuer + ") - Issuer subject DN ("
                    + signerSubject + ") mismatch subject was ");
        }

        // the naming constraint is enabled or the first is the end entity
        // (user) certificate,
        // enforce it
        if (namingConstraint == true /* || signer.getBasicConstraints() != -1 */) {
            LOGGER.debug("Checkin that " + DNHandler.getSubject(signer) + " matches end of "
                    + DNHandler.getSubject(sub) + " because either constraints were true [" + namingConstraint
                    + "] or signer basicContraints was equal to -1 [" + signer.getBasicConstraints() + "]");
            checkDNRestriction(sub, signer);

            return true;
        }

        LOGGER.debug("Certificate for \"" + DNHandler.getSubject(sub) + "\" is OK");

        // check if the following certs are restricted in namespace
        // TODO: path length restriction
        if (sub.getVersion() == 1) { // sub is v1 cert, assume sub is user

            // cert, enforce namig constraint
            return true;
        }
		if (sub.getBasicConstraints() == -1) {
		    return true; // sub cert without CA flag, assume sub is user

		    // cert
		}
		return false; // sub is a cert with CA flag, on naming constraints yet
    }

    /**
     * Does the same checks as checkCertificatePair and in addition checks that the sub is not listed in the possible
     * CRL issued by the CA represented by the anchor.
     * 
     * @param sub The sub certificate
     * @param anchor The TrustAnchor that issued the sub certificate
     * @return true if the following certs are subject to naming restriction, meaning that the sub can only sign certs
     *         that contain the sub DN
     * @throws CertPathValidatorException Thrown if the sub certificate is not issued by anchor, is revoked or the
     *             signature in sub is invalid.
     * @throws CertificateException Thrown if there is a problem accessing the data from the certificate or the trust
     *             anchor.
     * @throws CRLException Thrown in case the CRL parsing or usage fails.
     */
    public boolean checkLastAnchor(X509Certificate sub, TrustAnchor anchor) throws CertPathValidatorException,
            CertificateException, CRLException {
        LOGGER.debug("Checkin last cert and anchor");

        boolean namingConstraints = false;

        X509Certificate caCert = anchor.getTrustedCert();

        // do the normal cert pair check, except disable naming constraints as
        // CAs can sign anything (for now), should add CA naming constraitns
        namingConstraints = checkCertificatePair(sub, caCert, false);

        // check that the sub is not revoked
        if (crlChecker != null) {
            crlChecker.check(sub, null);
        }

        LOGGER.debug("Certificate for \"" + DNHandler.getSubject(sub) + "\" is validly issued by CA \""
                + DNHandler.getSubject(caCert) + "\"");

        return namingConstraints;
    }

    /**
     * Finds the TrustAnchor with the distinguished name (DN) dn.
     * 
     * @param dn The Principal holding the DN of the CA to be searched.
     * @throws CertPathValidatorException Thrown if no CA was found with that name
     * @return Returns the TrustAnchors that are named dn
     * @throws CertificateParsingException Thrown in case the CA certificate parsing fails.
     */
    public TrustAnchor[] findCA(DN dn) throws CertPathValidatorException, CertificateParsingException {
        Iterator anchorIter = trustAnchors.iterator();
        Vector<TrustAnchor> found = new Vector<TrustAnchor>();
        boolean caExpired = false;

        // String dnString = DNHandler.getDN(dn);
        // go through the trustAnchors and try to find all matching valid certs
        while (anchorIter.hasNext()) {
            TrustAnchor current = (TrustAnchor) anchorIter.next();

            DN anchorDN = DNHandler.getSubject(current.getTrustedCert());

            // check that the dn matches to either subject or subject
            // alternative names
            if (anchorDN.equals(dn) == false) {
                // Collection altNames =
                // current.getTrustedCert().getSubjectAlternativeNames();
                //
                // if(altNames == null){
                // continue;
                // }
                //
                // // check alternative names
                // Iterator altNameIter = altNames.iterator();
                // boolean altNameFound = false;
                // while(altNameIter.hasNext()){
                // String name = (String)(altNameIter.next());
                // if(name.equals(dnString)){
                // altNameFound = true;
                // break;
                // }
                // continue;
                // }
                //
                // if(altNameFound == false){
                continue;

                // }
            }

            // check that the Ca cert has not expired
            try {
                current.getTrustedCert().checkValidity();
            } catch (CertificateExpiredException e) {
                caExpired = true;
                LOGGER.warn("The CA certificate for " + anchorDN + " has expired, update or remove it!");

                continue;
            } catch (CertificateNotYetValidException e) {
                caExpired = true;
                LOGGER.warn("The CA certificate for " + anchorDN + " is not yet valid!");

                continue;
            }

            // valid CA found, add it to the list of found CAs
            found.add(current);
        }

        TrustAnchor[] accepted = {};

        if (found.size() > 0) {
            if (caExpired == true) {
                LOGGER.warn("Remove expired duplicate certificate(s) for CA " + dn);
            }

            return found.toArray(accepted);
        }

        // if the expired CA was the only CA found
        if (caExpired == true) {
            LOGGER.error("The CA certificate for " + dn + " has expired or is not yet valid, update or remove it!");
        }

        LOGGER.info("No CA named \"" + dn + "\" could be found");
        throw new CertPathValidatorException("No CA named \"" + dn + "\" could be found");
    }

    /**
     * Returns an array of accepted CA certificates
     * 
     * @return Returns the array of CA certificates
     */
    public java.security.cert.X509Certificate[] getCACerts() {
        Iterator iter = trustAnchors.iterator();

        Vector<X509Certificate> certs = new Vector<X509Certificate>();

        while (iter.hasNext()) {
            TrustAnchor anchor = (TrustAnchor) iter.next();
            X509Certificate cert = anchor.getTrustedCert();
            certs.add(cert); // should use clone...
        }

        LOGGER.debug("getCACerts: returning " + certs.size() + " ca certs");

        return certs.toArray(new java.security.cert.X509Certificate[] {});
    }

    /**
     * Checks that the subject DN starts with the DN parts of the signer.
     * 
     * @param sub the signer signed certificate.
     * @param signer the signer certificate.
     * @throws CertificateException thrown in case the proxy certificate DN of the sub is not the DN of the signer appended by additional CN= rdn.
     */
    public void checkDNRestriction(X509Certificate sub, X509Certificate signer) throws CertificateException {
        LOGGER.debug("Checking dn restriction");

        DN subDN = DNHandler.getSubject(sub);
        DN signerDN = DNHandler.getSubject(signer);

        try {
            DN subDNWithoutProxy = subDN.withoutLastCN(false);

            if (subDNWithoutProxy.equals(signerDN) != true) {
                throw new CertificateException("The DN [" + subDN + "] doesn't end with [" + signerDN
                        + "] as required for proxy certs");
            }
        } catch (Exception e) {
            LOGGER.info("Error while cheking naming constrainst between sub [" + subDN + "] and signer [" + signerDN
                    + " error: " + e + e.getMessage());
            if(LOGGER.isDebugEnabled()){
                LOGGER.debug("StackTrace: ", e);
            }
            if (e instanceof CertificateException) {
                throw (CertificateException) e;
            }

            throw new CertificateException("Error while cheking naming constrainst between sub [" + subDN
                    + "] and signer [" + signerDN + "] error: " + e + e.getMessage());
        }
    }
}

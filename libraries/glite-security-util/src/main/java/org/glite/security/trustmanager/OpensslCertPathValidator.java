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

package org.glite.security.trustmanager;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CRLException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Set;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.glite.security.util.CaseInsensitiveProperties;
import org.glite.security.util.CertificateRevokedException;
import org.glite.security.util.DN;
import org.glite.security.util.DNHandler;
import org.glite.security.util.FullTrustAnchor;
import org.glite.security.util.TrustStorage;
import org.glite.security.util.namespace.DNCheckerImpl;
import org.glite.security.util.proxy.ProxyCertInfoExtension;
import org.glite.security.util.proxy.ProxyCertificateInfo;

/**
 * OpenSSLCertPathValidator validates certificate paths. A certificate path is an array of certificates where the first
 * certificate is signed by the public key of the second, the second certificate is signed by the public key of the
 * third and so on. The certificate path might contain a certificate authority (CA) certificate as the last element or
 * it may not. If the path ends in CA certificate, the CA certificate is ignored. To validate the last non-CA
 * certificate the trust anchors given in the constructor are searched and if a CA that issued the certificate is found
 * the non-CA certificate is checked against the CA certificate. The last non-CA certificate is checked against the
 * optional certificate revocation lists (CRL) given in the setCRLs method. If all the certificates in the array are
 * valid and there is a CA that signed the last non-CA certificate, the path is valid. The certificates have to be
 * arranged in correct order. The have to be ordered from index 0 being the actual end certificate, 0 or more
 * intermediate certificates. The last item in the array can be the end certificate if it is signed by a CA, an
 * intermediate certificate that is signed by a CA or a CA certificate, which is ignored and the previous certificate is
 * used as the last of the array. Notice: a certificate path consisting of only a CA certificate is considered invalid
 * certificate path. The certificates are also checked for:
 * <ul>
 * <li>Date (the cert has to be valid for the time of check)</li>
 * <li>The certificate revocation list (CRL)</li>
 * <li>Namespace restrictions to check whether it is inside the accepted namespace of the CA</li>
 * </ul>
 * 
 * @author Joni Hahkala Created on Mar 6, 2008, 6:23 PM
 */
public class OpensslCertPathValidator {
    /** The logging facility. */
    private static final Logger LOGGER = Logger.getLogger(OpensslCertPathValidator.class.getName());

    /** The underlying trust storage used for the certificate checking. */
    private TrustStorage m_storage = null;

    /** The underlying certificate factory used for reading certificates. */
    private CertificateFactory m_certFact;

    /** The flag to show that the CRLs are required for the CAs to be valid and used for cert path checking. */
    private boolean m_crlRequired = true;

    /** Add the bouncy castle provider unless it's already set */
    static {
        if (Security.getProvider("BC") == null) {
            LOGGER.debug("ContextWrapper: bouncycastle provider set.");
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Creates a new instance of MyCertPathValidator
     * 
     * @param trustPath A vector or TrustAnchors (Certificate Authority certificates with additional info and wrapping)
     *            that are considered trusted.
     * @param crlRequired true if CRLs are required for each CA for them to be used in the certificate path checking.
     * @throws CertificateException in case CA certificate loading fails.
     * @throws NoSuchProviderException in case bouncycastle provider is not found.
     * @throws IOException in case CA, CRL or namespace file reading fails.
     * @throws ParseException in case the reading of namespace files fails.
     * @deprecated use OpensslCertPathValidator(String trustPath, boolean crlRequired, CaseInsensitiveProperties props) instead.
     */
    public OpensslCertPathValidator(String trustPath, boolean crlRequired) throws CertificateException,
            NoSuchProviderException, IOException, ParseException {
        m_certFact = CertificateFactory.getInstance("X.509", "BC");

        this.m_storage = new TrustStorage(trustPath, null);

        this.m_crlRequired = crlRequired;
    }

    /**
     * Creates a new instance of MyCertPathValidator
     * 
     * @param trustPath A vector or TrustAnchors (Certificate Authority certificates with additional info and wrapping)
     *            that are considered trusted.
     * @param crlRequired true if CRLs are required for each CA for them to be used in the certificate path checking.
     * @param props properties to pass along for child classes to use.
     * 
     * @throws CertificateException in case CA certificate loading fails.
     * @throws NoSuchProviderException in case bouncycastle provider is not found.
     * @throws IOException in case CA, CRL or namespace file reading fails.
     * @throws ParseException in case the reading of namespace files fails.
     */
    public OpensslCertPathValidator(String trustPath, boolean crlRequired, CaseInsensitiveProperties props) throws CertificateException,
    NoSuchProviderException, IOException, ParseException {
        m_certFact = CertificateFactory.getInstance("X.509", "BC");

        this.m_storage = new TrustStorage(trustPath, props);

        this.m_crlRequired = crlRequired;
    }

    /**
     * Searches for a parent CA from trustAnchors and add the cert to the cert chain.
     * 
     * @param inpath The input path.
     * @return the constructed path.
     */
    public boolean findAddParent(Vector<X509Certificate> inpath) {
        X509Certificate firstCert = inpath.lastElement();
        // If the cert is self signed, the path can't be extended.
        if (DNHandler.getSubject(firstCert).equals(DNHandler.getIssuer(firstCert))) {
            return false;
        }
        String hash = OpensslTrustmanager.getOpenSSLCAHash((X509Name) firstCert.getIssuerDN());

        FullTrustAnchor acceptedAnchor = null;

        // Get all the anchors that have the hash.
        FullTrustAnchor[] anchors = m_storage.getAnchors(hash);

        if (anchors != null) {
            LOGGER.debug("found " + anchors.length + " CAs that match, cheking which to use");

            // Check it any of them match.
            for (int n = 0; n < anchors.length; n++) {
                if (DNHandler.getSubject(anchors[n].m_caCert).equals(DNHandler.getIssuer(firstCert))) {
                    acceptedAnchor = anchors[n];
                    // Remove the CA from the chain.
                    inpath.add(acceptedAnchor.m_caCert);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Constructs the CA path of the given cert chain. If the chain starts with a CA cert, tries to replace it with one
     * from trustAnchors. If not starting with CA or if the starting CA is not in trustStore, searches for the CA that
     * signed the first cert. If trustanchor is found, tries to build upwards to parent CAs and returns the constructed
     * chain.
     * 
     * @param inpath The certificate chain to start with.
     * @return The constructed certificate chain using as many CA certs as possible (longest cert chain possible).
     * @throws CertPathValidatorException Thrown in case the certificate chain building fails, like if there is no valid
     *             trustanchor for the chain.
     * @throws CertificateException In case certificate handling fails, in case of corrupted certs etc.
     */
    public Vector<X509Certificate> buildPath(X509Certificate[] inpath) throws CertPathValidatorException,
            CertificateException {

        // convert certs to BC certs if necessary.
        Vector<X509Certificate> pathVect = new Vector();

        for (int i = 0; i < inpath.length; i++) {
            if (inpath[i] instanceof org.bouncycastle.jce.provider.X509CertificateObject) {
                pathVect.add(inpath[i]);
            } else {
                byte[] bytes = inpath[i].getEncoded();
                BufferedInputStream certIS = new BufferedInputStream(new ByteArrayInputStream(bytes));

                pathVect.add((X509Certificate) m_certFact.generateCertificate(certIS));
            }
        }

        // CA candidate
        X509Certificate caCert = pathVect.lastElement();
        boolean found = false;

        // if the last cert is a CA, check if it is in the trustAnchors
        if (caCert.getBasicConstraints() > -1) {
            // Check whether the chain ends with the CA cert or not.
            String hash = OpensslTrustmanager.getOpenSSLCAHash((X509Name) caCert.getSubjectDN());

            FullTrustAnchor acceptedAnchor = null;

            // Get all the anchors that have the hash.
            FullTrustAnchor[] anchors = m_storage.getAnchors(hash);

            if (anchors != null) {
                LOGGER.debug("found " + anchors.length + " CAs that match, cheking which to use");

                // Check it any of them match.
                for (int n = 0; n < anchors.length; n++) {
                    if (anchors[n].m_caCert.getPublicKey().equals(caCert.getPublicKey())
                            && DNHandler.getSubject(anchors[n].m_caCert).equals(DNHandler.getSubject(caCert))) {
                        acceptedAnchor = anchors[n];
                        // Remove the CA from the chain.
                        found = true;
                        pathVect.remove(caCert);
                        pathVect.add(acceptedAnchor.m_caCert);
                        break;
                    }
                }
            }
            // Fail if self signed CA cert that is not found.
            if (!found && DNHandler.getSubject(caCert).equals(DNHandler.getIssuer(caCert))) {
                LOGGER.info("Self-signed CA cert " + DNHandler.getSubject(caCert)
                        + " is not trusted, rejecting the certificate chain.");
                throw new CertPathValidatorException("Self-signed CA cert " + DNHandler.getSubject(caCert)
                        + " is not trusted, rejecting the certificate chain.");
            }
        }

        // If the CA cert in the chain was not found or there was none, search issuer from the trustanchors or fail.
        if (!found) {
            found = findAddParent(pathVect);
            // no trustanchors were found, fail.
            if (!found) {
                LOGGER.info("The root of the cert chain " + DNHandler.getSubject(caCert)
                        + " is not trusted CA nor issued by one, rejecting the certificate chain.");
                throw new CertPathValidatorException("The root of the cert chain " + DNHandler.getSubject(caCert)
                        + " is not trusted CA nor issued by one, rejecting the certificate chain.");
            }
        }

        // valid anchor was found, try to construct further by finding parent CAs in the trustAnchors.
        do {
            found = findAddParent(pathVect);
        } while (found);

        return pathVect;

    }

    /**
     * Checks that a certificate path is valid. Look above the class description for better explanation.
     * 
     * @param inpath The certificate path to check
     * @throws CertPathValidatorException Thrown if there was a problem linking two certificates
     * @throws CertificateException thrown if there was a problem with a single certificate
     */
    public void check(X509Certificate[] inpath) throws CertPathValidatorException, CertificateException {
        if (inpath.length == 0) {
            LOGGER.error("No certificates given to check");
            throw new CertPathValidatorException("No certificates given to check");
        }

        if (LOGGER.isDebugEnabled()) {
            for (int i = 0; i < inpath.length; i++) {
                LOGGER.debug("input path cert type: " + inpath[i].getClass().getName() + " DN ["
                        + inpath[i].getSubjectDN() + "]");
            }
        }
        // Find trustanchor and any parent CAs, if not found, throw exception.
        Vector<X509Certificate> pathVect = buildPath(inpath);

        // now we have trusted CA starting chain.
        LOGGER.debug("Given path len is " + inpath.length + " and constructed path lenght " + pathVect.size());

        CertPathValidatorState state = new CertPathValidatorState();
        state.m_proxyType = ProxyCertificateInfo.CA_CERT;

        X509Certificate[] certs = pathVect.toArray(new X509Certificate[] {});

        // check pairs, current is the parent, starting with the root of the chain.
        int currentIndex = certs.length - 1;

        checkValidity(certs[currentIndex]);

        // Check the anchors
        while (currentIndex > 0) {
            X509Certificate current = certs[currentIndex];
            X509Certificate next = certs[currentIndex - 1];

            // if the current cert is CA, do CA-cert pair checks.
            if (current.getBasicConstraints() > -1) {

                try {
                    state = checkAnchorAndCert(next, current, state, (currentIndex == certs.length - 1));

                } catch (CRLException e) {
                    LOGGER.info("Certificate for " + DNHandler.getSubject(next) + " revoked by "
                            + DNHandler.getSubject(current) + ", rejecting it");
                    throw new CertPathValidatorException("Certificate for " + DNHandler.getSubject(next)
                            + " revoked by " + DNHandler.getSubject(current) + ", rejecting it");
                } catch (Exception e) {
                    LOGGER.info("Certificate checking for " + DNHandler.getSubject(next)
                            + " failed, rejecting it. Error was: " + e.getMessage());
                    throw new CertPathValidatorException("Certificate checking for " + DNHandler.getSubject(next)
                            + " failed, rejecting it. Error was: " + e.getMessage(), e);
                }
            } else {
                // do just cert pair checks.
                try {
                    state = checkCertificatePair(next, current, state);
                } catch (CertPathValidatorException e) {
                    LOGGER.info(e.getMessage());
                    throw e;
                } catch (CertificateException e) {
                    LOGGER.info(e.getMessage());
                    throw e;
                }
            }
            currentIndex--;
        }

        LOGGER.info("certificate path for " + DNHandler.getSubject(inpath[0]) + " is valid");
    }

    /**
     * Checks that the certificate is valid now and throws the corresponding exception in case it isn't.
     * 
     * @param cert
     * @throws CertificateExpiredException
     * @throws CertificateNotYetValidException
     */
    public void checkValidity(X509Certificate cert) throws CertificateExpiredException, CertificateNotYetValidException {
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException e) {
            LOGGER.info("the Certificate for " + DNHandler.getSubject(cert) + " expired on " + cert.getNotAfter());
            throw new CertificateExpiredException("the Certificate for " + DNHandler.getSubject(cert) + " expired on "
                    + cert.getNotAfter());
        } catch (CertificateNotYetValidException e) {
            LOGGER.info("the Certificate for " + DNHandler.getSubject(cert) + " will only be valid after "
                    + cert.getNotBefore());
            throw new CertificateNotYetValidException("the Certificate for " + DNHandler.getSubject(cert)
                    + " will only be valid after " + cert.getNotBefore());
        }

    }

    /**
     * Checks that the sub certificate is signed by the signer.
     * 
     * @param sub The sub certificate, the certificate appearing before signer in the certificate path array.
     * @param signer The signer certificate, the certificate appearing after the sub in the certificate path array.
     * @throws CertPathValidatorException Thrown if the signature cheking fails
     * @throws CertificateException Thrown if a problem occures when accessing either certificate.
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
     * @param state the state for this certificate pair checking from the previous round.
     * @return state the state for the next certificate pair checking.
     * @throws CertPathValidatorException Thrown if the signature in sub is invalid or the certificate is not issued by
     *             signer.
     * @throws CertificateException Thrown if there is a problem accessing data from either of the certificates
     */
    public CertPathValidatorState checkCertificatePair(X509Certificate sub, X509Certificate signer,
            CertPathValidatorState state) throws CertPathValidatorException, CertificateException {
        LOGGER.debug("Checking a cert pair");

        // check that the sub is signed by the signer
        checkSignature(sub, signer);

        // check the validity of sub cert, the first signer is checked before the chain checking.
        checkValidity(sub);

        CertPathValidatorState newState = new CertPathValidatorState();

        // check that the issuer DN of the sub and the subject DN of the signer match
        DN subIssuer = DNHandler.getIssuer(sub);
        DN signerSubject = DNHandler.getSubject(signer);
        DN subSubject = DNHandler.getSubject(sub);
        Set<String> criticalOIDs = sub.getCriticalExtensionOIDs();

        // check for empty subjects, not supported, in rfc3820 proxies they are nor allowed, reject also elsewhere for
        // simplicity.
        if (signerSubject.isEmpty() || subSubject.isEmpty()) {
            throw new CertPathValidatorException("Subject DN of " + (signerSubject.isEmpty() ? "parent" : "sub")
                    + " certificate is empty, invalid certificate.");
        }

        LOGGER.debug("Checking cert basic constraints extension and proxy type");
        // process the basicConstraints and determine the possible proxy type.
        int signerPathLen = signer.getBasicConstraints();
        int subPathLen = sub.getBasicConstraints();
        if (signerPathLen >= 0) { // signer has a CA flag and it is ok as it is either anchor or accepted sub ca from
            // previous round. (case ca-ca or ca-end entity cert)
            if (subPathLen >= 0) { // sub is claiming to be a subCA
                if (state.m_basicConstraintsPathLimit >= 0) { // check that the ca certs path length limit isn't
                    // reached.
                    if (subPathLen < state.m_basicConstraintsPathLimit) {// check if the previous CAs limit the path
                        // shorter than this.
                        newState.m_basicConstraintsPathLimit = subPathLen - 1;
                        newState.m_basicConstraintsPathLimiter = subSubject;
                    } else {
                        newState.m_basicConstraintsPathLimit = state.m_basicConstraintsPathLimit - 1;
                        newState.m_basicConstraintsPathLimiter = state.m_basicConstraintsPathLimiter;
                    }
                } else { // ca path limit was reached, this CA cert can't be accepted.
                    throw new CertPathValidatorException("Certificate " + subSubject
                            + " has a CA flag, but path lenght is too long, it was limited by "
                            + newState.m_basicConstraintsPathLimiter);
                }

                newState.m_proxyType = ProxyCertificateInfo.CA_CERT;
            } else { // sub is not a CA cert, so it must be a usr/end entity cert.
                newState.m_proxyType = ProxyCertificateInfo.USER_CERT;
            }
        } else { // if the issuer is not a CA, and the cert is not yet defined as a proxy, find out if it is a proxy.
            // (case "end entity cert"-proxy or proxy-proxy)
            assert (state.m_proxyType == ProxyCertificateInfo.CA_CERT); // this must not happen, find out if it does
            if (subPathLen != -1) { // no cert after non-CA cert in path can claim to be a CA.
                throw new CertPathValidatorException("A certificate " + subSubject + " after non-CA cert "
                        + signerSubject + " has a CA flag, which is not allowed. Rejecting certificate path.");
            }
            if (criticalOIDs != null) {
                if (criticalOIDs.contains(ProxyCertInfoExtension.PROXY_CERT_INFO_EXTENSION_OID)) {
                    // remove the oid to mark it recognized.
                    criticalOIDs.remove(ProxyCertInfoExtension.PROXY_CERT_INFO_EXTENSION_OID);
                    newState.m_proxyType = ProxyCertificateInfo.RFC3820_PROXY;
                } else {
                    if (criticalOIDs.contains(ProxyCertInfoExtension.DRAFT_PROXY_CERT_INFO_EXTENSION_OID)) {
                        // remove the oid to mark it recognized.
                        criticalOIDs.remove(ProxyCertInfoExtension.DRAFT_PROXY_CERT_INFO_EXTENSION_OID);
                        newState.m_proxyType = ProxyCertificateInfo.DRAFT_RFC_PROXY;
                    } else { // no proxy extension found, but other critical extensions are present.
                        if (subSubject.getLastCNValue().toLowerCase().equals("proxy")
                                || subSubject.getLastCNValue().toLowerCase().equals("limited proxy")) {
                            newState.m_proxyType = ProxyCertificateInfo.LEGACY_PROXY;
                        } else { // invalid cert as not a valid proxy and signed by nonCA cert.
                            throw new CertPathValidatorException(
                                    "Unknown proxy type, no draft or RFC3820 extensions found and subject doesn't follow legacy proxy convention.");
                        }
                    }

                }
            } else { // no proxy extension or other critical extensions found.
                if (subSubject.getLastCNValue().toLowerCase().equals("proxy")
                        || subSubject.getLastCNValue().toLowerCase().equals("limited proxy")) {
                    newState.m_proxyType = ProxyCertificateInfo.LEGACY_PROXY;
                } else { // invalid cert as not a valid proxy and signed by nonCA cert.
                    throw new CertPathValidatorException(
                            "Unknown proxy type, no draft or RFC3820 extensions found and subject doesn't follow legacy proxy convention.");
                }
            }
        }

        LOGGER.debug("Checking cert transitions.");
        // double check the transitions
        if (state.m_proxyType == ProxyCertificateInfo.CA_CERT) { // ca cert to ca ot usercert is ok
            if (newState.m_proxyType != ProxyCertificateInfo.CA_CERT
                    && newState.m_proxyType != ProxyCertificateInfo.USER_CERT) {
                throw new CertPathValidatorException("The CA cert " + signerSubject
                        + " can only sign sub CAs or user certs. The cert " + subSubject + " is neither.");
            }
        } else { // user cert to proxy is ok
            if (state.m_proxyType == ProxyCertificateInfo.USER_CERT) {
                if (newState.m_proxyType != ProxyCertificateInfo.LEGACY_PROXY
                        && newState.m_proxyType != ProxyCertificateInfo.DRAFT_RFC_PROXY
                        && newState.m_proxyType != ProxyCertificateInfo.RFC3820_PROXY) {
                    throw new CertPathValidatorException("The end entity cert " + signerSubject
                            + " can only sign proxies. The cert " + subSubject + " wasn't recognized as a proxy.");
                }
            } else { // proxy cert to same type proxy is ok.
                if (state.m_proxyType == ProxyCertificateInfo.LEGACY_PROXY
                        || state.m_proxyType == ProxyCertificateInfo.DRAFT_RFC_PROXY
                        || state.m_proxyType == ProxyCertificateInfo.RFC3820_PROXY) {
                    if (state.m_proxyType != newState.m_proxyType) {
                        throw new CertPathValidatorException("The proxy cert " + signerSubject
                                + " and the sub proxy cert " + subSubject + " are of different type.");
                    }
                } else {
                    throw new CertPathValidatorException("Unknown cert " + signerSubject + " and transition");
                }
            }
        }

        // the naming constraint is enabled or the first is the end entity (user) certificate, enforce it, also check
        // key usage extension and proxy path limit
        if (newState.m_proxyType == ProxyCertificateInfo.LEGACY_PROXY
                || newState.m_proxyType == ProxyCertificateInfo.DRAFT_RFC_PROXY
                || newState.m_proxyType == ProxyCertificateInfo.RFC3820_PROXY) {

            LOGGER.debug("Checkin that " + DNHandler.getSubject(signer) + " matches end of "
                    + DNHandler.getSubject(sub) + " because proxy constraints");
            checkDNRestriction(sub, signer, state.m_proxyType);

            if (newState.m_proxyType == ProxyCertificateInfo.RFC3820_PROXY
                    || newState.m_proxyType == ProxyCertificateInfo.DRAFT_RFC_PROXY) {
                if (state.m_proxyInfoPathLimit < 0) {
                    throw new CertPathValidatorException("The proxy certificate path of \"" + subSubject
                            + "\" is longer than allowed by \"" + state.m_proxyInfoPathLimiter
                            + "\" that set the proxy path length limit.");
                }
                ProxyCertificateInfo info = new ProxyCertificateInfo(sub);
                int pathLimit;
                try {
                    pathLimit = info.getProxyPathLimit();
                } catch (IOException e) {
                    throw new CertificateException("Parsing of a proxy certificate \"" + subSubject
                            + "\" failed with: " + e.getMessage());
                }
                if (pathLimit < state.m_proxyInfoPathLimit) {
                    newState.m_proxyInfoPathLimit = pathLimit - 1;
                    newState.m_proxyInfoPathLimiter = subSubject;
                } else {
                	if(state.m_proxyInfoPathLimit != ProxyCertInfoExtension.UNLIMITED){
                		newState.m_proxyInfoPathLimit = state.m_proxyInfoPathLimit - 1;
                	} else { // do not substract the unlimited value
                		newState.m_proxyInfoPathLimit = state.m_proxyInfoPathLimit;
                	}
                    newState.m_proxyInfoPathLimiter = state.m_proxyInfoPathLimiter;
                }
                if(LOGGER.isDebugEnabled()){
                    LOGGER.debug("ProxyInfoPath limit is: " + newState.m_proxyInfoPathLimit);
                }
            }

            // check that the mandatory digital signature bit is set in case the proxy cert has keyUsage extension.
            boolean[] keyUsageBits = sub.getKeyUsage();
            if (keyUsageBits != null && keyUsageBits[0] != true) {
                throw new CertPathValidatorException("The proxy cert " + subSubject
                        + " has keyUsage extension, but the digital signature bit is not set as required.");
            }
        }

        // remove key usage extension from the unhandled list as it is recognized
        if (criticalOIDs != null && !criticalOIDs.isEmpty()) {
            criticalOIDs.remove("2.5.29.15");
        }
        
        // remove basic constraints extension from the unhandled list as it is recognized
        if (criticalOIDs != null && !criticalOIDs.isEmpty()) {
            criticalOIDs.remove("2.5.29.19");
        }

        if (criticalOIDs != null && !criticalOIDs.isEmpty()) {
            throw new CertPathValidatorException("Certificate " + subSubject
                    + " contains unsupported critical extensions, e.g. " + criticalOIDs.iterator().next());
        }

        LOGGER.debug("Checking DN match");

        if (!subIssuer.equals(signerSubject)) {
            throw new CertPathValidatorException("cert issuer DN (" + subIssuer + ") - Issuer subject DN ("
                    + signerSubject + ") mismatch.");
        }

        // check namespace constraints next round only if the parent is a CA, proxies are restricted to the end entity
        // DN anyway.
        if (signer.getBasicConstraints() > -1) {
            DNCheckerImpl checker = new DNCheckerImpl();

            FullTrustAnchor[] parentAnchors = state.m_anchorStack.toArray(new FullTrustAnchor[] {});

            // go through anchors from lowest CA towards the root, find first namespace and check the DN against that,
            // if none found, no restrictions.
            for (int i = parentAnchors.length - 1; i >= 0; i--) {
                FullTrustAnchor anchor = parentAnchors[i];
                if (anchor.m_namespace != null && !anchor.m_namespace.getPolices().isEmpty()) {
                    // check against the namespace, failure throws exception.
                    checker.check(subSubject, signerSubject, anchor.m_namespace.getPolices());
                    // success, namespace definition found and DN is allowed, no further checks necessary.
                    break;
                }
            }
            newState.m_anchorStack = state.m_anchorStack;
        }

        return newState;
    }

    /**
     * Does the same checks as checkCertificatePair and in addition checks that the sub is not listed in the possible
     * CRL issued by the CA represented by the anchor.
     * 
     * @param sub The sub certificate to check.
     * @param caCert The ca cert to check.
     * @param state The state from the possible previous steps.
     * @param firstAnchor The flag for first anchor in the chain. The anchor must be found, otherwise checking fails.
     * @return the state for the next certificate pair checking.
     * @throws CertPathValidatorException Thrown if the sub certificate is not issued by anchor, is revoked or the
     *             signature in sub is invalid.
     * @throws CertificateException Thrown if there is a problem accessing the data from the certificate or the trust
     *             anchor
     * @throws CRLException In case the CRL parsing or usage fails.
     */
    public CertPathValidatorState checkAnchorAndCert(X509Certificate sub, X509Certificate caCert,
            CertPathValidatorState state, boolean firstAnchor) throws CertPathValidatorException, CertificateException,
            CRLException {
        LOGGER.debug("Checkin cert and anchor");

        String hash = OpensslTrustmanager.getOpenSSLCAHash((X509Name) caCert.getSubjectDN());

        FullTrustAnchor currentAnchor = null;

        // Get all the anchors that have the hash.
        FullTrustAnchor[] anchors = m_storage.getAnchors(hash);

        if (anchors != null) {
            LOGGER.debug("found " + anchors.length + " CAs that match, cheking which to use");

            // Check it any of them match.
            for (int n = 0; n < anchors.length; n++) {
                if (anchors[n].m_caCert.getPublicKey().equals(caCert.getPublicKey())) {
                    currentAnchor = anchors[n];
                }
            }
        }

        // first anchor must be found, otherwise the chain can't be based on any trusted anchor.
        if (currentAnchor == null && firstAnchor) {
            throw new CertPathValidatorException("The CA certificate " + DNHandler.getSubject(caCert).getRFCDN()
                    + " was not found. Certificate chain isn't based on any trusted CA.");
        }
        // beware, can be a null!
        state.m_anchorStack.add(currentAnchor);

        CertPathValidatorState newstate = checkCertificatePair(sub, caCert, state);

        String caDN = DNHandler.getSubject(caCert).getRFCDN();
        String subDN = DNHandler.getSubject(sub).getRFCDN();

        // Check CRL ----------------
        // if no anchor found, and crls are, fail.
        if (currentAnchor == null) {
            if (m_crlRequired) {
                LOGGER.info("The certificate " + subDN + " is rejected as no CRL was found for CA " + caDN);
                throw new CertPathValidatorException("The certificate " + subDN
                        + " is rejected as no CRL was found for CA " + caDN);

            }
        } else {
            // RevocationChecker found
            if (currentAnchor.m_revChecker != null) {
                try {
                    currentAnchor.m_revChecker.check(sub);
                } catch (CertificateRevokedException e) {
                    LOGGER.info("The certificate " + subDN + " is revoked by " + caDN + ".");
                    throw new CertPathValidatorException("The certificate " + subDN + " revocation checking failed: " + e.getMessage());
                }catch (Exception e) {
                    if (m_crlRequired) {
                    	LOGGER.info("The certificate " + subDN + " revocation check failed! Problem was: " + e.getMessage());
                    	throw new CertPathValidatorException("The certificate " + subDN + " revocation check failed! Problem was: " + e.getMessage());
                    } else {
                    	LOGGER.debug("The certificate " + subDN + " revocation check failed because CRL problem, but CRL checking was not required. Problem was: " + e.getMessage());
                    }
                }
            } else {
                // if no checkers found, but they are required, fail.
                if (m_crlRequired) {
                    LOGGER.info("The certificate " + subDN + " is not trusted as the revocation checker creation hasn't succeeded for the CA " + caDN + ".");
                    throw new CertPathValidatorException("The certificate " + subDN + " is not trusted as the revocation cheker creation hasn't succeeded for the CA " + caDN + ".");
                }
                LOGGER.info("Revocation checker creation hasn't succeeded for CA " + caDN + ", but they're are not required, so accepting the cert " + subDN + ".");
            }
        }

        if (currentAnchor != null && currentAnchor.m_caCert != null) {
            // check that the mandatory keyCertSign bit is set in case the CA cert has keyUsage extension.
            boolean[] keyUsageBits = currentAnchor.m_caCert.getKeyUsage();
            if (keyUsageBits != null && keyUsageBits[5] != true) {
                throw new CertPathValidatorException("The CA cert " + caDN
                        + " has keyUsage extension, but the keyCertSign bit is not set as required.");
            }

            /*
             * Basic constraint exists and is > -1 as it was checked during anchor loading
             */
            int basicConstraints = currentAnchor.m_caCert.getBasicConstraints();

            /* Prepare for next round, set the basic constraints */
            if (basicConstraints < state.m_basicConstraintsPathLimit - 1) {
                newstate.m_basicConstraintsPathLimit = basicConstraints - 1;
                newstate.m_basicConstraintsPathLimiter = DNHandler.getSubject(currentAnchor.m_caCert);
            } else {
                newstate.m_basicConstraintsPathLimit = state.m_basicConstraintsPathLimit - 1;
                newstate.m_basicConstraintsPathLimiter = state.m_basicConstraintsPathLimiter;
            }
        } else {
            newstate.m_basicConstraintsPathLimit = state.m_basicConstraintsPathLimit;
            newstate.m_basicConstraintsPathLimiter = state.m_basicConstraintsPathLimiter;

        }

        LOGGER.debug("Certificate for " + subDN + " is validly issued by CA " + caDN);

        return newstate;
    }

    /**
     * Returns an array of accepted CA certificates
     * 
     * @return Returns the array of CA certificates
     */
    public java.security.cert.X509Certificate[] getCACerts() {

        FullTrustAnchor[] anchors = m_storage.getAnchors();

        if (anchors == null) {
            return null;
        }

        X509Certificate[] certs = new X509Certificate[anchors.length];

        for (int n = 0; n < anchors.length; n++) {
            certs[n] = anchors[n].m_caCert;
        }

        LOGGER.debug("getCACerts: returning " + certs.length + " ca certs");

        return certs;
    }

    /**
     * Checks that the subject DN starts with the DN parts of the signer.
     * 
     * @param sub The certificate to check.
     * @param signer The signer cert to take the DN from for the checking.
     * @param proxyType type of this proxy type.
     * @throws CertificateException Thrown in case there is problems in handling the certificates.
     * @see ProxyCertificateInfo
     */
    public void checkDNRestriction(X509Certificate sub, X509Certificate signer, int proxyType)
            throws CertificateException {
        LOGGER.debug("Checking dn restriction");

        DN subDN = DNHandler.getSubject(sub);
        DN signerDN = DNHandler.getSubject(signer);

        try {
            DN subDNWithoutProxy = subDN.withoutLastCN(false);

            if (subDNWithoutProxy.equals(signerDN) != true) {
                throw new CertificateException("The DN [" + subDN + "] doesn't end with [" + signerDN
                        + "] as required for proxy certs");
            }

            if (proxyType == ProxyCertificateInfo.LEGACY_PROXY) {
                String lastCN = subDN.getLastCNValue().toLowerCase();
                if (!lastCN.matches("limited proxy|proxy")) {
                    throw new CertPathValidatorException("Legacy proxy " + subDN.getCanon()
                            + " does not end with \"proxy\" or \"limited proxy\" as required.");
                }
            }

        } catch (Exception e) {
            LOGGER.info("Error while cheking naming constrainst between sub [" + subDN + "] and signer [" + signerDN
                    + " error: " + e + e.getMessage());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("StacTrace: ", e);
            }

            if (e instanceof CertificateException) {
                throw (CertificateException) e;
            }

            throw new CertificateException("Error while cheking naming constrainst between sub [" + subDN
                    + "] and signer [" + signerDN + "] error: " + e + e.getMessage());
        }
    }

    /**
     * Checks whether any trust anchor information has been updated on disk and reloads them if they have.
     * 
     * @throws IOException In case there is unrecoverable trust info reading failure during update.
     * @throws CertificateException In case there is unrecoverable certificate parsing or handling problem during
     *             update.
     * @throws ParseException In case there is an unrecoverable CRL or namespace parsing error during update.
     */
    public void checkUpdate() throws IOException, CertificateException, ParseException {
        if (m_storage != null) {
            m_storage.checkUpdate();
        }
    }

}

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
package org.glite.security.util;

import java.io.File;
import java.io.IOException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.IssuingDistributionPoint;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.glite.security.trustmanager.ContextWrapper;

/**
 * The RevocationChecker implementation for checking the certificate revocation against file system stored CRLs with .r0
 * file ending and CA hash value as file prefix.
 * 
 * @author Joni Hahkala
 */
public class FileCRLChecker extends RevocationChecker {
    /** Logging facility */
    private static final Logger LOGGER = Logger.getLogger(FileCRLChecker.class);

    /** The CRL to use. */
    private X509CRL m_crl = null;

    /*
     * TODO: remove this, do not store ca cert as it's not updated, make new method checkCRL with cacert as input.
     */
    /** The CA certificate for checking the CRL signature and possible extension checks. */
    private X509Certificate m_caCert;

    /** The CA files base name. */
    private String m_caBaseFilename;

    /** The CRL file name. */
    private String m_crlFilename;

    /** The CA number. */
    private int m_caNumber;

    /** The time the CRL file was last modified. */
    private long m_crlModified = -255;

    /** Whether the CRLs are required or not for the CA. */
    private boolean m_crlRequired = true;

    /** Set if the creation of CRL failed, to be printed as the failure later when checking fails. */
    private String failureString = null;

    /** Certificate reader used by all instances of this class, to avoid creating new ones for each. */
    static FileCertReader s_certReader;

    static {
        try {
            s_certReader = new FileCertReader();
        } catch (CertificateException e) {
            throw new RuntimeException("Security provider initialization failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a new instance of the checker.
     * 
     * @param caCert
     * @param caBaseFilename the base CA filename that is used to load the crl.
     * @param caNumber the number of ca with the same DN.
     * @param props the properties to use in case they need to be used for flags etc.
     */
    public FileCRLChecker(X509Certificate caCert, String caBaseFilename, int caNumber, CaseInsensitiveProperties props) {
        super(caCert, caBaseFilename, caNumber, props);
        m_caCert = caCert;
        m_caBaseFilename = caBaseFilename;
        m_caNumber = caNumber;
        m_crlFilename = m_caBaseFilename + FullTrustAnchor.CRL_FILE_ENDING_PREFIX + m_caNumber;

        String crlReqText = null;

        if (props != null) {
            crlReqText = props.getProperty(ContextWrapper.CRL_REQUIRED);
        }

        if (crlReqText != null) {
            crlReqText = crlReqText.trim().toLowerCase();
        } else {
            crlReqText = ContextWrapper.CRL_REQUIRED_DEFAULT;
        }

        if (crlReqText.startsWith("f") || crlReqText.startsWith("n")) {
            m_crlRequired = false;
        }
        checkUpdate(); // load the crl if possible
    }

    /**
     * Checks the CRL for validity.
     * 
     * @throws CertificateException if signature if wrong or the CRL has unsupported or invalid critical extensions.
     * @throws IOException in case CRL extension parsing fails.
     */
    private void checkCrl() throws CertificateException, IOException {
        if (m_crl == null) {
            throw new IllegalArgumentException();
        }

        // check that there are no unhandled critical extensions.
        Set<String> criticalOIDs = m_crl.getCriticalExtensionOIDs();
        if (criticalOIDs != null) {
            Iterator<String> iter = criticalOIDs.iterator();
            while (iter.hasNext()) {
                String oid = iter.next();

                checkExtension(oid);
            }
        }
        // verify that the CRL was signed by the CA.
        try {
            m_crl.verify(m_caCert.getPublicKey());
        } catch (SignatureException e) {
            LOGGER.debug("The crl " + m_crlFilename + " is not signed properly by CA " + DNHandler.getSubject(m_caCert)
                    + ".");
            throw new CertificateException("The crl " + m_crlFilename + " is not signed properly by CA "
                    + DNHandler.getSubject(m_caCert) + ".");
        } catch (Exception e) {
            LOGGER.debug("The verification of crl " + m_crlFilename + " failed: " + e.getMessage());
            throw new CertificateException("The verification of crl " + m_crlFilename + " failed: " + e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.glite.security.util.RevocationChecker#checkUpdate()
     */
    public void checkUpdate() {
        try {
            File crlFile = new File(m_crlFilename);
            long lastModified = crlFile.lastModified();
            if (lastModified != m_crlModified) {
                LOGGER.debug("CRL file changed, reloading it: " + crlFile.getName());
                loadCRL(crlFile.getAbsolutePath());
                // on successful update, remove failure flag
                failureString = null;
            }
        } catch (CertificateException e) {
            // only warn if CRLs are required and only first time, not every update
            if (m_crlRequired && failureString == null) {
                LOGGER.warn("CRL loading for CA file " + m_caBaseFilename + "." + m_caNumber
                        + " failed, the certificates from the CA " + DNHandler.getSubject(m_caCert).getRFCDN()
                        + " will be refused. Error was: " + e.getClass() + ": " + e.getMessage());
            }
            failureString = e.getMessage();
        } catch (IOException e) {
            // only warn if CRLs are required and only first time, not every update
            if (m_crlRequired && failureString == null) {
                LOGGER.warn("CRL loading for CA file " + m_caBaseFilename + "." + m_caNumber
                        + " failed, the certificates from the CA " + DNHandler.getSubject(m_caCert).getRFCDN()
                        + " will be refused. Error was: " + e.getClass() + ": " + e.getMessage());
            }
            failureString = e.getMessage();
        }
    }

    /**
     * Loads the CRL from given path. Also checks the CRL.
     * 
     * @param absolutePath The filename of the CRL to load.
     * @throws CertificateException thrown in case there is problems with the certificate handling.
     * @throws IOException thrown in case the extension parsing fails.
     */
    private void loadCRL(String absolutePath) throws CertificateException, IOException {
        Vector certs = s_certReader.readCRLs(absolutePath);
        if (certs == null || certs.isEmpty()) {
            throw new IOException("No CRL found in " + absolutePath + ".");
        }
        m_crl = (X509CRL) certs.get(0); // only support one CA cert on one file.
        File file = new File(absolutePath);
        m_crlModified = file.lastModified();

        checkCrl();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.glite.security.util.CRLChecker#check(java.security.cert.X509Certificate)
     */
    public void check(X509Certificate cert) throws IOException, CertificateException, CertificateRevokedException {
        DN subDN = DNHandler.getSubject(cert);
        DN caDN = DNHandler.getIssuer(cert);

        // if crl loading had failed, throw exception
        if (failureString != null) {
            throw new CertificateException("CRL checking failed, CRL loading had failed: " + failureString);
        }

        Date now = new Date(System.currentTimeMillis());
        // Check that the CRLs are valid, and either fail or warn depending on the crl required flag.
        Date nextUpdate = m_crl.getNextUpdate();
        if (nextUpdate.before(now)) {
            if (m_crlRequired) {
                LOGGER.info("The certificate " + subDN + " is not in the CRL of " + caDN
                        + ", but the CRL has expired on " + nextUpdate + ", so rejecting this certificate.");
                throw new CertificateRevokedException("The certificate " + subDN + " is not in the CRL of " + caDN
                        + ", but the CRL has expired on " + nextUpdate + ", so rejecting this certificate.");
            }
            LOGGER.warn("The CRL of " + caDN + " has expired on " + nextUpdate
                    + ", but as CRLs are not required, the cert is not rejected.");
        }
        Date thisUpdate = m_crl.getThisUpdate();
        if (thisUpdate.after(now)) {
            if (m_crlRequired) {
                LOGGER.info("The certificate " + subDN + " is not in the CRL of " + caDN
                        + ", but the CRL is not yet valid (valid from " + thisUpdate
                        + "), so rejecting this certificate.");
                throw new CertificateRevokedException("The certificate " + subDN + " is not in the CRL of " + caDN
                        + ", but the CRL is not yet valid (valid from " + thisUpdate
                        + "), so rejecting this certificate.");
            }
            LOGGER.warn("The CRL of " + caDN + " is not yet valid (valid from " + thisUpdate
                    + "), but as CRLs are not required, the cert is not rejected.");
        }

        X509CRLEntry entry = m_crl.getRevokedCertificate(cert.getSerialNumber());

        if (entry != null) {
            throw new CertificateRevokedException("The certificate " + subDN + " is revoked by CA " + caDN + " on "
                    + entry.getRevocationDate() + ".");
        }
    }

    /**
     * Checks the extension with given OID.
     * 
     * @param oid The oid of the extension to check.
     * @throws CertificateException thrown in case there is problems with the certificate handling.
     * @throws IOException thrown in case the extension parsing fails.
     */
    private void checkExtension(String oid) throws CertificateException, IOException {
        if (oid.equals(X509Extensions.DeltaCRLIndicator.getId())) {
            LOGGER.debug("Found DeltaCRLIndicator extension that can't be supported.");
            throw new CertificateException("Usupported critical extension in CRL: DeltaCRLIndicator");
        }
        if (oid.equals(X509Extensions.IssuingDistributionPoint.toString())) {
            checkIssuinDistributionPoint();
            return;
        }

        throw new CertificateException("Unrecognized critical extension in CRL: " + oid);
    }

    /**
     * Checks the issuerDistributionPoint extension, whether it contains unsupported information.
     * 
     * @throws CertificateException thrown in case there is problems with the certificate handling.
     * @throws IOException thrown in case the extension parsing fails.
     */
    private void checkIssuinDistributionPoint() throws CertificateException, IOException {
        byte extensionBytes[] = m_crl.getExtensionValue(X509Extensions.IssuingDistributionPoint.toString());

        ASN1Object object = ASN1Primitive.fromByteArray(extensionBytes);
        if (!(object instanceof DEROctetString)) {
            throw new CertificateException("Invalid data in IssuingDistributionPoint extension, not DEROctetString");
        }
        DEROctetString string = (DEROctetString) object;

        object = ASN1Primitive.fromByteArray(string.getOctets());
        if (!(object instanceof ASN1Sequence)) {
            throw new CertificateException("Invalid data in IssuingDistributionPoint extension, not ASN1Sequence");
        }

        IssuingDistributionPoint issuingDistributionPoint = IssuingDistributionPoint.getInstance((ASN1Sequence) object);

        if (issuingDistributionPoint.onlyContainsAttributeCerts()) {
            throw new CertificateException("CRL only contains attribute certs, not useful for authentication.");
        }

        if (issuingDistributionPoint.getOnlySomeReasons() != null) {
            throw new CertificateException(
                    "CRL only contains some reasons of revocations, can't trust the certificates without other complementing CRL(s), which is not supported.");
        }
    }

}

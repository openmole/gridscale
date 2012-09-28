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

package org.glite.security.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.glite.security.trustmanager.ContextWrapper;
import org.glite.security.util.namespace.EUGridNamespaceFormat;
import org.glite.security.util.namespace.LegacyNamespaceFormat;
import org.glite.security.util.namespace.NamespaceFormat;

/**
 * A class for representing and handling a trust anchor. This class will maintain the trust anchor data and can be used
 * to poll for updates of the information. I handles the CRL and namespace data in addition to the CA certificate.
 * 
 * @author joni.hahkala@cern.ch
 */

public class FullTrustAnchor {
    /** Logging facility. */
    private static final Logger LOGGER = Logger.getLogger(FullTrustAnchor.class);

    /** The suffix of the new format namespace files. */
    public static final String IGTF_NAMESPACE_ENDING = ".namespaces";

    /** The suffix of the old format namespace files. */
    public static final String GLOBUS_NAMESPACE_ENDING = ".signing_policy";

    /** The start of the ending of the CRL files. */
    public static final String CRL_FILE_ENDING_PREFIX = ".r";

    /** The property name holding the class for doing revocation checks. */
    public static final String REVOCATION_CHECKER_CLASS = "revocationChecker";

    /** The default class for making revocation checks. */
    private static final String REVOCATION_CHECKER_CLASS_DEFAULT = "org.glite.security.util.FileCRLChecker";

    /** Certificate reader used by all instances of this class, to avoid creating new ones for each. */
    static FileCertReader s_certReader;

    static {
        try {
            s_certReader = new FileCertReader();
        } catch (CertificateException e) {
            throw new RuntimeException("Security provider initialization failed: " + e.getMessage(), e);
        }
    }

    /** The 8 byte hash of the DN of this CA. */
    public String m_caHash;

    /** The filename of the CA files without the '.' and ending. */
    public String m_baseFilename;

    /** The running number of the CA, used for the ending. */
    public int m_caNumber;

    /** The CA certificate. */
    public X509Certificate m_caCert;

    /** The time the CA file was last modified. */
    public long m_caModified;

    /** The namespace object of this CA. */
    public NamespaceFormat m_namespace;

    /** The filename of the namespace in use. */
    public String m_namespaceFilename;

    /** The time the namespace file was last modified. */
    public long m_namespaceModified;

    /** The time the CA dir was last polled for changes. */
    public long m_lastUpdateCheck;

    /** The revocation checker instance. */
    public RevocationChecker m_revChecker;

    /** The revocation checker class definition. */
    private String m_revCheckerClass;

    /** The initial properties. */
    private CaseInsensitiveProperties m_props;

    /** Whether the CRL support is enabled */
    public boolean m_crlEnabled = true;

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("TrustAnchor hash: ");
        buf.append(m_caHash);
        buf.append(" DN: ");
        buf.append(DNHandler.getSubject(m_caCert).toString());
        buf.append("\n modified: ");
        buf.append(m_caModified);
        buf.append("\n RevocationChecker: ");
        buf.append(m_revChecker);
        buf.append("\n nameSpace from " + m_namespaceFilename + ": ");
        buf.append(m_namespace);
        buf.append("\n namespace modified: ");
        buf.append(m_namespaceModified);
        buf.append("\n last update check: ");
        buf.append(m_lastUpdateCheck);
        return buf.toString();
    }

    /**
     * Initializes the TrustAnchor from the filename given, loading the corresponding CRL and namespace definition.
     * 
     * @param caFilename The filename of the CA file.
     * @throws IOException Thrown if the file opening or reading fails.
     * @throws CertificateException Thrown in case the Certificate parsing fails.
     * @throws ParseException Thrown in case the namespace parsing fails.
     */
    FullTrustAnchor(String caFilename, CaseInsensitiveProperties props) throws IOException, CertificateException {
        initCAInfo(caFilename, props);
    }

    /**
     * Initializes the information of the CA.
     * 
     * @param caFilename the filename of the CA certificate.
     * @param props the settings to use and to pass to child classes.
     */
    @SuppressWarnings("boxing")
    private void initCAInfo(String caFilename, CaseInsensitiveProperties props) throws IOException,
            CertificateException {
        if (caFilename == null || caFilename.length() == 0) {
            throw new IOException("Can't initialize a trustanchor without filename.");
        }

        CAFilenameSplitter parts = CAFilenameSplitter.splitCAFilename(caFilename);

        m_caHash = parts.m_hash;
        m_caNumber = parts.m_number;
        m_baseFilename = parts.m_baseFilename;
        m_props = props;

        loadCACert(m_baseFilename + "." + m_caNumber);
        try {
            m_caCert.checkValidity(); // check that the CA is acceptable.
        } catch (CertificateNotYetValidException e) {
            throw new CertificateNotYetValidException(DNHandler.getSubject(m_caCert).getRFCDN() + " " + e.getMessage());
        } catch (CertificateExpiredException e) {
            throw new CertificateExpiredException(DNHandler.getSubject(m_caCert).getRFCDN() + " " + e.getMessage());
        }

        /* Check that the CA has required CA basic constraints setting */
        if (m_caCert.getBasicConstraints() == -1) {
            LOGGER.error("The CA certificate " + DNHandler.getSubject(m_caCert).getRFCDN()
                    + " is an invalid CA as it doesn't have the required CA basic constraints extension.");
            throw new CertificateException("The CA certificate " + DNHandler.getSubject(m_caCert).getRFCDN()
                    + " is an invalid CA as it doesn't have the required CA basic constraints extension.");
        }

        /* Check that the CA has required keyCertSign bit set */
        if (m_caCert.getKeyUsage() == null || m_caCert.getKeyUsage()[5] != true) {
            LOGGER.error("The CA certificate " + DNHandler.getSubject(m_caCert).getRFCDN()
                    + " is an invalid CA as it doesn't have the required keyCertSign flag set.");
            throw new CertificateException("The CA certificate " + DNHandler.getSubject(m_caCert).getRFCDN()
                    + " is an invalid CA as it doesn't have the required keyCertSign flag set.");
        }
        // force parsing at least some of the cert to force it to fail if there are errors in it.
        m_caCert.getNonCriticalExtensionOIDs();

        // see if the CRL support is disabled
        if (props != null) {
            String crlEnabledText = props.getProperty(ContextWrapper.CRL_ENABLED);

            if (crlEnabledText != null) {
                crlEnabledText = crlEnabledText.trim().toLowerCase();
            } else {
                crlEnabledText = ContextWrapper.CRL_ENABLED_DEFAULT;
            }

            if (crlEnabledText.startsWith("f") || crlEnabledText.startsWith("n")) {
                m_crlEnabled = false;
            }
        }

        // if CRL support is enabled, load and initialize the revocation checker
        if (m_crlEnabled == true) {
            if (props != null) {
                m_revCheckerClass = props.get(REVOCATION_CHECKER_CLASS);
            }
            if (m_revCheckerClass == null || m_revCheckerClass.length() < 1) {
                m_revCheckerClass = REVOCATION_CHECKER_CLASS_DEFAULT;
            }

            tryInitRevocationChecker();
        }

        // allow namespace loading to fail, namespaces will not be restricted in that case.
        tryLoadNamespace(m_baseFilename);
    }

    /**
     * Tries to initialize the revocation checker for this CA. Will warn if it fails.
     */
    private void tryInitRevocationChecker() {
        // allow CRL loading to fail, CA will most likely be disabled though.
        try {
            Class c = Class.forName(m_revCheckerClass);
            Constructor<RevocationChecker> constructor = c.getConstructor(X509Certificate.class, String.class,
                    int.class, CaseInsensitiveProperties.class);

            m_revChecker = constructor.newInstance(m_caCert, m_baseFilename, m_caNumber, m_props);
        } catch (InvocationTargetException e) {
            // failed call to constructor.newIstance
            LOGGER.warn("Certificate revocation checker creation for CA " + m_baseFilename + "." + m_caNumber
                    + " failed, depending on configuration the certificates from the CA "
                    + DNHandler.getSubject(m_caCert).getRFCDN() + " might be refused. Error was: "
                    + e.getCause().getMessage());
        } catch (Exception e) {
            LOGGER.warn(
                    "Certificate revocation checker for CA " + m_baseFilename + "." + m_caNumber
                            + " failed, depending on configuration the certificates from the CA "
                            + DNHandler.getSubject(m_caCert).getRFCDN() + " might be refused. Error was: "
                            + e.getClass(), e);
        }

    }

    /**
     * Loads the CA cert and sets the modified time.
     * 
     * @param filename The name of the file to load.
     * @throws CertificateException Thrown in case the cert parsing fails.
     * @throws IOException Thrown in case the file opening or reading fails.
     */
    void loadCACert(String filename) throws CertificateException, IOException {
        Vector certs = s_certReader.readCerts(filename);
        m_caCert = (X509Certificate) certs.get(0); // only support one CA cert on one file.
        File file = new File(filename);
        m_caModified = file.lastModified();

    }

    /**
     * Tries to load namespaces, if fails, log a warning and continue.
     * 
     * @param baseFilename The basis filename, without ending that depends on which type gets used.
     */
    void tryLoadNamespace(String baseFilename) {
        // allow namespace loading to fail, namespaces will not be restricted in that case.
        try {
            loadNamespace(baseFilename);
        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.warn("Namespace restrictions loading for CA " + m_baseFilename + "." + m_caNumber
                        + " failed, namespace is not restricted. Error was: " + e.getMessage(), e);

            } else {
                LOGGER.warn("Namespace restrictions loading for CA " + m_baseFilename + "." + m_caNumber
                        + " failed, namespace is not restricted. Error was: " + e.getMessage());
            }
        }
    }

    /**
     * Loads the namespace of this CA, trying first to find the IGTF format and the the legacy format.
     * 
     * @param filenameBase The base filename of the file to load.
     * 
     * @throws IOException Thrown in case the file opening or reading fails.
     * @throws ParseException in case namespace parsing fails.
     */
    void loadNamespace(String filenameBase) throws IOException, ParseException {
        // try to load first the new namespace format
        File file = new File(filenameBase + IGTF_NAMESPACE_ENDING);

        if (file.exists()) {
            try {
                m_namespaceFilename = filenameBase + IGTF_NAMESPACE_ENDING;
                m_namespace = new EUGridNamespaceFormat();
                m_namespace.parse(m_namespaceFilename);
                m_namespaceModified = file.lastModified();
                LOGGER.debug("loaded: " + m_namespaceFilename);
                return;
            } catch (ParseException e) { // fallback to the old globus format in case loading fails
                LOGGER.warn("Parsing of " + m_namespaceFilename + " failed! Falling back to the "
                        + GLOBUS_NAMESPACE_ENDING + " file. Error was: " + e.getMessage());
            }
        }
        // fallback to the old globus format in case new format is not found
        m_namespaceFilename = filenameBase + GLOBUS_NAMESPACE_ENDING;
        m_namespace = new LegacyNamespaceFormat();
        m_namespace.parse(m_namespaceFilename);
        file = new File(m_namespaceFilename);
        m_namespaceModified = file.lastModified();
        LOGGER.debug("loaded: " + m_namespaceFilename);
    }

    /**
     * Checks whether there is a need to update and reloads the corresponding file if needed. Will fall back to legacy
     * namespace if the IGTF format one is removed.
     * 
     * @throws CertificateException Thrown in case the CA cert or CRL parsing fails.
     * @throws IOException Thrown in case a file opening or reading fails.
     * @throws ParseException Thrown in case the namespace parsing fails.
     * @throws CertificateNotFoundException Thrown in case the CA cert is removed.
     */
    void checkUpdate() throws CertificateException, IOException, CertificateNotFoundException {
        try {
            File caFile = new File(m_baseFilename + "." + m_caNumber);
            if (caFile.exists()) {
                if (caFile.lastModified() != m_caModified) {
                    LOGGER.debug("CA file changed, reloading it: " + caFile);
                    loadCACert(caFile.getAbsolutePath());
                }
            } else {
                throw new CertificateNotFoundException("The CA file " + caFile.getName() + " can't be found anymore.");
            }
        } catch (CertificateNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificateException("Error loading a CA: " + e.getMessage());
        }

        if (m_revChecker != null) {
            m_revChecker.checkUpdate();
        } else {
            if (m_crlEnabled == true) {
                tryInitRevocationChecker();
            }
        }

        // if the current namespacefile is of the old format, check whether there is new format and try to load that
        // one.
        if (!m_namespaceFilename.equals(m_baseFilename + IGTF_NAMESPACE_ENDING)) {
            File namespaceFile = new File(m_baseFilename + IGTF_NAMESPACE_ENDING);
            LOGGER.debug("new format namespace found when old format used, trying to load new format: "
                    + namespaceFile.getName());
            tryLoadNamespace(m_baseFilename);
        } else {
            File namespaceFile = new File(m_namespaceFilename);
            if (namespaceFile.lastModified() != m_namespaceModified) {
                LOGGER.debug("Namespace file changed, reloading it: " + namespaceFile.getName());
                tryLoadNamespace(m_baseFilename);
            }
        }
    }
}

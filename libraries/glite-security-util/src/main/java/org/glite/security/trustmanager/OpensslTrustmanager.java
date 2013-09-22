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

import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.logging.Level;

import javax.net.ssl.X509TrustManager;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.glite.security.util.CaseInsensitiveProperties;

/**
 * @author Joni Hahkala
 */
public class OpensslTrustmanager implements X509TrustManager {

    /**
     * The logging facility for this class.
     */
    private static final Logger LOGGER = Logger.getLogger(OpensslTrustmanager.class);
    /**
     * The validator for the path verification.
     */
    private OpensslCertPathValidator m_validator = null;

    /**
     * @param dir The trust anchor directory (often /etc/grid-security/certificates)
     * @param crlRequired Whether the CRLs are required. If they are and the CRL is absent or expired all certs from that
     *            CA are rejected.
     * @throws IOException in case there is a read error during reading of CA certs, CRLs or namespace files.
     * @throws CertificateException in case there is problems handling the CA certs.
     * @throws ParseException in case there is problems parsing the namespace files.
     * @throws NoSuchProviderException in case Bouncycastle provider is not found and initialization fails.
     * @deprecated use constructor OpensslTrustmanager(String, boolean, CaseInsensitiveProperties) instead
     */
    public OpensslTrustmanager(String dir, boolean crlRequired) throws IOException, CertificateException,
            ParseException, NoSuchProviderException {
        this.m_validator = new OpensslCertPathValidator(dir, crlRequired, null);
    }

    /**
     * @param dir The trust anchor directory (often /etc/grid-security/certificates)
     * @param crlRequired Whether the CRLs are required. If they are and the CRL is absent or expired all certs from that
     *            CA are rejected.
     * @param props the properties to pass along for child classes to use. 
     * @throws IOException in case there is a read error during reading of CA certs, CRLs or namespace files.
     * @throws CertificateException in case there is problems handling the CA certs.
     * @throws ParseException in case there is problems parsing the namespace files.
     * @throws NoSuchProviderException in case Bouncycastle provider is not found and initialization fails.
     */
    public OpensslTrustmanager(String dir, boolean crlRequired, CaseInsensitiveProperties props) throws IOException, CertificateException,
            ParseException, NoSuchProviderException {
        this.m_validator = new OpensslCertPathValidator(dir, crlRequired, props);
    }

    /*
     * (non-Javadoc)
     * @see javax.net.ssl.X509TrustManager#checkClientTrusted(java.security.cert.X509Certificate[], java.lang.String)
     */
    public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
        LOGGER.debug("CheckClientTrusted cert=" + arg0[0] + "number of certs: " + arg0.length + " string= " + arg1);
        try {
            m_validator.check(arg0);
        } catch (Exception e) {
            LOGGER.info("The certificate validation for [" + arg0[0].getSubjectDN() + "] failed: " + e.getClass().getName() + " error was: " + e.getMessage());
            throw new CertificateException(e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * @see javax.net.ssl.X509TrustManager#checkServerTrusted(java.security.cert.X509Certificate[], java.lang.String)
     */
    public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
        LOGGER.debug("CheckServerTrusted cert=" + arg0[0] + " string= " + arg1);
        try {
        	checkClientTrusted(arg0, arg1);
        } catch (CertificateException e) {
            LOGGER.error("The certificate validation for [" + arg0[0].getSubjectDN() + "] failed: " + e.getMessage());
            throw e;
        }
    }

    /*
     * (non-Javadoc)
     * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
     */
    public X509Certificate[] getAcceptedIssuers() {
        LOGGER.debug("getAcceptedIssuers");
        if (m_validator != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("getAcceptedIssuers returning:");
                X509Certificate[] certs = m_validator.getCACerts();
                for (int i = 0; i < certs.length; i++) {
                    LOGGER.debug(i + ": " + certs[i].getSubjectDN());
                }
            }

            return m_validator.getCACerts();
        }

        return null;
    }

    /**
     * Generates the hex hash of the DN used by openssl to name the CA certificate files. The hash is actually the hex
     * of 8 least significant bytes of a MD5 digest of the the ASN.1 encoded DN.
     * 
     * @param subject the DN to hash.
     * @return the 8 character string of the hexadecimal hash.
     */
    @SuppressWarnings("boxing")
    public static String getOpenSSLCAHash(X509Name subject) {
        try {
            byte[] bytes = subject.getEncoded(ASN1Encoding.DER);

            MD5Digest digest = new MD5Digest();
            digest.update(bytes, 0, bytes.length);

            byte output[] = new byte[16];

            digest.doFinal(output, 0);

            StringBuffer outputString = new StringBuffer();

            outputString.append(String.format("%02x", output[3] & 0xFF));
            outputString.append(String.format("%02x", output[2] & 0xFF));
            outputString.append(String.format("%02x", output[1] & 0xFF));
            outputString.append(String.format("%02x", output[0] & 0xFF));

            return outputString.toString();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Checks whether the trustanchors need updates and if they do updates them.
     * 
     * @throws IOException thrown in case a file reading fails.
     * @throws CertificateException thrown if there are problems with the certificates.
     * @throws ParseException thrown in case there are problems parsing certificates, CRLs or namespaces.
     */
    public void checkUpdate() throws IOException, CertificateException, ParseException {
        if (m_validator != null) {
            m_validator.checkUpdate();
        }
    }

    /**
     * Generates the hex hash of the DN used by openssl to name the CA certificate files. The hash is actually the hex
     * of 8 least significant bytes of a MD5 digest of the the ASN.1 encoded DN.
     * 
     * @param cert the certificate from which the subject DN is taken and hashed.
     * @return the 8 character string of the hexadecimal hash.
     */
    public static String getOpenSSLCAHash(X509Certificate cert) {
        X509Name subject = (X509Name) cert.getSubjectDN();

        return getOpenSSLCAHash(subject);
    }

}

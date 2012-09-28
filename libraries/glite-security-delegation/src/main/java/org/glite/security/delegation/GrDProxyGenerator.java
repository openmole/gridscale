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

package org.glite.security.delegation;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.glite.security.util.FileCertReader;
import org.glite.security.util.PrivateKeyReader;

/**
 * Generate a proxy certificate.
     * @deprecated Use proxy generator from util-java
 */
@SuppressWarnings("unused")
public class GrDProxyGenerator {
    static Logger logger = Logger.getLogger(GrDProxyGenerator.class);
    private PrivateKey userPrivateKey = null;
    private String pwd = null;
    private int proxyType;
    private int pathLen = -1;
    private boolean limited = true;
    private String proxyFile = GrDPX509Util.getDefaultProxyFile();
    private String keyFile = null;
    private String certFile = null;
    private X509Certificate certificate;
    private int bits = 512;
    private int lifetime = 3600 * 12;
    private boolean quiet = false;
    private boolean debug = false;
    private X509Certificate certProxy = null;

    public GrDProxyGenerator() {
        if (Security.getProvider("BC") == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 6);
        }
    }

    /**
     * Creates a proxy certificate from a certificate request
     * 
     * @param inCertReq
     *            Certificate request
     * @param inUserCert
     *            Issuer certificate
     * @param inUserKey
     *            Issuer privateKey
     * @param pwd
     *            Issuer password
     * @return chaine of certificate containing proxy in first place
     * @deprecated Use proxy generator from util-java
     */
    public byte[] x509MakeProxyCert(byte[] inCertReq, byte[] inUserCert, byte[] inUserKey, String pwd1)
            throws CertificateException, GeneralSecurityException, Exception {
        X509Certificate[] userCert = null;
        PrivateKey pvk = null;

        // Read certificate request
        InputStream inTCertReq = null;

        inTCertReq = new ByteArrayInputStream(GrDPX509Util.readPEM(
                new ByteArrayInputStream(inCertReq), GrDPConstants.CRH,
                GrDPConstants.CRF));

        if ((inUserCert != null) && (inUserKey != null)) {
            // Reading chain of certificates from input stream
            userCert = GrDPX509Util.loadCertificateChain(new BufferedInputStream(new ByteArrayInputStream(inUserCert)));

            if (userCert.length <= 0) {
                logger.error("Invalid user certificate. Number of certificates in chain : " + userCert.length);
                throw new GeneralSecurityException("Invalid user certificate.");
            }

            pvk = PrivateKeyReader.read(new BufferedInputStream(new ByteArrayInputStream(inUserKey)), pwd1);
        } else {
            logger.error("Error, CreateProxyFromCertReq :: UserCertificate and UserKey can not be null.");
            throw new CertificateException("Error, CreateProxyFromCertReq :: UserCertificate and UserKey can not be null.");
        }

        // Loading chian of certificates
        X509Certificate[] cp = new X509Certificate[userCert.length + 1];

        ASN1InputStream derin = new ASN1InputStream(inTCertReq);
        DERObject reqInfo = derin.readObject();
        PKCS10CertificationRequest certReq = new PKCS10CertificationRequest(
                (DERSequence) reqInfo);
        logger.debug("Number of Certificates in chain : "
                + Integer.toString(userCert.length));

        if (!certReq.verify()) {
            throw new GeneralSecurityException(
                    "Certificate request verification failed!");
        }

        // Generating proxy certificate
        cp[0] = createProxyCertificate(userCert[0], pvk,
                certReq.getPublicKey(), lifetime, proxyType, "proxy");

        for (int index = 1; index <= userCert.length; ++index)
            cp[index] = userCert[index - 1];

        certProxy = cp[0];

        return GrDPX509Util.certChainToByte(cp);
    }

    /**
     * Creates a proxy certificate from a certificate request and a proxy
     * certificate
     * 
     * @param inCertReq
     *            Certificate request
     * @param inProxy
     *            user proxy
     * @param password
     *            Issuer password
     * @return chaine of certificate containing proxy in first place
     * @deprecated As of v1.5 replaced by {@link #x509MakeProxyCert(byte[],byte[])}
     */
    public byte[] x509MakeProxyCert(byte[] inCertReq, byte[] inProxy,
            String password) throws IOException, GeneralSecurityException {
        // calling the new method ignoring the password
        return x509MakeProxyCert(inCertReq, inProxy);
    }

    /**
     * Creates a proxy certificate from a certificate request and a proxy
     * certificate
     * 
     * @param inCertReq
     *            Certificate request
     * @param inProxy
     *            user proxy certificate 
     *            
     * @return chaine of certificate containing proxy in first place
     * @deprecated Use proxy generator from util-java
     */
    public byte[] x509MakeProxyCert(byte[] inCertReq, byte[] inProxy) 
        throws IOException, GeneralSecurityException {

        // Holds the cert chain loaded from the proxy file
        X509Certificate[] proxyCertChain = null;

        // Holds the priv key loaded from the proxy file
        PrivateKey proxyPrivKey = null;

        // Holds the final certificate chain of the proxy
        X509Certificate[] finalCertChain = null;

        // Load the proxy certificate chain
        proxyCertChain = GrDPX509Util
                .loadCertificateChain(new BufferedInputStream(
                        new ByteArrayInputStream(inProxy)));

        // Check for null arguments
        if (inCertReq == null || inProxy == null) {
            throw new GeneralSecurityException(
                    "Either the cert request or proxy cert were passed as null arguments."
                            + " Cannot continue.");
        }

        // Check for a valid chain
        if (proxyCertChain.length <= 0) {
            throw new GeneralSecurityException("Invalid number of certificates in proxy chain: "
                    + proxyCertChain.length);
        }
        logger.debug("Number of certificates in proxy chain: " + proxyCertChain.length);

        // Reading private key form proxy file
        FileCertReader fileReader = new FileCertReader();
        KeyStore store = fileReader.readProxy(new BufferedInputStream(
                new ByteArrayInputStream(inProxy)), "keypair");
        proxyPrivKey = (PrivateKey) store.getKey("host", "keypair"
                .toCharArray());

        // Load the certificate request
        InputStream inTCertReq = new ByteArrayInputStream(GrDPX509Util.readPEM(
                new ByteArrayInputStream(inCertReq), GrDPConstants.CRH,
                GrDPConstants.CRF));
        ASN1InputStream derin = new ASN1InputStream(inTCertReq);
        DERObject reqInfo = derin.readObject();
        PKCS10CertificationRequest certReq = new PKCS10CertificationRequest(
                (DERSequence) reqInfo);

        // Verify cert request validity
        if (!certReq.verify()) {
            throw new GeneralSecurityException(
                    "Certificate request verification failed!");
        }

        // Generating proxy certificate
        finalCertChain = new X509Certificate[proxyCertChain.length + 1];
        finalCertChain[0] = createProxyCertificate(proxyCertChain[0],
                proxyPrivKey, certReq.getPublicKey(), lifetime, proxyType,
                "proxy");

        for (int i = 0; i < proxyCertChain.length; ++i) {
            finalCertChain[i + 1] = proxyCertChain[i];
        }

        // TODO: this should be removed at some point
        certProxy = finalCertChain[0];

        return GrDPX509Util.certChainToByte(finalCertChain);
    }

    /**
     * Creates a proxy certificate from a certificate request
     * 
     * @param inCertReq
     *            Certificate request
     * @param inUserCert
     *            Issuer certificate
     * @param inUserKey
     *            Issuer privateKey
     * @param pwd
     *            Issuer password
     * @return chaine of certificate containing proxy in first place
     * @deprecated Use proxy generator from util-java
     */
    public X509Certificate[] createProxyFromCertReq(InputStream inCertReq,
            BufferedInputStream inUserCert, InputStream inUserKey, String pwd1)
            throws GeneralSecurityException, IOException, Exception {

        X509Certificate[] userCert = null;
        PrivateKey userPrivKey = null;
        PKCS10CertificationRequest certRequest = null;
        X509Certificate[] proxyCert = null;

        // Load the user certificate
        userCert = GrDPX509Util.loadCertificateChain(inUserCert);
        logger.debug("User Certificate - number of certificates in chain: "
                + userCert.length);

        // Load the private key
        userPrivKey = PrivateKeyReader.read(new BufferedInputStream(inUserKey),
                pwd1);

        // Load the certificate request
        ASN1InputStream derin = new ASN1InputStream(new ByteArrayInputStream(
                GrDPX509Util.readPEM(inCertReq, GrDPConstants.CRH,
                        GrDPConstants.CRF)));
        DERObject reqInfo = derin.readObject();
        certRequest = new PKCS10CertificationRequest(
                (DERSequence) reqInfo);

        // Initialize the proxy certificate chain
        proxyCert = new X509Certificate[userCert.length + 1];

        // Verify integrity of certificate request
        if (!certRequest.verify()) {
            throw new GeneralSecurityException(
                    "Certificate request verification failed.");
        }

        // Create the proxy certificate
        proxyCert[0] = createProxyCertificate(userCert[0], userPrivKey,
                certRequest.getPublicKey(), lifetime, proxyType, "proxy");

        // Complete the proxy certificate chain
        for (int index = 1; index <= userCert.length; ++index)
            proxyCert[index] = userCert[index - 1];

        certProxy = proxyCert[0];

        return proxyCert;
    }

    /**
     * Creates a proxy certificate from existing certificate
     * 
     * @param inCert
     *            User's certificate
     * @param inCACert
     *            issuer certificate
     * @param inCAKey
     *            issuer private key
     * @param caPwd
     *            issuer password
     * @return Created X509 proxy certificate
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws InvalidKeyException
     * @throws GeneralSecurityException
     * @deprecated Use proxy generator from util-java
     */
    public X509Certificate createProxyFromCert(InputStream inCert,
            InputStream inCACert, InputStream inCAKey, String caPwd)
            throws IOException, NoSuchAlgorithmException,
            NoSuchProviderException, InvalidKeyException,
            GeneralSecurityException {
        X509Certificate issuerCert = GrDPX509Util.loadCertificate(inCACert);
        X509Certificate rqCert = GrDPX509Util.loadCertificate(inCert);

        PublicKey publicKey = rqCert.getPublicKey();
        PrivateKey issuerKey = null;

        try {
            issuerKey = PrivateKeyReader.read(new BufferedInputStream(inCAKey),
                    caPwd);
        } catch (Exception e) {
            logger
                    .error("Error : createProxyFromCert , unable to load private key");
        }

        X509Certificate result = createProxyCertificate(issuerCert, issuerKey,
                publicKey, lifetime, proxyType, null);
        certProxy = result;

        return result;
    }

    /**
     * Getting created proxy certificate
     * 
     * @return x509 certificate
     * @deprecated Use proxy generator from util-java
     */
    public X509Certificate getCertProxy() {
        return certProxy;
    }

    /**
     * Save proxy certificate to file
     * 
     * @throws IOException
     * @throws CertificateEncodingException
     * @deprecated Use proxy generator from util-java
     */
    public void saveCertProxyTofile() throws IOException,
            CertificateEncodingException {
        FileOutputStream os = new FileOutputStream(proxyFile);

        if (!GrDPX509Util.changeFileMode(proxyFile, 600)) {
            System.err
                    .println("Warning: Please check file permissions for your proxy file.");
        }

        String s = GrDPX509Util.writePEM(certProxy.getEncoded(),
                GrDPConstants.CH + GrDPConstants.NEWLINE, GrDPConstants.CF
                        + GrDPConstants.NEWLINE);
        os.write(s.getBytes());
        os.close();
    }

    /**
     * Save proxy certificate to file
     * 
     * @param delegationID
     *            proxy delegation ID to be added in proxy file
     * @param userDN
     *            the user DN to be added in proxy file
     * @throws IOException
     * @throws CertificateEncodingException
     * @deprecated Use proxy generator from util-java
     */
    public void saveCertProxyTofile(String delegationID, String userDN)
            throws IOException, CertificateEncodingException {
        FileOutputStream os = new FileOutputStream(proxyFile);

        if (!GrDPX509Util.changeFileMode(proxyFile, 600)) {
            System.err
                    .println("Warning: Please check file permissions for your proxy file.");
        }

        String s = GrDPX509Util.writePEM(certProxy.getEncoded(),
                GrDPConstants.CH + GrDPConstants.NEWLINE, GrDPConstants.CF
                        + GrDPConstants.NEWLINE);
        s = delegationID + "\n" + userDN + "\n" + s;
        os.write(s.getBytes());
        os.close();
    }

    /**
     * Set the number of bits
     * 
     * @param bits
     *            number of bits
     * @deprecated Use proxy generator from util-java
     */
    public void setBits(int bits) {
        this.bits = bits;
    }

    /**
     * Set the life time
     * 
     * @param hours
     *            life time of proxy
     * @deprecated Use proxy generator from util-java
     */
    public void setLifetime(int hours) {
        this.lifetime = hours * 3600;
    }

    /**
     * Set the proxy type
     * 
     * @param proxyType
     * @deprecated Use proxy generator from util-java
     */
    public void setProxyType(int proxyType) {
        this.proxyType = proxyType;
    }

    /**
     * Set proxy as limited proxy
     * @deprecated Use proxy generator from util-java
     */
    public void setProxyAslimited() {
        this.limited = true;
    }

    /**
     * Set path length of proxy
     * 
     * @param pathLength
     * @deprecated Use proxy generator from util-java
     */
    public void setPathLength(int pathLength) {
        this.pathLen = pathLength;
    }

    /**
     * Set proxyFile name
     * 
     * @param proxyFile
     *            File name that proxy should be saved to
     * @deprecated Use proxy generator from util-java
     */
    public void setProxyFile(String proxyFile) {
        this.proxyFile = proxyFile;
    }

    /**
     * Set key file needed to create proxy
     * 
     * @param keyFile
     * @deprecated Use proxy generator from util-java
     */
    public void setKeyFile(String keyFile) {
        this.keyFile = keyFile;
    }

    /**
     * Set certificate file needed to create proxy
     * 
     * @param certFile
     * @deprecated Use proxy generator from util-java
     */
    public void setCertFile(String certFile) {
        this.certFile = certFile;
    }

    /**
     * Create a proxy certificate from a given certificate
     * 
     * @param issuerCert
     *            issuer certificate
     * @param issuerKey
     *            issuer private key
     * @param publicKey
     *            public key of delegatee
     * @param lifetime
     *            life time of proxy
     * @param proxyType
     *            type of proxy
     * @param cnValue
     *            common name of proxy
     * @return created proxy certificate
     * @throws GeneralSecurityException
     * @deprecated Use proxy generator from util-java
     */
    public X509Certificate createProxyCertificate(X509Certificate issuerCert,
            PrivateKey issuerKey, PublicKey publicKey, int lifetime1,
            int proxyType1, String cnValue) throws GeneralSecurityException {
        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();

        BigInteger serialNum = null;
        serialNum = issuerCert.getSerialNumber();

        X509Name issuer = (X509Name) issuerCert.getSubjectDN();

        ASN1Sequence seqSubject = (ASN1Sequence) issuer.getDERObject();

        logger.debug("SubjectDN of IssuerCert" + issuer);

        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(X509Name.CN);
        v.add(new DERPrintableString(cnValue));

        Enumeration subjectParts = seqSubject.getObjects();

        ASN1EncodableVector subjectVector = new ASN1EncodableVector();

        while (subjectParts.hasMoreElements()) {
            DERObject part = (DERObject) subjectParts.nextElement();
            subjectVector.add(part);
        }

        subjectVector.add(new DERSet(new DERSequence(v)));

        DERSequence subjDerSeq = new DERSequence(subjectVector);

        X509Name subjectX = new X509Name(subjDerSeq);

        logger.debug("SubjectDN :" + subjectX);

        certGen.setSubjectDN(subjectX);
        certGen.setIssuerDN(issuer);

        certGen.setSerialNumber(serialNum);
        certGen.setPublicKey(publicKey);
        certGen.setSignatureAlgorithm(issuerCert.getSigAlgName());
        certGen.addExtension(X509Extensions.KeyUsage, false, new KeyUsage(
                KeyUsage.dataEncipherment | KeyUsage.digitalSignature));

        GregorianCalendar date = new GregorianCalendar(TimeZone
                .getTimeZone("UTC"));

        date.add(Calendar.MINUTE, -5);
        certGen.setNotBefore(date.getTime());

        if (lifetime1 <= 0) {
            certGen.setNotAfter(issuerCert.getNotAfter());
        } else {
            date.add(Calendar.MINUTE, 5);
            date.add(Calendar.SECOND, lifetime1);
            certGen.setNotAfter(date.getTime());
        }

        return certGen.generateX509Certificate(issuerKey);
    }

}

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
package org.glite.security.util.proxy;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.logging.Level;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.glite.security.util.PrivateKeyReader;

/**
 * A class to make proxy certificates.
 * 
 * @author Joni Hahkala
 */
public class ProxyCertificateGenerator {
    /** The logging facility. */
    private static final Logger LOGGER = Logger.getLogger(ProxyCertificateGenerator.class);

    /** The parent cert. */
    private X509Certificate m_parentCert = null;
    /** The parent cert chain. */
    private X509Certificate[] m_parentCertChain = null;
    /** The issuer name the basis for the new DN. */
    private X509Name m_baseName = null;
    /** The private key of the new proxy, can be null as it is only needed for getting the whole proxy as pem, have to be
     * explicitly set if needed and not generated. */
    private PrivateKey m_privateKey = null;
    /** The public key of the new proxy, can be given as a request or by explicitly setting it. */
    private PublicKey m_publicKey = null;
    /** The 12h default lifetime of the new proxy. */
    private int m_lifetime = 12 * 60 * 60;
    /** The certificate generator to use. */
    private X509V3CertificateGenerator m_certGen = null;
    /** The new generated proxy. */
    private X509Certificate m_newProxy = null;
    /** The serial number of the new proxy. */
    private BigInteger m_serialNumber = null;
    /** The oid of the rfc proxy policy. */
    private String m_proxyPolicyOID = null;
    /** The contents of the rfc proxy policy. */
    private DEROctetString m_proxyPolicyOctets = null;
    /** The DN of the new proxy. */
    private X509Name m_newDN = null;
    /** The type of the proxy. */
    private int m_type = -1;
    /** Whether the proxy is limited (invalid for job submission) or not. */
    private boolean m_limited = false;
    /** The message digest algorithm to use, take it from the given parent certificate. */
    private String m_hashAlgorithm = null;
    /** The keylegth to use for this proxy. */
    private int m_keyLength = DEFAULT_KEY_LENGTH;
    /** The proxy path length limit for rfc proxies. */
    private int m_pathLenLimit = ProxyCertInfoExtension.UNLIMITED;

    /** The default proxy type if none is set and the cert given is end entity cert. Default is RFC3820 proxy. */
    public static final int DEFAULT_PROXY_TYPE = ProxyCertificateInfo.RFC3820_PROXY; // the default type of proxy
    /** The default key length for the proxy (1024 bits). */
    public static final int DEFAULT_KEY_LENGTH = 1024;

    /** static class constructor to make sure the Boucycastle provider is set */
    static {
        if (Security.getProvider("BC") == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 6);
        }
    }

    /**
     * Create a new proxy cert generator based on the parent cert chain. Useful when locally creating a proxy from
     * existing cert chain.
     * 
     * @param parentCertChain the parent certificate chain of the proxy.
     */
    public ProxyCertificateGenerator(X509Certificate[] parentCertChain) {
        m_parentCertChain = parentCertChain;
        m_parentCert = parentCertChain[0];
        m_baseName = (X509Name) m_parentCert.getSubjectDN();
        m_hashAlgorithm = m_parentCert.getSigAlgName();

        ProxyCertificateInfo parentProxyInfo = new ProxyCertificateInfo(m_parentCert);

        // the new proxy will be of the same type as the parent.
        // the type can also be unknown, which will be handled in generate()
        m_type = parentProxyInfo.getProxyType();
        m_certGen = new X509V3CertificateGenerator();
    }

    /**
     * Create a new proxy cert generator based on the parent cert. Useful when locally creating a proxy from existing
     * cert.
     * 
     * @param parentCert the parent certificate chain of the proxy.
     */
    public ProxyCertificateGenerator(X509Certificate parentCert) {
        this(new X509Certificate[] { parentCert });
    }

    /**
     * Create a new proxy cert generator based on certification request and a certificate chain. Used for example when
     * creating a proxy certificate on the client side from certificate request coming from a service.
     * 
     * @param parentCertChain The parent cert chain of the proxy.
     * @param certReq The certification request to generate the certificate from.
     * @throws InvalidKeyException Thrown if the public key in the request is invalid.
     * @throws NoSuchAlgorithmException Thrown if the request uses unsupported algorithm.
     * @throws NoSuchProviderException Thrown if the bouncycastle provider was not found.
     */
    public ProxyCertificateGenerator(X509Certificate[] parentCertChain, PKCS10CertificationRequest certReq)
            throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException {
        this(parentCertChain);

        // m_certReq = certReq;
        m_publicKey = certReq.getPublicKey();
        m_newDN = X509Name.getInstance(certReq.getCertificationRequestInfo().getSubject());
        /*
         * // test for DN violation, the new DN must be composed of the parentDN // and and additional CN component. DN
         * baseDN = DNHandler.getSubject(m_parentCert); DN reqSubject = DNHandler.getDN(m_newDN); try{
         * ProxyCertUtil.checkProxyDN(baseDN, reqSubject); } catch(IllegalArgumentException e){ throw new
         * IllegalArgumentException("Got an invalid proxy request subject, '" + reqSubject +
         * "' is not a valid proxy subject for '" + baseDN + "', error was: " + e.getMessage()); }
         */
        // in case the parent was unknown type, deduce the type from the cert
        // req. in case it's not legacy and not set later, use default in generate().
        if (m_type == ProxyCertificateInfo.UNKNOWN_PROXY_TYPE) {
            if (ProxyCertificateInfo.isLegacyDN(m_newDN)) {
                m_type = ProxyCertificateInfo.LEGACY_PROXY;
            }
        }
        // if the proxy is not legacy proxy, try to figure out the serial number from the DN of the request.
        if (m_type != ProxyCertificateInfo.LEGACY_PROXY) {
            BigInteger sn = ProxyCertUtil.getSN(m_newDN);
            if (sn != null) {
                m_serialNumber = sn;
            }
        }

        m_certGen = new X509V3CertificateGenerator();
    }

    /**
     * Create a new proxy cert generator based on certification request and a certificate. Used for example when
     * creating a proxy certificate on the client side from certificate request coming from a service.
     * 
     * @param parentCert
     * @param certReq
     * @throws NoSuchProviderException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public ProxyCertificateGenerator(X509Certificate parentCert, PKCS10CertificationRequest certReq)
            throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException {
        this(new X509Certificate[] { parentCert }, certReq);
    }

    /**
     * Set the proxy lifetime. If not set, the default is 12h.
     * 
     * @param lifetime the lifetime in seconds. (+-5min grace period will be added to the lifetime.)
     */
    public void setLifetime(int lifetime) {
        this.m_lifetime = lifetime;
    }

    /**
     * Add an extension to the proxy certificate to be generated.
     * 
     * @param oid the object identifier of the extension.
     * @param critical whether the extension is critical or not.
     * @param value The extension value.
     */
    public void addExtension(String oid, boolean critical, ASN1Encodable value) {
        m_certGen.addExtension(new DERObjectIdentifier(oid), critical, value);
    }

    /**
     * Set up the basic common proxy fields.
     */
    private void setupBasicProxy() {
        setupDates();

        m_certGen.setPublicKey(m_publicKey);
        m_certGen.setSignatureAlgorithm(m_parentCert.getSigAlgName());
        m_certGen.addExtension(X509Extensions.KeyUsage, false, new KeyUsage(KeyUsage.dataEncipherment
                | KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

    }

    /**
     * Generate the proxy certificate object.
     * 
     * @param privateKey the private key used to sign the proxy certificate.
     * @throws InvalidKeyException
     * @throws SignatureException
     * @throws NoSuchAlgorithmException
     * @throws CertificateEncodingException
     */
    public void generate(PrivateKey privateKey) throws InvalidKeyException, SignatureException,
            NoSuchAlgorithmException, CertificateEncodingException {

        // test if the keys are not set, and generate them if they aren't
        if (m_publicKey == null) {
            if (m_privateKey != null && m_publicKey == null) {
                throw new IllegalArgumentException(
                        "Only private key of the proxy is set. As it is, also public key has to be set.");
            }

            generateKeys();
        }

        // default to RFC proxy id all else fails.
        if (m_type == ProxyCertificateInfo.UNKNOWN_PROXY_TYPE) {
            m_type = DEFAULT_PROXY_TYPE;
        }

        switch (m_type) {
        case ProxyCertificateInfo.LEGACY_PROXY:
            setupOldProxy(m_limited);
            break;
        case ProxyCertificateInfo.RFC3820_PROXY:
            setupRFC3280Proxy(m_serialNumber, m_proxyPolicyOID, m_proxyPolicyOctets, m_pathLenLimit, true);
            break;
        case ProxyCertificateInfo.DRAFT_RFC_PROXY:
            setupRFC3280Proxy(m_serialNumber, m_proxyPolicyOID, m_proxyPolicyOctets, m_pathLenLimit, false);
            break;
        default:
            throw new IllegalArgumentException("Unknown or unsupported proxy type");
        }
        m_certGen.setIssuerDN(m_baseName);
        m_certGen.setSignatureAlgorithm(m_hashAlgorithm);
        // if the new DN is set (cert request), use it, otherwise set it up.
        /*
         * if (m_newDN != null) { m_certGen.setSubjectDN(m_newDN); } else { String newCN = guessCN(m_baseName, false);
         * setupDNs(newCN); }
         */
        m_newProxy = m_certGen.generate(privateKey);
    }

    /**
     * Generates the private and public keys if they are not given.
     */
    private void generateKeys() {
        try {
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA", new BouncyCastleProvider());
            //JDKKeyPairGenerator.RSA keyPairGen = new JDKKeyPairGenerator.RSA();
            keyPairGen.initialize(m_keyLength, new SecureRandom());
            KeyPair pair = keyPairGen.generateKeyPair();
            m_privateKey = pair.getPrivate();
            m_publicKey = pair.getPublic();
        } catch (NoSuchAlgorithmException ex) {
            new RuntimeException(ex);
        }
    }

    /**
     * Returns the certificate chain of the proxy.
     * 
     * @return the Certificate chain starting with the CA or end entity certificate and ending with the latest proxy.
     */
    public X509Certificate[] getCertChain() {
        X509Certificate[] newChain = new X509Certificate[m_parentCertChain.length + 1];
        for (int i = 0; i < m_parentCertChain.length; i++) {
            newChain[i + 1] = m_parentCertChain[i];
        }
        newChain[0] = m_newProxy;
        return newChain;
    }

    /**
     * Returns the generated or set private key of this proxy.
     * 
     * @return The private key.
     */
    public PrivateKey getPrivateKey() {
        return m_privateKey;
    }

    /**
     * Gives the certificate chain containing the proxy in PEM format.
     * 
     * @return the Certificate chain in PEM format, starting with the latest proxy and ending with either the end entity
     *         user certificate or CA certificate, depending on the input given chen callin the constructor.
     * @throws IOException In case there are string manipulation problems.
     */
    public String getCertChainAsPEM() throws IOException {
        X509Certificate[] newChain = getCertChain();
        StringWriter writer = new StringWriter();
        PEMWriter pemWriter = new PEMWriter(writer);
        for (int i = 0; i < newChain.length; i++) {
            pemWriter.writeObject(newChain[i]);
        }
        pemWriter.flush();
        return writer.toString();
    }

    /**
     * Gives the private key of the proxy if the keys were generated or set using setPrivateKey.
     * 
     * @return The private key of the proxy in PEM format.
     */
    public String getPrivateKeyAsPEM() {
        return PrivateKeyReader.getPEM(m_privateKey);

    }

    /**
     * Gives the proxy credentials in PEM encoded certificate chain containing the private key in unencrypted format.
     * See: http://dev.globus.org/wiki/Security/ProxyFileFormat
     * 
     * @return The PEM encoded proxy credentials as a String.
     * @throws IOException In case the string manipulations fail.
     */
    public String getProxyAsPEM() throws IOException {
        StringWriter writer = new StringWriter();
        PEMWriter pemWriter = new PEMWriter(writer);

        pemWriter.writeObject(m_newProxy);
        pemWriter.write(getPrivateKeyAsPEM());

        for (int i = m_parentCertChain.length - 1; i >= 0; i--) {
            pemWriter.writeObject(m_parentCertChain[i]);
        }
        pemWriter.flush();
        return writer.toString();

    }

    /**
     * Generates a new proxy DN based on the basename. If newCN is given, it is added to the end of the DN and the new
     * DN is returned. If newCN is null, the basename is analyzed. In case of old proxy DN, either "CN=proxy" or
     * "CN=limited proxy" is added depending on the value of limited argument. In case of new style proxy or nonproxy
     * DN, new style proxy is assumed and "CN=" with random number following it is added.
     * 
     * @param basename The DN to use as the basis of the new DN.
     * @param inputCN If given, this is used as the new CN value.
     * @param limited in case the newCN is not given and the basename is old style proxy, setting this to true will
     *            generate limited proxy.
     * @return the new DN.
     */
    @SuppressWarnings("unchecked")
    public X509Name generateDN(X509Name basename, String inputCN, boolean limited) {
        if (basename == null) {
            throw new IllegalArgumentException("generateDN: no basename given, can't generate DN.");
        }

        String newCN;

        if (inputCN == null) { // if no CN part given, guess it
            newCN = guessCN(basename, limited);
        } else {
            newCN = inputCN;
        }

        // generate new cn part
        ASN1EncodableVector newCnPart = new ASN1EncodableVector();
        newCnPart.add(X509Name.CN);
        newCnPart.add(new DERPrintableString(newCN));

        // copy the RDNs to a new vector so that the new part can be added.
        ASN1Sequence subjectSequence = (ASN1Sequence) basename.toASN1Primitive();
        Enumeration subjectParts = subjectSequence.getObjects();

        ASN1EncodableVector subjectVector = new ASN1EncodableVector();

        while (subjectParts.hasMoreElements()) {
            ASN1Primitive part = (ASN1Primitive) subjectParts.nextElement();
            subjectVector.add(part);
        }

        subjectVector.add(new DERSet(new DERSequence(newCnPart)));

        // transform the vector into a new X509Name
        DERSequence subjDerSeq = new DERSequence(subjectVector);
        X509Name proxySubject = new X509Name(subjDerSeq);

        LOGGER.debug("SubjectDN :" + proxySubject);

        return proxySubject;
    }

    /**
     * Guesses the value of the CN based on the basename DN. See generateDN for the logic.
     * 
     * @param basename the DN to use as the base of the guessing.
     * @param addLimited whether the new proxy will be limited or not in case the guess is olds style proxy.
     * @return the new CN string.
     */
    private String guessCN(X509Name basename, boolean addLimited) {
        String newCN;
        ASN1Sequence subjectSequence = (ASN1Sequence) basename.toASN1Primitive();
        int rdns = subjectSequence.size();
        DERSet rdn = (DERSet) subjectSequence.getObjectAt(rdns - 1);
        DERSequence rdnSequence = (DERSequence) rdn.getObjectAt(0);
        DERObjectIdentifier oid = (DERObjectIdentifier) rdnSequence.getObjectAt(0);
        if (oid.equals(X509Name.CN)) {
            String cn = rdnSequence.getObjectAt(1).toString();
            if (cn.equals("proxy")) { // old style unlimited proxy
                if (addLimited) { // new proxy will be limited
                    newCN = "limited proxy";
                } else { // new proxy will still be unlimited
                    newCN = "proxy";
                }
            } else {
                if (cn.equals("limited proxy")) { // in case the proxy is old
                    // style limited proxy, new
                    // one will be old style
                    // limited too
                    newCN = "limited proxy";
                } else { // otherwise generate new random number to use as CN.
                    newCN = getSerialNumber().toString();
                }
            }
        } else { // in case the DN doesn't end with a CN, assume new style proxy
            newCN = getSerialNumber().toString();
        }
        return newCN;
    }

    /**
     * Adds a new CN part to the end of the DN and sets it as the subject DN. Also sets the issuer DN.
     * 
     * @param newCn The string to be added as the CN value.
     */
    @SuppressWarnings("unchecked")
    private void setupDNs(String newCn) {
        ASN1Sequence seqSubject = (ASN1Sequence) m_baseName.toASN1Primitive();

        ASN1EncodableVector newCnPart = new ASN1EncodableVector();
        newCnPart.add(X509Name.CN);
        newCnPart.add(new DERPrintableString(newCn));

        Enumeration subjectParts = seqSubject.getObjects();

        ASN1EncodableVector subjectVector = new ASN1EncodableVector();

        while (subjectParts.hasMoreElements()) {
            ASN1Primitive part = (ASN1Primitive) subjectParts.nextElement();
            subjectVector.add(part);
        }

        subjectVector.add(new DERSet(new DERSequence(newCnPart)));

        DERSequence subjDerSeq = new DERSequence(subjectVector);

        X509Name proxySubject = new X509Name(subjDerSeq);
        m_newDN = proxySubject;

        LOGGER.debug("SubjectDN :" + proxySubject);

        m_certGen.setSubjectDN(proxySubject);
        m_certGen.setIssuerDN(m_baseName);

    }

    /**
     * Used to set the type of the proxy. Useful only in case the parent certificate is user certificate, otherwise the
     * generator will generate same type of proxy as the parent is. And trying to set different type here than in the
     * parent will result in IllegalArgumentException. If the parent certificate is user certificate and this method is
     * not used, BasicProxyCertificate.RFC3820_PROXY will be assumed.
     * 
     * @param type The type, see the type definitions in BasicProxyCertificate class.
     * @throws IllegalArgumentException In case trying to set the type to a different one than parent, if it is a proxy
     *             certificate.
     */
    public void setType(int type) throws IllegalArgumentException {
        // setting to same type as determined is OK.
        if (m_type == type) {
            return;
        }

        // in case the determined type is unknown, any type will be allowed.
        if (type == ProxyCertificateInfo.LEGACY_PROXY || type == ProxyCertificateInfo.RFC3820_PROXY
                || type == ProxyCertificateInfo.DRAFT_RFC_PROXY) {
            if (m_type == ProxyCertificateInfo.UNKNOWN_PROXY_TYPE) {
                m_type = type;
                return;
            }
            // otherwise at least warn that the types don't match,
            // maybe should throw an exception, but it might be useful
            // at least for test certs to be able to do invalid chains.
            LOGGER.warn("The proxy type setting is not the one of the parent or the cert request. Setting is:" + type);
            m_type = type;
        }

        throw new IllegalArgumentException("Trying to set the proxy type into an unsupported type");
    }

    /**
     * Generates an old style proxy (CN=proxy ending).
     * 
     * @param limited whether the CN should have "CN=limited proxy" instead of just "CN=proxy".
     */
    private void setupOldProxy(boolean limited) {
        if (limited) {
            setupDNs("limited proxy");
        } else {
            setupDNs("proxy");
        }
        setupBasicProxy();

        BigInteger serialNum = null;
        serialNum = m_parentCert.getSerialNumber();
        m_certGen.setSerialNumber(serialNum);
    }

    /**
     * Generates an rfc3820 style proxy (CN=234151 and proxy extension).
     * 
     * @param cn Optional serial number to use as the CN part and certificate serial number. If it is null, The one from
     *            the certification request is taken or one will be created.
     * @param policyOID optional proxy policy oid, if not given, the delegate all oid is used.
     * @param policy optional proxy policy. If no policy is given the oid is assumed to suffice.
     * @param pathLenLimit number of subproxies the proxy can have. If null, no restrictions are set.
     * @param isRfc flag to inform that the proxy is a normal RFC 3820 proxy. Setting this to false will result with a
     *            draft rfc proxy, which has a different OID for the proxyInfoExtension.
     */
    private void setupRFC3280Proxy(BigInteger cn, String policyOID, DEROctetString policy, int pathLenLimit, boolean isRfc) {
        setupBasicProxy();
        BigInteger serial;
        if (cn == null) {
            serial = getSerialNumber();
        } else {
            serial = cn;
        }
        m_certGen.setSerialNumber(serial);
        setupDNs(serial.toString());

        ProxyPolicy proxyPolicy;
        if (m_limited) {
            if (policyOID == null || policyOID.equals(ProxyPolicy.LIMITED_PROXY_OID)) {
                proxyPolicy = new ProxyPolicy(ProxyPolicy.LIMITED_PROXY_OID, policy);
            } else {
                throw new IllegalArgumentException(
                        "Proxy info extension policy OID set to conflicting value when limiting proxy. OID is: "
                                + policyOID);
            }
        } else {
            proxyPolicy = new ProxyPolicy(policyOID, policy);
        }

        ProxyCertInfoExtension extension = new ProxyCertInfoExtension(pathLenLimit, proxyPolicy);
        if (isRfc) {
            m_certGen.addExtension(ProxyCertInfoExtension.PROXY_CERT_INFO_EXTENSION_OID, true, extension);
        } else {
            m_certGen.addExtension(ProxyCertInfoExtension.DRAFT_PROXY_CERT_INFO_EXTENSION_OID, true, extension);
        }
    }

    /**
     * Sets up the dates in the proxy. The new proxy lifetime is the one set by setLifetime method unless the parent
     * proxy lifetime is shorter in which case the parent lifetime is used. <br>
     * <br>
     * A 5 minute grace time is added to both ends to avoid problems with time skew between machines. So, the real
     * lifetime is the given lifetime +10 minutes.
     */
    private void setupDates() {
        // set the not before to 5 minutes ago.
        GregorianCalendar date = new GregorianCalendar(TimeZone.getTimeZone("UTC"));

        date.add(Calendar.MINUTE, -5);
        m_certGen.setNotBefore(date.getTime());

        // reverse negative grace of not before and add 5min grace to the end.
        date.add(Calendar.MINUTE, 10);
        date.add(Calendar.SECOND, m_lifetime);

        // proxy can't be longer than the parent, so limit it to that.
        if (m_parentCert != null) {
            Date parentNotAfter = m_parentCert.getNotAfter();
            if (parentNotAfter.before(date.getTime())) {
                date.setTime(parentNotAfter);
            }
        }

        m_certGen.setNotAfter(date.getTime());
    }

    /**
     * Sets the length of the keys to be generated, only used if the keys are not set separately. If this method is not
     * used, the default is 1024 bits.
     * 
     * @param length The key length in bits.
     */
    public void setKeyLength(int length) {
        m_keyLength = length;
    }

    /**
     * Defines that the resulting proxy will be limited proxy, meaning job submission with is prevented.
     */
    public void setLimited() {
        m_limited = true;
    }

    /**
     * Returns the given or discovered serial number of the new proxy, or generates the serial number if not previously
     * set.
     * 
     * @return The previously set serial number or a new generated one.
     */
    private BigInteger getSerialNumber() {
        if (m_serialNumber == null) {
            SecureRandom rand = new SecureRandom();
            m_serialNumber = BigInteger.valueOf(rand.nextInt()).abs();
        }

        return m_serialNumber;
    }

    /**
     * Sets the new proxy serial number. Only applicable for rfc proxies.
     * 
     * @param sn The serial number for the new proxy.
     */
    public void setSerialNumber(BigInteger sn) {
        m_serialNumber = sn;
    }

    /**
     * Set the RFC proxy proxy extension policy OID and octets of the policy. See RFC3820. Policy can be null in case
     * the OID in it self defines the behaviour, like with "inherit all" policy or "independent" policy.
     * 
     * @param oid The oid of the policy language.
     * @param octets The actual policy info encoded as DEROctetString.
     */
    public void setPolicy(String oid, DEROctetString octets) {
        if (m_type == ProxyCertificateInfo.LEGACY_PROXY) {
            throw new IllegalArgumentException("Legacy proxies do not support setting the proxy policy.");
        }
        m_proxyPolicyOID = oid;
        m_proxyPolicyOctets = octets;
    }

    /**
     * Sets the proxy path length limit of this certificate. Only works on rfc3820 and RFC draft proxies.
     * 
     * @param pathLen The number of allowed proxy certificates in the chain allowed after this certificate.
     *            ProxyCertInfoExtension.UNLIMITED if not set.
     */
    public void setProxyPathLimit(int pathLen) {
        if (m_type == ProxyCertificateInfo.LEGACY_PROXY) {
            throw new IllegalArgumentException("Legacy proxies do not support setting the proxy path length limit.");
        }
        m_pathLenLimit = pathLen;
    }

    /**
     * Sets the proxy source restriction data.
     * 
     * @param data The data for the source restriction extension.
     */
    public void setProxySourceRestrictions(ProxyRestrictionData data) {
        m_certGen.addExtension(ProxyRestrictionData.SOURCE_RESTRICTION_OID, false, data.getNameConstraints());
    }

    /**
     * Sets the proxy target restriction data.
     * 
     * @param data The data for the target restriction extension.
     */
    public void setProxyTargetRestrictions(ProxyRestrictionData data) {
        m_certGen.addExtension(ProxyRestrictionData.TARGET_RESTRICTION_OID, false, data.getNameConstraints());
    }

    /**
     * Sets the issuer URL for the proxy tracing.
     * 
     * @param url The proxy tracing issuer URL in String format.
     */
    public void setProxyTracingIssuer(String url) {
        ProxyTracingExtension extension = new ProxyTracingExtension(url);
        m_certGen.addExtension(ProxyTracingExtension.PROXY_TRACING_ISSUER_EXTENSION_OID, false, extension.getNames());
    }

    /**
     * Sets the subject URL for the proxy tracing.
     * 
     * @param url The proxy tracing subject URL in String format.
     */
    public void setProxyTracingSubject(String url) {
        ProxyTracingExtension extension = new ProxyTracingExtension(url);
        m_certGen.addExtension(ProxyTracingExtension.PROXY_TRACING_SUBJECT_EXTENSION_OID, false, extension.getNames());
    }

}

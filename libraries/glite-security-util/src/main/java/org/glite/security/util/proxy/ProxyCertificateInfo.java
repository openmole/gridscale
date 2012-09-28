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
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.x509.X509Name;
import org.glite.security.util.CertUtil;

/**
 * A base class for digging up info from the proxy.
 * 
 * @author joni.hahkala@cern.ch
 */
public class ProxyCertificateInfo {
    /** Used to identify legacy globus toolkit 2 proxies. */
    public static final int LEGACY_PROXY = 52;
    /** Used to identify draft pre RFC3820 type proxies. */
    public static final int DRAFT_RFC_PROXY = 53;
    /** Used to identify RFC3820 type proxies. */
    public static final int RFC3820_PROXY = 54;
    /** Used to identify RFC3820 type proxies. */
    public static final int CA_CERT = 71;
    /** Used to identify RFC3820 type proxies. */
    public static final int USER_CERT = 72;
    /** Used to identify unknown proxy type, for example user cert. */
    public static final int UNKNOWN_PROXY_TYPE = 99;

    /** Used to point that there hasn't been a try to determine proxy type */  
    public static final int UNDEFINED_TYPE = -1;

    /** The starting proxy type is undefined. */
    private int m_proxyType = UNDEFINED_TYPE;

    /** The certificate to analyze. */
    private X509Certificate m_cert;

    /**
     * Generates a certificate object from the x509 data structure.
     * 
     * @param x509Cert The proxy to analyze.
     */
    public ProxyCertificateInfo(X509Certificate x509Cert) {
        m_cert = x509Cert;
    }

    /**
     * Analyzes the certificate and deducts what type of proxy this certificate is.
     * 
     * @return The type of the proxy.
     * @see #LEGACY_PROXY For globus toolkit 2 legacy proxy.
     * @see #RFC3820_PROXY For RFC3820 proxy (conformity unverified).
     * @see #UNKNOWN_PROXY_TYPE For unrecognized proxy.
     * @see #DRAFT_RFC_PROXY For globus toolkit 3 and 4.0 draft pre RFC3820 type proxy.
     */
    public int getProxyType() {
        if (m_proxyType != UNDEFINED_TYPE) {
            return m_proxyType;
        }

        // First detect whether it is a RFC3820 proxy
        if (m_cert.getExtensionValue(ProxyCertInfoExtension.PROXY_CERT_INFO_EXTENSION_OID) != null
                && m_cert.getExtensionValue(ProxyCertInfoExtension.PROXY_CERT_INFO_EXTENSION_OID).length > 0) {
            m_proxyType = RFC3820_PROXY;
            return m_proxyType;
        }
        // Then check if it is pre RFC draft proxy.
        if (m_cert.getExtensionValue(ProxyCertInfoExtension.DRAFT_PROXY_CERT_INFO_EXTENSION_OID) != null
                && m_cert.getExtensionValue(ProxyCertInfoExtension.DRAFT_PROXY_CERT_INFO_EXTENSION_OID).length > 0) {
            m_proxyType = DRAFT_RFC_PROXY;
            return m_proxyType;
        }

        // If not, check if the DN ends with either "cn=proxy" or "cn=limited"
        // proxy indicating that it is legacy proxy.
        X509Name subject = (X509Name) m_cert.getSubjectDN();
        DERObjectIdentifier oid = (DERObjectIdentifier) subject.getOIDs().lastElement();

        // not ending with CN RDN, don't know what this cert is.
        if (!X509Name.CN.equals(oid)) {
            m_proxyType = UNKNOWN_PROXY_TYPE;
            return m_proxyType;
        }

        String value = (String) subject.getValues().lastElement();
        if ("proxy".equals(value.toLowerCase()) || "limited proxy".equals(value.toLowerCase())) {
            m_proxyType = LEGACY_PROXY;
            return m_proxyType;
        }

        // Last RDN was CN, but not CN=proxy or CN=limited proxy, so don't know
        // what this cert is.
        m_proxyType = UNKNOWN_PROXY_TYPE;
        return m_proxyType;
    }

    /**
     * Used to check whether a DN indicates a legacy proxy or not.
     * 
     * @param subject The input DN used to check whether it indicates a legacy proxy
     * @return true in case DN is legacy proxy dn.
     */
    public static boolean isLegacyDN(X509Name subject) {
        DERObjectIdentifier oid = (DERObjectIdentifier) subject.getOIDs().lastElement();
        // not ending with CN RDN, don't know what this cert is.
        if (oid != X509Name.CN) {
            return false;
        }

        String value = (String) subject.getValues().lastElement();
        if ("proxy".equals(value.toLowerCase()) || "limited proxy".equals(value.toLowerCase())) {
            return true;
        }

        return false;
    }

    /**
     * Used to check whether the proxy is limited proxy or not.
     * 
     * @return true if the proxy is limited legacy proxy or limited RFC3820 proxy.
     * @throws CertificateException Thrown in case the proxy is of unknown format or invalid.
     * @throws IOException Thrown in case the proxy is RFC3820 proxy and the information parsing fails.
     */
    public boolean isLimited() throws CertificateException, IOException {
        if (m_proxyType == UNDEFINED_TYPE) {
            getProxyType();
        }
        if (m_proxyType == LEGACY_PROXY) {
            // If not, check if the DN ends with "cn=limited proxy" proxy
            // indicating that it is limited legacy proxy.
            X509Name subject = (X509Name) m_cert.getSubjectDN();
            DERObjectIdentifier oid = (DERObjectIdentifier) subject.getOIDs().lastElement();

            // not ending with CN RDN, this shouldn't happen as it passed the
            // getProxyType, but check anyway, defence in depth and all...
            if (oid != X509Name.CN) {
                throw new CertificateException(
                        "The certificate DN doesn't end with CN RDN as required for legacy proxies");
            }

            String value = (String) subject.getValues().lastElement();
            if ("limited proxy".equals(value.toLowerCase())) {
                return true;
            }

            return false;
        }

        if (m_proxyType == RFC3820_PROXY || m_proxyType == DRAFT_RFC_PROXY) {
            String policyOID = getProxyPolicyOID();
            if (policyOID.equals(ProxyPolicy.LIMITED_PROXY_OID)) {
                return true;
            }
            return false;
        }
        throw new CertificateException(
                "Can't determine whether the proxy is limited as it isn't legacy proxy or rfc 3820 proxy and thus unsupported or invalid");
    }

    /**
     * Returns the URL of the proxy tracing issuer if present.
     * 
     * @return The proxy tracing issuer URL in String format, or null if no extension was found or it was empty.
     * @throws IOException Thrown in case the parsing of the information failed.
     */
    public String getProxyTracingIssuer() throws IOException {
        byte[] bytes = CertUtil.getExtensionBytes(m_cert, ProxyTracingExtension.PROXY_TRACING_ISSUER_EXTENSION_OID);

        if (bytes == null || bytes.length == 0) {
            return null;
        }

        ProxyTracingExtension extension = new ProxyTracingExtension(bytes);

        return extension.getURL();
    }

    /**
     * Returns the URL of the proxy tracing subject if present.
     * 
     * @return The proxy tracing subject URL in String format, or null if no extension was found or it was empty.
     * @throws IOException Thrown in case the parsing of the information failed.
     */
    public String getProxyTracingSubject() throws IOException {
        byte[] bytes = CertUtil.getExtensionBytes(m_cert, ProxyTracingExtension.PROXY_TRACING_SUBJECT_EXTENSION_OID);

        if (bytes == null || bytes.length == 0) {
            return null;
        }

        ProxyTracingExtension extension = new ProxyTracingExtension(bytes);

        return extension.getURL();
    }

    /**
     * Returns the SAML extension form the certificate.
     * 
     * @return The SAML assertion in String format. In no SAML extension was found, null is returned.
     * @throws IOException In case there is a problem parsing the certificate.
     */
    public String getSAMLExtension() throws IOException {
        byte bytes[] = CertUtil.getExtensionBytes(m_cert, SAMLExtension.SAML_OID);

        // If no extension was found, try if there is one with legacy oid.
        if (bytes == null || bytes.length == 0) {
            bytes = CertUtil.getExtensionBytes(m_cert, SAMLExtension.LEGACY_SAML_OID);
        }

        if (bytes == null || bytes.length == 0) {
            return null;
        }

        SAMLExtension extension = new SAMLExtension(bytes);

        return extension.getSAML();

    }

    /**
     * Used to get the RFC3820 (or draft RFC) CertificateInfoExtension information. Will search for both the RFC 3820
     * extension and the draft proxy extension. Only works on rfc3820 and RFC draft proxies.
     * 
     * @return The ProxyCertInfoExtension object holding the information from the certificate extension.
     * @throws CertificateException Thrown in case the certificate is not a RFC 3820 proxy nor a draft RFC proxy, or the
     *             mandatory extension is missing.
     * @throws IOException Thrown in case the certificate parsing fails.
     */
    public ProxyCertInfoExtension getProxyCertInfoExtension() throws CertificateException, IOException {
        if (getProxyType() != RFC3820_PROXY && getProxyType() != DRAFT_RFC_PROXY) {
            throw new CertificateException("Trying to get proxyPathLimit from legacy or unsupported proxy type");
        }

        byte[] bytes = CertUtil.getExtensionBytes(m_cert, ProxyCertInfoExtension.PROXY_CERT_INFO_EXTENSION_OID);

        // if not found, check if there is draft extension
        if (bytes == null) {
            bytes = CertUtil.getExtensionBytes(m_cert, ProxyCertInfoExtension.DRAFT_PROXY_CERT_INFO_EXTENSION_OID);
        }

        if (bytes == null) {
            throw new CertificateException(
                    "The mandatory CertificateInfoExtention is missing, certificate is invalid RFC 3820 or draft RFC certificate.");
        }

        return new ProxyCertInfoExtension(bytes);

    }

    /**
     * Returns the proxy path length limit of this certificate. Will search for both the RFC 3820 extension and the
     * draft proxy extension. Only works on rfc3820 and RFC draft proxies.
     * 
     * @return The number of allowed proxy certificates in the chain allowed after this certificate.
     *         ProxyCertInfoExtension.UNLIMITED if not set.
     * @throws CertificateException thrown if the proxy is not rfc3820 nor RFC draft type proxy, or in case the
     *             mandatory ProxyCertInfoExtension is not found in the certificate.
     * @throws IOException Thrown in case the certificate parsing fails.
     */
    public int getProxyPathLimit() throws CertificateException, IOException {
        ProxyCertInfoExtension extension = getProxyCertInfoExtension();
        return extension.getProxyPathLimit();
    }

    /**
     * Get the mandatory proxy policy OID from the mandatory proxyCertInfoExtension. Will search for both the RFC 3820
     * extension and the draft proxy extension. Only works on rfc3820 and RFC draft proxies.
     * 
     * @return The proxy policy oid in String format. @see org.glite.security.util.proxy.ProxyPolicy
     * @throws CertificateException thrown if the proxy is not rfc3820 or RFC draft type proxy, or in case the mandatory
     *             ProxyCertInfoExtension is not found in the certificate.
     * @throws IOException In case there is a parsing problem.
     */
    public String getProxyPolicyOID() throws CertificateException, IOException {
        ProxyCertInfoExtension extension = getProxyCertInfoExtension();
        ProxyPolicy policy = extension.getPolicy();

        return policy.getPolicyOID();
    }

    /**
     * Get the optional policy in ASN1 structure. Will search for both the RFC 3820 extension and the draft proxy
     * extension. Only works on rfc3820 and RFC draft proxies.
     * 
     * @return The contents of the policy as an ASN1OctetString.
     * @throws CertificateException thrown if the proxy is not rfc3820 or RFC draft type proxy, or in case the mandatory
     *             ProxyCertInfoExtension is not found in the certificate.
     * @throws IOException In case there is a parsing problem.
     */
    public ASN1OctetString getPolicyASN1() throws CertificateException, IOException {
        ProxyCertInfoExtension extension = getProxyCertInfoExtension();
        ProxyPolicy policy = extension.getPolicy();

        return policy.getPolicyASN1();
    }

    /**
     * Gets the proxy source restriction data.
     * 
     * @return The data from the source restriction extension.
     * @throws IOException thrown if the certificate parsing fails.
     */
    public ProxyRestrictionData getProxySourceRestrictions() throws IOException {
        byte[] bytes = CertUtil.getExtensionBytes(m_cert, ProxyRestrictionData.SOURCE_RESTRICTION_OID);
        if (bytes == null) {
            return null;
        }
        return new ProxyRestrictionData(bytes);
    }

    /**
     * Gets the proxy target restriction data.
     * 
     * @return The data from the target restriction extension.
     * @throws IOException thrown if the certificate parsing fails.
     */
    public ProxyRestrictionData getProxyTargetRestrictions() throws IOException {
        byte[] bytes = CertUtil.getExtensionBytes(m_cert, ProxyRestrictionData.TARGET_RESTRICTION_OID);
        if (bytes == null) {
            return null;
        }
        return new ProxyRestrictionData(bytes);
    }
}

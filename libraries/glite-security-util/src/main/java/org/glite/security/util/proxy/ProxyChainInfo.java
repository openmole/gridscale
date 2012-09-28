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
import java.util.Iterator;
import java.util.Vector;

import org.glite.security.util.IPAddressComparator;

/**
 * A class to get the proxy information from the whole proxy cert chain.
 * 
 * @author Joni Hahkala
 */
public class ProxyChainInfo {
    /** The array of proxy certificate infos to analyze. */
    private ProxyCertificateInfo[] m_certInfos;
    /** The starting proxy type is undefined. */
    private int m_proxyType = ProxyCertificateInfo.UNDEFINED_TYPE;

    /**
     * Generates new instance of this class using the certificate chain as the source of the data.
     * 
     * @param chain The proxy chain to parse and analyze.
     */
    public ProxyChainInfo(X509Certificate[] chain) {
        m_certInfos = new ProxyCertificateInfo[chain.length];
        for (int i = 0; i < chain.length; i++) {
            m_certInfos[i] = new ProxyCertificateInfo(chain[i]);
        }
    }

    /**
     * Analyzes the certificate chain and deducts what type of proxy this certificate chain is.
     * 
     * @return The type of the proxy.
     * @throws CertificateException In case several kinds of proxies were present.
     * @see ProxyCertificateInfo#LEGACY_PROXY For globus toolkit 2 legacy proxy.
     * @see ProxyCertificateInfo#RFC3820_PROXY For RFC3820 proxy (conformity unverified).
     * @see ProxyCertificateInfo#UNKNOWN_PROXY_TYPE For unrecognized proxy.
     * @see ProxyCertificateInfo#DRAFT_RFC_PROXY For globus toolkit 3 and 4.0 draft pre RFC3820 type proxy.
     */
    public int getProxyType() throws CertificateException {
        if (m_proxyType != ProxyCertificateInfo.UNDEFINED_TYPE) {
            return m_proxyType;
        }

        int type = ProxyCertificateInfo.UNDEFINED_TYPE;
        for (int i = m_certInfos.length - 1; i >= 0; i--) {
            ProxyCertificateInfo certInfo = m_certInfos[i];
            // first just set the type to the one from the proxy.
            if (type == ProxyCertificateInfo.UNDEFINED_TYPE) {
                type = certInfo.getProxyType();
                continue;
            }
            // allow for nonproxies in the beginning.
            if (type != ProxyCertificateInfo.UNKNOWN_PROXY_TYPE && type != certInfo.getProxyType()) {
                throw new CertificateException(
                        "Proxy type mismatch, proxies in a chain should be of one type, several were present.");
            }
            type = certInfo.getProxyType();
        }
        m_proxyType = type;

        return m_proxyType;
    }

    /**
     * Used to check whether the proxy chain is limited proxy or not.
     * 
     * @return true if the proxy chain contains a limited legacy proxy or a limited RFC3820 proxy.
     * @throws CertificateException Thrown in case the proxy is of unknown format or invalid.
     * @throws IOException Thrown in case the proxy is RFC3820 proxy and the information parsing fails.
     */
    public boolean isLimited() throws CertificateException, IOException {
        for (int i = m_certInfos.length - 1; i >= 0; i--) {
            // don't check user certificate as that's never limited and causes exception.
            if (m_certInfos[i].getProxyType() != ProxyCertificateInfo.UNKNOWN_PROXY_TYPE) {
                if (m_certInfos[i].isLimited()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns an array of URLs of the proxy tracing issuers in the chain non-traced proxies will have null in the
     * array.
     * 
     * @return The proxy tracing issuer URLs in String format, or null in the array if an extension was not found or it
     *         was empty.
     * @throws IOException Thrown in case the parsing of the information failed.
     */
    public String[] getProxyTracingIssuers() throws IOException {
        int len = m_certInfos.length;
        String[] issuers = new String[len];
        for (int i = len - 1; i >= 0; i--) {
            issuers[len - i - 1] = m_certInfos[i].getProxyTracingIssuer();
        }
        return issuers;
    }

    /**
     * Returns an array of URLs of the proxy tracing subjects in the chain non-traced proxies will have null in the
     * array.
     * 
     * @return The proxy tracing subject URLs in String format, or null in the array if an extension was not found or it
     *         was empty.
     * @throws IOException Thrown in case the parsing of the information failed.
     */
    public String[] getProxyTracingSubjects() throws IOException {
        int len = m_certInfos.length;
        String[] subjects = new String[len];
        for (int i = len - 1; i >= 0; i--) {
            subjects[len - i - 1] = m_certInfos[i].getProxyTracingSubject();
        }
        return subjects;
    }

    /**
     * Returns the SAML extensions from the certificate chain.
     * 
     * @return The SAML assertions in String format. A null in the array means that no SAML extensions were found.
     * @throws IOException In case there is a problem parsing the certificates.
     */
    public String[] getSAMLExtensions() throws IOException {
        int len = m_certInfos.length;
        String[] samls = new String[len];
        for (int i = len - 1; i >= 0; i--) {
            samls[len - i - 1] = m_certInfos[i].getSAMLExtension();
        }
        return samls;
    }

    /**
     * Returns the proxy path length limit left of this chain. Will search for both the RFC 3820 extension and the draft
     * proxy extension. Only works on rfc3820 and RFC draft proxies. Notice: negative value except
     * ProxyCertInfoExtension.UNLIMITED means that the chain is invalid as it has passed the limit of delegations.
     * 
     * @return The number of allowed proxy certificates in the chain allowed after this chain.
     *         ProxyCertInfoExtension.UNLIMITED if the path length is not limited.
     * @throws CertificateException thrown if the proxy is not rfc3820 nor RFC draft type proxy, or in case the
     *             mandatory ProxyCertInfoExtension is not found in the certificate.
     * @throws IOException Thrown in case the certificate parsing fails.
     */
    public int getProxyPathLimit() throws CertificateException, IOException {
        int limit = ProxyCertInfoExtension.UNLIMITED;
        for (int i = m_certInfos.length - 1; i >= 0; i--) {
            if (m_certInfos[i].getProxyType() == ProxyCertificateInfo.UNKNOWN_PROXY_TYPE) {
                // don't try to get limit from non-proxy user cert.
                continue;
            }
            // first, reduce the limit from before by this cert
            if (limit != ProxyCertInfoExtension.UNLIMITED) {
                limit--;
            }
            // check, and act on new limit if present.
            if (m_certInfos[i].getProxyPathLimit() != ProxyCertInfoExtension.UNLIMITED) {
                if (m_certInfos[i].getProxyPathLimit() < limit) {
                    limit = m_certInfos[i].getProxyPathLimit();
                }
            }
        }

        return limit;
    }

    /**
     * Gets the proxy source restriction data from the chain. The allowed namespaces in different certificates in the
     * chain will be intersected and the excluded namespaces will be unioned. The returned array has as the first item
     * the array of allowed namespaces and as the second item the array of excluded namespaces. If extensions exist, but
     * in the end no allowed namespaces are left, the array is null, meaning that the proxy is unusable as it is not
     * allowed anywhere. If extensions exist, but no excluded namespaces are defined the array is null, meaning the
     * allowed namespaces define the usable namespace fully.
     * 
     * @return The data from the source restriction extensions. Null returned if no restrictions are found.
     * @throws IOException thrown if the certificate parsing fails.
     */
    public byte[][][] getProxySourceRestrictions() throws IOException {

        return getProxyRestrictions(true);
    }

    /**
     * Gets the proxy target restriction data from the chain. The allowed namespaces in different certificates in the
     * chain will be intersected and the excluded namespaces will be unioned. The returned array has as the first item
     * the array of allowed namespaces and as the second item the array of excluded namespaces. If extensions exist, but
     * in the end no allowed namespaces are left, the array is null, meaning that the proxy is unusable as it is not
     * allowed anywhere. If extensions exist, but no excluded namespaces are defined the array is null, meaning the
     * allowed namespaces define the usable namespace fully.
     * 
     * @return The data from the target restriction extensions. Null returned if no restrictions are found.
     * @throws IOException thrown if the certificate parsing fails.
     */
    public byte[][][] getProxyTargetRestrictions() throws IOException {
        return getProxyRestrictions(false);
    }

    /**
     * Calculates the union of the newSpaces and the given vectors of IPv4 and IPv6 namespaces.
     * 
     * @param newSpaces The namespaces to add.
     * @param ipV4Spaces The old IPv4 spaces.
     * @param ipV6Spaces The old IPv6 spaces.
     * @return the two resulting vectors, IPv4 vector first and the IPv6 vector second.
     */
    @SuppressWarnings("unchecked") // stupid generics not allowing creation of arrays, nor cloning without warnings
	private Vector<byte[]>[] union(byte[][] newSpaces, Vector<byte[]> ipV4Spaces, Vector<byte[]> ipV6Spaces) {
        // in case no spaces restrictions are set
        if (newSpaces == null) {
            // if spaces were not defined, return given vectors, meaning no change.
            return new Vector[] { ipV4Spaces, ipV6Spaces };
        }
        Vector<byte[]> newIPv4;
        Vector<byte[]> newIPv6;

        if (ipV4Spaces == null) {
            newIPv4 = new Vector<byte[]>();
        } else {
            newIPv4 = (Vector<byte[]>) ipV4Spaces.clone();
        }
        if (ipV6Spaces == null) {
            newIPv6 = new Vector<byte[]>();
        } else {
            newIPv6 = (Vector<byte[]>) ipV6Spaces.clone();
        }

        // just add new ones to the old ones.
        for (int i = 0; i < newSpaces.length; i++) {
            if (newSpaces[i].length == 8) {
                newIPv4.add(newSpaces[i]);
            } else {
                if (newSpaces[i].length == 32) {
                    newIPv6.add(newSpaces[i]);
                } else {
                    throw new IllegalArgumentException(
                            "IP space definition has to be either 8 bytes or 32 bytes, length was: " + newSpaces.length);
                }
            }
        }
        return new Vector[] { newIPv4, newIPv6 };
    }

    /**
     * Calculates the intersection of the newSpaces and the given vectors of IPv4 and IPv6 namespaces.
     * 
     * @param newSpaces The namespaces to intersect with.
     * @param ipV4Spaces The old IPv4 spaces.
     * @param ipV6Spaces The old IPv6 spaces.
     * @return the two resulting vectors, IPv4 vector first and the IPv6 vector second.
     */
    @SuppressWarnings("unchecked") // stupid generics not allowing creation of arrays, nor cloning without warnings
    private Vector<byte[]>[] intersect(byte[][] newSpaces, Vector<byte[]> ipV4Spaces, Vector<byte[]> ipV6Spaces) {
        // in case no spaces restrictions are set
        if (newSpaces == null) {
            // if spaces were not defined, return given vectors, meaning no change.
            return new Vector[] { ipV4Spaces, ipV6Spaces };
        }

        Vector<byte[]> newIPv4 = new Vector<byte[]>();
        Vector<byte[]> newIPv6 = new Vector<byte[]>();

        // go through the new spaces
        for (int i = 0; i < newSpaces.length; i++) {
            Vector<byte[]> newIPs;
            int len;
            if (newSpaces[i].length == 8) {
                newIPs = newIPv4;
                len = 8;
            } else {
                if (newSpaces[i].length == 32) {
                    newIPs = newIPv6;
                    len = 32;
                } else {
                    throw new IllegalArgumentException(
                            "Invalid namespace definition, length should be 8 or 32 bytes. It was: "
                                    + newSpaces[i].length + " bytes.");
                }
            }
            // if previous restrictions exist, calculate intersection, otherwise this is the first definition and use it
            // as it is.
            if (ipV4Spaces != null && ipV6Spaces != null) {
                // handle ipv4 space
                byte[] ip = IPAddressComparator.copyBytes(newSpaces[i], 0, len / 2);
                // go through old spaces and see which ones are still allowed in new spaces and whether more restricted.
                Iterator<byte[]> iter = newIPs.iterator();
                while (iter.hasNext()) {
                    byte[] oldSpace = iter.next();
                    if (IPAddressComparator.isWithinAddressSpace(ip, oldSpace)) {
                        // new space ip definition is within old space, check which has more restrictive netmask and use
                        // that.
                        boolean newTighter = true;
                        for (int n = 0; n < len / 2; n++) {
                            // more bits in netmask -> tighter, bigger number.
                            if ((oldSpace[n + len / 2] & 0xFF) < (newSpaces[i][n + len / 2] & 0xFF)) {
                                newTighter = false;
                                break;
                            }
                        }
                        if (newTighter) {
                            newIPs.add(newSpaces[i]);
                        } else {
                            newIPs.add(oldSpace);
                        }
                    }
                }
            } else {
                newIPs.add(newSpaces[i]);
            }
        }

        return new Vector[] { newIPv4, newIPv6 };
    }

    /**
     * Goes through the whole proxy chain and collects and combines either the source restrictions or target
     * restrictions.
     * 
     * @param source true if source extensions are to be collected. False if target extensions are to be collected.
     * @return The collected and combined restriction data.
     * @throws IOException Thrown in case a certificate parsing fails.
     */
    private byte[][][] getProxyRestrictions(boolean source) throws IOException {
        // if no restriction extensions are found, these stay null.
        Vector<byte[]> allowedIPv4Spaces = null;
        Vector<byte[]> allowedIPv6Spaces = null;
        Vector<byte[]> excludedIPv4Spaces = null;
        Vector<byte[]> excludedIPv6Spaces = null;
        // go through cert infos and search for source restriction extensions.
        for (int i = m_certInfos.length - 1; i >= 0; i--) {
            ProxyRestrictionData restrictions;
            if (source) {
                restrictions = m_certInfos[i].getProxySourceRestrictions();
            } else {
                restrictions = m_certInfos[i].getProxyTargetRestrictions();
            }
            // only change the restrictions in case there are new restrictions
            if (restrictions != null) {
                byte[][][] spaces = restrictions.getIPSpaces();
                // handle allowed spaces
                Vector<byte[]>[] newSpaces = intersect(spaces[0], allowedIPv4Spaces, allowedIPv6Spaces);
                allowedIPv4Spaces = newSpaces[0];
                allowedIPv6Spaces = newSpaces[1];
                // handle excluded spaces
                newSpaces = union(spaces[1], excludedIPv4Spaces, excludedIPv6Spaces);
                excludedIPv4Spaces = newSpaces[0];
                excludedIPv6Spaces = newSpaces[1];
            }
        }
        if (allowedIPv4Spaces == null && allowedIPv6Spaces == null && excludedIPv4Spaces == null
                && excludedIPv6Spaces == null) {
            // no definition found, return null to inform that no definitions were found.
            return null;
        }
        // some definitions were found, return non-null
        byte[][][] newSpaces = new byte[2][][];
        // the spaces are always non-null in pairs, so just test case then both are not null
        if (allowedIPv4Spaces != null && allowedIPv6Spaces != null) {
            newSpaces[0] = IPAddressComparator.concatArrayArrays(allowedIPv4Spaces.toArray(new byte[0][0]),
                    allowedIPv6Spaces.toArray(new byte[0][0]));
        }
        if (excludedIPv4Spaces != null && excludedIPv6Spaces != null) {
            newSpaces[1] = IPAddressComparator.concatArrayArrays(excludedIPv4Spaces.toArray(new byte[0][0]),
                    excludedIPv6Spaces.toArray(new byte[0][0]));
        }

        return newSpaces;
    }
}

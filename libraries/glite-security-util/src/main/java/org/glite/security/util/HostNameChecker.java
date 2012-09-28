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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import javax.net.ssl.SSLSocket;
import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.x509.GeneralName;

/**
 * A class to do hostname checking against a certificate to check whether the server answers with a certificate that is
 * allowed for that host. Follows the server identity part of RFC 2818.
 * 
 * @author Joni Hahkala
 */
public class HostNameChecker {
    /** Logging facility. */
    private static final Logger LOGGER = Logger.getLogger(HostNameChecker.class);

    /** The pattern to check whether the string appears to be an IP address. */
    public static final Pattern ipPattern = Pattern.compile("[\\d\\.]+|[\\d\\:]+");
    
    /** The localhost IPv4 address (only the exact address supported, not the whole block 127.0.0.0/8 as recognized in RFC 3330). */
    public static final byte[] localhostIPv4 = IPAddressComparator.parseIP("127.0.0.1");
    
    /** The localhost IPv6 address */
    public static final byte[] localhostIPv6 = IPAddressComparator.parseIP("::1");

    /**
     * Given a hostname and an open socket checks if the host presented a certificate that allows it to act as the host.
     * Notice that this routine does not do certificate path checking.
     * 
     * @param hostname The name (or in rare cases an IP address) the connection was opened to.
     * @param socket The socket where to get the host certificate.
     * @throws IOException Thrown if the socket is not open, if the certificate was not understood or if the certificate
     *             vs hostname check failed.
     */
	public static void checkHostname(String hostname, SSLSocket socket) throws IOException {
		if (!socket.isConnected()) {
			throw new IOException("Socket is not open, can't check the host certificate!");
		}

		Certificate[] certs = socket.getSession().getPeerCertificates();

		if (!(certs[0] instanceof X509Certificate)) {
			socket.close();
			throw new IOException(
					"Non X509 certificate given during SSL/TLS handshake, couldn't handle it. Class was: "
							+ certs[0].getClass().getName());
		}

		// find the end entity cert, the real host certificate.
		X509Certificate[] hostCerts = (X509Certificate[]) certs;
		int hostCertIndex = CertUtil.findClientCert(hostCerts);
		X509Certificate hostCert = (X509Certificate) certs[hostCertIndex];

		try {
			if (!HostNameChecker.checkHostName(hostname, hostCert)) {
				socket.close();
				throw new IOException("Hostname " + hostname + " not allowed with certificate for DN: "
						+ DNHandler.getSubject(hostCert).getRFCDN());
			}
		} catch (CertificateParsingException e) {
			socket.close();
			throw new IOException("Invalid certificate received, error was: " + e.getMessage());
		}

	}

	/**
	 * Checks whether the hostname is allowed by the certificate. Checks the certificate altnames and subject DN
	 * according to the RFC 2818. Wildcard '*' is supported both in dnsName altName and in the DN. Service prefix in DN
	 * CN format "[service name]/[hostname]" is recognized, but ignored. Localhost defined as "localhost", "127.0.0.1"
	 * or "::1" bypasses the check.
	 * 
	 * @param inHostname
	 *            The hostname to check against the certificate. Can be a DNS name, IP address or an URL.
	 * @param cert
	 *            The certificate the hostname is checked against.
	 * @return True in case the hostname is allowed by the certificate.
	 * @throws CertificateParsingException
	 *             Thrown in case the certificate parsing fails.
	 */
	public static boolean checkHostName(String inHostname, X509Certificate cert) throws CertificateParsingException {
		// Dig the hostname if the given string is an URL.
		String hostname = null;
		// check whether an URL is given (contains a slash).
		if (inHostname.indexOf('/') < 0) {
			// Not an URL, assume it's a hostname
			hostname = inHostname.trim().toLowerCase();
		} else {
			// if not, assume an URL
			try {
				URL url = new URL(inHostname.trim());
				hostname = url.getHost().toLowerCase();
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("Illegal URL given for the certificate host check: " + inHostname);
			}

		}

		// check if the input is ip address.
		boolean ipAsHostname = false;
		if (ipPattern.matcher(hostname).matches()) {
			ipAsHostname = true;
		}

		// Check if localhost. If yes, accept automatically.
		if (ipAsHostname) {
			byte[] hostnameIPBytes = IPAddressComparator.parseIP(hostname);
			if (hostnameIPBytes.length < 6) {
				if (IPAddressComparator.compare(hostnameIPBytes, localhostIPv4)) {
					LOGGER.debug("Localhost IPv4 address given, bypassing hostname - certificate matching.");
					return true;
				}
			} else {
				if (IPAddressComparator.compare(hostnameIPBytes, localhostIPv6)) {
					LOGGER.debug("Localhost IPv6 address given, bypassing hostname - certificate matching.");
					return true;
				}
			}
		} else {
			if (hostname.equals("localhost")) {
				LOGGER.debug("Localhost address given, bypassing hostname - certificate matching.");
				return true;
			}
		}

		// If there are subject alternative names, check the hostname against
		// them first.
		Collection<List<?>> collection = cert.getSubjectAlternativeNames();
		if (collection != null) {

			// If there are, go through them and check for matches.
			Iterator<List<?>> collIter = collection.iterator();
			while (collIter.hasNext()) {
				List<?> item = collIter.next();
				int type = ((Integer) item.get(0)).intValue();

				if (type == GeneralName.dNSName) { // check against DNS name
					if (!ipAsHostname) { // only if the hostname was not given
											// as IP address
						String dnsName = (String) item.get(1);
						if (checkDNS(hostname, dnsName)) {
							return true;
						} else {
							LOGGER.debug("Hostname \"" + hostname + "\" does not match \"" + dnsName + "\".");
						}
					}
				} else {
					if (type == GeneralName.iPAddress) { // Check against IP
															// address
						if (ipAsHostname) { // only if hostname was given as IP
											// address
							String ipString = (String) item.get(1);
							if (checkIP(hostname, ipString)) {
								return true;
							} else {
								LOGGER.debug("Hostname \"" + hostname + "\" does not match \"" + ipString + "\".");
							}
						}
					}
				}
			}
		}

		// If no match was found in subjectAltName, or they were not present,
		// check against the DN.
		if (checkBasedOnDN(hostname, cert)) {
			return true;
		} else {
			LOGGER.debug("Hostname \"" + hostname + "\" does not match DN \"" + DNHandler.getSubject(cert).getRFCDN()
					+ "\".");
		}

		return false;
	}

    /**
     * Checks whether the hostname matches the most specific CN in the certificate subject DN. Wildcard '*' is supported
     * and service name prefix is ignored.
     * 
     * @param hostname The hostname to check.
     * @param cert The certificate to get the subject DN from.
     * @return True in case the hostname matches the most specific CN in the certificate subject DN.
     */
    private static boolean checkBasedOnDN(String hostname, X509Certificate cert) {
        // First check whether the DN contains a match for the hostname
        X500Principal principal = cert.getSubjectX500Principal();
        if (principal != null && !"".equals(principal.getName())) {
            // separated the DN checking to make it easier later to add functionality for DN altName, if necessary.
            if (checkDN(hostname, DNHandler.getDN(principal))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks the hostname given in four dot separated decimal format (IPv4) or in number-colon format (IPv6) against
     * the given IP address.
     * 
     * @param hostname The hostname in IP format.
     * @param ip The IP address to match against.
     * @return True if the IP addresses match.
     */
    private static boolean checkIP(String hostname, String ip) {
        byte[] ipAltName = IPAddressComparator.parseIP(ip);
        byte[] ipHostname = IPAddressComparator.parseIP(hostname);
        if (ipAltName.length == ipHostname.length) {
            return IPAddressComparator.compare(ipAltName, ipHostname);
        }
        return false;
    }

    /**
     * Checks the hostname against the given dnsName. Wildcard '*' is supported, but can only match one part of the
     * hostname. E.g. dnsName "*.foobar.org" matches "aaa.foobar.org" but not "aaa.bbb.foobar.org".
     * 
     * @param hostname The hostname to match against the given dnsName.
     * @param dnsName The dnsName to match against.
     * @return True in case the hostname matches the dnsName.
     */
    private static boolean checkDNS(String hostname, String dnsName) {
        // check if the dnsName doesn't have wildcards
        if (dnsName.indexOf('*') < 0) {
            if (hostname.trim().equalsIgnoreCase(dnsName)) {
                return true;
            }

        } else { // there is a wildcard
            // exclude dots as the wildcard can only be one dns part as said in RFC 2818.
            String regexp = dnsName.replaceAll("\\*", "[^\\.]*");
            if (hostname.toLowerCase().matches(regexp.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks the hostname against the DN. Uses only the most specific CN as described in RFC 2818.
     * "[service name]/[hostname]" format is supported, and also wildcards.
     * 
     * @param hostname The hostname from e.g. URL.
     * @param dn The DN to search for a match for the hostname.
     * @return True in case the hostname matches with the hostname in the DN.
     */
    private static boolean checkDN(String hostname, DN dn) {
        String cnValue = dn.getLastCNValue();
        if (cnValue == null) {// no CN found, no match can be valid
            return false;
        }
        // check whether the name is prepended by service type, if yes, remove it.
        int index = cnValue.indexOf('/');
        if (index >= 0) {
            cnValue = cnValue.substring(index + 1, cnValue.length());
        }

        return checkDNS(hostname, cnValue);
    }
}

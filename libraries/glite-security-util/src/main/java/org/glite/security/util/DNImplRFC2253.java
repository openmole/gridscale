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

import org.apache.log4j.Logger;

import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.x509.X509Name;

import org.bouncycastle.jce.X509Principal;
import javax.security.auth.x500.X500Principal;

import java.security.Principal;
import java.util.Hashtable;
import java.util.Vector;

/**
 * DNImpl.java
 * 
 * @author Joni Hahkala
 * 
 *         Created on September 8, 2003, 7:21 PM
 */
public class DNImplRFC2253 implements DN {
    /** Marker for the RFC2253 format. */
    public static final int RFC2253 = 0;

    /** Marker for the X500 format. */
    public static final int X500 = 1;

    /** Marker for the canonicalized format. */
    public static final int CANON = 2;

    /** Logging facility. */
    private static final Logger LOGGER = Logger.getLogger(DNImplRFC2253.class);

    /**
     * The DN in RFC2253 format. A cache to avoid generating the string multiple times
     */
    public String m_rfc2253String = null;

    /**
     * The DN in X500 format. A cache to avoid generating the string multiple times
     */
    public String m_x500String = null;

    /**
     * The DN in canonical format. A cache to avoid generating the string multiple times
     */
    public String m_canonicalString = null;

    /** The array of relative distinguished names. */
    public String[] m_rdns = null;

    /** The array of object identifiers. */
    public DERObjectIdentifier[] m_oids = null;

    /** The number of fields in the DN. */
    public int m_count = 0;

    /**
     * Creates a new instance of DN.
     * 
     * @param newOids The array of object identifiers.
     * @param newRdns The array or relative distinguished names.
     * @param newCount The number of fields in the DN (both oids and rdns have to have this number of items).
     */
    public DNImplRFC2253(DERObjectIdentifier[] newOids, String[] newRdns, int newCount) {
        m_oids = newOids;
        m_rdns = newRdns;
        m_count = newCount;
    }

    /**
     * Creates a new DNImpl object. Assumes the string represents either an old openssl x500 format DN in direct format
     * or a proper reversed RFC2253 format DN.
     * 
     * @param name Generates a new DNImpl class from the DN in the name.
     */
    public DNImplRFC2253(String name) {
        if (name.startsWith("/")) {
            parseX500Int(name);
        } else {
            parse(name, true);
        }
    }

    /**
     * Creates a new DNImpl object.
     * 
     * @param name Generates a new DNImpl class from the DN in the name.
     * @param reversed Whether the given DN is reversed or not for proper RFC 2254 DNs this should be true.
     */
    public DNImplRFC2253(String name, boolean reversed) {
        if (name.startsWith("/")) {
            parseX500Int(name);
        } else {
            parse(name, reversed);
        }
    }

    /**
     * Creates a new DNImpl object.
     * 
     * @param principal The Principal holding the information to generate the DN from.
     */
    @SuppressWarnings("unchecked")
	public DNImplRFC2253(Principal principal) {
        X509Principal x509Principal;
//        LOGGER.debug("input is: " + principal.getClass().getName() + " from classloader: " + principal.getClass().getClassLoader() + " current one is: " + getClass().getClassLoader());

        if (principal instanceof X509Principal) {
        	// for X509Principal use it directly.
//        	LOGGER.debug("input is X509Principal");
            x509Principal = (X509Principal) principal;
        } else {
            if (principal instanceof X500Principal) {
            	// for X500Principal, get the encoded and reparse as bouncycastle X509Principal.
//                LOGGER.debug("input is java Principal");

                try {
                    x509Principal = new X509Principal((((X500Principal) principal).getEncoded()));
                } catch (Exception e) {
                    LOGGER.error("Invalid X500Principal DN name: " + principal);
                    throw new IllegalArgumentException("Invalid X500Principal DN name: "
                            + principal);
                }
            } else {
            	// for other principals, get the name and try to parse it.
                LOGGER.debug("input is some other principal: " + principal.getClass().getName());
                String name = principal.getName();
                String testName = name.toLowerCase().trim();
                // UGLY HACK, shouldn't do this, but there seems to be no way around it, input can be many classes that give the DN in different orders. And from different classloaders preventing casts etc.
                // if DN starts with email or CN, it's in reversed order
//                LOGGER.debug("test name: " + testName);
                if(testName.startsWith("email") || testName.startsWith("e=") || testName.startsWith("cn=") || testName.startsWith("uid=") || testName.startsWith("sn=")){
                    x509Principal = new X509Principal(true, principal.getName());
//                    LOGGER.debug("name first " + x509Principal);
                } else {
                	// if it starts with country or state, it's in direct order
                	if(testName.startsWith("c=") || testName.startsWith("st=") || testName.startsWith("ou=") || testName.startsWith("dc=") || testName.startsWith("o=")){
                        x509Principal = new X509Principal(false, principal.getName());
//                        LOGGER.debug("country first, reverse " + x509Principal);
                	} else {
                		// check if it end with CN, email, UID or SN, and then not flip it.
                		x509Principal = new X509Principal(false, principal.getName());
                		Vector oids = x509Principal.getOIDs();
                		String rdn = ((DERObjectIdentifier)oids.lastElement()).getId();
                    	if(rdn.equals(X509Name.CN.getId()) || rdn.equals(X509Name.E.getId()) || rdn.equals(X509Name.UID.getId()) || rdn.equals(X509Name.SN.getId())){
                            x509Principal = new X509Principal(false, principal.getName());
                    	} else {
	                   		// other cases assume it's in reverse order
	                        x509Principal = new X509Principal(true, principal.getName());
//                            LOGGER.debug("unknown first " + x509Principal);
                    	}
                	}
                }
            }
        }

        m_oids = (DERObjectIdentifier[]) x509Principal.getOIDs().toArray(new DERObjectIdentifier[] {});
        m_rdns = (String[]) x509Principal.getValues().toArray(new String[] {});
        m_count = m_oids.length;
    }

    /**
     * Creates a new DNImpl object.
     * 
     * @param x509Name The X509Name instance holding the information to generate the DN from.
     */
    @SuppressWarnings("unchecked")
	public DNImplRFC2253(X509Name x509Name) {
        m_oids = (DERObjectIdentifier[]) x509Name.getOIDs().toArray(new DERObjectIdentifier[] {});
        m_rdns = (String[]) x509Name.getValues().toArray(new String[0]);
        m_count = m_oids.length;

    }

    /**
     * Generates a X500 format string of the DN.
     * 
     * @return the X500 format string of the DN.
     */
    public String getX500() {
        if (m_x500String == null) {
            constructX500();
        }

        return m_x500String;
    }

    /* (non-Javadoc)
     * @see org.glite.security.util.DN#getRFC2253()
     */
    public String getRFC2253() {
        return constructRFC2253(false, false, DNHandler.s_rfc2253Lookup);
    }

    /* (non-Javadoc)
     * @see org.glite.security.util.DN#getRFC2253v2()
     * 
     */
    public String getRFC2253v2() {
        return constructRFC2253(false, false, DNHandler.s_rfc2253v2Lookup);
    }

    /* (non-Javadoc)
     * @see org.glite.security.util.DN#getRFCDN(boolean)
     */
    public String getRFCDN() {
        return constructRFC2253(false, true, DNHandler.s_rfc2253Lookup);
    }

    /* (non-Javadoc)
     * @see org.glite.security.util.DN#getRFCDNv2(boolean)
     */
    public String getRFCDNv2() {
        return constructRFC2253(false, true, DNHandler.s_rfc2253v2Lookup);
    }
    /**
     * Generates a canonical format string of the DN.
     * 
     * @return the canonical format string of the DN.
     */
    public String getCanon() {
        if (m_canonicalString == null) {
            constructRFC2253(true);
        }

        return m_canonicalString;
    }

    /**
     * Contructs the X500 format string of the DN.
     * 
     * @return the X500 format string of the DN.
     */
    public String constructX500() {
        StringBuffer buf = new StringBuffer();

        for (int n = 0; n < m_count; n++) {
            buf.append('/');

            String rdnSymbol = (String) DNHandler.s_rfc2253Lookup.get(m_oids[n]);
            if(rdnSymbol == null){
                rdnSymbol = m_oids[n].toString();
            }
            buf.append(rdnSymbol);
            buf.append('=');
            buf.append(m_rdns[n]);
        }

        m_x500String = buf.toString();

        return m_x500String;
    }

    /**
     * Constructs the RFC2253 format string of the DN.
     * 
     * @param canon whether to construct canonical (lowercase) version of the string.
     * @return the RFC2253 format string of the DN.
     * @deprecated Use getXXX or internally use private construct method.
     */
    public String constructRFC2253(boolean canon) {
		return constructRFC2253(canon, false, DNHandler.s_rfc2253Lookup);
    }

    /**
     * Constructs the RFC2253 format DN.
     * 
     * @param canon Whether the canonical (lowercase) format is wanted.
     * @param reverse Whether to reverse the DN (yes for actual rfc 2253 conformance, CN first).
     * @param lookup The table to lookup the RDN description strings (CN, O, C etc.).
     * @return The constructed string.
     */
    @SuppressWarnings("unchecked")
	private String constructRFC2253(boolean canon, boolean reverse, Hashtable lookup) {
        StringBuffer buf = new StringBuffer();
        boolean first = true;

        if(!reverse){
            for (int n = 0; n < m_count; n++) {
                addRDN(buf, n, first, lookup);
                first = false;
            }
        }else{
            for (int n = m_count - 1; n >= 0; n--) {
                addRDN(buf, n, first, lookup);
                first = false;
            }
        }

        m_rfc2253String = buf.toString();
        m_canonicalString = buf.toString().toLowerCase();

        if (canon) {
            return m_canonicalString;
        }
        return m_rfc2253String;
    }
    
    /**
     * Adds the given RDN part to the end of the buffer.
     * 
     * @param buf The buffer where the DN is being constructed.
     * @param n The index of the RDN to add.
     * @param first Whether this is the first RDN.
     * @param lookup There to lookup the textual representations of the RDN type.
     */
    @SuppressWarnings("unchecked")
	private void addRDN(StringBuffer buf, int n, boolean first, Hashtable lookup){
        if (!first) {
            buf.append(',');
        }

        String rdnSymbol = (String) lookup.get(m_oids[n]);
        if(rdnSymbol == null){
            rdnSymbol = m_oids[n].toString();
        }
        
        buf.append(rdnSymbol);
        buf.append('=');
        buf.append(m_rdns[n]);
    }

    /**
     * Parses the RFC2253 format string and puts the fields into the internal structure.
     * 
     * @param inputDN the string that contains the DN to parse.
     * @deprecated internal method, replaced with parse(String inputDN, boolean reversed)
     */
    public void parse(String inputDN) {
        parse(inputDN, false);
    }

    /**
     * Parses the RFC2253 format string and puts the fields into the internal structure.
     * 
     * @param inputDN the string that contains the DN to parse.
     * @param reversed Whether the given DN is to be considered reversed or not.
     */
    @SuppressWarnings("unchecked")
	private void parse(String inputDN, boolean reversed) {
        X509Principal x509Principal = new X509Principal(reversed, inputDN);

        m_oids = (DERObjectIdentifier[]) x509Principal.getOIDs().toArray(new DERObjectIdentifier[] {});
        m_rdns = (String[]) x509Principal.getValues().toArray(new String[0]);
        m_count = m_oids.length;
    }

    /**
     * Parses the X500 format string and puts the fields into the internal structure.
     * 
     * @param inputDN the string that contains the DN to parse.
     * @deprecated internal method, replaced with parseX500(String inputDN, boolean reversed)
     */
    public void parseX500(String inputDN) {
        parseX500Int(inputDN);
    }

    /**
     * Parses the input DN into the internal data structure.
     * @param inputDN The DN to parse.
     */
    @SuppressWarnings("unchecked")
	private void parseX500Int(String inputDN) {
        String[] parts = inputDN.split("/");

        if (parts.length < 2) {
            return;
        }

        StringBuffer newInput = new StringBuffer();
        newInput.append(parts[1]);

        for (int i = 2; i < parts.length; i++) {
            if(parts[i].contains("=")) {
                newInput = newInput.append(", ").append(parts[i]);
            } else{
                newInput.append('/').append(parts[i]);
            }
        }

        X509Principal x509Principal = new X509Principal(false, newInput.toString());

        m_oids = (DERObjectIdentifier[]) x509Principal.getOIDs().toArray(new DERObjectIdentifier[] {});
        m_rdns = (String[]) x509Principal.getValues().toArray(new String[0]);
        m_count = m_oids.length;
    }

    /**
     * The equals comparison of the DN with another DN. The comparison is done using oids and rdns.
     * 
     * @param inputDN2 The DN to compare with.
     * @return true if the DNs are equal (oids match, rdns match, count of fields match), false otherwise.
     */
    @SuppressWarnings("deprecation")
    public boolean equals(Object inputDN2) {
        int count;
        String[] rdns = null;
        DERObjectIdentifier[] oids = null;
        
        if (inputDN2 instanceof DNImpl) {
            DNImpl dn2 = (DNImpl) inputDN2;
            count = dn2.m_count;
            rdns = dn2.m_rdns;
            oids = dn2.m_oids;
        } else {
            if (inputDN2 instanceof DNImplRFC2253){
                DNImplRFC2253 dn2 = (DNImplRFC2253) inputDN2;
                count = dn2.m_count;
                rdns = dn2.m_rdns;
                oids = dn2.m_oids;
                
            } else {
                return false;
            }
        }

        if (m_count != count) {
            return false;
        }

        for (int n = 0; n < m_count; n++) {
            if (!m_oids[n].equals(oids[n])) {
                return false;
            }
        }

        for (int n = 0; n < m_count; n++) {
            if (!m_rdns[n].toLowerCase().equals(rdns[n].toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the RFC2253 format of the DN.
     * 
     * @return the RFC2253 format of the DN.
     */
    public String toString() {
        return getRFC2253();
    }

    /**
     * Returns the DN without the last CN. Throws IllegalArgumentException in case the DN doesn't end with CN or in case
     * the proxy checking is used and the DN does not end with the proxy CN.
     * 
     * @param checkProxy whether to check that the last CN is a proxy CN (matches "^((limited )*proxy|[0-9]*)$").
     * @return The DN without the last CN.
     */
    public DN withoutLastCN(boolean checkProxy) {
        if (!m_oids[m_count - 1].equals(X509Name.CN)) {
            throw new IllegalArgumentException("Trying to remove last CN from DN that does not end in CN. DN was: "
                    + toString());
        }

        if (checkProxy) {
            if (!m_rdns[m_count - 1].matches("^((limited )?proxy|[0-9]*)$")) {
                throw new IllegalArgumentException(
                        "Trying to remove the last proxy CN from DN that does not end in proxy CN. DN was: "
                                + toString());
            }
        }

        int newCount = m_count - 1;

        String[] newRdns = new String[newCount];
        DERObjectIdentifier[] newOids = new DERObjectIdentifier[newCount];

        for (int n = 0; n < newCount; n++) {
            newRdns[n] = m_rdns[n];
            newOids[n] = m_oids[n];
        }

        return new DNImplRFC2253(newOids, newRdns, newCount);
    }

    /**
     * Returns the hashcode of the instance.
     * 
     * @return the hashcode.
     */
    public int hashCode() {
        return java.util.Arrays.hashCode(m_rdns) + java.util.Arrays.hashCode(m_oids) + m_count;
    }

    
    /* (non-Javadoc)
     * @see org.glite.security.util.DN#getLastCNValue()
     */
    public String getLastCNValue() {
        for(int i = m_count -1; i >= 0; i--){
            if(m_oids[i].equals(X509Name.CN)){
                return m_rdns[i];
            }
        }
        return null;
    }

    /* (non-Javadoc)
     * @see org.glite.security.util.DN#isEmpty()
     */
    public boolean isEmpty() {
        if(m_count == 0){ // if no RDNs, the DN is empty.
            return true;
        }
        return false;
    }

}

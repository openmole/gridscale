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
package org.glite.security.util.proxy;

import java.io.IOException;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERSequence;

/**
 * Proxy cert info extension ASN1 class.
 * 
 * <pre>
 * ProxyCertInfoExtension ::= SEQUENCE { 
 *          pCPathLenConstraint    ProxyCertPathLengthConstraint OPTIONAL, 
 *          proxyPolicy            ProxyPolicy }
 *  
 *     ProxyCertPathLengthConstraint ::= INTEGER
 * </pre>
 * 
 * @author Joni Hahkala
 */
public class ProxyCertInfoExtension implements ASN1Encodable {

    /** Identifier for no proxy path length limit. */
    public static final int UNLIMITED = Integer.MAX_VALUE;
    /** The oid of the proxy cert info extension, defined in the RFC 3820. */
    public static final String PROXY_CERT_INFO_EXTENSION_OID = "1.3.6.1.5.5.7.1.14";
    /** The oid of the rfc draft proxy cert extension. */
    public static final String DRAFT_PROXY_CERT_INFO_EXTENSION_OID = "1.3.6.1.4.1.3536.1.1.222";

    /**
     * The sub proxy path lenght, default is not limited.
     */
    private int m_pathLen = UNLIMITED;
    /**
     * The underlying policy object.
     */
    private ProxyPolicy m_policy = null;

    /**
     * Generate new proxy certificate info extension with length limit len and policy policy. Use NO_PATH_LEN_LIMIT if
     * no limit is desired.
     * 
     * @param len the maximum number of proxy certificates to follow this one.
     * @param policy the proxy policy extension.
     */
    public ProxyCertInfoExtension(int len, ProxyPolicy policy) {
        m_pathLen = len;
        this.m_policy = policy;
    }

    /**
     * Generate a proxy that inherits all rights and that has no cert path limitations.
     */
    public ProxyCertInfoExtension() {
        m_policy = new ProxyPolicy(ProxyPolicy.INHERITALL_POLICY_OID);
    }

    /**
     * Constructor that generates instance out of byte array.
     * 
     * @param bytes The byte array to consider as the ASN.1 encoded proxyCertInfo extension.
     * @throws IOException thrown in case the parsing of the byte array fails.
     */
    public ProxyCertInfoExtension(byte[] bytes) throws IOException {
        this((ASN1Sequence) ASN1Primitive.fromByteArray(bytes));
    }

    /**
     * Read a proxyCertInfoExtension from the ASN1 sequence.
     * 
     * @param seq The sequence containing the extension.
     */
    public ProxyCertInfoExtension(ASN1Sequence seq) {
        int index = 0;
        if (seq != null && seq.size() > 0) {
            if (seq.getObjectAt(0) instanceof DERInteger) {
                m_pathLen = ((DERInteger) seq.getObjectAt(0)).getValue().intValue();
                index = 1;
            }
            if (seq.size() <= index) {
                throw new IllegalArgumentException(
                        "ProxyCertInfoExtension parser error, expected policy, but it was not found");
            }
            if (seq.getObjectAt(index) instanceof DERSequence) {
                m_policy = new ProxyPolicy((ASN1Sequence) seq.getObjectAt(index));
            } else {
                throw new IllegalArgumentException(
                        "ProxyCertInfoExtension parser error, expected policy sequence, but got: "
                                + seq.getObjectAt(index).getClass());
            }
            index++;
            if (seq.size() > index) {
                throw new IllegalArgumentException(
                        "ProxyCertInfoExtension parser error, sequence contains too many items");
            }
        }
    }

    /**
     * Get the proxy certificate path length limit of this extension, if set.
     * 
     * @return The number of allowed proxy certificates in the chain allowed after this certificate. UNLIMITED if not
     *         set.
     */
    public int getProxyPathLimit() {
        return m_pathLen;
    }

    /**
     * Get the policy object of this extension.
     * 
     * @return The ProxyPolicy object.
     */
    public ProxyPolicy getPolicy() {
        return m_policy;
    }

    /*
     * Return the extension in DER format.
     * @see org.bouncycastle.asn1.ASN1Encodable#toASN1Object()
     */
    public ASN1Primitive toASN1Primitive() {
        ASN1EncodableVector v = new ASN1EncodableVector();
        if (m_pathLen > -1 && m_pathLen != UNLIMITED) {
            v.add(new DERInteger(m_pathLen));
        }
        if (m_policy != null) {
            v.add(m_policy.toASN1Primitive());
        } else {
            throw new IllegalArgumentException("Can't generate ProxyCertInfoExtension without mandatory policy");
        }

        return new DERSequence(v);
    }

}

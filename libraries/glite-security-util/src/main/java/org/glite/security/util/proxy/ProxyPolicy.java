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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;

/**
 * Proxy policy ASN1 class. ProxyPolicy ::= SEQUENCE { policyLanguage OBJECT IDENTIFIER, policy OCTET STRING OPTIONAL }
 * 
 * @author Joni Hahkala
 */
public class ProxyPolicy implements ASN1Encodable {
    /**
     * The normal, default policy, the proxy inherits the rights of the parent. Defined in RFC 3820.
     */
    public final static String INHERITALL_POLICY_OID = "1.3.6.1.5.5.7.21.1";
    /**
     * The rarely used policy where the proxy is independent of the parent and does not inherit rights from it. Defined in the RFC 3820.
     */
    public final static String INDEPENDENT_POLICY_OID = "1.3.6.1.5.5.7.21.2";
    /**
     * The limited proxy, which should prevent the proxy from being used for job submission. Defined by Globus outside of RFCs.
     */
    public final static String LIMITED_PROXY_OID = "1.3.6.1.4.1.3536.1.1.1.9";

    /**
     * The oid of the policy, default is the inherit all.
     */
    private String m_oid = INHERITALL_POLICY_OID;
    /**
     * The ASN.1 octet string encoding of the policy.
     */
    private ASN1OctetString m_policy = null;

    /**
     * Generate basic proxy policy.
     * 
     * @param oid the policy language or policy to set. If not set using constructors, inherit all policy is assumed.
     */
    public ProxyPolicy(String oid) {
        this.m_oid = oid;
    }

    /**
     * Generate new policy object using language defined by oid and the policy.
     * 
     * @param oid the OID for the language. Null retains the default of inherit all.
     * @param policy the policy. Null means no policy.
     */
    public ProxyPolicy(String oid, ASN1OctetString policy) {
        if (oid != null) {
            this.m_oid = oid;
        }
        this.m_policy = policy;
    }

    /**
     * Read a new proxy policy object from the ASN1 sequence.
     * 
     * @param seq The proxy policy ASN1 sequence.
     */
    public ProxyPolicy(ASN1Sequence seq) {
        if (seq != null && seq.size() > 0) {
            if (seq.getObjectAt(0) instanceof DERObjectIdentifier) {
                m_oid = seq.getObjectAt(0).toString();
            } else {
                throw new IllegalArgumentException("ProxyPolicy parser error, expected object identifier, but got:"
                        + seq.getObjectAt(0).getClass());
            }
        } else {
            throw new IllegalArgumentException(
                    "ProxyPolicy parser error, expected nonempty sequence, but not no sequence or an empty sequence");
        }
        if (seq.size() > 1) {
            if (seq.getObjectAt(1) instanceof DEROctetString) {
                this.m_policy = (ASN1OctetString) seq.getObjectAt(1);
            } else {
                throw new IllegalArgumentException("ProxyPolicy parser error, expected octetstring but got: "
                        + seq.getObjectAt(1).getClass());
            }
        }
        if (seq.size() > 2) {
            throw new IllegalArgumentException("ProxyPolicy parser error, proxy policy can only have two items, got: "
                    + seq.size() + "items.");
        }

    }

    /**
     * Use to get the policy OID as a String.
     * 
     * @return The policy OID as a string. It is most likely one of the constants defined in this class, namely:
     *         <ul>
     *         <li>INHERITALL_POLICY_OID</li>
     *         <li>INDEPENDENT_POLICY_OID</li>
     *         <li>LIMITED_PROXY_OID</li>
     *         <li>something else</li>
     *         </ul>
     */
    public String getPolicyOID() {
        return m_oid;
    }

    /**
     * The optional policy information in this structure
     * 
     * @return The policy in ASN1 structure. Null if not present.
     */
    public ASN1OctetString getPolicyASN1() {
        return m_policy;
    }

    /**
     * output the ASN1 object of the proxy policy.
     * 
     * @see org.bouncycastle.asn1.ASN1Encodable#toASN1Object()
     */
    public ASN1Primitive toASN1Primitive() {
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new DERObjectIdentifier(m_oid));
        if (m_policy != null) {
            try {
                v.add(new DEROctetString(m_policy));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        return new DERSequence(v);
    }

}

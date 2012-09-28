/*********************************************************************
 *
 * Authors: 
 *      Vincenzo Ciaschini - vincenzo.ciaschini@cnaf.infn.it 
 *          
 * Copyright (c) 2006 INFN-CNAF on behalf of the EGEE project.
 * 
 * For license conditions see LICENSE
 *
 * Parts of this code may be based upon or even include verbatim pieces,
 * originally written by other people, in which case the original header
 * follows.
 *
 *********************************************************************/
package org.glite.voms.contact;

import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DEREncodableVector;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DERSequence;
import org.globus.gsi.GSIConstants;
import org.globus.gsi.GSIConstants.CertificateType;
import org.globus.gsi.proxy.ext.ProxyPolicy;

class MyProxyCertInfo implements DEREncodable {

    private int pathLen;
    private ProxyPolicy policy;
    private CertificateType version;

    public MyProxyCertInfo(ProxyPolicy policy, CertificateType version) {
        this.policy = policy;
        this.pathLen = -1;
        this.version = version;
    }

    public MyProxyCertInfo(int pathLenConstraint, 
                           ProxyPolicy policy, CertificateType version) {
        this.policy = policy;
        this.pathLen = pathLenConstraint;
        this.version = version;
    }

    public DERObject getDERObject() {
        DEREncodableVector vec = new DEREncodableVector();

        switch(version) {
        case GSI_3_IMPERSONATION_PROXY:
        case GSI_3_INDEPENDENT_PROXY:
        case GSI_3_LIMITED_PROXY:
        case GSI_3_RESTRICTED_PROXY:
            if (this.pathLen != -1) {
                vec.add(new DERInteger(this.pathLen));
            }
            vec.add(this.policy.getDERObject());
            break;

        case GSI_4_IMPERSONATION_PROXY:
        case GSI_4_INDEPENDENT_PROXY:
        case GSI_4_LIMITED_PROXY:
        case GSI_4_RESTRICTED_PROXY:
            vec.add(this.policy.getDERObject());
            if (this.pathLen != -1) {
                vec.add(new DERInteger(this.pathLen));
            }
            break;

        default:
            break;
        }
        return new DERSequence(vec);
    }
}

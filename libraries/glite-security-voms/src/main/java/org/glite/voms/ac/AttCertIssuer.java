/*
 * Copyright (c) Members of the EGEE Collaboration. 2004.
 * See http://eu-egee.org/partners/ for details on the copyright holders.
 * For license conditions see the license file or http://eu-egee.org/license.html
 */

package org.glite.voms.ac;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x509.GeneralNames;

import java.io.IOException;


/**
 * Shadow implementation of AttributeCertificateInfo from
 * BouncyCastle
 *
 * @author Joni Hahkala, Olle Mulmo
 */
public class AttCertIssuer implements ASN1Encodable {
    GeneralNames v1Form;
    V2Form v2Form;
    int version = -1;

    public AttCertIssuer(ASN1Encodable obj) throws IOException {
        if (obj instanceof ASN1TaggedObject) {
            ASN1TaggedObject cObj = (ASN1TaggedObject) obj;

            if (cObj.isExplicit() && (cObj.getTagNo() == 0)) {
                v2Form = new V2Form(ASN1Sequence.getInstance(cObj, /*explicit=*/
                            false));
                version = 2;
            }
        } else if (obj instanceof ASN1Sequence) {
            v1Form = GeneralNames.getInstance((ASN1Sequence) obj);
            version = 1;
        }

        if (version < 0) {
            throw new IllegalArgumentException("AttCertIssuer: input not a proper CHOICE");
        }
    }

    public AttCertIssuer(V2Form v2FormIn) {
        v2Form = v2FormIn;
        version = 2;
    }

    public AttCertIssuer(GeneralNames v1FormIn) {
        v1Form = v1FormIn;
        version = 1;
    }

    public GeneralNames getIssuerName() {
        switch (version) {
        case 1:
            return v1Form;

        case 2:
            return v2Form.getIssuerName();

        default:
            return null;
        }
    }

    /**
     * Produce an object suitable for an ASN1OutputStream.
     *
     * <pre>
     *
     *   AttCertIssuer ::= CHOICE {
     *        v1Form   GeneralNames,  -- MUST NOT be used in this
     *                                -- profile
     *        v2Form   [0] V2Form     -- v2 only
     *   }
     *
     * </pre>
     */
    public ASN1Primitive toASN1Primitive() {
        switch (version) {
        case 1:
            return v1Form.toASN1Primitive();

        case 2:
            return new DERTaggedObject(true, 0, v2Form);

        default:
            return null;
        }
    }
}

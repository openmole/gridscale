/*
 * Copyright (c) Members of the EGEE Collaboration. 2004.
 * See http://eu-egee.org/partners/ for details on the copyright holders.
 * For license conditions see the license file or http://eu-egee.org/license.html
 */

package org.glite.voms.ac;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Primitive;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DEREncodableVector;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUniversalString;
import org.bouncycastle.asn1.x509.GeneralNames;


/**
 * Implementation of <code>IetfAttrSyntax</code> as specified
 * by RFC3281.
 *
 * <pre>
 * IetfAttrSyntax ::= SEQUENCE {
 *   policyAuthority [0] GeneralNames OPTIONAL,
 *   values SEQUENCE OF CHOICE {
 *     octets OCTET STRING,
 *     oid OBJECT IDENTIFIER,
 *     string UTF8String
 *   }
 * }
 * </pre>
 *
 * @author mulmo
 */
public class IetfAttrSyntax implements ASN1Encodable {
    public static final int VALUE_OCTETS = 1;
    public static final int VALUE_OID = 2;
    public static final int VALUE_UTF8 = 3;
    GeneralNames policyAuthority = null;
    Vector values = new Vector();
    int valueChoice = -1;

    /**
     *
     */
    public IetfAttrSyntax(ASN1Sequence seq) {
        int i = 0;

        if (seq.getObjectAt(0) instanceof ASN1TaggedObject) {
            policyAuthority = GeneralNames.getInstance((ASN1TaggedObject) seq.getObjectAt(0), /*explicit=*/
                    false);
            i++;
        }

        if (!(seq.getObjectAt(i) instanceof ASN1Sequence)) {
            throw new IllegalArgumentException("Non-IetfAttrSyntax encoding");
        }

        seq = (ASN1Sequence) seq.getObjectAt(i);

        for (Enumeration e = seq.getObjects(); e.hasMoreElements();) {
            ASN1Primitive obj = (ASN1Primitive) e.nextElement();
            int type;

            if (obj instanceof DERObjectIdentifier) {
                type = VALUE_OID;
            } else if (obj instanceof DERUniversalString) {
                type = VALUE_UTF8;
            } else if (obj instanceof DEROctetString) {
                type = VALUE_OCTETS;
            } else {
                throw new IllegalArgumentException("Bad value type encoding IetfAttrSyntax");
            }

            if (valueChoice < 0) {
                valueChoice = type;
            }

            if (type != valueChoice) {
                throw new IllegalArgumentException("Mix of value types in IetfAttrSyntax");
            }

            values.add(obj);
        }
    }

    public GeneralNames getPolicyAuthority() {
        return policyAuthority;
    }

    public int getValueType() {
        return valueChoice;
    }

    public List getValues() {
        return values;
    }

    public ASN1Primitive toASN1Primitive() {
        DEREncodableVector v = new DEREncodableVector();

        if (policyAuthority != null) {
            v.add(new DERTaggedObject(0, policyAuthority));
        }

        DEREncodableVector v2 = new DEREncodableVector();

        for (Iterator i = values.iterator(); i.hasNext();) {
            v2.add((ASN1Encodable) i.next());
        }

        v.add(new DERSequence(v2));

        return new DERSequence(v);
    }
}

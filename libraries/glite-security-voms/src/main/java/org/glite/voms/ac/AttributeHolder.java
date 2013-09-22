/*********************************************************************
 *
 * Authors: Vincenzo Ciaschini - Vincenzo.Ciaschini@cnaf.infn.it
 *
 * Copyright (c) 2002, 2003, 2004, 2005, 2006 INFN-CNAF on behalf of the 
 * EGEE project.
 * For license conditions see LICENSE
 *
 * Parts of this code may be based upon or even include verbatim pieces,
 * originally written by other people, in which case the original header
 * follows.
 *
 *********************************************************************/

package org.glite.voms.ac;

import java.util.Enumeration;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Primitive;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEREncodableVector;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;


/**
 * This calss represents an Attribute Holder object.
 *
 * @author Vincenzo Ciaschini
 */
public class AttributeHolder implements ASN1Encodable {
    private List l;
    private GeneralNames grantor;

    /**
     * Empty constructor.
     */
    public AttributeHolder() {
        l = null;
        grantor = null;
    }

    /**
     * Creates an AttributeHolder object from a Sequence.
     *
     * @param seq the Sequence
     *
     * @throws IllegalArgumentException if there are parsing problems.
     */
    public AttributeHolder(ASN1Sequence seq) {
        l = new Vector();
        grantor = null;

        if (seq.size() != 2)
            throw new IllegalArgumentException("Encoding error in AttributeHolder");

        //        System.out.println("0 CLASS: " + seq.getObjectAt(0).getClass());
        //        System.out.println("1 CLASS: " + seq.getObjectAt(1).getClass());

        if ((seq.getObjectAt(0) instanceof ASN1Sequence) &&
            (seq.getObjectAt(1) instanceof ASN1Sequence)) {
            grantor = GeneralNames.getInstance(seq.getObjectAt(0));
            seq = (ASN1Sequence) seq.getObjectAt(1);
            for (Enumeration e = seq.getObjects(); e.hasMoreElements(); ) {
                GenericAttribute att = new GenericAttribute((ASN1Sequence)e.nextElement());
                l.add(att);
            }
        }
        else
            throw new IllegalArgumentException("Encoding error in AttributeHolder");
    }
    /**
     * Static variant of the constructor.
     *
     * @see #AttributeHolder(ASN1Sequence seq)
     */
    public static AttributeHolder getInstance(ASN1Sequence seq) {
        return new AttributeHolder(seq);
    }

    /**
     * Gets the Grantor of these attributes.
     *
     * @return the grantor.
     */
    public String getGrantor() {
        ASN1Sequence seq = ASN1Sequence.getInstance(grantor.toASN1Primitive());
        GeneralName  name  = GeneralName.getInstance(seq.getObjectAt(0));
        return DERIA5String.getInstance(name.getName()).getString();
    }

    /**
     *
     * Gets a list of Generic Attributes.
     *
     * @return the list or null if none was loaded.
     */
    public List getAttributes() {
        return l;
    }

    /**
     * Makes a DERObject representation.
     *
     * @return the DERObject
     */
    public ASN1Primitive toASN1Primitive() {
        DEREncodableVector v = new DEREncodableVector();

        v.add(grantor);
        
        DEREncodableVector v2 = new DEREncodableVector();

        for (ListIterator li = l.listIterator(); li.hasNext(); ) {
            GenericAttribute att = (GenericAttribute)li.next();
            v2.add(att);
        }
        ASN1Sequence seq = (ASN1Sequence) new DERSequence(v2);

        v.add(seq);

        return new DERSequence(v);
    }
}

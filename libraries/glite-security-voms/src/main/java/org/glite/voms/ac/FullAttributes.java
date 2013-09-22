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
import org.bouncycastle.asn1.DERSequence;

/**
 * This class represents the GenericAttributes extension which may be found
 * in the AC.
 *
 * @author Vincenzo Ciaschini
 */
public class FullAttributes implements ASN1Encodable {
    private List l;

    /**
     * Empty contructor
     */
    public FullAttributes() {
        l = new Vector();
    }

    /**
     * Creates a FullAttributes object from a sequence.
     *
     * @param seq the Sequence
     *
     * @throws IllegalArgumentException if there are parsing problems.
     */
    public FullAttributes(ASN1Sequence seq) {
        l = new Vector();
        if (seq.size() != 1)
            throw new IllegalArgumentException("Encoding error in FullAttributes");

        //        if (seq.getObjectAt(0) instanceof ASN1Sequence) {
        if (true) {
            seq = (ASN1Sequence) seq.getObjectAt(0);
            for (Enumeration e = seq.getObjects(); e.hasMoreElements(); ) {
                AttributeHolder holder = new AttributeHolder((ASN1Sequence)e.nextElement());
                l.add(holder);
            }
        }
        else
            throw new IllegalArgumentException("Encoding error in FullAttributes");
    }

    /**
     * Static variant of the constructor.
     *
     * @see #FullAttributes(ASN1Sequence seq)
     */
    public static FullAttributes getInstance(ASN1Sequence seq) {
        return new FullAttributes(seq);
    }

    /**
     * Returns a list of the AttributeHolders.
     *
     * @return the list or null if none was there.
     */
    public List getAttributeHolders() {
        return l;
    }

    /**
     * Makes a DERObject representation.
     *
     * @return the DERObject
     */
    public ASN1Primitive toASN1Primitive() {
        DEREncodableVector v2 = new DEREncodableVector();

        for (ListIterator li = l.listIterator(); li.hasNext(); ) {
            AttributeHolder holder = (AttributeHolder)li.next();
            v2.add(holder);
        }

        ASN1Sequence seq = (ASN1Sequence) new DERSequence(v2);
        DEREncodableVector v = new DEREncodableVector();
        v.add(seq);

        return new DERSequence(v);
    }
}

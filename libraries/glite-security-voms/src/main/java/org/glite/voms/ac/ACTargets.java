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
 * The intent of this class is to represent the ACTargets extension which
 * may be present in the AC.
 *
 * @author Vincenzo Ciaschini
 */
public class ACTargets implements ASN1Encodable {
    private List l;
    private List parsed;

    /**
     * Empty constructor.
     */
    public ACTargets() {
        l = new Vector();
        parsed = new Vector();
    }

    /**
     * Creates an ACTargets from a sequence.
     *
     * @param seq the sequence.
     *
     * @throws IllegalArgumentException if there are parsing errors.
     */
    public ACTargets(ASN1Sequence seq) {
        l = new Vector();
        parsed = new Vector();

        for (Enumeration e = seq.getObjects(); e.hasMoreElements(); ) {
            ACTarget targ = new ACTarget((ASN1Sequence)e.nextElement());
            l.add(targ);
            parsed.add(targ.toString());
        }
    }

    /**
     * Static variant of the constructor.
     *
     * @see #ACTargets(ASN1Sequence seq)
     */
    public static ACTargets getInstance(ASN1Sequence seq) {
        return new ACTargets(seq);
    }

    /**
     * Manually add a target.
     *
     * @param s the target.
     */
    public void addTarget(String s) {
        ACTarget trg = new ACTarget();
        trg.setName(s);
        l.add(trg);
    }

    /**
     * Manually add a target.
     *
     * @param act the target.
     *
     * @see ACTarget
     */
    public void AddTarget(ACTarget act) {
        l.add(act);
    }

    /**
     * Gets the list of targets.
     *
     * @return a List containing the targets, expressed as String.
     */
    public List getTargets() {
        return parsed;
    }

    /**
     * Makes a DERObject representation.
     *
     * @return the DERObject
     */
    public ASN1Primitive toASN1Primitive() {
        DEREncodableVector v = new DEREncodableVector();

        ListIterator li = l.listIterator();
        while (li.hasNext()) {
            ACTarget c = (ACTarget)li.next();
            v.add(c);
        }
        return new DERSequence(v);
    }
}

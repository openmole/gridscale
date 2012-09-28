package org.glite.voms.ac;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.IssuerSerial;

/**
 * The intent of this class is to represent a single target.
 *
 * @author Vincenzo Ciaschini
 */
public class ACTarget implements DEREncodable {
    private GeneralName name;
    private GeneralName  group;
    private IssuerSerial cert;

    /**
     * empty contructor
     */

    public ACTarget() {
        name = null;
        group = null;
        cert = null;
    }

    /**
     * Creates a string representation of the target.
     *
     * @return the string, or null if there were problems.
     */
    public String toString() {
        if (name != null)
            return getName();
        if (group != null)
            return getGroup();
        if (cert != null)
            return getIssuerSerialString();

        return null;
    }


    /**
     * Gets the name element.
     *
     * @return the name.
     */
    public String getName() {
        return new String(NameConverter.getInstance(name).getAsString());
    }

    /**
     * Gets the group element
     *
     * @return the group.
     */
    public String getGroup() {
        return new String(NameConverter.getInstance(group).getAsString());
    }

    /**
     * Gets the IssuerSerial
     *
     * @return the IssuerSerial
     */
    public IssuerSerial getIssuerSerial() {
        return cert;
    }

    /**
     * Gets the IssuerSerial element
     *
     * @return the IssuerSerial as String.
     */
    public String getIssuerSerialString() {
        ASN1Sequence seq = ASN1Sequence.getInstance(cert.getIssuer().getDERObject());
        GeneralName  name  = GeneralName.getInstance(seq.getObjectAt(0));

        return new String(NameConverter.getInstance(name).getAsString() + ":" +
                          (cert.getSerial().toString()));
    }

    /**
     * Sets the name
     *
     * @param n the name
     */
    public void setName(GeneralName n) {
        name = n;
    }

    /**
     * Sets the name
     *
     * @param s the name.
     */
    public void setName(String s) {
        name = new GeneralName(new DERIA5String(s), 6);
    }

    /**
     * Sets the group.
     *
     * @param g the group
     */
    public void setGroup(GeneralName g) {
        group = g;
    }

    /**
     * Sets the group
     *
     * @param s the group name.
     */
    public void setGroup(String s) {
        group = new GeneralName(new DERIA5String(s), 6);
    }

    /**
     * Sets the IssuerSerial
     *
     * @param is the IssuerSerial
     */
    public void setIssuerSerial(IssuerSerial is) {
        cert = is;
    }

    /**
     * Sets the IssuerSerial
     *
     * @param s a textual representation of the IssuerSerial, in the from
     * subject:serial
     */
    public void setIssuerSerial(String s) {
        int ch = s.lastIndexOf(':');
        if (ch != -1) {
            String iss, ser;
            iss = s.substring(0, ch);
            ser = s.substring(ch+1);
            GeneralName nm = new GeneralName(new DERIA5String(iss), 6);
            ASN1Sequence seq = ASN1Sequence.getInstance(name.getDERObject());

            DEREncodableVector v = new DEREncodableVector();
            v.add(nm);
            v.add(seq);
            cert = new IssuerSerial(new DERSequence(v));
        }
        else throw new IllegalArgumentException("cannot identify issuer and serial");
    }

    /**
     * Static variant of the constructor.
     *
     * @see #ACTarget(org.bouncycastle.asn1.ASN1Sequence seq)
     */
    public static ACTarget getInstance(ASN1Sequence seq) {
        return new ACTarget(seq);
    }

    /**
     * Creates an ACTarget from a sequence
     *
     * @param seq the Sequence
     *
     * @throws IllegalArgumentException if there are parsing problems.
     */
    public ACTarget(ASN1Sequence seq) {
        int i = 0;
        name = group = null;
        cert = null;

        while (i <= seq.size()) {
            if (seq.getObjectAt(i) instanceof ASN1TaggedObject) {
                ASN1TaggedObject obj = (ASN1TaggedObject) seq.getObjectAt(i);
                switch (obj.getTagNo()) {
                case 0:
                    group = null;
                    cert = null;
                    name = GeneralName.getInstance((ASN1TaggedObject)obj, true);
                    break;
                case 1:
                    cert = null;
                    group = GeneralName.getInstance((ASN1TaggedObject)obj, true);
                    name = null;
                    break;
                case 2:
                    group = null;
                    name = null;
                    cert = new IssuerSerial((ASN1Sequence)obj.getObject());
                    break;
                default:
                    throw new IllegalArgumentException("Bad tag in encoding ACTarget");
                }
            }
            else
                throw new IllegalArgumentException("Bad value type encoding ACTarget");
            i++;
        }
    }

    /**
     * Makes a DERObject representation.
     *
     * @return the DERObject
     */
    public DERObject getDERObject() {
        DEREncodableVector v = new DEREncodableVector();

        if (name != null)
            v.add(new DERTaggedObject(0, name));
        if (group != null)
            v.add(new DERTaggedObject(1, group));
        if (cert != null)
            v.add(new DERTaggedObject(2, cert));

        return new DERSequence(v);
    }
}

class NameConverter {
    private GeneralName name;
    private String      value;

    public NameConverter(GeneralName gn) {
        name = gn;

        switch (gn.getTagNo()) {
        case 6:
            value = DERIA5String.getInstance(name.getName()).getString();
            break;
        default:
            throw new IllegalArgumentException("Erroneous encoding of Targets");
        }
    }

    public static org.glite.voms.ac.NameConverter getInstance(GeneralName gn) {
        return new org.glite.voms.ac.NameConverter(gn);
    }

    public String getAsString() {
        return value;
    }
}
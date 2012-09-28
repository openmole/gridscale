/*
 * Copyright (c) Members of the EGEE Collaboration. 2004.
 * See http://eu-egee.org/partners/ for details on the copyright holders.
 * For license conditions see the license file or http://eu-egee.org/license.html
 */

package org.glite.voms.ac;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DEREncodableVector;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuerSerial;

/**
 * The Holder object.
 * <pre>
 *  Holder ::= SEQUENCE {
 *        baseCertificateID   [0] IssuerSerial OPTIONAL,
 *                 -- the issuer and serial number of
 *                 -- the holder's Public Key Certificate
 *        entityName          [1] GeneralNames OPTIONAL,
 *                 -- the name of the claimant or role
 *        objectDigestInfo    [2] ObjectDigestInfo OPTIONAL
 *                 -- used to directly authenticate the holder,
 *                 -- for example, an executable
 *  }
 * </pre>
 */
public class Holder implements DEREncodable {
    IssuerSerial baseCertificateID = null;
    GeneralNames entityName = null;
    ObjectDigestInfo objectDigestInfo = null;

    public Holder(X509Certificate cert) {
        this(cert.getIssuerX500Principal(), cert.getSerialNumber());
    }

    public Holder(X500Principal issuer, BigInteger serial) {
        DEREncodableVector v = new DEREncodableVector();
        v.add(Util.x500nameToGeneralNames(issuer));
        v.add(new DERInteger(serial));
        baseCertificateID = new IssuerSerial(new DERSequence(v));
    }

    public Holder(ASN1Sequence seq) {
        for (Enumeration e = seq.getObjects(); e.hasMoreElements();) {
            DERObject obj = (DERObject) e.nextElement();

            if (!(obj instanceof ASN1TaggedObject)) {
                throw new IllegalArgumentException("Holder element not tagged");
            }

            ASN1TaggedObject tObj = (ASN1TaggedObject) obj;

            switch (tObj.getTagNo()) {
            case 0:
                baseCertificateID = new IssuerSerial((ASN1Sequence) tObj.getObject());

                break;

            case 1:
                entityName = GeneralNames.getInstance(tObj, false);

                break;

            case 2:
                objectDigestInfo = new ObjectDigestInfo((ASN1Sequence) tObj.getObject());

                break;

            default:
                throw new IllegalArgumentException("Unknown tag number " + tObj.getTagNo());
            }
        }
    }

    public GeneralNames getIssuer() {
        if (baseCertificateID != null)
            return baseCertificateID.getIssuer();
        else if (entityName != null)
            return entityName;
        return null;
    }

    protected static boolean matchesDN(X500Principal subject, GeneralNames targets) {
        Enumeration e = ((ASN1Sequence) targets.getDERObject()).getObjects();

        while (e.hasMoreElements()) {
            GeneralName gn = GeneralName.getInstance(e.nextElement());

            if (gn.getTagNo() == 4) {
                try {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    new DEROutputStream(b).writeObject(gn.getName());
                    
                    X500Principal principal = new X500Principal(b.toByteArray());
                    
                    if (principal.equals(subject)) {
                        return true;
                    }
                } catch (IOException i) {
                }
            }
        }

        return false;
    }

    /*
     * check if the holder DN matches the DN of the user cert issuer and the SN the user cert SN 
     */
    public boolean isHolder(X509Certificate cert) {
        if (baseCertificateID != null) {
            if (baseCertificateID.getSerial().getValue().equals(cert.getSerialNumber())){
                
                if (matchesDN(cert.getIssuerX500Principal(), baseCertificateID.getIssuer())) {
                    return true;
                }
                //TODO: remove this cludge that works around a bug in voms versions pre 1.6.7
                if(matchesDN(cert.getSubjectX500Principal(), baseCertificateID.getIssuer())){
                    return true;
                }
            }
        }

        if (entityName != null) {
            if (matchesDN(cert.getSubjectX500Principal(), entityName)) {
                return true;
            }
        }

        /**
         * objectDigestInfo not supported
         */
        return false;
    }

    public DERObject getDERObject() {
        ASN1EncodableVector v = new ASN1EncodableVector();

        if (baseCertificateID != null) {
            v.add(new DERTaggedObject(false, 0, baseCertificateID));
        }

        if (entityName != null) {
            v.add(new DERTaggedObject(false, 1, entityName));
        }

        if (objectDigestInfo != null) {
            v.add(new DERTaggedObject(false, 2, objectDigestInfo));
        }

        return new DERSequence(v);
    }
}

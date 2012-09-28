/*
 * Copyright (c) Members of the EGEE Collaboration. 2004.
 * See http://eu-egee.org/partners/ for details on the copyright holders.
 * For license conditions see the license file or http://eu-egee.org/license.html
 */

package org.glite.voms.ac;

import java.io.ByteArrayInputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERConstructedSet;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DEREncodableVector;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AttCertValidityPeriod;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.glite.voms.FQAN;

/**
 * Shadow implementation of AttributeCertificateInfo from
 * BouncyCastle
 *
 * @author Joni Hahkala, Olle Mulmo
 */
public class AttributeCertificateInfo implements DEREncodable {
    DERInteger version;
    Holder holder;
    AttCertIssuer issuer;
    AlgorithmIdentifier signature;
    DERInteger serialNumber;
    AttCertValidityPeriod attrCertValidityPeriod;
    ASN1Sequence attributes;
    DERBitString issuerUniqueID;
    X509Extensions extensions;
    boolean badVomsEncoding = false;
    FullAttributes fullAttributes = null;
    ACTargets acTargets = null;
    ACCerts   acCerts = null;
    private String myVo = null;
    private String myHostPort = null;
    private Vector myStringList = new Vector();
    private Vector myFQANs = new Vector();
    private String myHost = null;
    private int    myPort = -1;

    public static final String AC_TARGET_OID = "2.5.29.55";
    public static final String AC_CERTS_OID  = "1.3.6.1.4.1.8005.100.100.10";
    public static final String AC_FULL_ATTRIBUTES_OID = "1.3.6.1.4.1.8005.100.100.11";
    public static final String VOMS_EXT_OID  = "1.3.6.1.4.1.8005.100.100.5";
    public static final String VOMS_ATTR_OID = "1.3.6.1.4.1.8005.100.100.4";

    public AttributeCertificateInfo(ASN1Sequence seq) {
        DERObjectIdentifier AC_TARGET_OID_DER = new DERObjectIdentifier(AC_TARGET_OID);
        DERObjectIdentifier AC_CERTS_OID_DER = new DERObjectIdentifier(AC_CERTS_OID);
        DERObjectIdentifier AC_FULL_ATTRIBUTES_OID_DER = new DERObjectIdentifier(AC_FULL_ATTRIBUTES_OID);
        version = (DERInteger) seq.getObjectAt(0);
        holder = new Holder((ASN1Sequence) seq.getObjectAt(1));
        issuer = new AttCertIssuer(seq.getObjectAt(2));
        signature = new AlgorithmIdentifier((ASN1Sequence) seq.getObjectAt(3));
        serialNumber = (DERInteger) seq.getObjectAt(4);

        // VOMS has encoding problems of attCertValidity (uses PrivateKeyUsagePeriod syntax instead)
        ASN1Sequence s2 = (ASN1Sequence) seq.getObjectAt(5);
        ASN1Sequence s3 = s2;

        if (s2.getObjectAt(0) instanceof ASN1TaggedObject) {
            badVomsEncoding = true;

            DEREncodableVector v = new DEREncodableVector();

            for (int i = 0; i < 2; i++) {
                byte[] bb = ((DEROctetString) ((ASN1TaggedObject) s2.getObjectAt(i)).getObject()).getOctets();
                v.add(new DERGeneralizedTime(new String(bb)));
            }

            s3 = (ASN1Sequence) new DERSequence(v);
        }

        attrCertValidityPeriod = new AttCertValidityPeriod(s3);
        attributes = (ASN1Sequence) seq.getObjectAt(6);

        // getting FQANs
        //        System.out.println("Getting FQANs");
        if (attributes != null && attributes.size() != 0) {
            for (Enumeration e = attributes.getObjects(); e.hasMoreElements();) {
//                 DERObject o = (DERObject)e.nextElement();
//                 byte[] value = null;
//                 try {
//                     value = o.getEncoded();
//                 }
//                 catch(Exception ex) {}
//                 System.out.println("Class is: " + o.getClass());
//                 System.out.print("Value is: ");
//                 for (int i =0; i < value.length; i++)
//                     System.out.print(Integer.toHexString(value[i]) + " ");
//                 System.out.println();

                ASN1Sequence attribute = (ASN1Sequence) e.nextElement();

                if (VOMS_ATTR_OID.equals(((DERObjectIdentifier) attribute.getObjectAt(0)).getId())) {
                    DERConstructedSet set = (DERConstructedSet) attribute.getObjectAt(1);

                    for (Enumeration s = set.getObjects(); s.hasMoreElements();) {
                        IetfAttrSyntax attr = new IetfAttrSyntax((ASN1Sequence)s.nextElement());
                        String url = ((DERIA5String) GeneralName.getInstance(((ASN1Sequence) attr.getPolicyAuthority()
                                                                              .getDERObject()).getObjectAt(0))
                                      .getName()).getString();
                        int idx = url.indexOf("://");

                        if ((idx < 0) || (idx == (url.length() - 1))) {
                            throw new IllegalArgumentException("Bad encoding of VOMS policyAuthority : [" + url + "]");
                        }

                        myVo = url.substring(0, idx);
                        myHostPort = url.substring(idx + 3);

                        idx = myHostPort.lastIndexOf(":");

                        if ((idx < 0) || (idx == (myHostPort.length() - 1))) {
                            throw new IllegalArgumentException("Bad encoding of VOMS policyAuthority : [" + url + "]");
                        }

                        myHost = myHostPort.substring(0, idx);
                        myPort  = Integer.valueOf(myHostPort.substring(idx+1)).intValue();

                        if (attr.getValueType() != IetfAttrSyntax.VALUE_OCTETS) {
                            throw new IllegalArgumentException(
                                                               "VOMS attribute values are not encoded as octet strings, policyAuthority = " + url);
                        }

                        for (Iterator j = attr.getValues().iterator(); j.hasNext();) {
                            String fqan = new String(((ASN1OctetString) j.next()).getOctets());
                            FQAN f = new FQAN(fqan);

                            // maybe requiring that the attributes start with vo is too much?
                            if (!myStringList.contains(fqan) && (fqan.startsWith("/" + myVo + "/") || fqan.equals("/" + myVo))) {
                                myStringList.add(fqan);
                                myFQANs.add(f);
                            }
                        }
                    }
                }
            }
        }
        
        // check if the following two can be detected better!!! 
        // for example, is it possible to have only the extensions? how to detect this?
        if (seq.size() > 8) {
            issuerUniqueID = new DERBitString(seq.getObjectAt(7));
            extensions = new X509Extensions((ASN1Sequence) seq.getObjectAt(8));
        } else if (seq.size() > 7) {
            extensions = new X509Extensions((ASN1Sequence) seq.getObjectAt(7));
        }

        // start parsing of known extensions
        //        System.out.println("Getting AC_TARGET");
        if (extensions.getExtension(AC_TARGET_OID_DER) != null) {
            byte[] data = (extensions.getExtension(AC_TARGET_OID_DER).getValue().getOctets());
            DERObject dobj = null;
            try {
                dobj = new ASN1InputStream(new ByteArrayInputStream(data)).readObject();

                //            System.out.println("DOBJ Class: " + dobj.getClass());
                acTargets = new ACTargets(ASN1Sequence.getInstance(dobj));
            } catch (Exception e) {
                throw new IllegalArgumentException("DERO: " + e.getMessage());
            }
        }

        //        System.out.println("Getting AC_CERTS");
        if (extensions.getExtension(AC_CERTS_OID_DER) != null) {
            byte[] data = (extensions.getExtension(AC_CERTS_OID_DER).getValue().getOctets());
            DERObject dobj = null;
            try {
                dobj = new ASN1InputStream(new ByteArrayInputStream(data)).readObject();
                //             System.out.println("DOBJ Class: " + dobj.getClass());
                acCerts = new ACCerts(ASN1Sequence.getInstance(dobj));
            } catch (Exception e) {
                throw new IllegalArgumentException("DERO: " + e.getMessage());
            }
        }

        //        System.out.println("Getting FULL_ATTRIBUTES");
        if (extensions.getExtension(AC_FULL_ATTRIBUTES_OID_DER) != null) {
            byte[] data = (extensions.getExtension(AC_FULL_ATTRIBUTES_OID_DER).getValue().getOctets());
            DERObject dobj = null;
            try {
                dobj = new ASN1InputStream(new ByteArrayInputStream(data)).readObject();

                //             System.out.println("DOBJ Class: " + dobj.getClass());
                fullAttributes = new FullAttributes(ASN1Sequence.getInstance(dobj));
            } catch (Exception e) {
                throw new IllegalArgumentException("DERO: " + e.getMessage());
            }
        }
    }

    public static AttributeCertificateInfo getInstance(ASN1Sequence seq) {
        return new AttributeCertificateInfo(seq);
    }

    public DERInteger getAttCertVersion() {
        return version;
    }

    public Holder getHolder() {
        return holder;
    }

    public AttCertIssuer getIssuer() {
        return issuer;
    }

    public AlgorithmIdentifier getSignature() {
        return signature;
    }

    public DERInteger getSerialNumber() {
        return serialNumber;
    }

    public AttCertValidityPeriod getAttrCertValidityPeriod() {
        return attrCertValidityPeriod;
    }

    public ASN1Sequence getAttributes() {
        return attributes;
    }

    public String getVO() {
        return myVo;
    }

    public String getHostPort() {
        return myHostPort;
    }


    public String getHost() {
        return myHost;
    }

    public int getPort() {
        return myPort;
    }

    public DERBitString getIssuerUniqueID() {
        return issuerUniqueID;
    }

    public X509Extensions getExtensions() {
        return extensions;
    }

    public FullAttributes getFullAttributes() {
        return fullAttributes;
    }

    public ACCerts getCertList() {
        return acCerts;
    }

    public ACTargets getTargets() {
        return acTargets;
    }

    /**
     * @return List of String of the VOMS fully qualified
     * attributes names (FQANs):<br>
     * <code>vo[/group[/group2...]][/Role=[role]][/Capability=capability]</code>
     */
    public List getFullyQualifiedAttributes() {
        return myStringList;
    }

    /**
     * @return List of FQAN of the VOMS fully qualified
     * attributes names (FQANs)
     * @see org.glite.voms.FQAN
     */
    public List getListOfFQAN() {
        return myFQANs;
    }

    /**
     * Produce an object suitable for an ASN1OutputStream.
     *
     * <pre>
     *
     *
     *
     *     AttributeCertificateInfo ::= SEQUENCE {
     *          version              AttCertVersion -- version is v2,
     *          holder               Holder,
     *          issuer               AttCertIssuer,
     *          signature            AlgorithmIdentifier,
     *          serialNumber         CertificateSerialNumber,
     *          attrCertValidityPeriod   AttCertValidityPeriod,
     *          attributes           SEQUENCE OF Attribute,
     *          issuerUniqueID       UniqueIdentifier OPTIONAL,
     *          extensions           Extensions OPTIONAL
     *     }
     *
     *     AttCertVersion ::= INTEGER { v2(1) }
     *
     *
     *
     * </pre>
     */
    public DERObject getDERObject() {
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(version);
        v.add(holder);
        v.add(issuer);
        v.add(signature);
        v.add(serialNumber);

        if (!badVomsEncoding) {
            v.add(attrCertValidityPeriod);
        } else {
            DEREncodableVector v2 = new DEREncodableVector();
            v2.add(new DERTaggedObject(false, 0,
                    new DEROctetString((attrCertValidityPeriod.getNotBeforeTime().getTime().substring(0, 14) + "Z").getBytes())));
            v2.add(new DERTaggedObject(false, 1,
                    new DEROctetString((attrCertValidityPeriod.getNotAfterTime().getTime().substring(0, 14) + "Z").getBytes())));
            v.add(new DERSequence(v2));
        }

        v.add(attributes);

        if (issuerUniqueID != null) {
            v.add(issuerUniqueID);
        }

        if (extensions != null) {
            v.add(extensions);
        }

        return new DERSequence(v);
    }
}

/*
 * Copyright (c) Members of the EGEE Collaboration. 2004.
 * See http://eu-egee.org/partners/ for details on the copyright holders.
 * For license conditions see the license file or http://eu-egee.org/license.html
 */

package org.glite.voms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.Vector;

import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.glite.voms.ac.ACCerts;
import org.glite.voms.ac.ACTargets;
import org.glite.voms.ac.AttributeCertificate;
import org.glite.voms.ac.FullAttributes;

/**
 * Representation of the authorization information (VO, server address
 * and list of Fully Qualified Attribute Names, or FQANs) contained in
 * a VOMS attribute certificate.
 *
 * @author Olle Mulmo
 * @author Vincenzo Ciaschini
 */
public class VOMSAttribute {
    private static Logger logger = Logger.getLogger(VOMSAttribute.class);

    /**
     * The ASN.1 object identifier for VOMS attributes
     */
    private static final String VOMS_ATTR_OID = "1.3.6.1.4.1.8005.100.100.4";
    private AttributeCertificate myAC;
    private Vector myStringList = new Vector();
    private Vector myFQANs = new Vector();

    /**
     * Returns the signature of the AC.
     * @return the byte representation of the AC signature.
     */

    public byte[] getSignature() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");
        return myAC.getSignature();
    }

    /**
     * Returns the serial number of the AC.
     * @return the serial number of the AC.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public String getSerial() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");
        return myAC.getSerialNumber().getValue().toString();
    }

    private static Date convert(String t) throws ParseException {
        SimpleDateFormat dateF;

        // BouncyCastle change the output of getTime() and instead
        // introduced a new method getDate() method... better make
        // sure we stay compatible 

        if (t.indexOf("GMT") > 0) {
            dateF = new SimpleDateFormat("yyyyMMddHHmmssz");
        } else {
            dateF = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
            dateF.setTimeZone(new SimpleTimeZone(0, "Z"));
        }

        return dateF.parse(t);
    }

    /**
     * Returns the end date of the AC validity.
     * @return the end Date.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public Date getNotAfter() throws ParseException {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        try {
            return myAC.getNotAfter();
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Invalid validity encoding in Attribute Certificate: " + e.getMessage());
        }
    }

    /**
     * Return the start date of the AC validity.
     * @return the start Date.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public Date getNotBefore() throws ParseException {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");
        try {
            return myAC.getNotBefore();
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Invalid validity encoding in Attribute Certificate: " + e.getMessage());
        }
    }

    /**
     * Checks if the AC was valid at the provided timestamp.
     * @param date if <code>null</code>, current time is used
     * @return true if the AC was valid at the time in question.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded or the dates have been encoded incorrectly.
     */
    public boolean validAt(Date date) {
        if (date == null) {
            date = new Date();
        }

        try {
            return (getNotAfter()).after(date) && (getNotBefore()).before(date);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid validity encoding in Attribute Certificate");
        }
    }

    /**
     * Returns an OpenSSL-style representation of the AC issuer.
     * @return the AC issuer.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public String getIssuer() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        Principal principal = myAC.getIssuer();

        return principal.getName();
    }

    /**
     * Returns an OpenSSL-style representation of the AC issuer.
     * @return the AC issuer.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public String getIssuerX509() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        Principal principal = myAC.getIssuerX509();

        if (principal != null)
            return PKIUtils.getOpenSSLFormatPrincipal(principal);

        return null;
    }

    /**
     * Returns an String representation of the AC holder.
     * @return the AC holder.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public String getHolder() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");


        GeneralNames names = myAC.getHolder().getIssuer();

        Enumeration e = ((ASN1Sequence) names.toASN1Primitive()).getObjects();
        if (e.hasMoreElements()) {
            GeneralName gn = GeneralName.getInstance(e.nextElement());
            
            if (gn.getTagNo() == 4) {
                try {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    new DEROutputStream(b).writeObject(gn.getName());

                    X500Principal principal = new X500Principal(b.toByteArray());
                    return principal.getName();
                }
                catch(IOException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Returns an OpenSSL-style representation of the AC holder.
     * @return the AC holder.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public String getHolderX509() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getHolderX509();
    }

    /**
     * Checks if the Attribute is valid.  Only checks start and end of
     * validity.
     *
     * @return true if is valid, false otherwise.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public boolean isValid() {
        return validAt(new Date());
    }

    /**
     * Checks the given X509 certificate to see if it is the holder of the AC.
     * @param cert the X509 certificate to check.
     * @return true if the give certificate is the holder of the AC.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public boolean isHolder(X509Certificate cert) {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getHolder().isHolder(cert);
    }

    /**
     * Checks the given X509 certificate to see if it is the issuer of the AC.
     * @param cert the X509 certificate to check.
     * @return true if the give certificate is the issuer of the AC.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public boolean isIssuer(X509Certificate cert) {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getIssuer().equals(cert.getSubjectX500Principal());
    }

    /**
     * Parses the contents of an attribute certificate.<br>
     * <b>NOTE:</b> Cryptographic signatures, time stamps etc. will <b>not</b> be checked.
     *
     * @param ac the attribute certificate to parse for VOMS attributes
     */
    public VOMSAttribute(AttributeCertificate ac) {
        if (ac == null) {
            throw new IllegalArgumentException("VOMSAttribute: AttributeCertificate is NULL");
        }

        myAC = ac;

        List l = ac.getAttributes(VOMS_ATTR_OID);

        if ((l == null) || (l.size() == 0)) {
            return;
        }

//         try {
//             for (Iterator i = l.iterator(); i.hasNext();) {
//                 ASN1Sequence seq = (ASN1Sequence) i.next();
//                 IetfAttrSyntax attr = new IetfAttrSyntax(seq);

//                 // policyAuthority is on the format <vo>/<host>:<port>
//                 String url = ((DERIA5String) GeneralName.getInstance(((ASN1Sequence) attr.getPolicyAuthority()
//                                                                                          .getDERObject()).getObjectAt(0))
//                                                         .getName()).getString();
//                 int idx = url.indexOf("://");

//                 if ((idx < 0) || (idx == (url.length() - 1))) {
//                     throw new IllegalArgumentException("Bad encoding of VOMS policyAuthority : [" + url + "]");
//                 }

//                 myVo = url.substring(0, idx);
//                 myHostPort = url.substring(idx + 3);

//                 if (attr.getValueType() != IetfAttrSyntax.VALUE_OCTETS) {
//                     throw new IllegalArgumentException(
//                         "VOMS attribute values are not encoded as octet strings, policyAuthority = " + url);
//                 }

//                 for (Iterator j = attr.getValues().iterator(); j.hasNext();) {
//                     String fqan = new String(((ASN1OctetString) j.next()).getOctets());
//                     FQAN f = new FQAN(fqan);

//                     // maybe requiring that the attributes start with vo is too much?
//                     if (!myStringList.contains(fqan) && (fqan.startsWith("/" + myVo + "/") || fqan.equals("/" + myVo))) {
//                         myStringList.add(fqan);
//                         myFQANs.add(f);
//                     }
//                 }
//             }
//         } catch (IllegalArgumentException ie) {
//             throw ie;
//         } catch (Exception e) {
//             throw new IllegalArgumentException("Badly encoded VOMS extension in AC issued by " +
//                 ac.getIssuer().getName());
//         }
    }

    /**
     * @deprecated Direct access to the Attribute Certificate is going to
     *             be removed. Use the getXXX methods in this same classe
     *             instead.
     *
     * @return The AttributeCertificate containing the VOMS information
     */
    public AttributeCertificate getAC() {
        return privateGetAC();
    }

    AttributeCertificate privateGetAC() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC;
    }

    /**
     * @return List of String of the VOMS fully qualified
     * attributes names (FQANs):<br>
     * <code>vo[/group[/group2...]][/Role=[role]][/Capability=capability]</code>
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public List getFullyQualifiedAttributes() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getFullyQualifiedAttributes();
    }

    /**
     * @return List of FQAN of the VOMS fully qualified
     * attributes names (FQANs)
     * @see FQAN
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public List getListOfFQAN() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getListOfFQAN();
    }

    /**
     * Returns the address of the issuing VOMS server, on the form <code>&lt;host&gt;:&lt;port&gt;</code>
     * @return String
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public String getHostPort() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getHostPort();
    }

    /**
     * Returns the hostName of the issuing VOMS server.
     *
     * @return hostName.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public String getHost() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getHost();
    }

    /**
     * Returns the port on which the issuing VOMS server is listening
     *
     * @return the port, or -1 if the informations could not be found.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public int getPort() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getPort();
    }

    /**
     * Returns the VO name
     * @return the VO name
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public String getVO() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getVO();
    }

    /**
     * Gets a (brief) string representation of this attribute.
     *
     * @return the Representation.
     */
    public String toString() {
        return "VO      :" + getVO() + "\n" + "HostPort:" + 
            getHostPort() + "\n" + "FQANs   :" + getListOfFQAN();
    }

    /**
     * Gets a copy of the Generic Attributes extension.
     *
     * @return the attributes, or null if they are not present.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public FullAttributes getFullAttributes() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getFullAttributes();
    }

    /**
     * Gets the certificates that signed the AC, if the ACCerts extension
     * is present.
     *
     * @return the ACCerts extension, or null if it is not present.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public ACCerts getCertList() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getCertList();
    }

    /**
     * Gets the targets of this AC.
     *
     * @return the ACTargets extension if present, or null otherwise.
     *
     * @throws IllegalArgumentException if no Attribute Certificate has been
     * loaded.
     */
    public ACTargets getTargets() {
        if (myAC == null)
            throw new IllegalArgumentException("No Attribute Certificate loaded.");

        return myAC.getTargets();
    }

}

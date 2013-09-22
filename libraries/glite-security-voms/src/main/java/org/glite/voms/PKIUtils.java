/*********************************************************************
 *
 * Authors: Vincenzo Ciaschini - Vincenzo.Ciaschini@cnaf.infn.it
 *
 * Copyright (c) 2006 INFN-CNAF on behalf of the 
 * EGEE project.
 * For license conditions see LICENSE
 *
 * Parts of this code may be based upon or even include verbatim pieces,
 * originally written by other people, in which case the original header
 * follows.
 *
 *********************************************************************/


package org.glite.voms;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.Security;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class PKIUtils {
    private static final Pattern emailPattern = Pattern.compile("/emailaddress", Pattern.CASE_INSENSITIVE);
    private static final Pattern uidPattern   = Pattern.compile("/USERID");
    private static final Pattern basename_pattern = Pattern.compile("(.*)\\.[^\\.]*");

    private static final String SUBJECT_KEY_IDENTIFIER   = "2.5.29.14";
    private static final String AUTHORITY_KEY_IDENTIFIER = "2.5.29.35";
    private static final String PROXYCERTINFO = "1.3.6.1.5.5.7.1.14";
    private static final String PROXYCERTINFO_OLD = "1.3.6.1.4.1.3536.1.222";
    private static final String BASIC_CONSTRAINTS_IDENTIFIER="2.5.29.19";
    private static final CertificateFactory factory;

    private static final int CERT = 1;
    private static final int CRL  = 2;

    private static final int keyCertSign = 5;
    private static final int digitalSignature = 0;

    private static final Logger logger = Logger.getLogger(PKIUtils.class);

     static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            factory = CertificateFactory.getInstance("X.509", "BC");
        }
         catch (NoSuchProviderException e) {
             throw new ExceptionInInitializerError("Cannot find BouncyCastle provider: " + e.getMessage());
         }
         catch (CertificateException e) {
             throw new ExceptionInInitializerError("X.509 Certificates unsupported. " + e.getMessage());
         }
     }

    /**
     * Gets the MD5 hash value of the subject of the given certificate.
     *
     * @param x509 The certificate from which to get the subject.
     *
     * @return the hash value.
     *
     * @throws IllegalArgumentException if x509 is null.
     * @throws InvalidStateException if the MD5 algorithm is not supported.
     */
    public static String getHash(X509Certificate x509) {
        if (x509 != null) {
            logger.debug("Getting hash of: " + x509.getSubjectDN().getName());

            return getHash(x509.getSubjectX500Principal());
        }
        throw new IllegalArgumentException("Null certificate passed to getHash()");
    }

    /**
     * Gets the MD5 hash value of the issuer of the given CRL.
     *
     * @param crl The CRL from which to get the issuer.
     *
     * @return the hash value.
     *
     * @throws IllegalArgumentException if crl is null.
     * @throws InvalidStateException if the MD5 algorithm is not supported.
     */
    public static String getHash(X509CRL crl) {
        if (crl != null) {
            return getHash(crl.getIssuerX500Principal());
        }
        throw new IllegalArgumentException("Null CRL passed to getHash()");
    }

    /**
     * Gets the MD5 hash value of the given principal.
     *
     * @param principal the principal.
     *
     * @return the hash value.
     *
     * @throws IllegalArgumentException if crl is null.
     * @throws InvalidStateException if the MD5 algorithm is not supported.
     */
    public static String getHash(X509Principal principal) {
        if (principal != null) {
            byte[] array = principal.getEncoded();
            return getHash(array);
        }
        throw new IllegalArgumentException("Null name passed to getHash()");
    }

    /**
     * Gets the MD5 hash value of the given principal.
     *
     * @param principal the principal.
     *
     * @return the hash value.
     *
     * @throws IllegalArgumentException if crl is null.
     * @throws InvalidStateException if the MD5 algorithm is not supported.
     */
    public static String getHash(X500Principal principal) {
        logger.debug("Examining: " + principal.getName());
        if (principal != null) {
            byte[] array = principal.getEncoded();
            return getHash(array);
        }
        throw new IllegalArgumentException("Null name passed to getHash()");
    }

    /**
     * Gets the MD5 hash value of the given byte array.
     *
     * @param name the data from which to compute the hash.
     *
     * @return the hash value.
     *
     * @throws IllegalArgumentException if crl is null.
     * @throws InvalidStateException if the MD5 algorithm is not supported.
     */
    public static String getHash(byte[] name) {
        if (name != null) {
            MessageDigest md = null;
            try {
                md = MessageDigest.getInstance("MD5");
            }
            catch(NoSuchAlgorithmException e) {
                logger.fatal("NO MD5! " + e.getMessage(), e);

                throw new IllegalStateException("NO MD5! " + e.getMessage());
            }

            md.update(name);

            byte[] digest = md.digest();
            ByteBuffer bb = ByteBuffer.wrap(digest).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            bb.rewind();

            return Integer.toHexString(bb.getInt());
        }
        throw new IllegalArgumentException("Null certificate passed to getHash()");
    }

    /**
     * Gets an OpenSSL-style representation of a principal.
     *
     * @param principal the principal
     *
     * @return a String representing the principal.
     */
    public static String getOpenSSLFormatPrincipal(Principal principal) {
        X509Name name = new X509Name(principal.getName());

        Vector oids   = name.getOIDs();
        Vector values = name.getValues();

        ListIterator oids_iter   = oids.listIterator();
        ListIterator values_iter = values.listIterator();
        String  result = new String();

        while (oids_iter.hasNext()) {
            DERObjectIdentifier oid = (DERObjectIdentifier)oids_iter.next();
            String value = (String)values_iter.next();
            if (oid.equals(X509Name.C))
                result += "/C=" + value;
            else if (oid.equals(X509Name.CN))
                result += "/CN=" + value;
            else if (oid.equals(X509Name.DC))
                result += "/DC=" + value;
            else if (oid.equals(X509Name.E))
                result += "/E=" + value;
            else if (oid.equals(X509Name.EmailAddress))
                result += "/Email=" + value;
            else if (oid.equals(X509Name.L))
                result += "/L=" + value;
            else if (oid.equals(X509Name.O))
                result += "/O=" + value;
            else if (oid.equals(X509Name.OU))
                result += "/OU=" + value;
            else if (oid.equals(X509Name.ST))
                result += "/ST=" + value;
            else if (oid.equals(X509Name.UID))
                result += "/UID=" + value;
            else
                result += "/" + oid.toString() + "=" + value;
        }

        logger.debug("SSLFormat: " + result);
        return result;
    }

    /**
     * Compares two DNs for equality, taking into account different
     * representations for the Email and UserID tags.
     *
     * @param dn1 the first dn to compare.
     * @param dn2 the second dn to compare
     *
     * @return true if dn1 and dn2 are equal, false otherwise.
     */
    public static boolean DNCompare(String dn1, String dn2) {
        String newdn1 = emailPattern.matcher(dn1).replaceAll("/Email");
        newdn1 = uidPattern.matcher(newdn1).replaceAll("/UID");

        String newdn2 = emailPattern.matcher(dn2).replaceAll("/Email");
        newdn2 = uidPattern.matcher(newdn2).replaceAll("/UID");

        if (newdn1.equals(newdn2))
            return true;
        return false;
    }

    /**
     * Gets the basename of a file.
     *
     * @param f File object representing a file.
     *
     * @return a string representing the file name, minus the path.
     */
    static public String getBaseName(File f) {
        Matcher m = basename_pattern.matcher(f.getName());
        if (m.matches())
            return m.group(1);
        else
            return f.getName();
    }

    /**
     * Checks if the give certificate is self-issued.
     *
     * @param cert The certificate to check.
     *
     * @return true if the certificate is self-issued, false otherwise.
     */
    static public boolean selfIssued(X509Certificate cert) {
        if (logger.isDebugEnabled())
            logger.debug("Checking self issued for: " + cert.getSubjectDN().getName());

        boolean ret = checkIssued(cert, cert);

        logger.debug("SelfIssued Result " + ret);
        return ret;
    }

    static private BigInteger getAuthorityCertificateSerialNumber(AuthorityKeyIdentifier akid) {
        ASN1Primitive obj = akid.toASN1Primitive();
        ASN1Sequence seq = ASN1Sequence.getInstance(obj);

        for (int i = 0; i < seq.size(); i++) {
            ASN1Primitive o = (ASN1Primitive) seq.getObjectAt(i);
            if ((o instanceof ASN1TaggedObject) &&
                (((ASN1TaggedObject)o).getTagNo() == 2)) {
                ASN1Primitive realObject = ((ASN1TaggedObject)o).getObject();
                if (realObject instanceof DERInteger) {
                    return ((DERInteger)realObject).getValue();
                }
            }
        }
        return null;
    }

    static private GeneralNames getAuthorityCertIssuer(AuthorityKeyIdentifier akid) {
        ASN1Primitive obj = akid.toASN1Primitive();
        ASN1Sequence seq = ASN1Sequence.getInstance(obj);

        for (int i = 0; i < seq.size(); i++) {
            ASN1Primitive o = (ASN1Primitive) seq.getObjectAt(i);
            if ((o instanceof ASN1TaggedObject) &&
                (((ASN1TaggedObject)o).getTagNo() == 1)) {
                return GeneralNames.getInstance(((DERTaggedObject)o), false);
                //                DERObject realObject = ((ASN1TaggedObject)o).getObject();
                //                if (realObject instanceof GeneralNames) {
                //                    return ((GeneralNames)realObject);
                //                }
            }
        }
        return null;
    }

    static private GeneralName[] getNames(GeneralNames gns) {
        ASN1Primitive obj = gns.toASN1Primitive();
        Vector v = new Vector();

        ASN1Sequence seq = (ASN1Sequence)obj;

        int size = seq.size();
        //        System.out.println("Size = " + size);
        for (int i = 0; i < size; i++) {
            //            System.out.println("Adding element:");
            //            System.out.println("Class is: " + ((DERTaggedObject)seq.getObjectAt(i)).getObject().getClass());
            //            ASN1Sequence dseq = (ASN1Sequence)((DERTaggedObject)seq.getObjectAt(i)).getObject();
//             int size2 = dseq.size();
//             for (int j = 0; j < size; j++) {
//                 System.out.println("2Adding element:");
//                 System.out.println("2Class is: " + dseq.getObjectAt(j));
//                 System.out.println("Class is: " + ((DERTaggedObject)dseq.getObjectAt(j)).getObject().getClass());
//                 //                ASN1Sequence dseq = (ASN1Sequence)((DERTaggedObject)seq.getObjectAt(i)).getObject();
//                 //                int size2 = dseq.size();

            v.add(GeneralName.getInstance(seq.getObjectAt(i)));
//             }
        }
        return (GeneralName[])v.toArray(new GeneralName[0]);
    }

    /**
     * Checks if a certificate issued another certificate, according to RFC 3280.
     *
     * @param issuer The candidate issuer certificate.
     * @param issued The candidate issued certificate.
     *
     * @return true if <em>issuer</em> issued <em>issued</em>, false othersie.
     */
    static public boolean checkIssued(X509Certificate issuer, X509Certificate issued) {
        X500Principal issuerSubject = issuer.getSubjectX500Principal();
        X500Principal issuedIssuer  = issued.getIssuerX500Principal();

        if (logger.isDebugEnabled()) {
            logger.debug("Is: " + issued.getSubjectDN().getName() +
                         " issued by " + issuer.getSubjectDN().getName() + "?");

            logger.debug("Is: " + issuedIssuer.getName() +
                         " issued by " + issuerSubject.getName() + "?");
            logger.debug("Is: " + issued.getSubjectDN().getName() +
                         " issued by " + issuer.getSubjectDN().getName());
            logger.debug("[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[");
        }
        //        try {
            boolean b = issuerSubject.equals(issuedIssuer);
            //        }
        //        catch(Exception e) {
            //            System.out.println("Caught: " + e.getMessage() + " " + e.getClass());
        //        }

        if (issuerSubject.equals(issuedIssuer)) {
            logger.debug("================================");
            logger.debug("issuersSubject = issuedIssuer");

            AuthorityKeyIdentifier akid = PKIUtils.getAKID(issued);
            if (logger.isDebugEnabled())
                logger.debug("akid = " + akid);

            if (akid != null) {
                logger.debug("Authority Key Identifier extension found in issued certificate.");

                logger.debug("Entered.");
                SubjectKeyIdentifier skid = PKIUtils.getSKID(issuer);

                if (logger.isDebugEnabled())
                    logger.debug("sid = " + skid);

                if (skid != null) {
                    logger.debug("subject Key Identifier extensions found in issuer certificate.");
                    logger.debug("comparing skid to akid");

                    byte[] skidValue = skid.getKeyIdentifier();
                    if (logger.isDebugEnabled()) {
                        logger.debug("skid");

                        String str = "";
                        for (int i = 0; i < skidValue.length; i++)
                            str += Integer.toHexString(skidValue[i]) + " ";
                        logger.debug(str);
                    }

                    byte[] akidValue = akid.getKeyIdentifier();
                    if (logger.isDebugEnabled()) {
                        logger.debug("akid");

                        String str = "";
                        for (int i = 0; i < akidValue.length; i++)
                            str += Integer.toHexString(akidValue[i]) + " ";
                        logger.debug(str);
                    }

                    logger.debug("skid/akid checking.");
                    if (!Arrays.equals(skidValue, akidValue))
                        return false;

                    logger.debug("skid/akid check passed.");
                }

                if (false) {
                    // The following should be skipped if the previous check passed.
                    // And code cannot reach here unless the previous step passed.
                BigInteger sn = getAuthorityCertificateSerialNumber(akid);
//
//                if (sn == null) {
//                    logger.error("Serial number missing from Authority Key Identifier");
//                    return false;
//                }
//
//                if (!sn.equals(issuer.getSerialNumber())) {
//                    logger.error("Serial number in Authority Key Identifier and in issuer certificate do not match");
//                    logger.error("From akid              : " + sn.toString());
//                    logger.error("From issuer certificate: " + issuer.getSerialNumber());
//                    return false;
//                }

                if (sn != null && !sn.equals(issuer.getSerialNumber())) {
                    logger.error("Serial number in Authority Key Identifier and in issuer certificate do not match");
                    logger.error("From akid              : " + sn.toString());
                    logger.error("From issuer certificate: " + issuer.getSerialNumber());
                    return false;
                }

                GeneralNames gns = getAuthorityCertIssuer(akid);

                if (gns != null) {
                    GeneralName names[] = getNames(gns);

                    //                System.out.println("GOT CERTISSUER");

                    int i = 0;
                    //                System.out.println("SIZE = " + names.length);
                    while (i < names.length) {
                        //                    System.out.println("NAME = " + names[i].getName());
                        //                    System.out.println("TAG IS: " + names[i].getTagNo());
                        if (names[i].getTagNo() == 4) {
                            ASN1Primitive dobj = names[i].getName().toASN1Primitive();
                            ByteArrayOutputStream baos = null;
                            ASN1OutputStream aos = null;
                            //                        System.out.println("Inside tag 4");
                            try {
                                baos = new ByteArrayOutputStream();
                                aos = new ASN1OutputStream(baos);
                                aos.writeObject(dobj);
                                aos.flush();
                            }
                            catch (IOException e) {
                                logger.error("Error in encoding of Authority Key Identifier." + e.getMessage());
                                return false;
                            }
                            X500Principal principal = new X500Principal(baos.toByteArray());
                            //                        System.out.println("PRINCIPAL: " + principal);
                            X500Principal issuerIssuer  = issuer.getIssuerX500Principal();

                            if (issuerIssuer.equals(principal)) {
                                logger.debug("PASSED");
                                break;
                            }
                            else {
                                logger.error("Issuer Issuer not found among Authority Key Identifier's Certifiacte Issuers.");
                                return false;
                            }
                        }
                    }
                }
                }
            }
            logger.debug("]]]]]]]]]]]]]]]]]]]]]]]]");

            boolean keyUsage[] = issuer.getKeyUsage();
            if (!PKIUtils.isCA(issuer)) {
                if ((keyUsage != null && !keyUsage[digitalSignature]) ||
                    !PKIUtils.isProxy(issued))
                    return false;
            }
            
            logger.debug("CHECK ISSUED PASSED");
            return true;

        }
        logger.debug("Check Issued failed.");
        return false;
    }

    /**
     * Checks if the passed certificate is a CA certificate.
     *
     * @param cert  the candidate CA certificate.
     *
     * @return true if <em>cert</em> is a CA certificate.
     */
    static public boolean isCA(X509Certificate cert) {
        if (cert == null)
            return false;

        if (logger.isDebugEnabled()) {
            logger.debug("Examining " + cert.getSubjectDN().getName());
            logger.debug ("Hash: " + PKIUtils.getHash(cert));
        }

        boolean[] keyUsage = cert.getKeyUsage();
        byte[] keybytes = cert.getExtensionValue("2.5.29.15");

        if (logger.isDebugEnabled()) {
            if (keybytes != null) {
                String str = "Real value : ";
                for (int j =0; j < keybytes.length; j++)
                    str += Integer.toHexString(keybytes[j]) + " ";
                logger.debug(str);
            }

            ASN1Primitive dobj = null;
            try {
                dobj = new ASN1InputStream(new ByteArrayInputStream(keybytes)).readObject();
                logger.debug("Class = " + dobj.getClass());
                dobj = new ASN1InputStream(new ByteArrayInputStream(((DEROctetString)dobj).getOctets())).readObject();
                logger.debug("Class = " + dobj.getClass());
                DERBitString bitstr = (DERBitString)dobj;
                //                logger.debug("as int    : " + bitstr.intValue());
                //                logger.debug("as binary : " + Integer.toBinaryString(bitstr.intValue()));
                logger.debug("pad bits  : " + bitstr.getPadBits());
            }
            catch(Exception e) {}
        }

        if (logger.isDebugEnabled()) {
            if (keyUsage != null)
                for (int i = 0; i < keyUsage.length ; i++)
                    logger.debug("Keyusage[" +i + "] = " + keyUsage[i]);
        }

        if (keyUsage != null && !keyUsage[keyCertSign]) {
            logger.error("keyUsage extension present, but CertSign bit not active.");
            return false;
        }

        int isCA = cert.getBasicConstraints();
        if (isCA == -1) {
            logger.debug("Is CA");
            return false;
        }
         
        logger.debug("Is not CA");
        return true;
    }

    /**
     * Checks if the passed certificate is a proxy certificate.  Recognizes
     * GT2, GT3 and GT4 proxies.
     *
     * @param cert  the candidate proxy certificate.
     *
     * @return true if <em>cert</em> is a proxy certificate.
     */
    static public boolean isProxy(X509Certificate cert) {
        if (cert == null)
            return false;

        if (logger.isDebugEnabled())
            logger.debug("Check for proxyness: " + cert.getSubjectDN().getName());

        byte[] proxy = cert.getExtensionValue(PROXYCERTINFO);
        byte[] proxy_old = cert.getExtensionValue(PROXYCERTINFO_OLD);
        if (proxy != null || proxy_old != null) {
            logger.debug("Proxyness confirmed.");
            return true;
        }

        String subject = cert.getSubjectX500Principal().getName();
        String issuer = cert.getIssuerX500Principal().getName();

        logger.debug("ENDNAME CHECK?");

        if (subject.endsWith(issuer)) {
            logger.debug("ENDNAME CHECK OK");

            String s = subject.replaceFirst(issuer, "");

            logger.debug("TO CHECK: " + s);

            if (s.equals("CN=proxy,") || s.equals("CN=limited proxy,")) {
                logger.debug("Proxyness confirmed.");
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the AuthorityKeyIdentifier extension form the passed certificate.
     *
     * @param cert The certificate from which to get the extension.
     *
     * @return the extension if present, or null if not present.
     */
    static public AuthorityKeyIdentifier getAKID(X509Certificate cert) {
        if (cert != null) {
            
            byte[] akid = cert.getExtensionValue(AUTHORITY_KEY_IDENTIFIER);
            int i = 0;
            //            if (akid != null)
            //                for (i = 0; i < akid.length; i++)
            //                    System.out.print(akid[i] + " ");
            //            System.out.println("");
            if (akid != null) {
                ASN1OctetString string = new DEROctetString(akid);
                org.bouncycastle.asn1.x509.X509Extension ex = new org.bouncycastle.asn1.x509.X509Extension(false, string);
//                 byte[] list = ex.getValue().getOctets();
//                 for (i = 0; i < list.length; i++)
//                     System.out.print(list[i] + " ");

//                System.out.println("EXAMINED");
                byte[] llist2 = string.getOctets();
                //                for (i = 0; i < llist2.length; i++)
                //                    System.out.print(llist2[i] + " ");
                //                System.out.println("");
                        
                ASN1Primitive dobj = null;
                try {
                    dobj = new ASN1InputStream(new ByteArrayInputStream(llist2)).readObject();
                    dobj = new ASN1InputStream(new ByteArrayInputStream(((DEROctetString)dobj).getOctets())).readObject();
                }
                catch (ClassCastException e) {
                    throw new IllegalArgumentException("Erroneous encoding in Authority Key Identifier " + e.getMessage());
                }
                catch (Exception e) {
                    throw new IllegalArgumentException("While extracting Authority Key Identifier " + e.getMessage());
                }
                
                //                System.out.println("dobj is: " + dobj.getClass());
                //                System.out.println("dobj is also: " + dobj);
//                 byte[] list2 = ((DEROctetString)dobj).getOctets();
//                 for (i = 0; i < list2.length; i++)
//                     System.out.print(list2[i] + " ");
//                 System.out.println("");

                return AuthorityKeyIdentifier.getInstance(ASN1Sequence.getInstance(dobj));
            }
        }
        return null;
    }

    /**
     * Gets the SubjectKeyIdentifier extension form the passed certificate.
     *
     * @param cert The certificate from which to get the extension.
     *
     * @return the extension if present, or null if not present.
     */
    static public SubjectKeyIdentifier getSKID(X509Certificate cert) {
        if (cert != null) {
            byte[] akid = cert.getExtensionValue(SUBJECT_KEY_IDENTIFIER);
            if (akid != null) {
                ASN1Primitive dobj = null;
                try {
                    dobj = new ASN1InputStream(new ByteArrayInputStream(akid)).readObject();
                    dobj = new ASN1InputStream(new ByteArrayInputStream(((DEROctetString)dobj).getOctets())).readObject();
                }
                catch (Exception e) {
                    throw new IllegalArgumentException("While extracting Subject Key Identifier " + e.getMessage());
                }
                //                System.out.println("SKID CLASS IS: " + dobj.getClass());

                return SubjectKeyIdentifier.getInstance(dobj);
                //                return SubjectKeyIdentifier(dobj.getDEREncoded());
            }
        }
        return null;
    }
    /**
     * Gets the BasicConstraints extension form the passed certificate.
     *
     * @param cert The certificate from which to get the extension.
     *
     * @return the extension if present, or null if not present.
     */
    static public BasicConstraints getBasicConstraints(X509Certificate cert) {
        if (cert != null) {
            byte[] akid = cert.getExtensionValue(BASIC_CONSTRAINTS_IDENTIFIER);
            if (akid != null) {
                ASN1Primitive dobj = null;
                try {
                    dobj = new ASN1InputStream(new ByteArrayInputStream(akid)).readObject();
                }
                catch (Exception e) {
                    throw new IllegalArgumentException("While extracting Subject Key Identifier " + e.getMessage());
                }

                return BasicConstraints.getInstance(ASN1Sequence.getInstance(dobj));
            }
        }
        return null;
    }


    /**
     * Loads a set of credentials from a file.
     *
     * @param filename the name of the file from which to load the certificates.
     *
     * @return an array containing the certificates that were present in the file.
     *
     * @throws CertificateException if there were problems parsing the certificates.
     * @throws IllegalArgumentException if the file cannot be found.
     */
    static public X509Certificate[] loadCertificates(String filename) throws CertificateException {
        return loadCertificates(new File(filename));
    }

    /**
     * Loads a set of credentials from a file.
     *
     * @param file the File object from which to load the certificates.
     *
     * @return an array containing the certificates that were present in the file.
     *
     * @throws CertificateException if there were problems parsing the certificates.
     * @throws IllegalArgumentException if the file cannot be found.
     *
     * @see java.io.File
     */
    static public X509Certificate[] loadCertificates(File file) throws CertificateException {
        BufferedInputStream bis = null;

        try {
            bis = new BufferedInputStream(new FileInputStream(file));
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Cannot find file " + file.getName());
        }


        X509Certificate certificates[] = null;
        try {
            certificates = loadCertificates(bis);
        }
        catch (IOException e) {
            certificates = null;
        }
        finally {
            try {
                bis.close();
            }
            catch(IOException e) {
                logger.error("While closing: " + file.getName() + " " + e.getMessage());
            }
        }

        return certificates;
    }

    /**
     * Loads a set of credentials from a BufferedInputStream.
     *
     * @param bis the BufferedInputStream from which to load the certificates.
     *
     * @return an array containing the certificates that were present in the file.
     *
     * @throws CertificateException if there were problems parsing the certificates.
     * @throws IllegalArgumentException if the file cannot be found.
     */
    static private X509Certificate[] loadCertificates(BufferedInputStream bis) throws CertificateException, IOException {
        List certificates = new Vector();

        int type;

        while ((type = skipToCertBeginning(bis)) != -1) {
            if (type == CERT) {
//                 Certificate cert = factory.generateCertificate(bis);
//                 byte[] data = cert.getEncoded();
//                 ASN1Sequence seq = (ASN1Sequence) new DERInputStream(new ByteArrayInputStream(data)).readObject();
//                 data = new X509CertificateObject(X509CertificateStructure.getInstance(seq)).getEncoded();
//                 certificates.add((X509Certificate)factory.generateCertificate(new ByteArrayInputStream(data)));
                certificates.add(factory.generateCertificate(bis));
            }
        }

        // Object[] arr = certificates.toArray();
        //        System.out.println("CLASS: " + arr[0].getClass());
        //        System.out.println("CLASS: " + arr.getClass());

        X509Certificate[] arr = new X509Certificate[0];

        //        System.out.println("SIZE = " + certificates.size());
        //        System.out.println("SIZE = " + certificates.get(0));
        return (X509Certificate[])certificates.toArray(arr);
    }



    /**
     * Loads a CRL from a file.
     *
     * @param filename the name of the file from which to load the CRL.
     *
     * @return an array containing the certificates that were present in the file.
     *
     * @throws CRLException if there were problems parsing the CRL.
     * @throws IllegalArgumentException if the file cannot be found.
     */
    static public X509CRL loadCRL(String filename) throws CRLException {
        return loadCRL(new File(filename));
    }

    /**
     * Loads a CRL from a file.
     *
     * @param file the File object from which to load the CRL.
     *
     * @return an array containing the certificates that were present in the file.
     *
     * @throws CRLException if there were problems parsing the CRL.
     * @throws IllegalArgumentException if the file cannot be found.
     */
    static public X509CRL loadCRL(File file) throws CRLException {
        BufferedInputStream bis = null;

        try {
            bis = new BufferedInputStream(new FileInputStream(file));
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Cannot find file " + file.getName());
        }

        X509CRL crl = null;

        try {
            crl = loadCRL(bis);
        }
        catch(IOException e) {
            throw new IllegalArgumentException("Cannot load CRL from file: " + file.getName() + " cause: " + e.getMessage());
        }
        finally {
            try {
                if (bis != null)
                    bis.close();
            }
            catch(IOException e) {
                logger.error("While closing: " + file.getName() + " " + e.getMessage());
            }
            
        }


        return crl;
    }

    /**
     * Loads a CRL from a BufferedInputStream.
     *
     * @param bis the BufferedInputStream from which to load the CRL.
     *
     * @return an array containing the certificates that were present in the file.
     *
     * @throws CRLException if there were problems parsing the CRL.
     * @throws IllegalArgumentException if the file cannot be found.
     */
    static private X509CRL loadCRL(BufferedInputStream bis) throws CRLException, IOException {
        int type;

        X509CRL crl = null;

        if (skipToCertBeginning(bis) == CRL) {
            crl = (X509CRL)factory.generateCRL(bis);
        }

        return crl;
    }

    /**
     * Reads either a certificate or a CRL from a file.
     *
     * @param f the file from which to read;
     *
     * @return the Object loaded.
     *
     * @throws IOException if there have been problems reading the file.
     * @throws CertificateException if there have been problems parsing the certificate.
     * @throws CRLException if there have been problems parsing the CRL.
     */
    static public Object readObject(File f) throws IOException, CertificateException, CRLException {
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));

        int type = skipToCertBeginning(bis);
        try {
            switch (type) {
            case CRL:
                Object o = loadCRL(bis);
                bis.close();
                bis = null;
                return o;
            case CERT:
                //            System.out.println("++++++++++++++++++++++++++++");
                Vector result = new Vector(Arrays.asList(loadCertificates(bis)));
                //            System.out.println("++++++++++++++++++++++++++++");
                //            System.out.println("COPY: = " + result.get(0));
                bis.close();
                bis = null;
                return result;
            default:
                return null;
            }
        }
        finally {
            if (bis != null)
                bis.close();
        }

        //        return null;
    }


    /**
     * Prepares a BufferedInputStream to read either a certificate or a CRL
     * from it. Skips everything in front of "-----BEGIN" in the stream.
     *
     * @param stream The stream to read and skip.
     *
     * @return CERT if a certificate is the next object to be read from the
     * stream, CRL if the next object is a CRL, -1 if the next object is of
     * type unknown.
     *
     * @throws IOException Thrown if there is a problem skipping.
     *
     * Note: this a modified version of code originally written by Joni Hakhala
     */
    public static int skipToCertBeginning(BufferedInputStream stream)
        throws IOException {
        int BUF_LEN = 1000;
        byte[] b = new byte[BUF_LEN]; // the byte buffer
        stream.mark(BUF_LEN + 2); // mark the beginning

        while (stream.available() > 0) { // check that there are still something to read

            int num = stream.read(b); // read bytes from the file to the byte buffer
            String buffer = new String(b, 0, num); // generate a string from the byte buffer
            int index  = buffer.indexOf("----BEGIN CERTIFICATE"); // check if the certificate beginning is in the chars read this time
            int index2 = buffer.indexOf("----BEGIN X509 CRL");

            //            System.out.println("BUFFER: " + buffer);
            //            System.out.println("INDEX :  " + index);
            //            System.out.println("INDEX2:  " + index2);
            if (index == -1 && index2 == -1) { // not found
                //                System.out.println("skipping:" + buffer);
                stream.reset(); // rewind the file to the beginning of the last read
                stream.skip(BUF_LEN - 100); // skip only part of the way as the "----BEGIN" can be in the transition of two 1000 char block
                stream.mark(BUF_LEN + 2); // mark the new position
            } else { // found

                if (index != -1) {
                    while ((buffer.charAt(index - 1) == '-') && (index > 0)) { // search the beginnig of the ----BEGIN tag
                        index--;

                        if (index == 0) { // prevent charAt test when reaching the beginning of buffer

                            break;
                        }
                    }

                    //                System.out.println("Last skip:" + buffer.substring(0, index));
                    stream.reset(); // rewind to the beginning of the last read
                    stream.skip(index); // skip to the beginning of the tag
                    stream.mark(10000); // mark the position

                    return CERT;
                }
                else {
                    while ((buffer.charAt(index2 - 1) == '-') && (index2 > 0)) { // search the beginnig of the ----BEGIN tag
                        index2--;

                        if (index2 == 0) { // prevent charAt test when reaching the beginning of buffer
                            
                            break;
                        }
                    }

                    //                System.out.println("Last skip:" + buffer.substring(0, index));
                    stream.reset(); // rewind to the beginning of the last read
                    stream.skip(index2); // skip to the beginning of the tag
                    stream.mark(10000); // mark the position
                        
                    //                    System.out.println("RETURNING CRL");
                    return CRL;
                    
                }
            }
        }
        return -1;
    }

}

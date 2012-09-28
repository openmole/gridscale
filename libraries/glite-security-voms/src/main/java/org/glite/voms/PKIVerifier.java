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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.glite.voms.ac.ACTargets;
import org.glite.voms.ac.AttributeCertificate;
import org.glite.voms.ac.AttributeCertificateInfo;
import org.glite.voms.ac.VOMSTrustStore;
import org.glite.voms.ac.*;

public class PKIVerifier {

    private static Logger logger = Logger.getLogger( PKIVerifier.class
            .getName() );

    public static final String SUBJECT_KEY_IDENTIFIER = "2.5.29.14";

    public static final String AUTHORITY_KEY_IDENTIFIER = "2.5.29.35";

    public static final String PROXYCERTINFO = "1.3.6.1.5.5.7.1.14";

    public static final String PROXYCERTINFO_OLD = "1.3.6.1.4.1.3536.1.222";

    public static final String BASIC_CONSTRAINTS_IDENTIFIER = "2.5.29.19";

    public static final String KEY_USAGE_IDENTIFIER = "2.5.29.15";

    public static final String TARGET = "2.5.29.55";

    private static final String[] OIDs = { SUBJECT_KEY_IDENTIFIER,
            AUTHORITY_KEY_IDENTIFIER, PROXYCERTINFO, PROXYCERTINFO_OLD,
            BASIC_CONSTRAINTS_IDENTIFIER, KEY_USAGE_IDENTIFIER };

    private static final String[] AC_OIDs = { TARGET };

    private static final Set handledOIDs = new TreeSet( Arrays.asList( OIDs ) );

    private static final Set handledACOIDs = new TreeSet( Arrays
            .asList( AC_OIDs ) );

    private PKIStore caStore = null;

    private VOMSTrustStore vomsStore = null;

    static {
        if ( Security.getProvider( "BC" ) == null ) {
            Security.addProvider( new BouncyCastleProvider() );
        }
    }

    /**
     * Initializes the verifier.
     * 
     * @param vomsStore
     *            the VOMSTrustStore object which represents the vomsdir store.
     * @param caStore
     *            the PKIStore object which represents the CA store.
     */
    public PKIVerifier( VOMSTrustStore vomsStore, PKIStore caStore ) {

        this.vomsStore = vomsStore;
        this.caStore = caStore;
    }

    /**
     * Initializes the verifier. The CA store is initialized at:
     * "/etc/grid-security/certificates."
     * 
     * @param vomsStore
     *            the VOMSTrustStore object which represents the vomsdir store.
     * 
     * @throws IOException
     *             if there have been IO errors.
     * @throws CertificateException
     *             if there have been problems parsing a certificate
     * @throws CRLException
     *             if there have been problems parsing a CRL.
     */
    public PKIVerifier( VOMSTrustStore vomsStore ) throws IOException,
            CertificateException, CRLException {

        
        this( vomsStore, new PKIStore( PKIStore.DEFAULT_CADIR,
                PKIStore.TYPE_CADIR, true ) );
    }

    /**
     * Initializes the verifier. 
     * 
     * If the VOMSDIR and CADIR system properties are set, those values
     * are used to initialize the voms and ca certificates trust stores.
     * Tipically, the VOMSDIR should point to a directory that contains
     * voms server certificates, while the CADIR should point to a 
     * directory where CA certificates and crl are stored.
     * 
     * If the system properties are not set, The CA store is initialized to:
     * "/etc/grid-security/certificates.", while the VOMS store is initialized
     * to "/etc/grid-security/vomsdir" (slash becomes backslash on windows).
     * 
     * @throws IOException
     *             if there have been IO errors.
     * @throws CertificateException
     *             if there have been problems parsing a certificate
     * @throws CRLException
     *             if there have been problems parsing a CRL.
     */
    public PKIVerifier() throws IOException, CertificateException, CRLException {

        String vomsDir = System.getProperty( "VOMSDIR" );
        String caDir = System.getProperty( "CADIR");
        
        
        
        if (vomsDir != null)
            vomsStore = new PKIStore(vomsDir,PKIStore.TYPE_VOMSDIR,true);
        else
            vomsStore = new PKIStore(PKIStore.DEFAULT_VOMSDIR,PKIStore.TYPE_VOMSDIR,true);
        
        if (caDir != null)
            caStore = new PKIStore(
                    caDir, PKIStore.TYPE_CADIR, true );
        else
            caStore = new PKIStore(
                    PKIStore.DEFAULT_CADIR, PKIStore.TYPE_CADIR, true );
    }

    /**
     * Cleans up resources allocated by the verifier.
     * 
     * This method MUST be called prior to disposal of this object, otherwise
     * memory leaks and runaway threads will occur.
     */
    public void cleanup() {

        if ( vomsStore != null )
            vomsStore.stopRefresh();

        if ( caStore != null )
            caStore.stopRefresh();

        vomsStore = null;
        caStore = null;
    }

    /**
     * Sets a new CAStore.
     * 
     * @param store
     *            the new CA store.
     */
    public void setCAStore( PKIStore store ) {

        if ( caStore != null ) {
            caStore.stopRefresh();
            caStore = null;
        }
        caStore = store;
    }

    /**
     * Sets a new VOMSStore.
     * 
     * @param store
     *            the new VOMS store.
     */
    public void setVOMSStore( VOMSTrustStore store ) {

        if ( vomsStore != null ) {
            vomsStore.stopRefresh();
            vomsStore = null;
        }
        vomsStore = store;
    }

    private static String getHostName() {

        try {
            InetAddress addr = InetAddress.getLocalHost();
            return addr.getCanonicalHostName();
        } catch ( UnknownHostException e ) {
            logger.error( "Cannot discover hostName." );
            return "";
        }
    }

    /**
     * Verifies an Attribute Certificate according to RFC 3281.
     * 
     * @param ac
     *            the Attribute Certificate to verify.
     * 
     * @return true if the attribute certificate is verified, false otherwise.
     */
    public boolean verify( AttributeCertificate ac ) {

        if ( ac == null || vomsStore == null )
            return false;

        AttributeCertificateInfo aci = ac.getAcinfo();
        X509Certificate[] certificates = null;

        ACCerts certList = aci.getCertList();

        LSCFile lsc = null;
        String voName = ac.getVO();

        if ( certList != null )
            lsc = vomsStore.getLSC( voName, ac.getHost() );

        logger.debug("LSC is: " + lsc);
        if ( lsc != null ) {
            boolean success = false;
            Vector dns = lsc.getDNLists();
            Iterator dnIter =dns.iterator();

            // First verify if LSC file applies;

            while ( !success && dnIter.hasNext()) {
                boolean doBreak = false;

                while (dnIter.hasNext() && !doBreak ) {
                    Iterator certIter = certList.getCerts().iterator();
                    Vector realDNs = (Vector) dnIter.next();
                    Iterator realDNsIter = realDNs.iterator();

                    while (realDNsIter.hasNext() && certIter.hasNext() && !doBreak ) {
                        String dn = null;
                        String is = null;

                        try {
                            dn = (String) realDNsIter.next();
                            is = (String) realDNsIter.next();
                        } catch ( NoSuchElementException e ) {
                            doBreak = true;
                        }
                        X509Certificate cert = (X509Certificate) certIter.next();
                        String candidateDN = PKIUtils
                            .getOpenSSLFormatPrincipal( cert.getSubjectDN() );
                        String candidateIs = PKIUtils
                            .getOpenSSLFormatPrincipal( cert.getIssuerDN() );

                        logger.debug("dn is : " + dn);
                        logger.debug("is is : " + is);
                        logger.debug("canddn is : " + candidateDN);
                        logger.debug("candis is : " + candidateIs);
                        logger.debug("dn == canddn is " + dn.equals(candidateDN));
                        logger.debug("is == candis is " + is.equals(candidateIs));
                        if ( !dn.equals( candidateDN ) || !is.equals( candidateIs ) )
                            doBreak = true;
                    }

                    if ( !doBreak && !realDNsIter.hasNext() && !certIter.hasNext() )
                        success = true;
                }
            }

            if ( success == true ) {
                // LSC found. Now verifying certificate
                certificates = (X509Certificate[]) certList.getCerts().toArray(
                        new X509Certificate[] {} );
            }
        }

        if ( certificates == null ) {
            // lsc check failed
            logger.debug("lsc check failed.");
            // System.out.println("Looking for certificates.");
            if ( logger.isDebugEnabled() )
                logger.debug( "Looking for hash: "
                        + PKIUtils.getHash( ac.getIssuer() )
                        + " for certificate: " + ac.getIssuer().getName() );

            X509Certificate[] candidates = vomsStore.getAACandidate( ac
                    .getIssuer(), voName );

            if (candidates == null)
                logger.debug("No candidates found!");
            else if ( candidates.length != 0 ) {
                int i = 0;
                while ( i < candidates.length ) {
                    X509Certificate currentCert = (X509Certificate) candidates[i];
                    PublicKey key = currentCert.getPublicKey();

                    if ( logger.isDebugEnabled() ) {
                        logger.debug( "Candidate: "
                                + currentCert.getSubjectDN().getName() );
                        logger.debug( "Key class: " + key.getClass() );
                        logger.debug( "Key: " + key );
                        byte[] data = key.getEncoded();
                        String str = "Key: ";

                        for ( int j = 0; j < data.length; j++ )
                            str += Integer.toHexString( data[j] ) + " ";

                        logger.debug( str );
                    }

                    if ( ac.verifyCert( currentCert ) ) {
                        logger.debug( "Signature Verification OK" );

                        certificates = new X509Certificate[1];
                        certificates[0] = currentCert;
                        break;
                    } else {
                        logger.debug( "Signature Verification false" );
                    }
                    i++;
                }
            }
        }

        if ( certificates == null ) {
            logger.error( "Cannot find usable certificates to validate the AC. Check that the voms server host certificate is in your vomsdir directory." );
            return false;
        }

        if ( logger.isDebugEnabled() ) {
            for ( int l = 0; l < certificates.length; l++ )
                logger.debug( "Position: " + l + " value: "
                        + certificates[l].getSubjectDN().getName() );
        }

        if ( !verify( certificates ) ) {
            logger.error( "Cannot verify issuer certificate chain for AC" );
            return false;
        }

        if ( !ac.isValid() ) {
            logger.error( "Attribute Certificate not valid at current time." );
            return false;
        }

        // AC Targeting verification

        ACTargets targets = aci.getTargets();

        if ( targets != null ) {
            String hostname = getHostName();

            boolean success = false;
            Iterator i = targets.getTargets().iterator();

            while ( i.hasNext() ) {
                String name = (String) i.next();

                if ( name.equals( hostname ) ) {
                    success = true;
                    break;
                }
            }
            if ( !success ) {
                logger.error( "Targeting check failed!" );
                return false;
            }
        }

        // unhandled extensions check
        X509Extensions exts = aci.getExtensions();

        if ( exts != null ) {
            Enumeration oids = exts.oids();
            while ( oids.hasMoreElements() ) {
                DERObjectIdentifier oid = (DERObjectIdentifier) oids
                        .nextElement();
                X509Extension ext = exts.getExtension( oid );
                if ( ext.isCritical() && !handledACOIDs.contains( oid ) ) {
                    logger.error( "Unknown critical extension discovered: "
                            + oid.getId() );
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Verifies an certificate chain according to RFC 3280.
     * 
     * @param certs
     *            the chain to verify.
     * 
     * @return true if the chain is verified, false otherwise.
     */
    public boolean verify( X509Certificate[] certs ) {

        if ( caStore == null ) {
            logger.error( "No Trust Anchor are known." );
            return false;
        }

        if ( certs.length <= 0 ) {
            logger
                    .error( "Certificate verification: passed empty certificate array." );
            return false;
        }

        Hashtable certificates = caStore.getCAs();

        Stack certStack = new Stack();

        // First, build the certification path
        certStack.push( certs[0] );

        logger.info( "Certificate verification: Verifying certificate '"
                + certs[0].getSubjectDN().getName() + "'" );

        X509Certificate currentCert = certs[0];

        logger.debug( "path length = " + certs.length );

        for ( int i = 1; i < certs.length; i++ ) {
            if ( logger.isDebugEnabled() )
                logger.debug( "Checking: " + certs[i].getSubjectDN().getName() );

            if ( PKIUtils.checkIssued( certs[i], certs[i - 1] ) ) {

                logger.debug( "Is issuer" );

                certStack.push( certs[i] );
                currentCert = certs[i];

                if ( logger.isDebugEnabled() )
                    logger.debug( "ELEMENT: "
                            + currentCert.getSubjectDN().getName() );
            }
            logger.debug( "Is not issuer" );
        }

        logger.debug( "Before anchor searching." );
        X509Certificate candidate = null;

        if ( logger.isDebugEnabled() ) {
            Iterator j = certStack.iterator();
            while ( j.hasNext() )
                logger.debug( "Content: "
                        + ( (X509Certificate) j.next() ).getSubjectDN()
                                .getName() );
        }

        // replace self-signed certificate passed with one from store.
        if ( PKIUtils.selfIssued( currentCert ) ) {
            String hash = PKIUtils.getHash( currentCert );
            Vector candidates = (Vector) certificates.get( hash );
            int index = -1;
            if ( candidates != null
                    && ( index = candidates.indexOf( currentCert ) ) != -1 ) {
                certStack.pop();
                candidate = (X509Certificate) candidates.elementAt( index );
                certStack.push( candidate );

                if ( logger.isDebugEnabled() )
                    logger.debug( "ELEMENT: "
                            + candidate.getSubjectDN().getName() );
            } else {
                logger
                        .error( "Certificate verification: self-signed certificate '"
                                + candidate.getSubjectDN().getName()
                                + "' not found among trusted certificates." );
                return false;
            }
        } else {
            candidate = null;

            logger.debug( "Looking for anchor" );
            // now, complete the certification path.
            do {
                String hash = PKIUtils.getHash( currentCert
                        .getIssuerX500Principal() );

                logger.debug( "hash = " + hash );
                Vector candidates = (Vector) certificates.get( hash );
                if ( candidates != null ) {

                    logger.debug( "CANDIDATES: " + candidates );
                    Iterator i = candidates.iterator();
                    while ( i.hasNext() ) {
                        candidate = (X509Certificate) i.next();

                        if ( logger.isDebugEnabled() )
                            logger.debug( "Candidate = "
                                    + candidate.getSubjectDN().getName() );

                        if ( PKIUtils.checkIssued( candidate, currentCert ) ) {
                            certStack.push( candidate );
                            currentCert = candidate;

                            if ( logger.isDebugEnabled() )
                                logger.debug( "ELEMENT: "
                                        + candidate.getSubjectDN().getName() );

                            break;
                        } else
                            candidate = null;
                    }
                }
            } while ( candidate != null && !PKIUtils.selfIssued( currentCert ) );
        }

        // no trust anchor found
        if ( candidate == null ) {
            logger.error( "Certificate verification: no trust anchor found." );
            return false;
        }

        int currentLength = 0;
        boolean success = true;

        PublicKey candidatePublicKey = null;
        X509Certificate issuerCert = null;

        if ( logger.isDebugEnabled() ) {
            logger.debug( "Constructed chain:" );

            Iterator j = certStack.iterator();
            while ( j.hasNext() )
                logger.debug( "Content: "
                        + ( (X509Certificate) j.next() ).getSubjectDN()
                                .getName() );
        }

        // now verifies it
        while ( !certStack.isEmpty() ) {
            currentCert = (X509Certificate) certStack.pop();

            if ( logger.isDebugEnabled() )
                logger.debug( "VERIFYING : "
                        + currentCert.getSubjectDN().getName() );

            if ( PKIUtils.selfIssued( currentCert ) ) {
                if ( currentLength != 0 ) {
                    logger
                            .error( "Certificate verification: Self signed certificate not trust anchor" );
                    logger.error( "subject: "
                            + currentCert.getSubjectDN().getName() );
                    success = false;
                    break;
                } else {
                    // this is the trust anchor
                    candidatePublicKey = currentCert.getPublicKey();
                    issuerCert = currentCert;
                }
            }

            logger.debug( "Checking chain" );

            if ( !currentCert.getIssuerX500Principal().equals(
                    issuerCert.getSubjectX500Principal() ) ) {
                logger
                        .error( "Certificate verification: issuing chain broken." );
                return false;
            }

            logger.debug( "Checking validity" );

            try {
                currentCert.checkValidity();
            } catch ( CertificateExpiredException e ) {
                logger.error(
                        "Certificate verification: certificate in chain expired. "
                                + e.getMessage(), e );
                logger.error( "Faulty certificate: "
                        + currentCert.getSubjectDN().getName() );
                logger.error( "End validity      : "
                        + currentCert.getNotAfter().toString() );
                return false;
            } catch ( CertificateNotYetValidException e ) {
                logger.error(
                        "Certificate verification: certificate in chain not yet valid. "
                                + e.getMessage(), e );
                logger.error( "Faulty certificate: "
                        + currentCert.getSubjectDN().getName() );
                logger.error( "Start validity      : "
                        + currentCert.getNotBefore().toString() );
                return false;
            }

            logger.debug( "Checking key" );

            try {
                currentCert.verify( candidatePublicKey );
            } catch ( Exception e ) {
                logger.error(
                        "Certificate verification: cannot verify signature. "
                                + e.getMessage(), e );
                logger.error( "Faulty certificate: "
                        + currentCert.getSubjectDN().getName() );
                return false;
            }

            logger.debug( "Checking revoked" );

            if ( isRevoked( currentCert, issuerCert ) ) {
                logger
                        .error( "Certificate verification: certificate in chain has been revoked." );
                logger.error( "Faulty certificate: "
                        + currentCert.getSubjectDN().getName() );
                return false;
            }

            boolean isCA = PKIUtils.isCA( issuerCert );

            logger.debug( "Checking CA " + isCA );
            if ( isCA ) {
                if ( !allowsPath( currentCert, issuerCert ) ) {
                    logger.error( "Certificate verification: subject '"
                            + currentCert.getSubjectDN().getName()
                            + "' not allowed by CA '"
                            + issuerCert.getSubjectDN().getName() + "'" );
                    return false;
                }

                // check path length
                int maxPath = currentCert.getBasicConstraints();

                logger.debug( "stack.size = " + certStack.size()
                        + " maxPath = " + maxPath );

                if ( maxPath != -1 ) {
                    if ( maxPath < certStack.size() ) {
                        logger
                                .error( "Certificate verification: Maximum certification path length exceeded." );
                        success = false;
                        break;
                    }
                }
            } else {
                // not a ca. Maybe a proxy?
                logger.debug( "Checking for Proxy" );

                if ( !PKIUtils.isProxy( currentCert ) ) {
                    logger
                            .error( "Certificate verification: Non-proxy, non-CA certificate issued a certificate." );
                    return false;
                }
            }

            // check for unhandled critical extensions
            Set criticals = currentCert.getCriticalExtensionOIDs();
            if ( criticals != null ) {
                if ( !handledOIDs.containsAll( criticals ) ) {
                    logger
                            .error( "Certificate verification: Certificate contain unhandled critical extensions." );
                    return false;
                }
            }

            issuerCert = currentCert;
            candidatePublicKey = currentCert.getPublicKey();
            currentLength++;
        }

        if ( !success )
            return false;

        return true;
    }

    private boolean allowsPath( X509Certificate cert, X509Certificate issuer ) {

        Hashtable signings = caStore.getSignings();
        // System.out.println("CLASS IS: " +
        // signings.get(PKIUtils.getHash(issuer)).getClass());
        SigningPolicy signCandidate = (SigningPolicy) signings.get( PKIUtils
                .getHash( issuer ) );

        logger.debug("signCandidate is: " + signCandidate);
        if ( signCandidate != null ) {
            logger.debug("Class of issuer is : " + issuer.getClass());
            logger.debug("Class of Subject is: " + issuer.getSubjectDN().getClass());

            String issuerSubj = PKIUtils.getOpenSSLFormatPrincipal( issuer
                    .getSubjectDN() );
            logger.debug("Subject is : " + issuerSubj);
            //            if (true) return true;

            Vector nameVector = getAllNames( cert );

            if ( nameVector == null )
                return false;

            Iterator i = nameVector.iterator();

            while ( i.hasNext() ) {
                boolean matched = false;
                String certSubj = (String) i.next();

                logger.debug( "Looking for " + issuerSubj );
                int index = signCandidate.findIssuer( issuerSubj );

                while ( index != -1 ) {
                    logger.debug( "Inside index" );
                    signCandidate.setCurrent( index );
                    if ( signCandidate.getAccessIDCA().equals( issuerSubj ) ) {
                        Vector subjects = signCandidate.getCondSubjects();

                        Iterator subjIter = subjects.iterator();

                        while ( subjIter.hasNext() ) {
                            String subj = (String) subjIter.next();

                            logger.debug( "Comparing certSubj: '" + certSubj
                                    + "' to '" + subj + "'" );

                            subj = subj.replaceFirst( "\\*", "\\.\\*" );
                            if ( certSubj.matches( subj ) ) {
                                matched = true;
                                logger.debug( "Subject: '" + certSubj
                                        + "' matches with subject: '" + subj
                                        + "' from signing policy." );
                                break;
                            }
                            logger.debug( "Subject: '" + certSubj
                                    + "' does not match subject: '" + subj
                                    + "' from signing policy." );
                        }

                    }
                    index = signCandidate.findIssuer( issuerSubj, index );
                }

                if ( !matched ) {
                    nameVector.clear();
                    return false;
                }
            }
        }
        return true;
    }

    private Vector getAllNames( X509Certificate cert ) {

        if ( cert != null ) {
            Vector v = new Vector();
            v.add( PKIUtils.getOpenSSLFormatPrincipal( cert.getSubjectDN() ) );

//             Collection c;
//             try {
//                 c = cert.getSubjectAlternativeNames();
//             }
//             catch (CertificateParsingException e) {
//                 logger.error("Error in encoding Subject Alternative Names extension! " + e.getMessage());
//                 v.clear();
//                 return null;
//             }

//             Iterator i = c.iterator();

//             while (i.hasNext()) {
//                 List l = (List)i.next();

//                 int type = ((Integer)l.get(0)).intValue();
//                 switch (type) {
//                 case 1: case 2: case 6:
//                 case 7: case 8:
//                     v.add((String)l.get(1));
//                     break;

//                 case 4:
//                     String dn = (String)l.get(1);
//                     X500Principal principal = new X500Principal(dn);
//                     v.add(PKIUtils.getOpenSSLFormatPrincipal(principal));
//                     break;

//                 case 0: case 3: case 5:
//                     v.clear();
//                     return null;

//                 default:
//                     break;
//                 }
//             }

            return v;
        }

        return null;
    }

    private boolean isRevoked( X509Certificate cert, X509Certificate issuer ) {

        Hashtable crls = caStore.getCRLs();
        Vector crllist = (Vector) ( crls.get( PKIUtils.getHash( issuer ) ) );

        boolean issued = false;

        if ( crllist != null ) {
            Iterator i = crllist.iterator();

            while ( i.hasNext() ) {

                X509CRL candidateCRL = (X509CRL) i.next();

                if ( candidateCRL != null ) {
                    try {
                        candidateCRL.verify( issuer.getPublicKey() );
                    } catch ( Exception e ) {
                        continue;
                    }
                    {
                        if ( candidateCRL.getCriticalExtensionOIDs() == null ) {
                            if ( candidateCRL.getIssuerX500Principal().equals(
                                    issuer.getIssuerX500Principal() ) ) {
                                if ( candidateCRL.getNextUpdate().compareTo(
                                        new Date() ) >= 0
                                        && candidateCRL.getThisUpdate()
                                                .compareTo( new Date() ) <= 0 ) {

                                    X509CRLEntry entry = candidateCRL
                                            .getRevokedCertificate( cert
                                                    .getSerialNumber() );
                                    if ( entry == null ) {
                                        return false;
                                    }
                                }
                            }
                        }
                        issued = true;
                    }
                }
            }
        }
        return issued;
    }
}

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


package org.glite.voms;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.Security;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Logger;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.glite.voms.ac.VOMSTrustStore;

/**
 * PKIStore is the class serving to store all the components of a common PKI
 * installation, i.e.: CA certificates, CRLs, Signing policy files...
 *
 * It is also capable of storing files specific to the handling of VOMS
 * proxies, i.e. the content of the vomsdir diectory.
 *
 * @author Vincenzo Ciaschini
 */
public class PKIStore implements VOMSTrustStore {
    private Hashtable certificates = null;
    private Hashtable crls         = null;
    private Hashtable signings     = null;
    private Hashtable lscfiles     = null;
    private Hashtable vomscerts    = null;

    private static Logger logger = Logger.getLogger(PKIStore.class.getName());

    /**
     * This PKIStore object will contain data from a vomsdir directory.
     */
    public static final int TYPE_VOMSDIR = 1;

    /**
     * This PKIStore object will contain data from a CA directory.
     */
    public static final int TYPE_CADIR = 2;

    private static final int CERT = 1;
    private static final int CRL  = 2;
    private static final int SIGN = 3;
    private static final int LSC  = 4;
    private static final int HASHCAPACITY = 75;

    private boolean aggressive = false;
    private Timer theTimer = null;

    private String certDir = null;
    private int type = -1;
    
    public static final String DEFAULT_VOMSDIR= File.separator
    + "etc" + File.separator + "grid-security" + File.separator
    + "vomsdir";
    
    public static final String DEFAULT_CADIR = File.separator
    + "etc" + File.separator + "grid-security" + File.separator
    + "certificates";

    /**
     * @return hashtable containing CA certificates.  The key is
     * the PKIUtils.getHash() of the subject of the CA.  The value is
     * a Vector containing all the CA certificates with the given hash.
     *
     * @see PKIUtils#getHash(X509Certificate cert)
     * @see PKIUtils#getHash(X500Principal principal)
     * @see PKIUtils#getHash(X509Principal principal)
     * @see java.util.Vector
     */
    public Hashtable getCAs() {
        return (Hashtable)certificates.clone();
    }

    /**
     * @return hashtable containing CRL.  The key is
     * the PKIUtils.getHash() of the issuer of the CRL.  The value is
     * a Vector containing all the CRL with the given hash.
     *
     * @see PKIUtils#getHash(X509Certificate cert)
     * @see PKIUtils#getHash(X500Principal principal)
     * @see PKIUtils#getHash(X509Principal principal)
     * @see java.util.Vector
     */

    public Hashtable getCRLs() {
        return crls;
    }

    /**
     * @return hashtable containing SigningPolicy objects.  The key is
     * the PKIUtils.getHash() of the issuer of the SigningPolicy.  The value is
     * a Vector containing all the CRL with the given hash.
     *
     * @see SigningPolicy
     * @see PKIUtils#getHash(X509Certificate cert)
     * @see PKIUtils#getHash(X500Principal principal)
     * @see PKIUtils#getHash(X509Principal principal)
     * @see java.util.Vector
     */

    public Hashtable getSignings() {
        return signings;
    }

//     /**
//      * @return hashtable containing LSC files.  The key is
//      * the VO name of the issuer of the LSC.  The value is
//      * an Hashtable having as key the fully qualified host name of the
//      * server which issued the Attribute Certificate, and as value the
//      * associated LSCFile object.
//      *
//      * @see LSCFile
//      * @see PKIUtils#getHash(X509Certificate cert)
//      * @see PKIUtils#getHash(X500Principal principal)
//      * @see PKIUtils#getHash(X509Principal principal)
//      * @see java.util.Vector
//      */

//     public Hashtable getLSCs() {
//         return lscfiles;
//     }

//     /**
//      * @return hashtable containing VOMS server certificates.  The key is
//      * the PKIUtils.getHash() of the AC issuer.  The value is a HashSet
//      * containing as key the VO name, and as value all the CRL with the given hash.
//      *
//      * @see PKIUtils#getHash(X509Certificate cert)
//      * @see PKIUtils#getHash(X500Principal principal)
//      * @see PKIUtils#getHash(X509Principal principal)
//      * @see java.util.Vector
//      */

//     public Hashtable getVOMSCerts() {
//         return vomscerts;
//     }

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

//     private static String getHostName() {
//         try {
//             InetAddress addr = InetAddress.getLocalHost();
//             return addr.getCanonicalHostName();
//         }
//         catch(UnknownHostException e) {
//             logger.error("Cannot discover hostName.");
//             return "";
//         }
//     }


    private class Refreshener extends TimerTask {
        public void run() {
            refresh();
        }
    }

    /**
     * Refreshes the content of the PKIStore object.
     *
     */
    public synchronized void refresh() {
        PKIStore newReader = null;

        try {
            newReader = new PKIStore(certDir, type, aggressive, false);
        } 
        catch (Exception e) {
            logger.error("Cannot refresh store: " + e.getMessage());
            return;
        }
        finally {
            if (newReader != null)
                newReader.stopRefresh();
        }
        
        //        Hashtable tmp = null;

         try {
             certificates.clear();
             certificates = newReader.certificates;
             newReader.certificates = null;

             crls.clear();
             crls = newReader.crls;
             newReader.crls = null;

             signings.clear();
             signings = newReader.signings;
             newReader.signings = null;

             lscfiles.clear();
             lscfiles = newReader.lscfiles;
             newReader.lscfiles = null;

             vomscerts.clear();
             vomscerts = newReader.vomscerts;
             newReader.vomscerts = null;
         }
         finally {
             newReader = null;
         }
//          tmp = certificates;
//          certificates = newReader.certificates;
//          //        newReader.certificates = null;
//          tmp.clear();
//          tmp = null;

//          tmp = crls;
//          crls         = newReader.crls;
//          tmp.clear();
//          tmp = null;

//          tmp = signings;
//          signings     = newReader.signings;
//          tmp.clear();
//          tmp = null;

//          tmp = lscfiles;
//          lscfiles     = newReader.lscfiles;
//          tmp.clear();
//          tmp = null;

//          tmp = vomscerts;
//          vomscerts    = newReader.vomscerts;
//          tmp.clear();
//          tmp = null;

//          newReader = null;

//        System.out.println("STORE REFRESHED");
    }

    PKIStore(String dir, int type, boolean aggressive, boolean timer)  throws IOException, CertificateException, CRLException {
        this.aggressive = aggressive;
        certificates = new Hashtable(HASHCAPACITY);
        crls         = new Hashtable(HASHCAPACITY);
        signings     = new Hashtable(HASHCAPACITY);
        lscfiles     = new Hashtable(HASHCAPACITY);
        vomscerts    = new Hashtable(HASHCAPACITY);

        if (type != TYPE_VOMSDIR &&
            type != TYPE_CADIR)
            throw new IllegalArgumentException("Unsupported value for type parameter in PKIReader constructor");

        if ((dir == null) || dir.equals("")) {
            if (type == TYPE_VOMSDIR)
                dir = DEFAULT_VOMSDIR;
            else if (type == TYPE_CADIR)
                dir = DEFAULT_CADIR;
        }

        
        // Some sanity checks on VOMSDIR and CA dir
        File theDir = new File(dir);
        
        if (!theDir.exists()){
         
            StringBuffer message = new StringBuffer();
            message.append( "Directory "+dir+" doesn't exist on this machine!" );
            if (type == TYPE_VOMSDIR)
                message.append(" Please specify a value for the vomsdir directory or set the VOMSDIR system property.");
            else
                message.append(" Please specify a value for the cadir directory or set the CADIR system property.");
            
            throw new FileNotFoundException(message.toString());
            
        }
        
        if (!theDir.isDirectory()){
            
            throw new IllegalArgumentException(((type == TYPE_VOMSDIR)? "Voms certificate" : "CA certificate")+ 
                    " directory passed as argument is not a directory! ["+theDir.getAbsolutePath()+"]");
            
        }
        
        if (theDir.list().length == 0){
            
            throw new IllegalArgumentException(((type == TYPE_VOMSDIR)? "Voms certificate" : "CA certificate")+ 
                    " directory passed as argument is empty! ["+theDir.getAbsolutePath()+"]");
        }
        
        certDir = dir;
        this.type = type;

        load();

        if (timer) {
            theTimer = new Timer(true);

            theTimer.scheduleAtFixedRate(new Refreshener(), 30000, 30000);
        }
    }

    /**
     * @param dir        -- The directory from which to read the files.
     *                      If null or the empty string, this will default
     *                      to "/etc/grid-security/certificates" if type is
     *                      TYPE_CADIR, or "etc/grid-security/vomsdir" if
     *                      type is TYPE_VOMSDIR.
     * @param type       -- either TYPE_CADIR for CA certificates,
     *                      or TYPE_VOMSDIR for VOMS certificate.
     * @param aggressive -- if true, loading of data will continue even if
     *                      a particular file could not be loaded, while if
     *                      false loading will stop as soon as an error occur.
     *
     * @throws IOException if type is neither TYPE_CADIR nor TYPE_VOMSDIR.
     * @throws CertificateException if there are parsing errors while loading
     *                              a certificate.
     * @throws CRLException if there are parsing errors while loading a CRL.
     */
    public PKIStore(String dir, int type, boolean aggressive) throws IOException, CertificateException, CRLException {
        this(dir, type, aggressive, true);
    }

    /**
     * This is equivalent to PKIStore(dir, type, true)
     *
     * @see #PKIStore(String dir, int type, boolean aggressive)
     */
    public PKIStore(String dir, int type) throws IOException, CertificateException, CRLException {
        this(dir, type, true, true); 
    }

    public PKIStore() {
        aggressive = true;
        certificates = new Hashtable(HASHCAPACITY);
        crls         = new Hashtable(HASHCAPACITY);
        signings     = new Hashtable(HASHCAPACITY);
        lscfiles     = new Hashtable(HASHCAPACITY);
        vomscerts    = new Hashtable(HASHCAPACITY);
    }


    /**
     * Changes the interval between refreshes of the store.
     *
     * @param millisec New interval (in milliseconds)
     */

    public void rescheduleRefresh(int millisec) {
        if (theTimer != null)
            theTimer.cancel();
        theTimer = null;

        theTimer = new Timer(true);
        theTimer.scheduleAtFixedRate(new Refreshener(), millisec, millisec);
    }

    /**
     * Stop all refreshes.
     *
     * NOTE: This method must ALWAYS be called prior to disposing of a PKIStore
     * object.  The penalty for not doing it is a memor leak.
     */
    public void stopRefresh() {
        if (theTimer != null)
            theTimer.cancel();
        theTimer = null;
    }

    /**
     * Changes the aggressive mode of the store.
     *
     * @param b -- if true (default) load as much as possible,
     *             otherwise stop loading at the first error.
     */
    public void setAggressive(boolean b) {
        aggressive = b;
    }

    private class Couple {
        Object first;
        Object second;

        Couple(Object first, Object second) {
            this.first = first;
            this.second = second;
        }
    }

    /**
     * Gets the LSC file corresponding to the given VO, for the given
     * server.
     *
     * @param voName   -- The name of the VO.
     * @param hostName -- The hostName of the issuing server.
     *
     * @return The corresponding LSCFile object, or null if none is present.
     */
    public LSCFile getLSC(String voName, String hostName) {
        Hashtable lscList = (Hashtable)lscfiles.get(voName);
        //            if (lscList == null)
        //                lscList = (Hashtable)lscfiles.get("");

        if (lscList != null) {
            return (LSCFile)lscList.get(hostName);
        }
        return null;
    }

    /**
     * Gets an array of candidate issuer certificates for an AC with the
     * given issuer and belonging to the given VO.
     *
     * @param issuer The issuer of the AC.
     * @param voName The name of the VO.
     *
     * @return the array of candidates, or null if none is found.
     */
    public X509Certificate[] getAACandidate(X500Principal issuer, String voName) {
        Hashtable listCerts = (Hashtable)vomscerts.get(PKIUtils.getHash(issuer));

        if (logger.isDebugEnabled())
            logger.debug("listcerts content: " + listCerts);
        if (listCerts != null) {
            HashSet certSet = (HashSet)listCerts.get(voName);
            if (certSet == null)
                certSet = (HashSet)listCerts.get("");

            if (certSet != null)
                return (X509Certificate[])certSet.toArray(new X509Certificate[] {});
        }
        return null;
    }

    /**
     * Loads the files from the directory specified in the constructors
     *
     * @throws IOException if type is neither TYPE_CADIR nor TYPE_VOMSDIR.
     * @throws CertificateException if there are parsing errors while loading
     *                              a certificate.
     * @throws CRLException if there are parsing errors while loading a CRL.
     */

    public void load() throws IOException, CertificateException, CRLException  {
        switch (type) {
        case TYPE_VOMSDIR:
            getForVOMS(new File(certDir), null);
            break;
        case TYPE_CADIR:
            getForCA(new File(certDir));
            break;
        default:
            break;
        }
    }

    private void load(X509Certificate cert, String voname) {
        if (cert == null)
            return;

        if (logger.isDebugEnabled())
            logger.debug("CERT = " + cert + " , vo = " + voname);

        String hash = PKIUtils.getHash(cert);

        if (logger.isDebugEnabled()) {
            logger.debug("Registered HASH: " + hash +
                         " for " + cert.getSubjectDN().getName() +
                         " for vo: " + voname);
            logger.debug("Class of getSubjectDN: " + cert.getSubjectDN().getClass());
            logger.debug("KNOWN HASH ? " + vomscerts.containsKey(hash));
            logger.debug("VOMSCERTS = " + vomscerts);
        }

        if (vomscerts.containsKey(hash)) {
            logger.debug("Already exixtsing HASH");

            Hashtable certList = (Hashtable)vomscerts.get(hash);
            HashSet voSet = (HashSet)certList.get(voname);
            if (voSet != null)
                voSet.add(cert);
            else {
                HashSet set = new HashSet();
                set.add(cert);
                certList.put(voname, set);
            }
        }
        else {
            logger.debug("Originally EMPTY table");

            Hashtable certList = new Hashtable(HASHCAPACITY);
            HashSet set = new HashSet();
            set.add(cert);
            certList.put(voname, set);
            vomscerts.put(hash, certList);

            if (logger.isDebugEnabled()) {
                logger.debug("Inserted HASH: " + hash);
                logger.debug("NEW VOMSCERTS = " + vomscerts);
            }
        }
    }

    private void load(X509Certificate[] certs, String voname) {
        int len = certs.length;
        logger.debug("LEN = " +len);

        for (int i =0; i < len; i++) {

            if (logger.isDebugEnabled())
                logger.debug("PARSING: " + i + " value: " + (Object)certs[i]);

            load(certs[i], voname);
        }
    }


    private void load(X509Certificate cert) {
        String hash = PKIUtils.getHash(cert);

        if (certificates.containsKey(hash)) {
            ((Vector)certificates.get(hash)).add(cert);
        }
        else {
            Vector certs = new Vector();
            certs.add(cert);
            certificates.put(hash, certs);
        }
    }

    private void load(X509Certificate[] certs) {
        int len = certs.length;

        for (int i = 0; i < len; i++) {
            load(certs[i]);
        }
    }

    private void load(X509CRL crl) {
        String hash = PKIUtils.getHash(crl);

        if (crls.containsKey(hash)) {
            ((Vector)crls.get(hash)).add(crl);
        }
        else {
            Vector c = new Vector();
            c.add(crl);
            crls.put(hash, c);
        }
    }

    private void load(X509CRL[] crls) {
        int len = crls.length;

        for (int i = 0; i < len; i++) {
            load(crls[i]);
        }
    }

    private void load(SigningPolicy sp) {
        String key = sp.getName();

        signings.put(key, sp);
//         if (signings.containsKey(key)) 
//             ((Vector)signings.get(key)).add(sp);
//         else {
//             Vector signs = new Vector();
//             signs.add(sp);
//             signings.put(key, signs);
//         }
    }

    private void load(SigningPolicy[] sps) {
        int len = sps.length;

        for (int i = 0; i < len; i++) {
            load(sps[i]);
        }
    }

    private void load(LSCFile lsc, String vo) {
        String key = lsc.getName();
        Hashtable lscList = null;

        if (!lscfiles.containsKey(vo)) {
            lscList = new Hashtable();
            lscfiles.put(vo, lscList);
        }

        if (lscList == null)
            lscList = (Hashtable)lscfiles.get(vo);

        lscList.put(key, lsc);
    }

    private void load(LSCFile[] lscs, String vo) {
        int len = lscs.length;

        for (int i = 0; i < len; i++) {
            load(lscs[i], vo);
        }
    }


    private void getForCA(File file) throws IOException, CertificateException, CRLException {
        File[] files = file.listFiles();
        Iterator contents = Arrays.asList(files).iterator();

        while (contents.hasNext()) {
            File f = (File)contents.next();

            logger.debug("filename: " + f.getName());

            try {
                Couple c = getObject(f);
                if (c != null) {
                    int value = ((Integer)c.second).intValue();
                    logger.debug("TYPE: " + value);

                    if (value == CRL)
                        load((X509CRL)c.first);
                    else if (value == CERT) {
                        X509Certificate[] arr = new X509Certificate[0];
                        load((X509Certificate[])((List)(c.first)).toArray(arr));
                    }
                    else if (value == SIGN) {
                        load((SigningPolicy)c.first);
//                         if (logger.isDebugEnabled()) {
//                             logger.debug("Access_id_CA: " + ((SigningPolicy)c.first).getAccessIDCA());
//                             logger.debug("Pos_rights  : " + ((SigningPolicy)c.first).getPosRights());
//                             Vector subjects = ((SigningPolicy)c.first).getCondSubjects();
//                             ListIterator li = subjects.listIterator();
//                             while (li.hasNext())
//                                 logger.debug("Subject     : " + (String)li.next());
//                         }
                    }
                }
            }
            catch(IOException e) {
                logger.error(e.getMessage(), e);
                f = null;
                if (!aggressive)
                    throw e;
            }
            catch(CRLException e) {
                logger.error(e.getMessage(), e);
                f = null;
                if (!aggressive)
                    throw e;
            }
            catch(CertificateException e) {
                logger.error(e.getMessage(), e);
                f = null;
                if (!aggressive)
                    throw e;
            }
        }
    }


    private void getForVOMS(File file, String vo) throws IOException, CertificateException, CRLException  {
        File[] files = file.listFiles();
        Iterator contents = Arrays.asList(files).iterator();
        if (vo == null)
            vo="";

        logger.debug("For VO: " + vo);

        while (contents.hasNext()) {
            File f = (File)contents.next();
            try {
                logger.debug("NAME: " + f.getName());

                if (!f.isDirectory()) {
                    Couple c = getObject(f);
                    if (c != null) {
                        int value = ((Integer)c.second).intValue();
                        logger.debug("TYPE: " + value);

                        if (value == CERT) {
                            X509Certificate[] arr = new X509Certificate[0];
                            load((X509Certificate[])((List)(c.first)).toArray(arr), vo);
                        }
                        else if (value == LSC) {
                            load((LSCFile)c.first, vo);

                            if (logger.isDebugEnabled()) {
                                Vector v = ((LSCFile)c.first).getDNLists();
                                ListIterator li = v.listIterator();
                                int i = 0;
                                while (li.hasNext()) {
                                    logger.debug("Sequence: " + i);
                                    Vector w = (Vector)li.next();
                                    ListIterator li2 = w.listIterator();
                                    while (li2.hasNext())
                                        logger.debug("DN: " + (String)li2.next());
                                }
                            }
                        }
                    }
                }
                else if (vo == "")
                    getForVOMS(f, f.getName());
                f = null;
            }
            catch(CertificateException e) {
                logger.error(e.getMessage(), e);
                f = null;

                if (!aggressive)
                    throw e;
            }
            catch(CRLException e) {
                logger.error(e.getMessage(), e);
                f = null;

                if (!aggressive)
                    throw e;
            }
            catch(IOException e) {
                logger.error(e.getMessage(), e);
                f = null;

                if (!aggressive)
                    throw e;
            }
//             catch(Exception e) {
//                 logger.error(e.getMessage(), e);
//                 f = null;

//                 if (!aggressive)
//                     throw e;
//             }
        }
    }

    private Couple getObject(File f) throws IOException, CertificateException, CRLException {
        if (f.getName().matches(".*\\.lsc")) {
            return new Couple(new LSCFile(f), new Integer(LSC));
        }

        if (f.getName().matches(".*\\.signing_policy")) {
            return new Couple(new SigningPolicy(f), new Integer(SIGN));

        }

        Object o = null;
        try {
            o = PKIUtils.readObject(f);
        }
        catch(FileNotFoundException e) {
            logger.error("Problem reading file " + f.getName() +
                         ": " + e.getMessage());
            return null;
        }

        if (o instanceof X509CRL)
            return new Couple(o, new Integer(CRL));

        if (o instanceof List)
            return new Couple(o, new Integer(CERT));

        return null;
    }
}
        

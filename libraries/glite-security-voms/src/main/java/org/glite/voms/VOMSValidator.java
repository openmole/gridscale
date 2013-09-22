/*
 * Copyright (c) Members of the EGEE Collaboration. 2004.
 * See http://eu-egee.org/partners/ for details on the copyright holders.
 * For license conditions see the license file or http://eu-egee.org/license.html
 */

package org.glite.voms;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.Security;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.glite.voms.ac.ACTrustStore;
import org.glite.voms.ac.*;
import org.glite.voms.ac.AttributeCertificate;
import org.glite.voms.ac.VOMSTrustStore;

/**
 * The main (top) class to use for extracting VOMS information from
 * a certificate and/or certificate chain. The VOMS information can
 * simply be parsed or validated. No validation is performed on the
 * certificate chain -- that is assumed to already have happened.
 * <br>
 * The certificate chain is assumed to already be validated. It is
 * also assumed to be sorted in TLS order, that is certificate
 * issued by trust anchor first and client certificate last.
 * <br>
 * Example of use: this will validate any VOMS attributes in the
 * certificate chain and check if any of the attributes grants the
 * user the "admin" role in the group (VO) "MyVO".
 * <pre>
 * boolean isAdmin = new VOMSValidator(certChain).validate().getRoles("MyVO").contains("admin");
 * </pre>
 *
 * @author mulmo
 * @author Vincenzo Ciaschini
 */
public class VOMSValidator {
    static Logger log = Logger.getLogger(VOMSValidator.class);
    public static final String VOMS_EXT_OID = "1.3.6.1.4.1.8005.100.100.5";
    protected static ACTrustStore theTrustStore;
    protected ACValidator myValidator;
    protected X509Certificate[] myValidatedChain;
    protected Vector myVomsAttributes = new Vector();
    protected boolean isParsed = false;
    protected boolean isValidated = false;
    //    protected boolean isPreValidated = false;
    protected FQANTree myFQANTree = null;
    //    private VomsdataPeer vp = null;
    protected static VOMSTrustStore vomsStore = null;

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Convenience constructor in the case where you have a single
     * cert and not a chain.
     * @param validatedCert
     * @see #VOMSValidator(X509Certificate[])
     */
    public VOMSValidator(X509Certificate validatedCert) {
        this(new X509Certificate[] { validatedCert });
    }

    /**
     * Convenience constructor<br>
     * Same as <code>VOMSValidator(validatedChain, null)</code>
     * @param validatedChain
     */
    public VOMSValidator(X509Certificate[] validatedChain) {
        this(validatedChain, null);
    }

    /**
     * If <code>validatedChain</code> is <code>null</code>, a call to
     * <code>setValidatedChain()</code> MUST be made before calling
     * <code>parse()</code> or <code>validate()</code>.
     *
     * @param validatedChain The (full), validated certificate chain
     * @param acValidator The AC validator implementation to use (null is default with a BasicVOMSTrustStore)
     *
     * @see org.glite.voms.ac.ACValidator
     * @see BasicVOMSTrustStore
     */
    public VOMSValidator(X509Certificate[] validatedChain, ACValidator acValidator) {
        myValidatedChain = validatedChain; // allow null


        if (theTrustStore == null) {
            if (vomsStore == null) {
                try {
                    vomsStore = new PKIStore("/etc/grid-security/vomsdir", PKIStore.TYPE_VOMSDIR, true);
                }
                catch(IOException e) {}
                catch(CertificateException e) {}
                catch(CRLException e) {}
            }
        }
        else if (theTrustStore instanceof BasicVOMSTrustStore) {
            BasicVOMSTrustStore store = (BasicVOMSTrustStore)theTrustStore;
            store.stopRefresh();
            if (vomsStore == null) {
                String directory = store.getDirList();
                
                try {
                    vomsStore = new PKIStore(directory, PKIStore.TYPE_VOMSDIR, true);
                }
                catch(IOException e) {}
                catch(CertificateException e) {}
                catch(CRLException e) {}
            }
        }
        else if (vomsStore == null)
            log.error("Cannot replace passed truststore.  Validation may not be complete.");

        if (vomsStore != null)
            myValidator = (acValidator == null) ? new ACValidator(vomsStore) : acValidator;
        else
            myValidator = (acValidator == null) ? new ACValidator(theTrustStore) : acValidator;
    }

    /**
     * Sets the ACTrustStore instance to use with the default
     * ACValidator. Default is <code>BasicVOMSTrustStore</code>
     *
     * @param trustStore
     *
     * @see #setTrustStore(VOMSTrustStore trustStore)
     * @see BasicVOMSTrustStore
     * @deprecated use setTrustStore(VOMSTrustStore trustStore) instead.
     */
    public static void setTrustStore(ACTrustStore trustStore) {
        if (trustStore instanceof BasicVOMSTrustStore) {
            BasicVOMSTrustStore store = (BasicVOMSTrustStore)trustStore;
            String directory = store.getDirList();
            try {
                setTrustStore(new PKIStore(directory, PKIStore.TYPE_VOMSDIR, true));
                store.stopRefresh();
            }
            catch(Exception e) {
                log.error("Cannot set upgraded truststore!");
                theTrustStore = trustStore;
            }
        }
        else {
            log.error("Cannot set upgraded truststore!");
            theTrustStore = trustStore;
        }
    }

    /**
     * Sets the trustStore to use with the default ACValidator.
     *
     * @param trustStore the trustStore.
     *
     * @see org.glite.voms.ac.VOMSTrustStore
     */
    public static void setTrustStore(VOMSTrustStore trustStore) {
        vomsStore = trustStore;
    }

    /**
     * Cleans up the object.
     *
     * This method MUST be called before disposing of the object, on pains of
     * a memory leak.
     */
    public void cleanup() {
        myValidatedChain = null;

        if (myVomsAttributes != null) {
            myVomsAttributes.clear();
            myVomsAttributes  = null;
        }

        myFQANTree       = null;

        if (myValidator != null) {
            myValidator.cleanup();
            myValidator = null;
        }

        if (vomsStore != null) {
            vomsStore.stopRefresh();
            vomsStore = null;
        }

        if (theTrustStore != null) {
            if (theTrustStore instanceof BasicVOMSTrustStore) {
                ((BasicVOMSTrustStore)theTrustStore).stopRefresh();
            }
            theTrustStore = null;
        }
    }

    /**
     * Convenience method: enables you to reuse a <code>VOMSValidator</code>
     * instance for another client chain, thus avoiding overhead in
     * instantiating validators and trust stores and other potentially
     * expensive operations.
     * <br>
     * This method returns the object itself, to allow for chaining
     * of commands: <br>
     * <code>vomsValidator.setValidatedChain(chain).validate().getVOMSAttributes();</code>
     *
     * @param validatedChain The new validated certificate chain to inspect
     * @return the object itself
     */
    public VOMSValidator setClientChain(X509Certificate[] validatedChain) {
        myValidatedChain = validatedChain;
        myVomsAttributes = new Vector();
        myFQANTree = null;
        isParsed = false;
        isValidated = false;

        //        if (vp != null)
        //            vp = null;

        //        vp = new VomsdataPeer();
        //        isPreValidated = vp.Retrieve(validatedChain[0], validatedChain, VomsdataPeer.RECURSIVE);

        return this;
    }

    /**
     * Parses the assumed-validated certificate chain (which may also
     * include proxy certs) for any occurances of VOMS extensions containing
     * attribute certificates issued to the end entity in the certificate
     * chain.
     * <br>
     * <b>No validation of timestamps and/or signatures are
     * performed by this method.</b>
     * <br>
     * @return the voms attributes
     * @see #validate()
     */
    public static Vector parse(X509Certificate[] myValidatedChain) {
        //        System.out.println("WRONG");
        if (log.isDebugEnabled()) {
            log.debug("VOMSValidator : parsing cert chain");
        }

        int aclen = -1;

        int clientIdx = CertUtil.findClientCert(myValidatedChain);

        if (clientIdx < 0) {
            log.error("VOMSValidator : no client cert found in cert chain");
        }

        if (log.isDebugEnabled()) {
            log.debug("Parsing VOMS attributes for subject " +
                myValidatedChain[clientIdx].getSubjectX500Principal().getName());
        }

        //        VomsdataPeer vp = new VomsdataPeer("","");
        //        vp.Retrieve(myValidatedChain[0], myValidatedChain, VomsdataPeer.RECURSIVE);

        Vector myVomsAttributes = new Vector();

        for (int i = 0; i < myValidatedChain.length; i++) {
            byte[] payload = myValidatedChain[i].getExtensionValue(VOMS_EXT_OID);

            if (payload == null) {
                if (log.isDebugEnabled()) {
                    log.debug("No VOMS extension in certificate issued to " +
                        myValidatedChain[i].getSubjectX500Principal().getName());
                }

                continue;
            }

            try {
                // Strip the wrapping OCTET STRING
                payload = ((DEROctetString) new ASN1InputStream(new ByteArrayInputStream(payload)).readObject()).getOctets();

                // VOMS extension is SEQUENCE of SET of AttributeCertificate
                // now, SET is an ordered sequence, and an AC is a sequence as
                // well -- thus the three nested ASN.1 sequences below...
                ASN1Sequence seq1 = (ASN1Sequence) new ASN1InputStream(new ByteArrayInputStream(payload)).readObject();

                for (Enumeration e1 = seq1.getObjects(); e1.hasMoreElements();) {
                    ASN1Sequence seq2 = (ASN1Sequence) e1.nextElement();


                    for (Enumeration e2 = seq2.getObjects(); e2.hasMoreElements();) {
                        AttributeCertificate ac = new AttributeCertificate((ASN1Sequence) e2.nextElement());
                        aclen++;

                        for (int j = clientIdx; j < myValidatedChain.length; j++) {
                            if (ac.getHolder().isHolder(myValidatedChain[j])) {
                                VOMSAttribute va = new VOMSAttribute(ac);

                                if (log.isDebugEnabled()) {
                                    log.debug("Found VOMS attribute from " + va.getHostPort() +
                                        " in certificate issued to " +
                                        myValidatedChain[j].getSubjectX500Principal().getName());
                                }

                                myVomsAttributes.add(va);
                            }else{
                                log.debug("VOMS attribute cert found, but holder checking failed!");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.info("Error parsing VOMS extension in certificate issued to " +
                    myValidatedChain[i].getSubjectX500Principal().getName(), e);
                throw new IllegalArgumentException("Error parsing VOMS extension in certificate issued to " +
                        myValidatedChain[i].getSubjectX500Principal().getName() + "error was:" + e.getMessage());
            }
        }

        return myVomsAttributes;
    }
    
    /**
     * Parses the assumed-validated certificate chain (which may also
     * include proxy certs) for any occurances of VOMS extensions containing
     * attribute certificates issued to the end entity in the certificate
     * chain.
     * <br>
     * <b>No validation of timestamps and/or signatures are
     * performed by this method.</b>
     * <br>
     * This method returns the object itself, to allow for chaining
     * of commands: <br>
     * <code>new VOMSValidator(certChain).parse().getVOMSAttributes();</code>
     * @return the object itself
     * @see #validate()
     * @deprecated use the parse(X509Certificate[]) instead
     */
    public VOMSValidator parse() {
        //        System.out.println("CORRECT");
        if (log.isDebugEnabled()) {
            log.debug("VOMSValidator : parsing cert chain");
        }

        if (isParsed) {
            return this;
        }

        int aclen = -1;

        int clientIdx = CertUtil.findClientCert(myValidatedChain);

        if (clientIdx < 0) {
            log.error("VOMSValidator : no client cert found in cert chain");
        }

        if (log.isDebugEnabled()) {
            log.debug("Parsing VOMS attributes for subject " +
                myValidatedChain[clientIdx].getSubjectX500Principal().getName());
        }

        for (int i = 0; i < myValidatedChain.length; i++) {
            byte[] payload = myValidatedChain[i].getExtensionValue(VOMS_EXT_OID);

            if (payload == null) {
                if (log.isDebugEnabled()) {
                    log.debug("No VOMS extension in certificate issued to " +
                        myValidatedChain[i].getSubjectX500Principal().getName());
                }

                continue;
            }

            try {
                // Strip the wrapping OCTET STRING
                payload = ((DEROctetString) new ASN1InputStream(new ByteArrayInputStream(payload)).readObject()).getOctets();

                // VOMS extension is SEQUENCE of SET of AttributeCertificate
                // now, SET is an ordered sequence, and an AC is a sequence as
                // well -- thus the three nested ASN.1 sequences below...
                ASN1Sequence seq1 = (ASN1Sequence) new ASN1InputStream(new ByteArrayInputStream(payload)).readObject();

                for (Enumeration e1 = seq1.getObjects(); e1.hasMoreElements();) {
                    ASN1Sequence seq2 = (ASN1Sequence) e1.nextElement();

                    for (Enumeration e2 = seq2.getObjects(); e2.hasMoreElements();) {
                        AttributeCertificate ac = new AttributeCertificate((ASN1Sequence) e2.nextElement());

                        for (int j = clientIdx; j < myValidatedChain.length; j++) {
                            if (ac.getHolder().isHolder(myValidatedChain[j])) {
                                aclen++;
                                VOMSAttribute va = new VOMSAttribute(ac);

                                if (log.isDebugEnabled()) {
                                    log.debug("Found VOMS attribute from " + va.getHostPort() +
                                        " in certificate issued to " +
                                        myValidatedChain[j].getSubjectX500Principal().getName());
                                }

                                myVomsAttributes.add(va);
                            }else{
                                log.debug("VOMS attribute cert found, but holder checking failed!");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.info("Error parsing VOMS extension in certificate issued to " +
                    myValidatedChain[i].getSubjectX500Principal().getName(), e);
            }
        }

        isParsed = true;

        return this;
    }
    
    /**
     * Parses the assumed-validated certificate chain (which may also
     * include proxy certs) for any occurances of VOMS extensions containing
     * attribute certificates issued to the end entity in the certificate
     * chain.
     * The attribute certificates are validated: any non-valid entries will
     * be ignored.
     * <br>
     * This method returns the object itself, to allow for chaining
     * of commands: <br>
     * <code>new VOMSValidator(certChain).parse().getVOMSAttributes();</code>
     * @return the object itself
     * @see #parse()
     */
    public VOMSValidator validate() {
        if (isValidated) {
            return this;
        }

        if (!isParsed) {
            parse();
            isParsed = true;
        }

        //        if (!isPreValidated) {
            for (ListIterator i = myVomsAttributes.listIterator(); i.hasNext();) {
                AttributeCertificate ac = ((VOMSAttribute) i.next()).privateGetAC();

                if (!myValidator.validate(ac)) {
                    i.remove();
                }
            }
            //        }

        isValidated = true;

        return this;
    }

    /**
     * Populates the hierarchial FQAN tree with the parsed and/or
     * validated ACs.
     */
    private void populate() {
        if (!isParsed && !isValidated) {
            throw new IllegalStateException(
                "VOMSValidator: trying to populate FQAN tree before call to parse() or validate()");
        }

        myFQANTree = new FQANTree();

        for (ListIterator i = myVomsAttributes.listIterator(); i.hasNext();) {
            myFQANTree.add(((VOMSAttribute) i.next()).getListOfFQAN());
        }
    }

    /**
     * Returns a collection of all the FQANs in all the ACs found in the
     * credential, in order.
     * @return Vector of FQANs
     */
    public String[] getAllFullyQualifiedAttributes() {
        ArrayList clientAttributes = new ArrayList();

        for (int i = 0; i < myVomsAttributes.size(); i++) {
            List vomsAttributes = ((VOMSAttribute)myVomsAttributes.get(i)).getFullyQualifiedAttributes();
            clientAttributes.addAll(vomsAttributes);
        }

        return (String[])clientAttributes.toArray(new String[] {});
    }

    /**
     * Returns a list of VOMS attributes, parsed and possibly validated.
     * @return List of <code>VOMSAttribute</code>
     * @see VOMSAttribute
     * @see #parse()
     * @see #validate()
     * @see #isValidated()
     */
    public List getVOMSAttributes() {
        return myVomsAttributes;
    }

    /**
     * Returns a list of all roles attributed to a (sub)group, by
     * combining all VOMS attributes in a hiearchial fashion.
     * <br>
     * <b>Note:</b> One of the methods <code>parse()</code> or
     * <code>validate()</code> must have been called before calling
     * this method. Otherwise, an <code>IllegalStateException</code>
     * is thrown.
     *
     * @param subGroup
     * @see VOMSValidator.FQANTree
     * @return the List of roles.
     */
    public List getRoles(String subGroup) {
        if (!isParsed && !isValidated) {
            throw new IllegalStateException("Must call parse() or validate() first");
        }

        if (myFQANTree == null) {
            populate();
        }

        return myFQANTree.getRoles(subGroup);
    }

    /**
     * Returns a list of all capabilities attributed to a (sub)group,
     * by combining all VOMS attributes in a hiearchial fashion.
     * <br>
     * <b>Note:</b> One of the methods <code>parse()</code> or
     * <code>validate()</code> must have been called before calling
     * this method. Otherwise, an <code>IllegalStateException</code>
     * is thrown.
     *
     * @param subGroup
     * @see VOMSValidator.FQANTree
     * @return A list containing all the capabilities
     * @deprecated Capabilities are deprecated.
     */
    public List getCapabilities(String subGroup) {
        if (!isParsed && !isValidated) {
            throw new IllegalStateException("Must call parse() or validate() first");
        }

        if (myFQANTree == null) {
            populate();
        }

        return myFQANTree.getCapabilities(subGroup);
    }

    /**
     * @return whether the validation process has been ran on VOMS attributes
     *
     * @see #validate()
     */
    public boolean isValidated() {
        return isValidated;
    }
    
    
    public boolean isValid(){
        
        return true;
    }

    public String toString() {
        return "isParsed : " + isParsed + "\nhas been validated : " + isValidated + "\nVOMS attrs:" + myVomsAttributes;
    }

    /**
     * Helper container that fills up with roles and capabilties
     * as the FQANTree is traversed.
     */
    class RoleCaps {
        // NOTE: these are not initialized by default, but only if this
        // structure is added non-null Vector content via add(). That
        // way, we can distuingish between the returning null and the empty
        // set (as the Vector may be empty, consider FQAN "/A/Role=")
        List roles;
        List caps;

        void add(List v, String s) {
            if (s == null) {
                return;
            }

            if (!v.contains(s)) {
                v.add(s);
            }
        }

        public void add(Vector fqans) {
            if (fqans == null) {
                return;
            }

            if (roles == null) {
                roles = new Vector();
                caps = new Vector();
            }

            for (Iterator i = fqans.iterator(); i.hasNext();) {
                FQAN f = (FQAN) i.next();
                add(roles, f.getRole());
                add(caps, f.getCapability());
            }
        }

        public List getRoles() {
            return roles;
        }

        public List getCapabilities() {
            return caps;
        }
    }

    /**
     * Class to sort out the hierarchial properties of FQANs. For example,
     * given the FQANs <code>/VO/Role=admin</code> and
     * </code>/VO/SubGroup/Role=user</code>, this means that the
     * applicable roles for </code>/VO/SubGroup</vo> is both
     * <code>admin</code> as well as <code>user</code>
     */
    public class FQANTree {
        Hashtable myTree = new Hashtable();
        Hashtable myResults = new Hashtable();

        public void add(List fqans) {
            if (fqans == null) {
                return;
            }

            for (Iterator i = fqans.iterator(); i.hasNext();) {
                add((FQAN) i.next());
            }
        }

        public void add(FQAN fqan) {
            String group = fqan.getGroup();
            Vector v = (Vector) myTree.get(group);

            if (v == null) {
                myTree.put(group, v = new Vector());
            }

            if (!v.contains(fqan)) {
                v.add(fqan);
            }
        }

        protected RoleCaps traverse(String voGroup) {
            RoleCaps rc = (RoleCaps) myResults.get(voGroup);

            if (rc != null) {
                return rc;
            }

            rc = new RoleCaps();

            StringTokenizer tok = new StringTokenizer(voGroup, "/", true);
            StringBuffer sb = new StringBuffer();

            while (tok.hasMoreTokens()) {
                sb.append(tok.nextToken());
                rc.add((Vector) myTree.get(sb.toString()));
            }

            myResults.put(voGroup, rc);

            return rc;
        }

        public List getRoles(String voGroup) {
            return traverse(voGroup).getRoles();
        }

        public List getCapabilities(String voGroup) {
            return traverse(voGroup).getCapabilities();
        }
    }
}

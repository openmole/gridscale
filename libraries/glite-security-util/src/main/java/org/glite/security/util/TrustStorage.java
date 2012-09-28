/*
Copyright (c) Members of the EGEE Collaboration. 2004. 
See http://www.eu-egee.org/partners/ for details on the copyright
holders.  

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 

    http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
 */

package org.glite.security.util;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import org.apache.log4j.Logger;

/**
 * A class that handles the information obtained from the trust directory.
 * 
 * @author hahkala
 */

public class TrustStorage {
    /**
     * The logging facility.
     */
    static final Logger LOGGER = Logger.getLogger(TrustStorage.class.getName());
    
    /** The size of initial CA vector. */
    private static final int INITIAL_CA_VECTOR_SIZE = 400;

    /**
     * A hashtable of trust anchors. The key is the ASN1 encoded DN of the CA for speed of finding it. The item in the
     * hashtable is a vector of CAs with that DN. The item is a @see Vector as there can be sevaral CA certs with the
     * same DN, for example in case of CA cert rollover. They each have their own CRL etc, so they have to be their own @see
     * FullTrustAnchor.
     */
    Hashtable<String, Vector<FullTrustAnchor>> m_trustStore = new Hashtable<String, Vector<FullTrustAnchor>>(INITIAL_CA_VECTOR_SIZE);

    /** The path to the trust anchors, CRLs and namespace files. */
    String m_storagePath = null;

    /** Whether the trust anchors are preloaded or not. True if the trust anchors are preloaded. */
    boolean m_preloaded = false;

    /** Flag to indicate the trust storage update is in process. To avoid multiple updates at the same time */
    private boolean m_updating;
    
    /** The properties to use for loading the trustanchors. */
    private CaseInsensitiveProperties m_props;

    /**
     * Generates a new TrustStorage instance.
     * 
     * @param storagePath The path to the trust anchors, CRLs and name spaces.
     * @throws IOException If there is a file access problem.
     * @throws CertificateException in case the certificate handling fails.
     * @throws ParseException in case the namespace parsing fails.
     * @deprecated use the constructor TrustStorage(String, CaseInsensitiveProperties) instead.
     */
    public TrustStorage(String storagePath) throws IOException, CertificateException, ParseException {
        this.m_storagePath = storagePath;
        checkUpdate();
        if (m_trustStore.isEmpty()) {
            throw new IOException("No certificate authority files were found from: " + storagePath);
        }
    }

    /**
     * Generates a new TrustStorage instance.
     * 
     * @param storagePath The path to the trust anchors, CRLs and name spaces.
     * @param props the properties to pass along for child classes to use.
     * @throws IOException If there is a file access problem.
     * @throws CertificateException in case the certificate handling fails.
     * @throws ParseException in case the namespace parsing fails.
     */
    public TrustStorage(String storagePath, CaseInsensitiveProperties props)  throws IOException, CertificateException, ParseException {
        m_props = props;
        this.m_storagePath = storagePath;
        checkUpdate();
        if (m_trustStore.isEmpty()) {
            throw new IOException("No certificate authority files were found from: " + storagePath);
        }
    }

    /**
     * Sets the updating flag. Returns the old value. Used to avoid several updates at the same time.
     * 
     * @return the old value of the flag. If it is true someone is updating and no update should happen. If returns
     *         false, the requester must update and release afterwards.
     */
    private synchronized boolean setUpdating() {
        boolean oldValue = m_updating;
        m_updating = true;
        return oldValue;
    }

    /**
     * Release the updating flag. Returns the old value for debugging. Should always return true as only one updater
     * should be updating at a time thus only the one that is updating should release and thus if it is false someone
     * else released and things have gone wrong.
     * 
     * @return The old value of the flag.
     */
    private synchronized boolean releaseUpdating() {
        boolean oldValue = m_updating;
        m_updating = false;
        return oldValue;
    }

    /**
     * Returns the anchors that correspond to the given hash.
     * 
     * @param hash The hash used to identify the CA.
     * @return The array of trust anchors that are identified by the hash.
     */
    public FullTrustAnchor[] getAnchors(String hash) {
        Vector<FullTrustAnchor> anchors = m_trustStore.get(hash);
        if (anchors == null) {
            return null;
        }

        return anchors.toArray(new FullTrustAnchor[] {});
    }

    /**
     * Loads all the trust anchors into the internal structure. Used by the constructor.
     * 
     * @throws IOException Thrown in case there is problems reading the files.
     * @throws CertificateException Thrown in case there is problems parsing the Certificates.
     * @throws ParseException Thrown in case the namespace file parsing fails.
     * @deprecated use checkUpdating also for initial loading.
     */
    public void loadAnchors() throws IOException, CertificateException, ParseException {
        Hashtable<String, Vector<FullTrustAnchor>>  tempStore = new Hashtable<String, Vector<FullTrustAnchor>>(INITIAL_CA_VECTOR_SIZE);
        TrustDirHandler dirHandler = new TrustDirHandler(m_storagePath);
        dirHandler.init();

        File files[] = dirHandler.getCAs();

        int i = 0;

        while (i < files.length) {
            String file = files[i].getAbsolutePath();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Loading CA: " + file);
            }

            FullTrustAnchor anchor;
            try {
                anchor = new FullTrustAnchor(file, m_props);
            } catch (Exception e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.warn("Error loading a CA from: " + file + ", CA not enabled. Error was: " + e.getMessage(), e);
                } else {
                    LOGGER.warn("Error loading a CA from: " + file + ", CA not enabled. Error was: " + e.getMessage());
                }
                i++; // skip to the next CA without adding this CA.
                continue;
            }

            // anchors with same hash are in vector, if this is the first with the hash, generate new vector.
            Vector<FullTrustAnchor> anchors = tempStore.get(anchor.m_caHash);
            if (anchors == null) {
                anchors = new Vector<FullTrustAnchor>();
            }
            
            anchors.add(anchor);
            tempStore.put(anchor.m_caHash, anchors);

            i++;
        }
        m_trustStore = tempStore;
    }

    /**
     * Checks the trust store. It updates the existing CAs, CRLs and namespace definitions in case the files have
     * changed. It also adds any new CAs along with their CRL and namespaces and removes CAs that have been removed from
     * the storage.
     * 
     * @throws IOException in case there is a problem reading files from the file system.
     * @throws CertificateException in case there is problems parsing the CA certificates or CRLs.
     * @throws ParseException in case there is problems parsing the name space files.
     */
    public void checkUpdate() throws IOException, CertificateException, ParseException {
        try {
            if (setUpdating() == true) {
                return;
            }
            TrustDirHandler dirHandler = new TrustDirHandler(m_storagePath);
            dirHandler.init();

            File files[] = dirHandler.getCAs();

            String filenames[] = new String[files.length];

            for (int n = 0; n < files.length; n++) {
                filenames[n] = files[n].getAbsolutePath();
            }

            int i = 0;

            // update existing CAs and add new ones.
            while (i < filenames.length) {
                String filename = filenames[i];

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Checking a CA for update, processing file: " + filename);
                }

                // split the CA filename to get the CA hash and number.
                CAFilenameSplitter parts = CAFilenameSplitter.splitCAFilename(filename);

                // check if we already have a CA with that hash.
                Vector<FullTrustAnchor> cas = m_trustStore.get(parts.m_hash);

                // if not, create and add it.
                if (cas == null) {
                    LOGGER.debug("Adding new anchor from file: " + filename);
                    FullTrustAnchor anchor;
                    try {
                        anchor = new FullTrustAnchor(filename, m_props);
                    } catch (Exception e) {
                        LOGGER.warn("Error loading a CA from: " + filename + ", CA not enabled. Error was: "
                                + e.getMessage());
                        i++; // skip to the next CA without handling this invalid ca file.
                        continue;
                    }
                    Vector<FullTrustAnchor> newVector = new Vector<FullTrustAnchor>();
                    newVector.add(anchor);
                    m_trustStore.put(parts.m_hash, newVector);
                    i++;
                    continue;
                }

                boolean found = false;

                // if we do have CA with that hash, check if one of them has the same number.
                Iterator<FullTrustAnchor> iter = cas.iterator();
                while (iter.hasNext()) {
                    FullTrustAnchor anchor = iter.next();
                    if (anchor.m_caNumber == parts.m_number) {
                        found = true;
                        try {
                            anchor.checkUpdate();
                        } catch (CertificateNotFoundException e) {
                            LOGGER.info("CA file: " + filename + "." + parts.m_number + ", was removed. Disabling the CA.");
                            cas.remove(anchor);
                        } catch (CertificateException e) {
                            LOGGER.warn("Loading of CA file: " + filename + "." + parts.m_number + ", failed. Disabling the CA. Error was: " + e.getMessage());
                            cas.remove(anchor);
                        } catch (Exception e) {
                            if (LOGGER.isDebugEnabled()) {
                            	LOGGER.warn("Error loading a CA from: " + filename + " number " + parts.m_number
                                        + ", CA not fully updated. Error was: " + e + ": " + e.getMessage(), e);
                            } else {
                            	LOGGER.warn("Error loading a CA from: " + filename + " number " + parts.m_number
                                    + ", CA not fully updated. Error was: " + e + ": " + e.getMessage());
                            }
                        }
                        break;
                    }
                }

                if (!found) {
                    // CA with a number used in the file couldn't be found, so add it.
                    FullTrustAnchor anchor;
                    try {
                        anchor = new FullTrustAnchor(filename, m_props);
                    } catch (Exception e) {
                        LOGGER.warn("Error loading a CA from: " + filename + ", CA not enabled. Error was: " + e.getMessage());
                        i++; // skip to the next CA file.
                        continue;
                    }
                    cas.add(anchor);
                }
                i++;
            }

            // remove nonexisting CAs.
            Iterator<Vector<FullTrustAnchor>> iter = m_trustStore.values().iterator();

            // go through the CA vectors
            while (iter.hasNext()) {
                Vector<FullTrustAnchor> anchors = iter.next();
                Iterator<FullTrustAnchor> numberIter = anchors.iterator();
                // go through the CAs in the Vector
                while (numberIter.hasNext()) {
                    FullTrustAnchor anchor = numberIter.next();
                    // try to find the file for CA
                    int n;
                    for (n = 0; n < filenames.length; n++) {
                        // test for match between the trustanchor and filename.
                        if (filenames[n].equals(anchor.m_baseFilename + "." + anchor.m_caNumber)) {
                            break;
                        }
                    }

                    // CA file has been removed, remove it from the trust store.
                    if (n == filenames.length) {
                        numberIter.remove();
                    }
                }
                // check if the CA(s) from the vector were removed.
                if (anchors.isEmpty()) {
                    iter.remove();
                }
            }
        } finally {
            if (releaseUpdating() == false) {
                LOGGER.fatal("Internal synchronization violation, two trustanchor updaters mixing.");
                throw new RuntimeException("Internal synchronization violation, two trustanchor updaters mixing.");
            }
        }
    }

    /**
     * Used to get the list of trust anchors in the storage.
     * 
     * @return The array of trust anchors in the storage.
     */
    public FullTrustAnchor[] getAnchors() {
        if (m_trustStore.isEmpty()) {
            return null;
        }
        Enumeration<Vector<FullTrustAnchor>> enumer = m_trustStore.elements();

        Vector<FullTrustAnchor> trustAnchors = new Vector<FullTrustAnchor>();

        while (enumer.hasMoreElements()) {
            trustAnchors.addAll(enumer.nextElement());
        }

        if (trustAnchors.isEmpty()) {
            return null;
        }

        return trustAnchors.toArray(new FullTrustAnchor[] {});

    }

}

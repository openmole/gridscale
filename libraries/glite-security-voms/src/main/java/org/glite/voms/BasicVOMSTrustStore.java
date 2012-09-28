package org.glite.voms;

import org.glite.voms.ac.ACTrustStore;
import org.apache.log4j.Logger;

import javax.security.auth.x500.X500Principal;
import java.util.*;
import java.io.IOException;
import java.io.File;
import java.security.cert.X509Certificate;

/**
 * @deprecated  This class does not expose the necessary information. Use
 * PKIStore instead.
 *
 * Implementation of a AC trust store for use with VOMS. The store
 * keeps an in-memory cache of issuer certificates, which can be
 * refreshed periodically.
 *
 * @author mulmo
 * @author Vincenzo Ciaschini
 */
public final class BasicVOMSTrustStore implements ACTrustStore {
    static Logger log = Logger.getLogger(BasicVOMSTrustStore.class);
    public static final String DEFAULT_TRUST_STORE_LISTING = PKIStore.DEFAULT_VOMSDIR;
    String trustedDirList = null;
    private Hashtable issuerCerts = new Hashtable();
    private long refreshPeriod = -1;
    private String path = null;
    private Timer theTimer = null;

    /**
     * Creates a default VOMS trust store. Equivalent to<br>
     * <code>new BasicVOMSTrustStore(DEFAULT_TRUST_STORE_LISTING, 300000);</code>
     */
    public BasicVOMSTrustStore() {
        this(DEFAULT_TRUST_STORE_LISTING, 300000);
    }

    /**
     * Creates and manages an in-memory cache of VOMS issuers by
     * periodically scanning a directory containing the trusted
     * issuers.
     *
     * If <code>refreshPeriod</code> is 0, it never refreshes.<br>
     *
     * @param trustedDirList directory listing containing trusted VOMS certs
     * @param refreshPeriod  refresh period in milliseconds
     *
     * @see org.glite.voms.DirectoryList
     */
    public BasicVOMSTrustStore(String trustedDirList, long refreshPeriod) {
        super();

        if (refreshPeriod < 0) {
            throw new IllegalArgumentException("refreshPeriod is negative");
        }

        List l;

        try {
            l = new DirectoryList(trustedDirList).getListing();
        } catch (IOException e) {
            l = null;
        }

        if ((l == null) || l.isEmpty()) {
            String msg = "VOMS trust anchors " + trustedDirList + " does not appear to exist";
            log.fatal(msg);
            throw new IllegalArgumentException(msg);
        }

        this.trustedDirList = trustedDirList;
        this.refreshPeriod = refreshPeriod;

        if (refreshPeriod == 0) {
            refresh();
        }

        if (refreshPeriod > 0) {
            theTimer = new Timer(true);
            theTimer.scheduleAtFixedRate(new BasicVOMSTrustStore.Refreshener(), 0, refreshPeriod);
        }
    }

    public String getDirList() {
        return trustedDirList;
    }


    public void stopRefresh() {
        if (theTimer != null)
            theTimer.cancel();
        theTimer = null;
    }

    /**
     * Refreshes the in-memory cache of trusted signer certificates.
     */
    public void refresh() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Refreshing in-memory VOMS issuer cache from " + trustedDirList);
            }

            Hashtable newTable = new Hashtable();
            List certs = new FileCertReader().readCerts(trustedDirList);

            for (Iterator i = certs.iterator(); i.hasNext();) {
                X509Certificate cert = (X509Certificate) i.next();
                Object key = cert.getSubjectX500Principal();
                List l = (List) newTable.get(key);

                if (l == null) {
                    l = new Vector();
                }

                l.add(cert);
                newTable.put(key, l);
            }

            issuerCerts = newTable;

            if (log.isDebugEnabled()) {
                log.debug("Refreshing of in-memory VOMS issuer cache done. Read " + certs.size() + " certs");
            }
        } catch (Exception e) {
            log.error("Unexpected error while refreshing in-memory VOMS issuer cache from " + trustedDirList + " : " +
                e.getMessage());
        }
    }

    /* (non-Javadoc)
     * @see org.glite.voms.ac.ACTrustStore#getAACandidate(org.glite.voms.ac.AttributeCertificate)
     */
    public X509Certificate[] getAACandidate(X500Principal issuer) {
        if (refreshPeriod < 0) {
            refresh();
        }

        List l = (List) issuerCerts.get(issuer);

        if (l != null) {
            return (X509Certificate[]) l.toArray(new X509Certificate[l.size()]);
        }

        return null;
    }

    private class Refreshener extends TimerTask {
        public void run() {
            refresh();
        }
    }
}

/** Lists all the files in the given directory that end with
 * a certain ending.
 */
class FileEndingIterator {
    static Logger logger = Logger.getLogger(org.glite.voms.FileEndingIterator.class.getName());

    /** The file ending.
     */
    protected String ending;

    /** A flag to show that there are more files that match.
     */
    protected boolean nextFound = false;

    /** The list of files in the directory.
     */
    protected File[] fileList;

    /** The index of the next match in the fileList.
     */
    protected int index = 0;

    /** Creates new FileIterator and searches the first match.
     * @param path The directory used for the file search.
     * @param ending The file ending to search for.
     */
    public FileEndingIterator(String path, String ending) {
        this.ending = ending;

        try {
            // open the directory
            File directory = (path.length() != 0) ? new File(path) : new File(".").getAbsoluteFile();

            // list the files and dirs inside
            fileList = directory.listFiles();

            // find the first match for the ending
            nextFound = findNext();
        } catch (Exception e) {
            logger.error("no files found from \"" + path + "\" error: " + e.getMessage());

            //            e.printStackTrace();
            return;
        }
    }

    /** Used to get the next matching file.
     * @return Returns the next matching file.
     */
    public File next() {
        if (nextFound == false) {
            return null;
        }

        File current = fileList[index++];

        nextFound = findNext();

        return current;
    }

    /** Used to check that there are more matching files to get
     * using next().
     * @return Returns true if there are more matching files.
     */
    public boolean hasNext() {
        return nextFound;
    }

    /** Finds the next matching file in the list of files.
     * @return Returns true if a matching file was found.
     */
    protected boolean findNext() {
        try {
            // search the next file with proper ending
            while ((index < fileList.length) &&
                    (fileList[index].isDirectory() || !fileList[index].getName().endsWith(ending))) {
                //               System.out.println("FileIterator::next: Skipping file " + fileList[index].getName());
                index++;
            }
        } catch (Exception e) {
            logger.error("Error while reading directory " + e.getMessage());

            //            e.printStackTrace(System.out);
            return false;
        }

        // check if the loop ended because of a match or because running out of choices.
        if (index < fileList.length) {
            return true;
        }

        return false;
    }
}
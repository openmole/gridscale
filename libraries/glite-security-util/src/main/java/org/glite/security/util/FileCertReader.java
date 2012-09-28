/*
 * Copyright (c) Members of the EGEE Collaboration. 2004. See
 * http://www.eu-egee.org/partners/ for details on the copyright holders.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.glite.security.util;

import org.apache.log4j.Logger;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;

import java.util.Iterator;
import java.util.Vector;

/**
 * Reads all certificates from given files, accepts binary form of DER encoded
 * certs and the Base64 form of the DER encoded certs (PEM). The base64 certs
 * can contain garbage in front of the actual certificate that has to begin with
 * "-----BEGIN". Should accept multiple certs in one file, not tested!
 * 
 * @author Joni Hahkala Created on March 27, 2002, 8:24 PM
 */
public class FileCertReader {
    /** Logging facility. */
    static Logger logger = Logger.getLogger(FileCertReader.class.getName());

    /** Buffer length for file reading. */
    static final int BUF_LEN = 1000;

    /** The carriage return char. */
    static final byte CARR = '\r';

    /** The newline char. */
    static final byte NL = '\n';

    /** The type for TrustAnchor */
    static final int TYPE_ANCHOR = 100;

    /** The type for certificate revocation list */
    static final int TYPE_CRL = 101;

    /** the type for X509 certificate */
    static final int TYPE_CERT = 102;

    /** sets up the provider unless already done */
    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** The certificate factory for reading the certificates. */
    CertificateFactory certFactory;

    /**
     * Creates a new instance of CertReader.
     * 
     * @throws CertificateException in case the certificate factory creation fails.
     */
    public FileCertReader() throws CertificateException {
        try {
            certFactory = CertificateFactory.getInstance("X.509", "BC");
        } catch (NoSuchProviderException e) {
            logger.error("Error while creating a FileCertReader: " + e.getMessage());
            throw new CertificateException("Error while creating a FileCertReader: " + e.getMessage());
        }
    }

    /**
     * Creates a new instance of CertReader with the specified provider.
     * 
     * @param provider the provider to be used in creating the certificates etc.
     * @throws CertificateException in case the certificate factory creation fails.
     */
    public FileCertReader(Provider provider) throws CertificateException {
        try {
            certFactory = CertificateFactory.getInstance("X.509", provider);
        } catch (Exception e) {
            logger.error("Error while creating a FileCertReader: " + e.getMessage());
            throw new CertificateException("Error while creating a FileCertReader: " + e.getMessage());
        }
    }

    /**
     * Creates a new instance of CertReader with the specified provider
     * 
     * @param provider the provider to be used in creating the certificates etc.
     * @throws CertificateException in case the certificate factory creation fails.
     */
    public FileCertReader(String provider) throws CertificateException {
        try {
            certFactory = CertificateFactory.getInstance("X.509", provider);
        } catch (Exception e) {
            logger.error("Error while creating a FileCertReader: " + e.getMessage());
            throw new CertificateException("Error while creating a FileCertReader: " + e.getMessage());
        }
    }

    /**
     * Reads the certificates from the files defined in the argument. See DirectoryList for file definition format.
     * 
     * @param files The file definition.
     * @return Returns the Vector of certificates read.
     * @throws IOException in case the certificate file reading fails.
     * @throws CertificateException in case the certificate factory creation fails.
     * @see org.glite.security.util.DirectoryList
     */
    @SuppressWarnings("unchecked")
	public Vector readCerts(String files) throws IOException, CertificateException {
        Vector certs = readFiles(files, TYPE_CERT);

        Iterator certIter = certs.iterator();

        logger.debug("read certs: ");

        while (certIter.hasNext()) {
            X509Certificate cert = (X509Certificate) certIter.next();
            logger.debug("Read cert: " + cert.getSubjectDN().toString());
        }
        
        if(certs.isEmpty()){
            throw new IOException("No certificates found in: " + files);
        }

        return certs;
    }

    /**
     * Reads the certificates from the files defined in the argument and makes TrustAnchors from them. See DirectoryList
     * for file definition format.
     * 
     * @param files The file definition.
     * @throws CertificateException Thrown if the certificate handling fails.
     * @return Returns a Vector of TrustAnchors read from the files.
     * @throws IOException Thrown in case the certificate reading fails.
     * @see org.glite.security.util.DirectoryList
     */
    @SuppressWarnings({"rawtypes" })
	public Vector readAnchors(String files) throws IOException, CertificateException {
        Vector anchors = readFiles(files, TYPE_ANCHOR);

        Iterator anchorIter = anchors.iterator();

        logger.debug("read TrustAnchors: ");

        while (anchorIter.hasNext()) {
            TrustAnchor anchor = (TrustAnchor) anchorIter.next();
            logger.debug("Read TrustAnchor: " + anchor.getTrustedCert().getSubjectDN().toString());
        }

        return anchors;
    }

    /**
     * @param files The file definition.
     * @return Returns a vector of CRLs read from the files.
     * @throws IOException Thrown if the CRL reading failed.
     * @throws CertificateException Thrown in case the CRL parsing or handling fails.
     * @see org.glite.security.util.DirectoryList
     */
    @SuppressWarnings({ "unchecked" })
	public Vector readCRLs(String files) throws IOException, CertificateException {
        Vector crls = readFiles(files, TYPE_CRL);

        Iterator crlIter = crls.iterator();

        logger.debug("read CRLs: ");

        while (crlIter.hasNext()) {
            X509CRL crl = (X509CRL) crlIter.next();
            logger.debug("Read CRL: " + crl.getIssuerDN().toString());
        }

        return crls;
    }

    /**
     * Reads the certificates or CRLs from the files defined by the first argument, see DirectoryList for file
     * definition format.
     * 
     * @param files The file definition.
     * @param type The type of things to read from the files. Currently supported are TYPE_ANCHOR, TYPE_CRL and
     *            TYPE_CERT defined in this class.
     * @throws CertificateException Thrown if the reading of files fails.
     * @return Returns a Vector of objects of type given that were read from the files given.
     * @see org.glite.security.util.DirectoryList
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
	private Vector readFiles(String files, int type) throws CertificateException {
        Vector storeVector = new Vector();

        try {
            // load CA certificates
            // System.out.println("Reading CA certificates");
            DirectoryList dir = new DirectoryList(files); // get the list of
                                                          // files matching
                                                          // CAFiles

            Iterator CAFileIter = dir.getListing().iterator();

            // create a iterator that returns inputsteam for all the files from
            // dir CAPath with ending CAEnding
            // FileEndingIterator CAFileIter = new FileEndingIterator(CAPath,
            // CAEnding);
            // go through the files
            while (CAFileIter.hasNext()) { // go through the files reading the
                                           // certificates

                File nextFile = (File) CAFileIter.next();

                storeVector.addAll(readFile(nextFile, type));
            }
        } catch (IOException e) {
            logger.warn("Error while reading certificates or CRLs: " + e.getMessage());

            // e.printStackTrace();
            throw new CertificateException("Error while reading certificates or CRLs: " + e.getMessage());
        }

        return storeVector;
    }

    /**
     * Reads the objects of given type from the File.
     * 
     * @param certFile The file to read.
     * @param type The type of objects to read form the file.
     * @throws IOException Thrown if the reading of objects of given type fails.
     * @return Returns the Vector of objects read form the file.
     */
    @SuppressWarnings("unchecked")
	public Vector readFile(File certFile, int type) throws IOException {
        // System.out.println("Opening " + nextFile.toString());
        BufferedInputStream binStream = null;
        Vector objects = new Vector();

        try {
            // get the buffered stream to facilitate marking
            binStream = new BufferedInputStream(new FileInputStream(certFile));

            while (binStream.available() > 0) {
                Object obj = objectReader(binStream, type);

                if (obj != null) {
                    objects.add(obj);
                }

                skipEmptyLines(binStream);
            }
        } catch (Exception e) {
            logger.warn("Error while reading certificates or crls from file " + certFile.toString() + " error was: "
                    + e.getMessage());

            // e.printStackTrace();
            throw new IOException(e.getMessage());
        } finally {
            if (binStream != null) {
                binStream.close();
            }
        }

        return objects;
    }

    /**
     * Reads a certificate or a CRL from the stream, doing some error correction.
     * 
     * @param binStream The stream to read the object from.
     * @param type The type of object to read from the stream.
     * @throws CertificateException Thrown if an error occurs while reading the object.
     * @throws IOException Thrown if an error occurs while reading the object.
     * @return Returns the object read.
     */
    public Object objectReader(BufferedInputStream binStream, int type) throws CertificateException, IOException {
        Object object = null;
        int errors = 0; // no errors in the beginning
        binStream.mark(10000);

        do { // try twice, first with plain file (reads binary and plain Base64
             // certificates,
            // second with skipping possible garbage in the beginning.

            try {
                if (errors == 1) { // if the first try failed, try if it was
                                   // because of garbage in the beginning
                    // before the actual base64 encoded certificate
                    errors = 2; // if this try fails, don't try anymore

                    skipToCertBeginning(binStream); // skip the garbage
                }

                binStream.mark(100000);

                /*
                 * System.out.println("The file contains:--------------");
                 * while(binStream.available()>0){ int num = binStream.read(b);
                 * System.out.println(new String(b, 0, num)); }
                 * System.out.println("The file ends-------------------");
                 */
                binStream.reset();

                // System.out.println("Reading certificate form file" +
                // nextFile.toString() + " Available bytes:" +
                // binStreamCA.available());
                object = readObject(binStream, type);
            } catch (Exception e) {
                if (errors != 0) { // if the error persists after first pass,
                                   // fail
                    // e.printStackTrace();
                    logger.warn("Certificate or CRL reading failed: " + e.getMessage());
                    throw new CertificateException("Certificate or CRL reading failed: " + e.getMessage());
                }

                // System.out.println("Trying again");
                errors = 1; // first try failed, try again with skipping
                binStream.reset(); // rewind the file to the beginning of this
                                   // try
            }
        } while (errors == 1); // try again after first try

        return object;
    }

    /**
     * Does the actual reading of the object.
     * 
     * @param binStream The stream to read the object from.
     * @param type The type of the object.
     * @throws CertificateException Thrown if there is a problem reading the object.
     * @return Returns the object read or null if no object was found.
     */
    public Object readObject(BufferedInputStream binStream, int type) throws CertificateException {
        Object obj;

        if (type == TYPE_CRL) { // reading certificate revocation lists

            try {
                obj = certFactory.generateCRL(binStream);
            } catch (CRLException e) {
                logger.warn("CRL loading failed: " + e.getMessage());
                throw new CertificateException(e.getMessage());
            }
        } else { // reading certs or trust anchors

            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(binStream); // try
                                                                                                 // to
                                                                                                 // read
                                                                                                 // the
                                                                                                 // certificate

            if (cert == null) {
                return null;
            }

            if (type == TYPE_ANCHOR) {
                // add the certificate to trustanchors, no name contstraints
                // (should add the nameconstraints!)
                obj = new TrustAnchor(cert, null);
            } else {
                if (type == TYPE_CERT) {
                    obj = cert;
                } else {
                    logger.warn("Internal error: Invalid data type " + type + " when trying to read certificate");
                    throw new CertificateParsingException("Internal error: Invalid data type " + type
                            + " when trying to read certificate");
                }
            }
        }

        return obj;
    }

    /**
     * Skips everything in front of "-----BEGIN" in the stream.
     * 
     * @param stream The stream to read and skip.
     * @throws IOException Thrown if there is a problem skipping.
     */
    static public void skipToCertBeginning(BufferedInputStream stream) throws IOException {
        byte[] b = new byte[BUF_LEN]; // the byte buffer
        stream.mark(BUF_LEN + 2); // mark the beginning

        while (stream.available() > 0) { // check that there are still something
                                         // to read

            int num = stream.read(b); // read bytes from the file to the byte
                                      // buffer
            String buffer = new String(b, 0, num); // generate a string from the
                                                   // byte buffer
            int index = buffer.indexOf("----BEGIN"); // check if the certificate
                                                     // beginning is in the
                                                     // chars read this time

            if (index == -1) { // not found
                // System.out.println("skipping:" + buffer);
                stream.reset(); // rewind the file to the beginning of the last
                                // read
                stream.skip(BUF_LEN - 100); // skip only part of the way as the
                                            // "----BEGIN" can be in the
                                            // transition of two 1000 char block
                stream.mark(BUF_LEN + 2); // mark the new position
            } else { // found

                while ((buffer.charAt(index - 1) == '-') && (index > 0)) { // search
                                                                           // the
                                                                           // beginnig
                                                                           // of
                                                                           // the
                                                                           // ----BEGIN
                                                                           // tag
                    index--;

                    if (index == 0) { // prevent charAt test when reaching the
                                      // beginning of buffer

                        break;
                    }
                }

                // System.out.println("Last skip:" + buffer.substring(0,
                // index));
                stream.reset(); // rewind to the beginning of the last read
                stream.skip(index); // skip to the beginning of the tag
                stream.mark(10000); // mark the position

                return;
            }
        }
    }

    /**
     * Skips empty lines in the stream.
     * 
     * @param stream The stream possibly containing empty lines.
     * @throws IOException Thrown if a problem occurs.
     */
    static public void skipEmptyLines(BufferedInputStream stream) throws IOException {
        byte[] b = new byte[BUF_LEN]; // the byte buffer
        stream.mark(BUF_LEN + 2); // mark the beginning

        while (stream.available() > 0) { // check that there are still something
                                         // to read

            int num = stream.read(b); // read bytes from the file to the byte
                                      // buffer

            int i = 0;

            while ((i < num) && ((b[i] == CARR) || (b[i] == NL))) {
                i++;
            }

            stream.reset();
            stream.skip(i);

            if (i < num) {
                stream.mark(10000);

                return;
            }
			stream.mark(BUF_LEN);
        }
    }
    
    /**
     * Reads a certificate chain from the stream.
     * 
     * @param stream The stream to read certificates from.
     * @return The certificates that were read.
     * @throws IOException In case the reading failed.
     */
    public Vector<X509Certificate> readCertChain(BufferedInputStream stream) throws IOException{
        Vector<X509Certificate> chain = new Vector<X509Certificate>();

        try {
            do {
                X509Certificate userCert = (X509Certificate) objectReader(stream, TYPE_CERT);

                // System.out.println(proxyCert.getSubjectDN());
                if (userCert != null) {
                    chain.add(userCert);
                }
            } while (stream.available() > 0);
        } catch (Exception e) {
            logger.warn("Invalid certificate chain, certificate unreadable. Cause: " + e.getMessage());
            throw new IOException("Invalid certificate chain, certificate unreadable. Cause: " + e.getMessage());
        }
        
        return chain;
    }

    /**
     * Reads a grid proxy from a stream.
     * 
     * @param stream  the stream to read the proxy from.
     * @param storePasswd  the password used for the private key if any.
     * @return a keystore containing the credentials read. The credentials are stored under alias "host".
     * @throws IOException in case of error.
     */
    @SuppressWarnings("deprecation")
    public KeyStore readProxy(BufferedInputStream stream, String storePasswd) throws IOException {
        try {
            stream.mark(10000);

            X509Certificate proxyCert = (X509Certificate) objectReader(stream, TYPE_CERT);

            stream.mark(10000);

            PrivateKey privateKey = PrivateKeyReader.read(stream);

            Vector<X509Certificate> beforeChain = readCertChain(stream);

            KeyStore store = KeyStore.getInstance("JKS");

            beforeChain.insertElementAt(proxyCert, 0);

            X509Certificate[] chain = beforeChain.toArray(new X509Certificate[] {});

            store.load(null, null);

            store.setKeyEntry("host", privateKey, storePasswd.toCharArray(), chain);

            return store;
        } catch (IOException e) {
            logger.error("Proxy certificate reading failed: " + e.getMessage());
            throw new IOException("Proxy certificate reading failed: " + e.getMessage());
        } catch (KeyStoreException e) {
            logger.error("Keystore generation failure: " + e.getMessage());
            throw new IOException("Keystore generation failure: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            logger.error("Unsupported algorithtm used in a proxy: " + e.getMessage());
            throw new IOException("Unsupported algorithtm used in a proxy: " + e.getMessage());
        } catch (CertificateException e) {
            logger.error("Invalid certificate in a proxy: " + e.getMessage());
            throw new IOException("Invalid certificate in a proxy: " + e.getMessage());
        }
    }
}

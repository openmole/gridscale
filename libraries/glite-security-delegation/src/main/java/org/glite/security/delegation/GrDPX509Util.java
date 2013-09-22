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

package org.glite.security.delegation;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
//import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.glite.security.SecurityContext;
import org.glite.security.delegation.storage.GrDPStorageFactory;
import org.glite.security.util.PrivateKeyReader;
import org.glite.voms.VOMSValidator;
//import org.glite.voms.PKIStore;
//import org.glite.voms.ac.ACValidator;


/**
 * Utility to manage X509 certificates
 *
 * @author Mehran Ahsant 
 * @author Akos Frohner <Akos.Frohner@cern.ch>
 * @author Joni Hahkala
 */
public class GrDPX509Util {
    private final static Logger LOGGER = Logger.getLogger(GrDPX509Util.class);
    public static final String CERT_CHAIN_CONTENT_TYPE = "application/x-x509-user-cert-chain";
    public static final String CERT_REQ_CONTENT_TYPE = "application/x-x509-cert-request";
    private static MessageDigest s_digester = null;
    /** static PKIStore to avoid initializing it for every request */
//    private static PKIStore s_trustStore;
    /** The path to the CA trust directory.*/
//    private static final String CA_PATH_PROPERTY = "CA_PATH";
    /** the default CA trust directory. */
//    private static final String CA_PATH_DEFAULT = "/etc/grid-security/certificates/";
    
    

    static {
        LOGGER.info("initalizing ACValidator");
        try{
            s_digester = MessageDigest.getInstance("SHA-1");
        }catch(Exception e){
            LOGGER.fatal("Message digester implementation not found: " + e.getMessage(), e);
            throw new RuntimeException("Delegation utilities code initialization failed: " + e.getMessage(), e);
        }
        
/*        String location = System.getProperty(CA_PATH_PROPERTY, CA_PATH_DEFAULT);
        
        try {
            LOGGER.info("initalizing pkistore");
            s_trustStore = new PKIStore(location, PKIStore.TYPE_CADIR, true);
            LOGGER.info("initalized");
        } catch (CertificateException e) {
            LOGGER.fatal("Voms trustStore initialization failed: " + e.getMessage(), e);
            throw new RuntimeException("Voms trust anchors loading failed: " + e.getMessage());
        } catch (CRLException e) {
            LOGGER.fatal("Voms trustStore initialization failed: " + e.getMessage(), e);
            throw new RuntimeException("Voms trust anchors loading failed: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.fatal("Voms trustStore initialization failed: " + e.getMessage(), e);
            throw new RuntimeException("Voms trust anchors loading failed: " + e.getMessage());
        }
*/
    }
//------------------- following code is deprecated and will be removed
// TODO: marker
    /**
     * Generate a PEM encoded string of certificate from a header and a footer
     * @param bytes input stream
     * @param hdr Header delimeter of certificate
     * @param ftr footer delimeter of certificate
     * @return encoded byte in pem
     * @throws IOException
	 * @deprecated Use org.bouncycastle.openssl.PEMWriter
     */
    public static String writePEM(byte[] bytes, String hdr, String ftr) {
    	
        StringBuffer buff = new StringBuffer();
        byte[] pemBytes = Base64.encode(bytes);
        buff.append(hdr);

        int n = 0;

        while (n < pemBytes.length) {
            if ((pemBytes.length - n) < 64) {
                buff.append(new String(pemBytes, n, pemBytes.length - n));
            } else {
                buff.append(new String(pemBytes, n, 64));
            }

            buff.append("\n");
            n = n + 64;
        }

        buff.append(ftr);

        return buff.toString();
    }

    /**
     * Read a PEM encoded base64 stream and decode it
     * @param is Base64 PEM encoded stream
     * @param hdr Header delimeter
     * @param ftr Footer delimeter
     * @return decoded DER bytes
     * @throws IOException if a read error occurs
     * @deprecated Use org.glite.security.util.FileCertReader
     */
    public static byte[] readPEM(InputStream is, String hdr, String ftr)
        throws IOException {
        InputStreamReader irr = new InputStreamReader(is);
        BufferedReader r = new BufferedReader(irr);

        StringBuffer buff = new StringBuffer();
        String line;
        boolean read = false;

        while ((line = r.readLine()) != null) {
            if (line.equals(hdr)) {
                read = true;

                continue;
            }

            if (line.equals(ftr)) {
                read = false;
            }

            if (read) {
                buff.append(line);
            }
        }

        return Base64.decode(buff.toString().getBytes());
    }

    /**
     * Read a PEM encoded base64 stream and decode it
     * @param in Base64 PEM encoded string
     * @param hdr Header delimeter
     * @param ftr Footer delimeter
     * @return decoded DER bytes
     * @throws IOException if a read error occurs
     * @deprecated Use org.bouncycastle.openssl.PEMWriter
     */
    public static byte[] readPEM(String in, String hdr, String ftr) {
        int hdrIndex = in.indexOf(hdr);
        hdrIndex += hdr.length();
        int ftrIndex = in.indexOf(ftr, hdrIndex);

        return Base64.decode(in.substring(hdrIndex, ftrIndex).getBytes());
    }

    /**
     * Create an X509 Certificate DN
     * @param organization Organization
     * @param orgUnit Organization Unit
     * @param commonName X509 Common Name
     * @param country Country
     * @param email Email address
     * @return X509Name of generated DN
     * @deprecated Use org.glite.security.util.proxy.ProxyCertificateGenerator
     */
    public static X509Name makeGridCertDN(String organization, String orgUnit,
        String commonName, String country, String email) {
        Hashtable attrs = new Hashtable();
        attrs.put(X509Name.O, organization);
        attrs.put(X509Name.OU, orgUnit);
        attrs.put(X509Name.C, country);
        attrs.put(X509Name.EmailAddress, email);
        attrs.put(X509Name.CN, commonName);

        X509Name x509Name = new X509Name(attrs);

        LOGGER.debug("GrDPX509Util : " + x509Name.toString());

        return x509Name;
    }

    /**
     * Create an X509 Certificate DN
     * @param DN The client's distiungished name.
     * @return X509Name of DN
     * @deprecated Use org.glite.security.util.proxy.ProxyCertificateGenerator
     */
    public static X509Name makeGridCertDN(String DN) {
        X509Name x509Name = new X509Name(DN);
        LOGGER.debug("GrDPX509Util : " + x509Name.toString());

        return x509Name;
    }

    /**
     * Save a certificate request in specific location
     * @param certReq given certificate request to save
     * @param fileLocation location of certificare request
     * @throws IOException
     * @deprecated Use delegation storage, don't write to file.
     */
    public static void saveCertReqToFile(String certReq, String fileLocation)
        throws IOException {
        FileOutputStream os = new FileOutputStream(fileLocation);
        os.write(certReq.getBytes());
        os.close();
    }

    /**
     * save a proxy certificate in specific location
     * @param certProxy Given proxy certificate to save
     * @param fileLocation location of proxy certificate
     * @deprecated use org.glite.security.util.proxy.ProxyCertificateGenerator
     */
    public static void saveCertProxyTofile(X509Certificate certProxy,
        String fileLocation) {
        try {
            OutputStream os = new FileOutputStream(fileLocation);

            if (!changeFileMode(fileLocation, 600)) {
                LOGGER.error(
                    "Warning: Please check file permissions for your proxy file.");
            }

            String s = GrDPX509Util.writePEM(certProxy.getEncoded(),
                    GrDPConstants.CH + GrDPConstants.NEWLINE,
                    GrDPConstants.CF + GrDPConstants.NEWLINE);
            os.write(s.getBytes());
            os.close();
        } catch (IOException ie) {
            LOGGER.error("Error saving certt to file" + ie.getMessage());
        } catch (CertificateEncodingException e) {
            LOGGER.error("Error writePEM " + e.getMessage());
        }
    }

    /**
     * save a proxy certificate in specific location
     * @param certProxy Given proxy certificate to save
     * @param fileLocation location of proxy certificate
     * @param delegationID
     * @param userDN
     * @deprecated use org.glite.security.util.proxy.ProxyCertificateGenerator.
     */
    public static void saveCertProxyTofile(String inCertProxy,
        String fileLocation, String delegationID, String userDN, @SuppressWarnings("unused") boolean append) {
        String crt = null;
        String certProxyChain = null;
        String ENDCERT = GrDPConstants.CF;
        String certProxy = inCertProxy;

        try {
            RandomAccessFile f = new RandomAccessFile(fileLocation, "rw");
            byte[] privateKeyByte = new byte[(int) f.length()];
            f.read(privateKeyByte);
            f.seek(0);
            certProxy = delegationID + "\n" +
                
                //userDN.replaceAll("," + GrDPConstants.CNPROXY, "\0") + "\n" +
                userDN.replaceAll(GrDPConstants.CNPROXY + ",", "") + "\n" +
                certProxy;
            crt = certProxy.substring(0,
                    certProxy.indexOf(ENDCERT) + ENDCERT.length() + 1);
            certProxyChain = certProxy.substring(certProxy.indexOf(ENDCERT) +
                    ENDCERT.length(), certProxy.length());
            f.writeBytes(crt);
            f.write(privateKeyByte);
            f.writeBytes(certProxyChain);
            f.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!changeFileMode(fileLocation, 600)) {
            LOGGER.error(
                "Warning: Please check file permissions for your proxy file.");
        }
    }

    /**
     * save a private key in specific location
     * @param pk Given private  key to save
     * @param fileLocation location of private key
     * @param delegationID the ID of the delegation
     * @param userDN the DN of the client's certificate
     * @deprecated Use delegation storage.
     */
    public static void savePrivateKey(PrivateKey pk, String fileLocation,
        String delegationID, String userDN) 
    	throws FileNotFoundException, IOException {
        String prvkey = null;

        prvkey = GrDPX509Util.writePEM(PrivateKeyReader.getEncoded(pk),
        		GrDPConstants.PRVH + GrDPConstants.NEWLINE,
        		GrDPConstants.PRVF + GrDPConstants.NEWLINE);
        prvkey = delegationID + "\n" +
        	userDN.replaceAll("," + GrDPConstants.CNPROXY, "\0") + "\n" +
        	prvkey;

            FileOutputStream os = new FileOutputStream(fileLocation);
            os.write(prvkey.getBytes());
            os.close();
    }

    /**
     * Search for a generated proxy in cache
     * @param strDirCache cache directory
     * @param delegationID Delegation ID
     * @param userDN UserDN
     * @return File name of proxy
     * @deprecated Use delegation storage.
     */
    public static String findProxyInCache(String strDirCache,
        String delegationID, String userDN) {
        File dir = new File(strDirCache);
        FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir1, String name) {
                    return !name.startsWith(".");
                }
            };

        String[] proxyfilesList = dir.list(filter);

        if (proxyfilesList == null) {
            LOGGER.error("Error : No file in proxy cache");
        } else {
            for (int i = 0; i < proxyfilesList.length; i++) {
                String filename = proxyfilesList[i];

                try {
                    BufferedReader in = new BufferedReader(new FileReader(dir.getPath() +
                                "/" + filename));

                    if ((in.readLine()).equals(delegationID)) {
                        if ((in.readLine().equals(userDN))) {
                            in.close();

                            //return (dir.getPath()+"/"+filename);
                            return (filename);
                        }
                    }

                    in.close();
                } catch (IOException e) {
                    LOGGER.error("Error in reading proxy file");
                }
            }
        }

        return null;
    }

    /**
     * Search for associated private key in cache
     * @param strDirCache cache directory
     * @param delegationID Delegation ID
     * @param userDN UserDN
     * @return File name of private key
     * @deprecated Use delegation storage.
     */
    public static String findPrivateKeyInCache(String strDirCache, String delegationID, String userDN) {
        File dir = new File(strDirCache);
        FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir1, String name) {
                    return name.startsWith(".");
                }
            };

        String[] proxyfilesList = dir.list(filter);

        if (proxyfilesList == null) {
            LOGGER.error("Error : No private key file in proxy cache");
        } else {
            for (int i = 0; i < proxyfilesList.length; i++) {
                String filename = proxyfilesList[i];

                try {
                    BufferedReader in = new BufferedReader(new FileReader(dir.getPath() +
                                "/" + filename));

                    //while ((str = in.readLine()) != null) {
                    if ((in.readLine().equals(delegationID))) {
                        if ((in.readLine().equals(userDN))) {
                            in.close();

                            //return (dir.getPath()+"/"+filename);
                            return (filename);
                        }
                    }

                    in.close();
                } catch (IOException e) {
                    LOGGER.error("Error in reading private key file");
                }
            }
        }

        return null;
    }

    /**
     * Load x509 certificate
     * @param cert certificate to load
     * @return X509 Certificate
     * @throws IOException
     * @throws GeneralSecurityException
     * @deprecated Use delegation storage or org.glite.security.util.FileCertReader.
     */
    public static X509Certificate loadCertificate(InputStream cert)
        throws NoSuchProviderException {
        X509Certificate certificate = null;

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
            certificate = (X509Certificate) cf.generateCertificate(cert);
        } catch (CertificateException e) {
            e.printStackTrace();
        }

        return certificate;
    }

    /**
     * Load chain of certificates from byte
     * @param bCerts
     * @return Array of loaded certificates
     * @throws IOException
     * @throws GeneralSecurityException
     * @deprecated Use delegation storage or org.glite.security.util.FileCertReader.
     */
    public static X509Certificate[] loadCertificateChain(byte[] bCerts)
        throws IOException, CertificateException, NoSuchProviderException {

        return loadCertificateChain(
        		new BufferedInputStream(
        				new ByteArrayInputStream(bCerts)));
    }

    /**
     * Load a chain of certificates from BIS
     * @param bisCerts
     * @return Array of loaded certificates
     * @throws IOException
     * @throws GeneralSecurityException
     * @deprecated Use delegation storage or org.glite.security.util.FileCertReader.
     */
    public static X509Certificate[] loadCertificateChain(
        BufferedInputStream bisCerts)
        throws IOException, CertificateException, NoSuchProviderException {
        Vector certVector = new Vector();

        CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");

        while (bisCerts.available() > 0) {
        	//certificate[index++] = (X509Certificate) cf.generateCertificate(bisCerts);
            certVector.add(cf.generateCertificate(bisCerts));
       }
        
        X509Certificate[] certificate = new X509Certificate[certVector.size()];
        certVector.copyInto(certificate);

        return certificate;
    }

    /**
     * Reconstruct a certificate request from a PEM encoded string.
     * @param request BASE64 PEM encoded string
     * @return certificate request
     * @deprecated Use delegation storage or org.glite.security.util.FileCertReader.
     */
    public static PKCS10CertificationRequest loadCertificateRequest(String request) {
        return new PKCS10CertificationRequest(readPEM(request,
                GrDPConstants.CRH + GrDPConstants.NEWLINE,
                GrDPConstants.CRF + GrDPConstants.NEWLINE));
    }
    
    /**
     * Returns converted byte array input to hex value
     * @param input
     * @return Hex value
     * @deprecated Use org.bouncycastle.util.encoders.Hex
     */
/*    static private String convertToHex(byte[] input) {
        StringBuffer result = new StringBuffer();
        char[] digits = {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c',
                'd', 'e', 'f'
            };

        for (int index = 0; index < input.length; ++index) {
            byte b = input[index];
            result.append(digits[(b & 0xf0) >> 4]);
            result.append(digits[b & 0x0f]);
        }

        return result.toString();
    }
*/
    /**
     * Reading IO file in byte
     * @param file File name
     * @return File contents in byte
     * @throws IOException
     * @deprecated use relevant functions in util-java or bouncycastle.
     */
    public static byte[] getFilesBytes(File file) throws IOException {
        InputStream is = new FileInputStream(file);
        long length = file.length();

        if (length > Integer.MAX_VALUE) {
            LOGGER.error("getFilesBytes :: File is too long to read !");
        }

        byte[] bytes = new byte[(int) length];
        int offset = 0;
        int numRead = 0;

        while ((offset < bytes.length) &&
                ((numRead = is.read(bytes, offset, bytes.length - offset)) >= 0)) {
            offset += numRead;
        }

        if (offset < bytes.length) {
            throw new IOException("Uncomplete file reading  " + file.getName());
        }

        is.close();

        return bytes;
    }

    /**
     * Convert array of x509certificates into byte format of PEMs
     * @param x509Cert
     * @return x509Certificates in byte format
     * @deprecated use org.glite.security.util.proxy.ProxyCertificateGenerator.
     */
    public static byte[] certChainToByte(X509Certificate[] x509Cert) 
        throws CertificateEncodingException {
        String strX509CertChain = "";

        for (int index = 0; index < x509Cert.length; ++index) {
            strX509CertChain = strX509CertChain +
                GrDPX509Util.writePEM(x509Cert[index].getEncoded(),
                    GrDPConstants.CH + GrDPConstants.NEWLINE,
                    GrDPConstants.CF + GrDPConstants.NEWLINE);
            LOGGER.debug("CertRequestHandler : Generated proxyCertificate" +
                strX509CertChain);
        }


        return strX509CertChain.getBytes();
    }

// TODO: marker
//-------------------    End of old code to remove

    /**
     * A synchronizer wrapper for the static digester, only access it through this utility method.
     *  
     * @param input The bytes to digest.
     * @return the digested bytes.
     */
    public static synchronized byte[] digest(byte[] input){
//        GrDPX509Util utils = new GrDPX509Util();
        return s_digester.digest(input);
    }

    /**
     * Change the access mode of a file in the filesystem (!!! system specific !!!).
     * 
     * @param file Location of the file to be changed.
     * @param mode New mode for the file.
     * @return True if file mode has changed.
     */
    public static boolean changeFileMode(String file, int mode) {
        Runtime runtime = Runtime.getRuntime();
        String[] cmd = new String[] { "chmod", String.valueOf(mode), file };

        try {
            Process process = runtime.exec(cmd, null);

            return (process.waitFor() == 0) ? true : false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retrieves the location of the user cert file.
     * from X509_USER_CERT.
     * @return String the location of the user cert file
     */
    public static String getDefaultCertFile() {
        String location = null;
        location = System.getProperty("X509_USER_CERT");

        return location;
    }

    /**
     * Retrieves the location of the user key file.
     * from X509_USER_KEY.
     * @return String the location of the user key file
     */
    public static String getDefaultKeyFile() {
        String location = null;
        location = System.getProperty("X509_USER_KEY");

        return location;
    }

    /**
     * Retrieves the location of the CA cert files.
     * from X509_CERT_DIR.
     * @return String the locations of the CA certificates
     */
    public static String getDefaultCertLocation() {
        String location = null;
        location = System.getProperty("X509_CERT_DIR");

        return location;
    }

    /**
     * Retrieves the location of the proxy file.
     * from X509_USER_PROXY.
     * @return String the location of the proxy file
     */
    public static String getDefaultProxyFile() {
        String location;
        location = System.getProperty("X509_USER_PROXY");

        return location;
    }

    /**
     * Returns SHA1 hash digest of file name based on given delegationID and DER encoded DN
     * in form of SHA1_HASH(DelegationID)+"-"+SHA1_HASH(DN)
     * @param delegationid_in delegationID of proxy certificate
     * @param DN_in DER encoded DN
     * @return Digested file name
     */
    public static String digestFileName(String delegationid_in, String DN_in) {
        byte[] dgstDlgID = null;
        byte[] dgstDN = null;
        String filenameP1 = null;
        String filenameP2 = "-";
        String filenameP3 = null;

        dgstDlgID = digest(delegationid_in.getBytes());
        dgstDlgID = get8MostSignificant(dgstDlgID);
        filenameP1 = new String(Hex.encode(dgstDlgID));

        LOGGER.debug("DN TO DIGEST : " + DN_in.replaceAll(GrDPConstants.CNPROXY + ",", ""));

        dgstDN = digest((DN_in.replaceAll(GrDPConstants.CNPROXY + ",", "")).getBytes());

        dgstDN = get8MostSignificant(dgstDN);
        filenameP3 = new String(Hex.encode(dgstDN));

        // result = filename.digest( randomNum.getBytes() );

        LOGGER.debug("Digest of file name : " + filenameP1 + filenameP2 + filenameP3);

        return filenameP1 + filenameP2 + filenameP3;
    }

    /**
     * Returns 8 most significant bytes of byte array
     * @param input input byte array
     * @return 8 MS bytes
     */
    private static byte[] get8MostSignificant(byte[] input) {
        byte[] result = new byte[8];

        for (int i = 0; i <= 7; ++i)
            result[i] = input[i];

        return result;
    }

    /**
     * Returns 'n' most significant bytes of byte array
     * @param input input byte array
     * @return 'n' MS bytes
     */
    private static byte[] getMostSignificant(byte[] input, int n) {
        byte[] result = new byte[n];

        for (int i = 0; i <= n-1; ++i)
            result[i] = input[i];

        return result;
    }

    /**
     * Returns a certificate request in HTTP MIME type format
     * @param certReq certificate request to response
     * @return http response format
     */
    public static String certReqResponse(String certReq) {
        // Constructing HTTP message headers.
        StringBuffer buffer = new StringBuffer();
        
        buffer.append("HTTP/1.1 200 ok\r\n");
        buffer.append("Content-type: " + CERT_REQ_CONTENT_TYPE + "\r\n\r\n");
        buffer.append(certReq);

        return buffer.toString();
    }

    /**
     * Returns a proxy certificate in HTTP MIME type format
     * @param proxyCert proxy certificate to response
     * @return http response format
     */
    public static String certProxyResponse(String proxyCert) {
        // Constructing HTTP message headers.
        StringBuffer buffer = new StringBuffer();
        
        buffer.append("HTTP/1.1 200 ok\r\n");
        buffer.append("Content-type: " + CERT_CHAIN_CONTENT_TYPE + "\r\n\r\n");
        buffer.append(proxyCert);

        return buffer.toString();
    }

    /**
     * Makes an HTTP error message out of the error message.
     * @param errorMsg to send
     * @return The HTTP error message.
     */
    public static String errorResponse(String errorMsg) {
        // Constructing HTTP message headers.
        StringBuffer buffer = new StringBuffer();
        
        buffer.append("HTTP/1.1 " + errorMsg + "\r\n");
        buffer.append("\r\n");

        return buffer.toString();
    }

    /**
     * Retrieve the path to the delegatee property file
     * @return Path to the porperty file
     */
    public static String getDlgeePropertyFile() {
        String dlgeePropertyFile = null;
        dlgeePropertyFile = System.getProperty("GLITE_DLGEE_PROPERTY", "dlgee.properties");

        LOGGER.debug("GLITE_DLGEE_PROPERTY : " + dlgeePropertyFile);
        
        return dlgeePropertyFile;
    }

    /**
     * Retrieve the path to the delegator property file
     * @return Path to the porperty file
     */
    public static String getDlgorPropertyFile() {
        String dlgorPropertyFile = null;
        dlgorPropertyFile = System.getProperty("GLITE_DLGOR_PROPERTY", "dlgor.properties");

        return dlgorPropertyFile;
    }

    /**
     * Get the factory to create storage instances.
     * 
     * @param factoryClass The full name of the class implementing the storage factory.
     * @return A factory for creating storage object instances.
     * @throws ClassNotFoundException Could not find the specified class in classpath
     * @throws NoSuchMethodException Failed to instantiate a factory object
     * @throws InvocationTargetException Failed to instantiate a factory object
     * @throws IllegalAccessException Failed to instantiate a factory object
     * @throws InstantiationException Failed to instantiate a factory object
     */
    @SuppressWarnings("unused")
    public static GrDPStorageFactory getGrDPStorageFactory(String factoryClass) throws ClassNotFoundException,
            NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        LOGGER.debug("Entered getGrDStorage.");

        // Get the class references for the helper and DBManager objects
        Class storageClass = Class.forName(factoryClass);
        LOGGER.debug("Successfully loaded class '" + factoryClass + "'");

        // Create a new helper object instance and return
        return (GrDPStorageFactory)storageClass.newInstance();
    }
    
    /**
     * Create a new certificate request.
     * 
     * @param subjectDN The dn to include in the certificate request.
     * @param sigAlgName The algorithm to be used.
     * @param keyPair The keypair to include in the certificate.
     * @return A PEM encoded certificate request.
     * @throws GeneralSecurityException Failed to generate the certificate request.
     * @deprecated use the method with certificate input instead to avoid problems with DN encoding.
     */
    public static String createCertificateRequest(X509Name subjectDN,
            String sigAlgName, KeyPair keyPair) throws GeneralSecurityException {
        
        PKCS10CertificationRequest certRequest = new PKCS10CertificationRequest(sigAlgName, subjectDN,
                keyPair.getPublic(), null, keyPair.getPrivate());
        
        StringWriter stringWriter = new StringWriter();
        PEMWriter pemWriter = new PEMWriter(stringWriter);
        try {
			pemWriter.writeObject(certRequest);
			pemWriter.flush();
		} catch (IOException e) {
			throw new GeneralSecurityException("Certificate output as string failed: " + e.getMessage());
		}
        
        return stringWriter.toString();
    }
   
    /**
     * Create a new certificate request.
     * 
     * @param subjectDN The dn to include in the certificate request.
     * @param sigAlgName The algorithm to be used.
     * @param keyPair The keypair to include in the certificate.
     * @return A PEM encoded certificate request.
     * @throws GeneralSecurityException Failed to generate the certificate request.
     */
    public static String createCertificateRequest(X509Certificate subjectCert,
            String sigAlgName, KeyPair keyPair) throws GeneralSecurityException {
        
        PKCS10CertificationRequest certRequest = new PKCS10CertificationRequest(sigAlgName, subjectCert.getSubjectX500Principal(),
                keyPair.getPublic(), null, keyPair.getPrivate());
        
        StringWriter stringWriter = new StringWriter();
        PEMWriter pemWriter = new PEMWriter(stringWriter);
        try {
            pemWriter.writeObject(certRequest);
            pemWriter.flush();
        } catch (IOException e) {
            throw new GeneralSecurityException("Certificate output as string failed: " + e.getMessage());
        }
        
        return stringWriter.toString();
    }
   
    /**
     * Generate a new key pair.
     * 
     * @return The generated KeyPair object.
     */
    public static KeyPair getKeyPair(int size) {
        try {
            SecureRandom rand = new SecureRandom();
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA", new BouncyCastleProvider());
            //JDKKeyPairGenerator.RSA keyPairGen = new JDKKeyPairGenerator.RSA();
            keyPairGen.initialize(size, rand);
            return keyPairGen.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Generates a new session ID based on the public key.
     * @param pk public key of a certificate (request)
     * @return The generated session ID
     */
    @SuppressWarnings("unused")
    public static String generateSessionID(PublicKey pk) throws java.security.NoSuchAlgorithmException {
       return new String(Hex.encode(getMostSignificant(digest(pk.getEncoded()), 20)));

    }
    
    /**
     * Generates a new delegation ID starting from the given DN and list of VOMS attributes.
     * 
     * @param dn The dn to be used in the hashing process.
     * @param vomsAttributes The list of attributes to be used in the hashing process.
     * @return The generated delegation ID.
     */
    public static String genDlgID(String dn, String[] vomsAttributes) {

    	String originalString = dn;
        if (vomsAttributes != null) {
            for (int i = 0; i < vomsAttributes.length; i++) {
                originalString += vomsAttributes[i];
            }
        } else {
            LOGGER.debug("No VOMS attributes in client certificate. Generating DLG ID using" + "only the client DN.");
        }

        String digestString = new String(Hex.encode(getMostSignificant(digest(originalString.getBytes()), 20)));
        LOGGER.debug("Digest VOMS Attributes: " + digestString);

    	return digestString;
    	
    }
    
    /**
     * Returns the list of VOMS attributes exposed in the given SecurityContext.
     * 
     * @param sc The SecurityContext object from which to take the attributes
     * @return A String list containing the attributes. Empty (0 element) array if no attributes.
     */
    public static String[] getVOMSAttributes(SecurityContext sc) {
        // TODO: should use static truststores or something to avoid initializing them for each request.
        VOMSValidator validator = new VOMSValidator(sc.getClientCertChain());
        String attributes[] =  validator.validate().getAllFullyQualifiedAttributes();
        // kill the truststrores.
        validator.cleanup();
        return attributes;
    }
    
    /**
     * Returns a single string representation of the VOMS attributes list.
     * @param vomsAttributes The VOMS attributes array
     * @return A single string representation of the VOMS attributes list
     */
    public static String toStringVOMSAttrs(String[] vomsAttributes) {
        if(vomsAttributes == null) {
            return "";
        }
        
        String vomsAttrsStr = "";
        for(int i=0; i < vomsAttributes.length; i++) {
            vomsAttrsStr+= "\t" + vomsAttributes[i];
        }
        
        return vomsAttrsStr;
    }
    
    /**
     * Returns the list of VOMS attributes from a single string representation.
     * @param vomsAttributesStr A single string representation of a VOMS attributes list.
     * @return A string array containing the VOMS attributes 
     */
    public static String[] fromStringVOMSAttrs(String vomsAttributesStr) {
        if(vomsAttributesStr == null) {
            return new String[0];
        }

        StringTokenizer st = new StringTokenizer("\t");
        ArrayList vomsAttributes = new ArrayList();
        
        while(st.hasMoreTokens()) {
            vomsAttributes.add(st.nextToken());
        }
        
        return (String[])vomsAttributes.toArray(new String[] {});
    }
    
}

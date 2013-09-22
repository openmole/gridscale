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

import org.apache.log4j.Logger;

import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKeyStructure;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.bouncycastle.util.encoders.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import org.bouncycastle.asn1.ASN1Primitive;

/**
 * This class is used to read and write private keys.
 * 
 * @author Joni Hahkala Created on April 8, 2002, 9:26 PM
 */
public class PrivateKeyReader {
    /** The logging facility. */
    private static final Logger LOGGER = Logger.getLogger(PrivateKeyReader.class);

    /** The carriage return char. */
    private static final byte CARR = '\r';

    /** The newline char. */
    private static final byte NL = '\n';

    /** The length of internal buffer. */
    private static final int BUF_LEN = 1000;

    /** The length of marking. */
    private static final int MARK_LEN = 10000;

    /** The length of the lines when creating the pem encoded string out of the private key. */
    private static final int LINE_LEN = 64;

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Reads the private key form the stream that was given in the constructor.
     * After the reading, the file mark is at the end of the key.
     * 
     * @param bin The BufferedInputStream to read the key from.
     * @param finder The password finder class to use for requesting the password.
     * @return Returns the private key read.
     * @throws IOException Thrown if the key reading fails.
     * @deprecated use rather the read method with reader input.
     */
    public static PrivateKey read(final BufferedInputStream bin, final PasswordFinder finder) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(bin));
        return read(reader, finder);
    }

    /**
     * Reads the private key form the stream that was given in the constructor.
     * After the reading, the file mark is at the end of the key.
     * 
     * @param bin The BufferedInputStream to read the key from.
     * @param finder The password finder class to use for requesting the password.
     * @return Returns the private key read.
     * @throws IOException Thrown if the key reading fails.
     */
    public static PrivateKey read(final BufferedReader reader, final PasswordFinder finder) throws IOException {
        try {
//            TestBase.printInterval("start keyreader");
            reader.mark(MARK_LEN);
            String line = null;
            while ((line = reader.readLine()) != null){
                // see if beginning tag of private key is not found
                if(line.indexOf("-----BEGIN ") == -1 || line.indexOf("PRIVATE KEY") == -1){
                    reader.mark(MARK_LEN);
                } else{
                    // go back to the beginning of the line with -----BEGIN...
                    reader.reset();
                    break;
                }
            }
            
            
            
//            TestBase.printInterval("setup");
            PEMReader pemReader = new PEMReader(reader, finder, "BC");
//            TestBase.printInterval("setup reader");
            Object o = pemReader.readObject();
//            TestBase.printInterval("read key");
            
            // skip possible certificates in the file.
            while (o instanceof X509Certificate){
                o = pemReader.readObject();            	
            }
            
            if(o == null){
                reader.reset();
                
                // try backup pkcs#8 parser
                PrivateKey key = backupPKCS8Reader(reader);
                if(key != null){
                    LOGGER.debug("Read pkcs8 key with backup implementation");
                    return key;
                }
                reader.reset();
                line = reader.readLine() + reader.readLine();
                LOGGER.debug("No key found, first two lines were: " + line);
                throw new IOException("No private key found.");
            }

//            LOGGER.debug("read the keypair" + o);
            PrivateKey privateKey = null;
            // old method returned keypair
            if(o instanceof KeyPair){
            	KeyPair pair = (KeyPair) o;
            	privateKey = pair.getPrivate();
            } else {
            	// new method returns just private key
            	if(o instanceof PrivateKey){
            		privateKey = (PrivateKey) o;
            	} else {
                    LOGGER.debug("Error while reading private key. Item is not a private key. Item is: " + o.getClass());
                    throw new IOException("Error while reading private key. Item is not a private key. Item is: " + o.getClass());
            		
            	}
            }
//            TestBase.printInterval("handle key");

//            LOGGER.debug("the private key is " + privateKey);

//            TestBase.printInterval("reset for skipping");

            reader.mark(MARK_LEN);

            return privateKey;
        } catch (IOException e) {
            LOGGER.debug("Error while reading private key. Exception: " + e.getClass().getName()
                    + " message:" + e.getMessage());

            throw e;
        }

    }

    /**
     * Reads the private key form the stream that was given in the constructor. After the reading, the file mark is
     * advanced 100 chars to avoid reparsing the key. If there is 100 or more chars in the stream before the private
     * key, the next read for certificate, crl, or private key will reread the private key and probably cause problems.
     * 
     * @param bin The BufferedInputStream to read the key from.
     * @return Returns the private key read.
     * @throws IOException Thrown if the key reading fails.
     * @deprecated Rather use the method with reader argument.
     */
    public static PrivateKey read(final BufferedInputStream bin) throws IOException {
        bin.mark(MARK_LEN);
        PrivateKey key = read(bin, (PasswordFinder) null);
        bin.reset();
        if(key != null){
            // skip 100 chars to avoid reparsing the key.
            bin.skip(100);
        }
        bin.mark(MARK_LEN);
        return key;
    }

    /**
     * Reads the private key form the stream that was given in the constructor.
     * After the reading, the file mark is at the end of the key.
     * 
     * @param bin The BufferedInputStream to read the key from.
     * @return Returns the private key read.
     * @throws IOException Thrown if the key reading fails.
     */
    public static PrivateKey read(final BufferedReader reader) throws IOException {
        return read(reader, (PasswordFinder) null);
    }

    /**
     * Reads the private key form the stream that was given in the constructor.
     * After the reading, the file mark is at the end of the key.
     * 
     * @param bin The BufferedInputStream to read the key from.
     * @param passwd The password to use to access the private key.
     * @return Returns the private key read.
     * @throws IOException Thrown if the key reading fails.
     */
    public static PrivateKey read(final BufferedInputStream bin, final String passwd) throws IOException {
        bin.mark(MARK_LEN);
        if (passwd == null) {
            return read(bin, (PasswordFinder) null);
        }
		return read(bin, new Password(passwd.toCharArray()));
    }

    /**
     * Skips to the next line.
     * 
     * @param stream the input stream.
     * @throws IOException thrown in case of read error.
     * @deprecated do not use, use bufferedReader.readline instead. Scheduled for removal.
     */
    public static void skipLine(final BufferedInputStream stream) throws IOException {
        byte[] b = new byte[BUF_LEN]; // the byte buffer
        stream.mark(BUF_LEN + 2); // mark the beginning

        int num = 0;

        while (stream.available() > 0) { // check that there are still
            // something to read
            num = stream.read(b); // read bytes from the file to the byte
            // buffer

            int i = 0;

            while ((i < num) && (b[i] != CARR) && (b[i] != NL)) { // skip until the first newline or carriage break
                i++;
            }

            stream.reset(); // rewind the file to the beginning of the last read

            if ((b[i] == CARR) || (b[i] == NL)) {
                // String test = new String(b, 0, i);
                // System.out.println("Skipping: " + test);
                stream.skip(i);
                stream.mark(BUF_LEN + 2); // mark the new position

                break;
            }

            stream.skip(BUF_LEN);
            stream.mark(BUF_LEN + 2); // mark the new position
        }

        stream.read(b);
        
        // LOGGER.debug("Remains: " + new String(b, 0, num));
        if ((b[0] != CARR) && (b[0] != NL)) {
            LOGGER.error("No newline char found when trying to skip line");
            throw new IOException("No newline char found when trying to skip line");
        }

        stream.reset();

        // check for carriage return-newline combination
        if ((b[1] == CARR) || ((b[1] == NL) && (b[0] != b[1]))) { 
            stream.skip(2);
        } else {
            stream.skip(1);
        }

        stream.mark(BUF_LEN + 2);

        return;
    }

    /**
     * Return a PKCS1v2 representation of the key. The sequence returned
     * represents a full PrivateKeyInfo object.
     * 
     * @param inKey the key to encode.
     * @return a PKCS1v2 representation of the key.
     */
    public static byte[] getEncoded(PrivateKey inKey) {
    	RSAPrivateCrtKey key;

        if (inKey instanceof RSAPrivateCrtKey) {
            key = (RSAPrivateCrtKey) inKey;
        } else {
            throw new IllegalArgumentException("Argument was:" + inKey.getClass() + " Expected: JCERSAPrivateCrtKey");
        }

        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        DEROutputStream dOut = new DEROutputStream(bOut);
        RSAPrivateKeyStructure info = new RSAPrivateKeyStructure(key.getModulus(), key.getPublicExponent(), key
                .getPrivateExponent(), key.getPrimeP(), key.getPrimeQ(), key.getPrimeExponentP(), key
                .getPrimeExponentQ(), key.getCrtCoefficient());

        try {
            dOut.writeObject(info);
            dOut.close();
        } catch (IOException e) {
            throw new RuntimeException("Error encoding RSA public key");
        }

        return bOut.toByteArray();
    }

    /**
     * Return a PEM formatted PKCS1v2 representation of the key. The sequence
     * returned represents a full PrivateKeyInfo object. Had to re-implement as
     * the other PEM encoders produce wrong version of the key.
     * 
     * @param inKey the key to encode.
     * @return a PEM format PKCS1v2 representation of the key.
     */
    public static String getPEM(final PrivateKey inKey) {
        byte[] bytes = getEncoded(inKey);
        StringBuffer buffer = new StringBuffer();
        if (inKey instanceof RSAPrivateKey) {

            buffer.append("-----BEGIN RSA PRIVATE KEY-----\n");
            String keyPEM = new String(Base64.encode(bytes));

            // split the PEM data into LINE_LEN long lines.
            for (int i = 0; i < keyPEM.length(); i += LINE_LEN) {
                if (keyPEM.length() < i + LINE_LEN) {
                    buffer.append(keyPEM.substring(i, keyPEM.length()));

                } else {
                    buffer.append(keyPEM.substring(i, i + LINE_LEN));
                }
                buffer.append("\n");
            }
            buffer.append("-----END RSA PRIVATE KEY-----\n");
            return buffer.toString();
        }
        throw new IllegalArgumentException(
                "Trying to get PEM format string of non-RSA private key, while only RSA is supported. Class was: "
                        + inKey.getClass().getName());
    }
    
    /**
     * The backup implementation for reading pkcs#8 private keys until bouncycastle implementation is released.
     * 
     * @param reader the reader to read the pem encoded key from.
     * @return the read private key.
     * @throws IOException in case the reading fails.
     */
    public static PrivateKey backupPKCS8Reader(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null){
            // see if beginning tag of private key is found
            if(line.indexOf("-----BEGIN") != -1 && line.indexOf("PRIVATE KEY") != -1){
                return readPEM(reader);
            }
        }
        return null;
    }
    
    /**
     * Reads PEM pkcs#8 key from the reader.
     *  
     * @param reader reader to read from.
     * @return the private key read.
     * @throws IOException if reading fails.
     */
    private static PrivateKey readPEM(BufferedReader reader) throws IOException{
        String line;
        StringBuffer buf = new StringBuffer();
        
        while ((line = reader.readLine()) != null)
        {
            if (line.indexOf("-----END") != -1 && line.indexOf("PRIVATE KEY") != -1)
            {
                break;
            }
            buf.append(line.trim());
        }

        if (line == null)
        {
            throw new IOException("Private key end marker not found");
        }

        byte[] keyData = Base64.decode(buf.toString());
        
        try {
            PrivateKeyInfo info = PrivateKeyInfo.getInstance(ASN1Primitive.fromByteArray(keyData));
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyData);

            KeyFactory keyFact = KeyFactory.getInstance(info.getAlgorithmId().getObjectId().getId(), "BC");

            return keyFact.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new IOException("problem parsing PRIVATE KEY: " + e.toString());
        }
    }
}

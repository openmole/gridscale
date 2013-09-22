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

import java.io.IOException;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAKey;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.openssl.PEMWriter;

/**
 * Certificate utilities.
 * 
 */
public class CertUtil {
    /** log4j util for logging */
    static Logger logger = Logger.getLogger(CertUtil.class.getName());

    /**
     * Outputs the certificate in PEM encoded form.
     * @param cert the Certificate to encode.
     * @return the PEM encoded certificate string.
     * @throws IOException in case the certificate is invalid.
     */
    public static String getPEM(X509Certificate cert) throws IOException{
        StringWriter stringWriter = new StringWriter();
        PEMWriter pemWriter = new PEMWriter(stringWriter);
        // don't do try-catch as the IOException is only thrown in case input is invalid
        pemWriter.writeObject(cert);
        pemWriter.flush();

        return stringWriter.toString();
    }
    
    /**
     * Outputs the certificates in PEM encoded form.
     * Invalid to and from values result in ArrayIndexOutOfBoundsException anyway.
     * 
     * @param certs the Certificate to encode.
     * @param from the index of the first cert to encode (0 means first, max certs.length - 1).
     * @param to the index of the last cert to encode (0 means first, max certs.length - 1).
     * @return the PEM encoded certificate string.
     * @throws IOException in case the certificate is invalid.
     */
    public static String getPEM(X509Certificate[] certs, int from, int to) throws IOException{
        
        // No error checking for from and to as invalid values result in ArrayIndexOutOfBoundsException anyway.

        StringWriter stringWriter = new StringWriter();
        PEMWriter pemWriter = new PEMWriter(stringWriter);
        // don't do try-catch as the IOException is only thrown in case input is invalid
        int i = from;
        while (i <= to) {
            pemWriter.writeObject(certs[i]);
            i++;
        }
        pemWriter.flush();

        return stringWriter.toString();
    }
    
    /**
     * Outputs the certificates in PEM encoded form.
     * @param certs the Certificate to encode.
     * @return the PEM encoded certificate string.
     * @throws IOException in case the certificate is invalid.
     */
    public static String getPEM(X509Certificate[] certs) throws IOException{

        StringWriter stringWriter = new StringWriter();
        PEMWriter pemWriter = new PEMWriter(stringWriter);
        // don't do try-catch as the IOException is only thrown in case input is invalid
        int i = 0;
        while (i < certs.length) {
            pemWriter.writeObject(certs[i]);
        }
        pemWriter.flush();

        return stringWriter.toString();
    }
    
    
    
    /**
     * Finds out the index of the client cert in a certificate chain.
     * 
     * @param chain the cert chain
     * @return the index of the client cert of -1 if no client cert was found
     */
    public static int findClientCert(X509Certificate[] chain) {
        int i;
        // get the index for the first cert that isn't a CA or proxy cert
        for (i = chain.length - 1; i >= 0; i--) {
            // if constrainCheck = -1 the cert is NOT a CA cert
            if (chain[i].getBasicConstraints() == -1) {
                // double check, if issuerDN = subjectDN the cert is CA
                if (!chain[i].getIssuerDN().equals(chain[i].getSubjectDN())) {
                    break;
                }
            }
        }

        // no valid client certs found, print an error message?
        if (i == chain.length) {
            logger.error("UpdatingKeymanager: invalid certificate chain, client cert missing.");

            return -1;
        }
		return i;
    }
    
    /**
     * Compares whether the given private key and the public key in the certificate belong together. Meaning private key
     * can decrypt what public key encrypts. Only RSA keys are supported at the moment.
     * 
     * @param key The private key.
     * @param certificate The certificate holding the public key.
     * @return True if the keys match. False if not. Throws IllegalArgumentException in case the keys are not RSA keys.
     */
    public static boolean keysMatch(PrivateKey key, X509Certificate certificate) {
        PublicKey pubKey = certificate.getPublicKey();
        return keysMatch(key, pubKey);
    }

    /**
     * Compares whether the given private key and the public key belong together. Meaning private key can decrypt what
     * public key encrypts. Only RSA keys are supported at the moment.
     * 
     * @param key The private key.
     * @param pubKey The public key.
     * @return True if the keys match. False if not. Throws IllegalArgumentException in case the keys are not RSA keys.
     */
    public static boolean keysMatch(PrivateKey key, PublicKey pubKey) {
        if (key instanceof RSAKey && pubKey instanceof RSAKey) {
            return ((RSAKey) key).getModulus().equals(((RSAKey) pubKey).getModulus());
        }
        
        //TODO: implement other algorithms by cipher/decipher?

        String text;
		if (!(key instanceof RSAKey) && !(pubKey instanceof RSAKey)) {
		    text = "neither";
		} else {
		    if (key instanceof RSAKey) {
		        text = "private key";
		    } else {
		        text = "public key";
		    }
		}
		throw new IllegalArgumentException(
		        "When comparing public and private keys, only RSA keys are supported. Of the keys, " + text
		                + " was RSA key.");
    }
    
    /**
     * Gets the certificate extension identified by the oid and returns the value bytes unwrapped by the ASN1OctetString.
     * @param cert The certificate to inspect.
     * @param oid The extension OID to fetch.
     * @return The value bytes of the extension, returns null in case the extension was not present or was empty.
     * @throws IOException thrown in case the certificate parsing fails.
     */
    static public byte[] getExtensionBytes(X509Certificate cert, String oid) throws IOException{
        byte[] bytes = cert.getExtensionValue(oid);
        if(bytes == null){
            return null;
        }
        DEROctetString valueOctets = (DEROctetString)ASN1Primitive.fromByteArray(bytes);
        return valueOctets.getOctets();
    }
    
    /**
     * Gets the user end entity certificate DN form teh proxy chain. Meaning the original user certificate DN before the proxies.
     * 
     * @param certChain the certificate chain to search for the DN.
     * @return DN the user DN.
     * @throws IOException in case no user certificate was found.
     */
    static public DN getUserDN(X509Certificate[] chain) throws IOException {
        int i = findClientCert(chain);
        if(i < 0){
            throw new IOException("No user certificate found in proxy chain for: " + DNHandler.getSubject(chain[0]).getRFCDN());
        }
        return DNHandler.getSubject(chain[i]);
    }

}

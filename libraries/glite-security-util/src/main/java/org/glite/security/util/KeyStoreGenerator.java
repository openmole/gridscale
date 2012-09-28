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

import org.bouncycastle.openssl.PasswordFinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import java.util.Vector;


/** Generates a keystore from the certificate and the private
 * key from the files.
 *
 * @author  Joni Hahkala
 * Created on April 9, 2002, 5:00 PM
 */
public class KeyStoreGenerator {
    /**
     * The logging facility.
     */
    static Logger logger = Logger.getLogger(KeyStoreGenerator.class.getName());

    /**
     * Generates the new KeyStore using the information given in the constructor.
     * 
     * @param certFile The file to read the certificate from.
     * @param keyFile The file where to read the private key from.
     * @param finder The password finder to use to prompt for user to input the password.
     * @param storePasswd The password to use as the keystore password.
     * @throws Exception Thrown is the certificate or private key reading fails.
     * @return Returns the new KeyStore.
     */
    public static KeyStore generate(String certFile, String keyFile, PasswordFinder finder, String storePasswd)
        throws Exception {
        FileCertReader reader = new FileCertReader();
        Vector identityChain = reader.readCerts(certFile);

        File file = new File(keyFile);
        FileReader fileReader = null;
        PrivateKey key = null;

        try {
            fileReader = new FileReader(file);

            BufferedReader buffReader = new BufferedReader(fileReader);

            key = PrivateKeyReader.read(buffReader, finder);
        } catch (Exception e) {
            throw e;
        } finally {
            if (fileReader != null) {
                fileReader.close();
            }
        }

        KeyStore store = KeyStore.getInstance("JKS");

        X509Certificate[] chain = new X509Certificate[] {};
        chain = (X509Certificate[]) identityChain.toArray(chain);

        store.load(null, null);

        store.setKeyEntry("identity", key, storePasswd.toCharArray(), chain);

        return store;
    }
}

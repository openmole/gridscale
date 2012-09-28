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

package org.glite.security.trustmanager;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

import org.apache.log4j.Logger;
import org.bouncycastle.openssl.PasswordFinder;
import org.glite.security.SecurityContext;
import org.glite.security.util.CaseInsensitiveProperties;
import org.glite.security.util.CertUtil;
import org.glite.security.util.DNHandler;
import org.glite.security.util.FileCertReader;
import org.glite.security.util.KeyStoreGenerator;
import org.glite.security.util.Password;

/**
 * A KeyManager that reloads the credentials periodically. <b>Notice!</b> If the identity certificate changes, the
 * private key changes too. The SSL mechanism fetches the certificate chain and the private key using two different
 * calls, so there is a possibility that update happens between calls to these methods thus leading to the situation
 * that you get old cert and new private key and they do not work together. If the system has built-in retry, this
 * shouldn't matter, only a small delay occurs. But if there is no retry, failure occurs. This can only be solved by
 * changing the internal api inside java or by using mutexes in the software that uses this class. So, solution is not
 * likely.
 * 
 * @author Joni Hahkala <joni.hahkala@cern.ch> Created on January 20, 2003, 5:07 PM
 */
public class UpdatingKeyManager extends X509ExtendedKeyManager {
    /**
     * The logging facility.
     */
    static final Logger LOGGER = Logger.getLogger(UpdatingKeyManager.class.getName());

    /**
     * The password for the underlying keystore.
     */
    char[] passwd;

    /**
     * The underlying keystore where the identity is stored keystore.
     */
    KeyStore keyStore;

    /**
     * The underlying keymanager implementation.
     */
    X509KeyManager managerImpl;

    /**
     * The update interval, <0 means never, which is the default.
     */
    long intervalSecs = -1;

    /**
     * The key manager factory to use.
     */
    KeyManagerFactory keyManagerFactory;

    /**
     * The filename of the certificate to use.
     */
    String identityCertFile;

    /**
     * The filename of the private key to use.
     */
    String identityKeyFile;

    /**
     * The private key password.
     */
    String identityKeyPasswd;

    /**
     * The filename of the keystore containing the identity.
     */
    String identityStoreFile;

    /**
     * The type of the keystore containing the identity. Can be jks or pkcs12.
     */
    String identityStoreType;

    /**
     * The keystore password.
     */
    String identityStorePasswd;

    /**
     * The filename of the proxy to use as identity.
     */
    String proxyFile;

    /**
     * The string defining the interval for polling the identity file(s) for updates.
     */
    String proxyIntervalBlob;

    /**
     * The class to use for finding out the password, for example a dialog.
     */
    PasswordFinder passwordFinder;

    /**
     * The timer for polling the identity file(s) for updates.
     */
    Timer identityTimer = null;
    
    /** the credential file used, if applicable. Only for error messages, as the filename can be from many sources or settings. Best effort basis. No thread safety etc.*/
    public String m_credentialFile;

    /**
     * Creates a new instance of UpdatingKeyManager
     * 
     * @param config the configuration to get the information from for setting up the keymanager.
     * @param finder the class to use for prompting the user for password.
     * @throws NoSuchAlgorithmException thrown in case the RSA algorithm or the cert algoritmh is not supported.
     * @throws CertificateException in case the certificate loading fails.
     */
    public UpdatingKeyManager(CaseInsensitiveProperties config, PasswordFinder finder) throws NoSuchAlgorithmException,
            CertificateException {
        identityCertFile = config.getProperty(ContextWrapper.CREDENTIALS_CERT_FILE);
        identityKeyFile = config.getProperty(ContextWrapper.CREDENTIALS_KEY_FILE);
        identityKeyPasswd = config.getProperty(ContextWrapper.CREDENTIALS_KEY_PASSWD);

        identityStoreFile = config.getProperty(ContextWrapper.CREDENTIALS_STORE_FILE);
        identityStoreType = config.getProperty(ContextWrapper.CREDENTIALS_STORE_TYPE,
                ContextWrapper.KEYSTORE_TYPE_DEFAULT);
        identityStorePasswd = config.getProperty(ContextWrapper.CREDENTIALS_STORE_PASSWD);

        proxyFile = config.getProperty(ContextWrapper.CREDENTIALS_PROXY_FILE);
        proxyIntervalBlob = config.getProperty(ContextWrapper.CREDENTIALS_UPDATE_INTERVAL);

        // Create the key manager factory to extract the server key
        try {
            keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        } catch (NoSuchAlgorithmException e) {
            LOGGER.fatal("Internal: X509 key manager initialization failed: " + e.getMessage());
            throw e;
        }

        passwordFinder = finder;

        try {
            loadKeystore();
        } catch (CertificateException e) {
            LOGGER.fatal("credentials loading failed: " + e.getMessage());
            throw e;
        }

        if (proxyIntervalBlob != null) {
            intervalSecs = ContextWrapper.getIntervalSecs(proxyIntervalBlob);
            startUpdateLoop();
        }
    }

    /**
     * Creates a new UpdatingKeyManager object.
     * 
     * @param store The key store to use as the credentials.
     * @param pass The password to access the keystore, if required.
     * @throws Exception thrown case the initialization of the KeyManager fails.
     */
    public UpdatingKeyManager(KeyStore store, char[] pass) throws Exception {
        // Create the key manager factory to extract the server key
        try {
            keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        } catch (Exception e) {
            LOGGER.fatal("UpdatingKeymanager: initialization failed because of internal error: " + e.getMessage());
            throw e;
        }

        setManager(store, pass);
    }

    /**
     * Sets the keystore and password to use internally.
     * 
     * @param store The key store to use.
     * @param pass The password to access the keystore, if required.
     * @throws CertificateException Thrown in case the initialization fails, like when private key and certificate key don't match.
     */
    private void setManager(KeyStore store, char[] pass) throws CertificateException {
        keyStore = store;
        passwd = pass;

        try {
            LOGGER.debug("Setting key manager implementation with keystore: " + store + " containing aliases: "
                    + store.aliases());
            X509Certificate cert = (X509Certificate)keyStore.getCertificate(store.aliases().nextElement());
            PrivateKey key = (PrivateKey)keyStore.getKey(store.aliases().nextElement(), pass);

            // check the the private key and certificate match.
            if(!CertUtil.keysMatch(key, cert)){
                throw new CertificateException("When loading credentials, certificate and private key don't match.");
            }
            
            keyManagerFactory.init(keyStore, pass);

            managerImpl = (X509KeyManager) keyManagerFactory.getKeyManagers()[0];
        } catch (Exception e) {
            LOGGER.debug("Credentials reading failed: " + e.getMessage());
            throw new CertificateException(e.getMessage());
        }
    }

    /**
     * Determines which way the credentials are defined as a keystore, cert-key pair or as a proxy and tries to load them. 
     * 
     * @throws CertificateException in case the credential loading fails.
     */
    void loadKeystore() throws CertificateException {
        FileInputStream istream = null;
        ByteArrayInputStream inputStream = null;
        BufferedInputStream bis = null;

        KeyStore identityStore = null;

        try {
            if (identityStoreFile != null) { // identity stored in a KeyStore
                LOGGER.debug("using credentials from keystore: " + identityStoreFile);
                m_credentialFile = identityStoreFile;
                // try loading a keystore as identityCert or identityKey are undefined
                identityStore = KeyStore.getInstance(identityStoreType);
                istream = new FileInputStream(identityStoreFile);
                if(identityStorePasswd != null){
                    identityStore.load(istream, identityStorePasswd.toCharArray());
                    setManager(identityStore, identityStorePasswd.toCharArray());
                }else{
                    identityStore.load(istream, null);
                    setManager(identityStore, null);
                }
            } else {
                if ((identityCertFile != null) && (identityKeyFile != null)) { // identity
                    LOGGER.debug("using credential cert file: " + identityCertFile + " and credential key file: "
                            + identityKeyFile);
                    m_credentialFile = identityCertFile + ", " + identityKeyFile;
                    if ((passwordFinder == null) && (identityKeyPasswd != null)) {
                        passwordFinder = new Password(identityKeyPasswd.toCharArray());
                    }

                    // generate a keystore from identityCert and identityKey
                    identityStore = KeyStoreGenerator.generate(identityCertFile, identityKeyFile, passwordFinder,
                            ContextWrapper.INT_KEYSTORE_PASSWD);
                    setManager(identityStore, ContextWrapper.INT_KEYSTORE_PASSWD.toCharArray());
                } else { // identity stored as a grid proxy file
                    LOGGER.debug("proxyfile given: " + proxyFile);
                    if (proxyFile == null) {
                        // Look for Grid Proxy STREAM
                        String proxyStream = System.getProperty(ContextWrapper.GRID_PROXY_STREAM);
                        if ((proxyStream != null) && (proxyStream.length() > 0)) {
                            LOGGER.debug("Loading proxy from a stream");
                            m_credentialFile = "(internal stream)";
                            try {
                                inputStream = new ByteArrayInputStream(proxyStream.getBytes());
                                bis = new BufferedInputStream(inputStream);
                            } catch (Exception e) {
                                LOGGER.debug("Unable to load Proxy from Stream");
                                // e.printStackTrace();
                            }

                        } else {
                            // Look for Grid Proxy FILE
                            try {
                                LOGGER.debug("no proxyfile given, using default");
                                proxyFile = findProxy();
                                LOGGER.debug("read proxy: " + proxyFile);
                                m_credentialFile = proxyFile;
                                bis = new BufferedInputStream(new FileInputStream(proxyFile));
                            } catch (Exception e) {
                                LOGGER.debug("Credetials loading failed, no credentials defined and default "
                                        + "credentials couldn't be found");
                                throw new CertificateException(
                                        "Credetials loading failed, no credentials defined and default credentials"
                                                + " couldn't be found");
                            }
                        }
                    } else {
                        m_credentialFile = proxyFile;
                        bis = new BufferedInputStream(new FileInputStream(proxyFile));
                    }
                    FileCertReader reader = new FileCertReader();
                    identityStore = reader.readProxy(bis, ContextWrapper.INT_KEYSTORE_PASSWD);
                    setManager(identityStore, ContextWrapper.INT_KEYSTORE_PASSWD.toCharArray());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Identity reading failed: " + e.getMessage());
            throw new CertificateException(e.getMessage());
        } finally {
            if (istream != null) {
                try {
                    istream.close();
                } catch (IOException e) {
                    // swallow the failed close
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // swallow the failed close, then again closing a byte array input stream shouldn't fail.
                }
            }
        }
    }

    /**
     * @see javax.net.ssl.X509ExtendedKeyManager#chooseEngineClientAlias(java.lang.String[], java.security.Principal[],
     *      javax.net.ssl.SSLEngine)
     */
    public String chooseEngineClientAlias(String[] keyType, java.security.Principal[] issuers, SSLEngine engine) {
        SecurityContext sc = SecurityContext.getCurrentContext();

        if (sc == null) {
            sc = new SecurityContext();
            SecurityContext.setCurrentContext(sc);
        }

        if (issuers != null) {
            sc.setPeerCas(issuers);
        }

        String alias = managerImpl.chooseClientAlias(keyType, issuers, null);
        LOGGER.debug("UpdatingKeyManager.chooseEngineClientAlias: alias is=" + alias);

        return alias;
    }

    /**
     * @see javax.net.ssl.X509ExtendedKeyManager#chooseEngineServerAlias(java.lang.String, java.security.Principal[],
     *      javax.net.ssl.SSLEngine)
     */
    public String chooseEngineServerAlias(String keyType, java.security.Principal[] issuers, SSLEngine engine) {
        SecurityContext sc = SecurityContext.getCurrentContext();

        if (sc == null) {
            sc = new SecurityContext();
            SecurityContext.setCurrentContext(sc);
        }

        if (issuers != null) {
            sc.setPeerCas(issuers);
        }

        String alias = managerImpl.chooseServerAlias(keyType, issuers, null);
        LOGGER.debug("UpdatingKeyManager.chooseEngineServerAlias: alias is=" + alias);

        return alias;
    }

    /*
     * (non-Javadoc)
     * @see javax.net.ssl.X509KeyManager#chooseClientAlias(java.lang.String[], java.security.Principal[],
     * java.net.Socket)
     */
    public String chooseClientAlias(String[] str, java.security.Principal[] principal, java.net.Socket socket) {
        SecurityContext sc = SecurityContext.getCurrentContext();

        if (sc == null) {
            sc = new SecurityContext();
            SecurityContext.setCurrentContext(sc);
        }

        if (principal != null) {
            sc.setPeerCas(principal);
        }

        if (LOGGER.isDebugEnabled()) { // print lots of debug info...
            LOGGER.debug("types are:");
            for (int n = 0; n < str.length; n++) {
                LOGGER.debug(str[n]);
            }
            if (principal != null) {
                LOGGER.debug("principals are:");
                for (int n = 0; n < principal.length; n++) {
                    LOGGER.debug(DNHandler.getDN(principal[n]));
                }
            } else {
                LOGGER.debug("no principals received");
            }
            LOGGER.debug("socket is: " + socket);
            LOGGER.debug("UpdatingKeyManager.chooseClientAlias: ks=" + managerImpl);
        }

        String alias = managerImpl.chooseClientAlias(str, principal, socket);
        LOGGER.debug("UpdatingKeyManager.chooseClientAlias: alias is=" + alias);

        return alias;
    }

    /*
     * (non-Javadoc)
     * @see javax.net.ssl.X509KeyManager#chooseServerAlias(java.lang.String, java.security.Principal[], java.net.Socket)
     */
    public String chooseServerAlias(String str, java.security.Principal[] principal, java.net.Socket socket) {
        SecurityContext sc = SecurityContext.getCurrentContext();

        if (sc == null) {
            sc = new SecurityContext();
            SecurityContext.setCurrentContext(sc);
        }

        if (principal != null) {
            sc.setPeerCas(principal);
        }

        String alias = managerImpl.chooseServerAlias(str, principal, socket);
        LOGGER.debug("UpdatingKeyManager.chooseServerAlias: type=" + str + " issuers=" + Arrays.toString(principal) + " socket="
                + socket + " alias is=" + alias);

        return alias;
    }

    /*
     * (non-Javadoc)
     * @see javax.net.ssl.X509KeyManager#getCertificateChain(java.lang.String)
     */
    public java.security.cert.X509Certificate[] getCertificateChain(String str) {
        X509Certificate[] chain = managerImpl.getCertificateChain(str);
        LOGGER.debug("alias=" + str + " DN is = " + (chain == null ? null : chain[0].getSubjectDN()));

        return chain;
    }

    /*
     * (non-Javadoc)
     * @see javax.net.ssl.X509KeyManager#getClientAliases(java.lang.String, java.security.Principal[])
     */
    public String[] getClientAliases(String str, java.security.Principal[] principal) {
        String[] aliases = managerImpl.getClientAliases(str, principal);
        if (LOGGER.isDebugEnabled()) {
            StringBuffer list = new StringBuffer();
            if (aliases != null) {
                for (int i = 0; i < aliases.length; i++) {
                    list.append(aliases[i]);
                    list.append(", ");
                }
            } else {
                list.append("none");
            }
            LOGGER.debug("UpdatingKeyManager.getClientAliases: type=" + str + " issuers=" + Arrays.toString(principal) + " aliases are="
                    + list.toString());
        }
        return aliases;
    }

    /*
     * (non-Javadoc)
     * @see javax.net.ssl.X509KeyManager#getPrivateKey(java.lang.String)
     */
    public java.security.PrivateKey getPrivateKey(String str) {
        PrivateKey key = managerImpl.getPrivateKey(str);
        LOGGER.debug("UpdatingKeyManager.getPrivateKey: alias= " + str + " key " + key == null ? "not found."
                : "found.");

        return key;
    }

    /*
     * (non-Javadoc)
     * @see javax.net.ssl.X509KeyManager#getServerAliases(java.lang.String, java.security.Principal[])
     */
    public String[] getServerAliases(String str, java.security.Principal[] principal) {
        String[] aliases = managerImpl.getServerAliases(str, principal);
        if (LOGGER.isDebugEnabled()) {
            StringBuffer list = new StringBuffer();
            if (aliases != null) {
                for (int i = 0; i < aliases.length; i++) {
                    list.append(aliases[i]);
                    list.append(", ");
                }
            } else {
                list.append("none");
            }
            LOGGER.debug("UpdatingKeyManager.getServerAliases: type=" + str + " issuers=" + Arrays.toString(principal) + " aliases are="
                    + list.toString());
        }
        return aliases;
    }

    /**
     * Starts the timer to reload the credentials periodically.
     */
    void startUpdateLoop() {
        if (intervalSecs > 0) {
            // refresh the identity every interval
            identityTimer = new Timer(true);
            identityTimer.schedule(new RefreshIdentity(), 0, intervalSecs * 1000);
        }
    }

    /**
     * Stops the possible identity updater.
     */
    void stop() {
        if (identityTimer != null) {
            identityTimer.cancel();
            identityTimer = null;
        }
    }

    /**
     * Searches for a
     * 
     * @return DOCUMENT ME!
     * @throws IOException DOCUMENT ME!
     */
    public String findProxy() throws IOException {
        File tmpDir;
        String uid;
        try {
            String proxyFileSystem = System.getProperty("X509_USER_PROXY");

            if (proxyFileSystem != null) {
                return proxyFileSystem;
            }

            proxyFileSystem = System.getProperty("X509_PROXY_FILE");

            if (proxyFileSystem != null) {
                return proxyFileSystem;
            }

            tmpDir = new File(System.getProperty("java.io.tmpdir"));

            if (!tmpDir.exists() || !tmpDir.isDirectory()) {
                LOGGER.fatal("directory " + tmpDir + " not found for default proxy loading");
                throw new IOException("directory " + tmpDir + " not found for default proxy loading");
            }

            uid = System.getProperty("UID");

            if (uid == null) {
                LOGGER.fatal("No credentials defined and couldn't discover user uid for the proxy loading");
                throw new java.lang.NoSuchFieldError(
                        "No credentials defined and couldn't discover user uid for the proxy loading");
            }
        } catch (IOException e) {
            LOGGER.fatal("Proxy file finding failed: " + e.getMessage());
            throw new IOException("Proxy file finding failed: " + e.getMessage());
        }
        return tmpDir.getAbsolutePath() + File.separator + "x509up_u" + uid;
    }

    public String toString() {
        if (managerImpl == null) {
            return "UpdatingKeyManager (uninitialized) [" + super.toString() + "]";
        }
        return "UpdatingKeyManager [" + managerImpl.getCertificateChain(null)[0].toString() + "]";
    }

    /**
     * The timer that automatically reloads the credentials if the time interval is set.
     * 
     * @author Joni Hahkala <joni.hahkala@cern.ch>
     */
    class RefreshIdentity extends TimerTask {
        /**
         * DOCUMENT ME!
         */
        public void run() {
            LOGGER.debug("refreshing credentials.\n");

            try {
                loadKeystore();
            } catch (Exception e) {
                LOGGER.fatal("Credentials reload failed");
                throw new RuntimeException("Credentials reload failed");
            }
        }

        public String toString() {
            return "UpdatingKeyManager.RefreshIdentity timer task" + super.toString();
        }
    }
}

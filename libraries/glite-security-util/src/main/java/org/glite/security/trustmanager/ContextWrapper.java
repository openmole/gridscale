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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;

import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PasswordFinder;
import org.glite.security.util.CaseInsensitiveProperties;
import org.glite.security.util.DNHandler;
import org.glite.security.util.FileCertReader;

/**
 * A class wrapping the SSLContext. It adds support for PEM certs, grid proxy certs, timeouts, dynamic reloading of CRLs
 * etc. ContextWrapper.java
 * 
 * @author Joni Hahkala Joni.Hahkala@cern.ch Created on July 18, 2002, 6:10 PM
 */
public class ContextWrapper implements SSLContextWrapper {
    /** The logging facility */
    // not private as the timer task uses it too.
    static final Logger LOGGER = Logger.getLogger(ContextWrapper.class.getName());

    /** Identity The credential proxy property name. */
    public static final String CREDENTIALS_PROXY_FILE = "gridProxyFile";

    /** The name of setting for interval for polling the credentials for update. */
    public static final String CREDENTIALS_UPDATE_INTERVAL = "credentialsUpdateInterval";

    /** Identity cert property name. */
    public static final String CREDENTIALS_CERT_FILE = "sslCertFile";

    /** Credential key property name. */
    public static final String CREDENTIALS_KEY_FILE = "sslKey";

    /** The password for the private key. */
    public static final String CREDENTIALS_KEY_PASSWD = "sslKeyPasswd";

    /** identity keystore property names */
    public static final String CREDENTIALS_STORE_FILE = "sslCertStore";

    /** The type of the credential keystore if keystore is used. */
    public static final String CREDENTIALS_STORE_TYPE = "sslCertStoreType";

    /** The password for the credential keystore. */
    public static final String CREDENTIALS_STORE_PASSWD = "sslCertStorePasswd";

    /** CA file names */
    public static final String CA_FILES = "sslCAFiles";

    /** CA keystore file name */
    public static final String CA_STORE_FILE = "sslCAStore";

    /** CA store type property name */
    public static final String CA_STORE_TYPE = "sslCAStoreType";

    /** CA store password */
    public static final String CA_STORE_PASSWD = "sslCAStorePasswd";

    // CRL related properties
    /** The file definition for the CRL files. */
    public static final String CRL_FILES = "crlFiles";

    /** The setting for whether the CRL support is enabled or not. */
    public static final String CRL_ENABLED = "crlEnabled";

    /** The setting for whether the CRLs are required for each of the CAs. */
    public static final String CRL_REQUIRED = "crlRequired";

    /** The setting for interval for CRL updates or polling the trust directory for updates. */
    public static final String CRL_UPDATE_INTERVAL = "crlUpdateInterval";

    // logging related properties
    /** The file where to load the log4j configuration. */
    public static final String LOG_CONF_FILE = "log4jConfFile";

    /** The file to log to. */
    public static final String LOG_FILE = "logFile";

    // SSL properties
    /** The SSL protocol to use. Options are SSLv3, TLSv1 and SSLv2Hello. Can be multiple, separated with comma. */
    public static final String SSL_PROTOCOL = "sslProtocol";

    // config file
    /**
     * The config file setting. If set the configuration is read from that file. Otherwise explicit settings and
     * defaults are used.
     */
    public static final String CONF_FILE = "sslConfigFile";

    /** timeout for ssl handshake and reading in milliseconds */
    public static final String SSL_TIMEOUT_SETTING = "sslTimeout";

    /** The connect timeout setting. */
    public static final String CONNECT_TIMEOUT = "sslConnectTimeout";

    /** Flag to override the credential expiration check on loading for testing purposes only. */
    public static final String OVERRIDE_EXPIRATION_CHECK_ON_INIT = "internalOverrideExpirationCheck";

    /** The stream to load the proxy from */
    public static final String GRID_PROXY_STREAM = "gridProxyStream";

    // defaults
    /** Default keystore type: JKS. */
    public static final String KEYSTORE_TYPE_DEFAULT = "JKS";

    /**
     * CRL reloading, and trustdir polling interval for updates default: 0, meaning disabled. This should be enabled in
     * servers and in long lived clients.
     */
    public static final String CRL_UPDATE_INTERVAL_DEFAULT = "0";

    /** CRL required default: true. */
    public static final String CRL_REQUIRED_DEFAULT = "true";

    /** SSL protocol default: TLSv1. */
    public static final String SSL_PROTOCOL_DEFAULT = "TLSv1";

    /** CRL support enabled default: true. */
    public static final String CRL_ENABLED_DEFAULT = "true";

    /** Credentials reload interval default "0 s", meaning disabled. */
    public static final String CREDENTIALS_UPDATE_INTERVAL_DEAFULT = "0 s";

    /** CA files default "/etc/grid-security/certificates/*.0". */
    public static final String CA_FILES_DEFAULT = "/etc/grid-security/certificates/*.0";

    /** CRL file default "/etc/grid-security/certificates/*.r0". */
    public static final String CRL_FILES_DEFAULT = "/etc/grid-security/certificates/*.r0";

    /** Timeout default 1 minute. */
    public static final String TIMEOUT_DEFAULT = "60000";

    /** Internal keystore password. */
    public static final String INT_KEYSTORE_PASSWD = "internal";

    /** The trust store setting */
    public static final String TRUSTSTORE_DIR = "trustStoreDir";

    /** The default trust store dir */
    public static final String TRUSTSTORE_DIR_DEFAULT = "/etc/grid-security/certificates";

    /** The hostname checking setting */
    public static final String HOSTNAME_CHECK = "hostnameCheck";

    /** Whether to set up log4j logging, needed to disable it when using slf4j. */
    public final static String WANT_LOG4J_SETUP = "wantLog4jSetup";

    /** Whether to set up log4j logging, needed to disable it when using slf4j. */
    public final static String WANT_LOG4J_SETUP_DEFAULT = "true";

    /** The hostname checking default */
    public static final String HOSTNAME_CHECK_DEFAULT = "true";

    /** The flag for whether the logging is configured or not. */
    private static boolean s_loggerConfigured = false;

    /** The settings of this ContextWrapper given in the constructor */
    public CaseInsensitiveProperties config;

    /** The key manager array of this wrapper. */
    public KeyManager[] identityKeyManagers = null;

    /** The trust anchors of this contextWrapper. */
    public Vector trustAnchors = null;

    /** The CRLs of this wrapper. */
    public Vector crls = null;

    /** The certificate reader instance to use to read the certificates, to avoid initializing it many times. */
    public FileCertReader certReader = null;

    /** The old trustmanager instance if the old configuration method is used. */
    public CRLFileTrustManager trustManager = null;

    /** The new trustmanager instance if the new configuration method is used (Trust directory). */
    public OpensslTrustmanager m_trustmanager = null;

    /**
     * Switch to bypass the expiration check. Only for testing! Overrides the expiration checking during the cert
     * loading so that expired certs can be loaded to test the certificate rejection at the server end.
     */
    public boolean overrideExpirationCheck = false;

    /** The timer for the CRL polling, also used for the trust dir polling. */
    Timer crlTimer = null;

    /** The underlying SSL context that this class wraps. */
    SSLContext sslContext;

    /** The resulting serverSocketFarctory. */
    javax.net.ssl.SSLServerSocketFactory serverSocketFactory = null;

    /** The resulting socketFactory for client usage. */
    SSLSocketFactory socketFactory = null;

    /** Add the bouncycastle provider unless it's already set */
    static {
        if (Security.getProvider("BC") == null) {
            LOGGER.debug("ContextWrapper: bouncycastle provider set.");
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Creates a new ContextWrapper object.
     * 
     * @param inputConfig the configuration to use.
     * @param wantLog4jConf a flag telling whether log4j should be configured
     * @throws IOException in case there is a problem reading config file, certificates, key or CRLs.
     * @throws GeneralSecurityException if there is a problem initializing the SSLContext.
     */
    public ContextWrapper(Properties inputConfig, boolean wantLog4jConf) throws IOException, GeneralSecurityException {
        loadConfig(inputConfig, wantLog4jConf);

        init(null, null, null);
    }

    /**
     * Creates a new ContextWrapper object.
     * 
     * @param inputConfig the configuration to use.
     * @throws IOException in case there is a problem reading config file, certificates, key or CRLs.
     * @throws GeneralSecurityException if there is a problem initializing the SSLContext.
     */
    public ContextWrapper(Properties inputConfig) throws IOException, GeneralSecurityException {
        loadConfig(inputConfig, true);

        init(null, null, null);
    }

    /**
     * Creates a new ContextWrapper object.
     * 
     * @param inputConfig The configuration values given.
     * @param chain the certificate chain to use for authentication.
     * @param key the key to use for authentication.
     * @throws IOException in case the SSL context initialization fails.
     * @throws GeneralSecurityException
     */
    public ContextWrapper(Properties inputConfig, X509Certificate[] chain, PrivateKey key) throws IOException,
            GeneralSecurityException {
        loadConfig(inputConfig, true);

        init(null, chain, key);
    }

    /**
     * Creates a new instance of ContextWrapper
     * 
     * @param inputConfig The properties used for configuring the instance of context wrapper.
     * @param finder
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public ContextWrapper(Properties inputConfig, PasswordFinder finder) throws IOException, GeneralSecurityException {
        loadConfig(inputConfig, true);
        init(finder, null, null);
    }

    /**
     * Depending on the configuration given either uses the configuration or loads the configuration from a file if the
     * configuration file setting is set.
     * 
     * @param inputConfig the configuration values.
     * @param wantLog4jConfiguration switch allowing the bypass of log4j initialization to allow the use of slf4j etc.
     * @throws FileNotFoundException if the config file pointed to by the config values is not found.
     * @throws IOException if the config file loading fails.
     */
    public void loadConfig(Properties inputConfig, boolean wantLog4jConfiguration) throws FileNotFoundException,
            IOException {
        String confFile = inputConfig.getProperty(CONF_FILE);

        if (confFile != null) {
            config = new CaseInsensitiveProperties();
            config.load(new FileInputStream(confFile));
        } else {
            config = new CaseInsensitiveProperties(inputConfig);
        }

        if (config.getProperty(OVERRIDE_EXPIRATION_CHECK_ON_INIT) != null) {
            overrideExpirationCheck = true;
        }

        // check if the log4j was disable from the properties.
        String wantLog4jSetup = config.getProperty(WANT_LOG4J_SETUP, WANT_LOG4J_SETUP_DEFAULT).trim().toLowerCase();
        boolean withLog4jSetup = true;
        
        if(wantLog4jSetup.startsWith("n") || wantLog4jSetup.startsWith("f")){
        	withLog4jSetup = false;
        }

        if (!s_loggerConfigured && wantLog4jConfiguration && withLog4jSetup) {
            String logConf = config.getProperty(LOG_CONF_FILE);
            String logFile = config.getProperty(LOG_FILE);

            Log4jConfigurator.configure(logConf, logFile);
        }
    }

    /**
     * Returns the underlying SSLContext that is wrapped. Only for debugging.
     * 
     * @return The underlying SSLContext.
     */
    public SSLContext getContext() {
        return sslContext;
    }

    /**
     * Creates a ServerSocketFactory.
     * 
     * @return The SSLServerSocketFactory created using the configuration values.
     * @throws SSLException if a problem occurs while creating the factory.
     */
    public javax.net.ssl.SSLServerSocketFactory getServerSocketFactory() throws SSLException {
        if (socketFactory != null) {
            LOGGER.fatal("Trying to use a client-use ContextWrapper to create server socket factory");
            throw new SSLException("Trying to use a client-use ContextWrapper to create server socket factory");
        }

        if (serverSocketFactory == null) {
            serverSocketFactory = sslContext.getServerSocketFactory();
        }

        return serverSocketFactory;
    }

    /**
     * Create a TimeoutSSLSocketFactory instance with the configuration requested.
     * 
     * @return SSLSocketFactory (TimeoutSSLSocketFactory) instance.
     * @throws SSLException In case of problems an exception is thrown.
     */
    public SSLSocketFactory getSocketFactory() throws SSLException {
        if (serverSocketFactory != null) {
            LOGGER.fatal("Trying to use a server-use ContextWrapper to create client socket factory");
            throw new SSLException("Trying to use a server-use ContextWrapper to create client socket factory");
        }

        if (socketFactory == null) {
            socketFactory = new TimeoutSSLSocketFactory(sslContext.getSocketFactory(), config);
        }

        return socketFactory;
    }

    /**
     * Initializes the key manager.
     * 
     * @param finder the Password Finder implementation to use to ask the user for password to access the private key.
     * @param chain the certificate chain to be used as credentials.
     * @param key the private key to be used as credential.
     * @throws CertificateException if certificate reading failed.
     * @throws GeneralSecurityException in case there is a security violation.
     * @throws NoSuchAlgorithmException if certificate or key uses unsupported algorithm.
     * @throws IOException if certificate reading failed.
     */
    public void init(PasswordFinder finder, X509Certificate[] chain, PrivateKey key) throws CertificateException,
            GeneralSecurityException, IOException {
        certReader = new FileCertReader();

        try {
            if ((chain == null) && (key == null)) {
                initKeyManagers(finder);
            } else {
                if ((chain == null) || (key == null)) {
                    LOGGER.fatal("Internal error: either certificate chain or private key of credentials is not defined");
                    throw new CertificateException(
                            "Internal error: either certificate chain or private key of credentials is not defined");
                }

                initKeyManagers(chain, key);
            }

            String CAFiles = config.getProperty(CA_FILES);

            TrustManager[] managerArray;

            // If the CAfiles variable is set, use the old way of handling the trust anchors and olt trustmanager
            // otherwise use the new trustmanager.

            LOGGER.debug(CA_FILES + " is " + CAFiles);

            if (CAFiles != null) {
                LOGGER.debug("old way with " + CA_FILES + "=" + CAFiles);
                initTrustAnchors();

                trustManager = new CRLFileTrustManager(trustAnchors);
                managerArray = new TrustManager[] { trustManager };

            } else {
                String trustDir = config.getProperty(TRUSTSTORE_DIR, TRUSTSTORE_DIR_DEFAULT);
                String crlRequired = config.getProperty(CRL_REQUIRED, CRL_REQUIRED_DEFAULT);
                crlRequired = crlRequired.trim().toLowerCase();
                LOGGER.debug("new way with trust dir: " + trustDir);
                if (crlRequired.startsWith("f") || crlRequired.startsWith("n")) {
                    m_trustmanager = OpensslTrustmanagerFactory.getTrustmanager(null, trustDir, false, config);
                } else {
                    m_trustmanager = OpensslTrustmanagerFactory.getTrustmanager(null, trustDir, true, config);
                }
                managerArray = new TrustManager[] { m_trustmanager };

            }

            String protocol = config.getProperty(SSL_PROTOCOL, SSL_PROTOCOL_DEFAULT);

            LOGGER.debug("Using transport protocol: " + protocol);

            // Create an SSL context used to create an SSL socket factory
            sslContext = SSLContext.getInstance(protocol);

            LOGGER.debug("Actually using transport protocol: " + sslContext.getProtocol());

            // Initialize the context with the key managers
            sslContext.init(identityKeyManagers, managerArray, new java.security.SecureRandom());

            startCRLLoop();
        } catch (GeneralSecurityException e) {
            LOGGER.fatal("ContextWrapper initialization failed: " + e.getMessage());
            throw e;
        } catch (IOException e) {
            LOGGER.fatal("ContextWrapper initialization failed: " + e.getMessage());
            throw e;
        } catch (ParseException e) {
            LOGGER.fatal("ContextWrapper initialization failed: " + e.getMessage());
            throw new IOException("ContextWrapper initialization failed: " + e.getMessage());

        }
    }

    /**
     * Initializes the key manager, the key manager will be updating keymanager and updates if the update interval is set.
     * 
     * @param finder the PasswordFinder implementation to use to ask the user for password to access the private key.
     * @throws CertificateException if certificate reading failed.
     * @throws NoSuchAlgorithmException If RSA algorithm is not supported.
     */
    public void initKeyManagers(PasswordFinder finder) throws CertificateException, NoSuchAlgorithmException {
        try {
            LOGGER.debug("ContextHandler.initKeyManagers");

            UpdatingKeyManager updatingKeyManager = new UpdatingKeyManager(config, finder);

            identityKeyManagers = new KeyManager[] { updatingKeyManager };

            String[] aliases = updatingKeyManager.getClientAliases("RSA", null);

            if ((aliases == null) || (aliases.length == 0)) {
                aliases = updatingKeyManager.getServerAliases("RSA", null);
            }

            if ((aliases == null) || (aliases.length == 0)) {
                throw new CertificateException("No credentials found");
            }

            X509Certificate[] chain = updatingKeyManager.getCertificateChain(aliases[0]);

            if (overrideExpirationCheck == false) {
                for (int n = 0; n < chain.length; n++) {

                    try {
                        chain[n].checkValidity();
                    } catch (CertificateExpiredException e) {
                        throw new CertificateExpiredException("Certificate for "
                                + DNHandler.getSubject(chain[n]).getRFCDN() + ", cert file was "
                                + updatingKeyManager.m_credentialFile + ": " + e.getMessage());
                    } catch (CertificateNotYetValidException e) {
                        throw new CertificateNotYetValidException("Certificate for "
                                + DNHandler.getSubject(chain[n]).getRFCDN() + ", cert file was "
                                + updatingKeyManager.m_credentialFile + ": " + e.getMessage());
                    }
                }
            }
        } catch (CertificateException e) {
            LOGGER.fatal("The credentials reading failed:  " + e.getMessage());
            throw e;
        } catch (NoSuchAlgorithmException e) {
            LOGGER.fatal("Internal error: while reading credentials " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Initializes the key manager, the key manager doesn't know where update from, so it will be not updating by itself.
     * 
     * @param chain the certificate chain to be used as credentials.
     * @param key the private key to be used as credential.
     * @throws CertificateException if certificate reading failed.
     * @throws NoSuchAlgorithmException if certificate or key uses unsupported algorithm.
     * @throws IOException if certificate reading failed.
     */
    public void initKeyManagers(X509Certificate[] chain, PrivateKey key) throws CertificateException,
            NoSuchAlgorithmException, IOException {
        try {
            if (overrideExpirationCheck == false) {
                for (int n = 0; n < chain.length; n++) {
                    chain[n].checkValidity();
                }
            }

            LOGGER.debug("ContextHandler.initKeyManagers(chain, key)");

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");

            KeyStore store = KeyStore.getInstance("JKS");

            store.load(null, null);

            store.setKeyEntry("identity", key, ContextWrapper.INT_KEYSTORE_PASSWD.toCharArray(), chain);

            keyManagerFactory.init(store, ContextWrapper.INT_KEYSTORE_PASSWD.toCharArray());

            identityKeyManagers = keyManagerFactory.getKeyManagers();
        } catch (CertificateException e) {
            LOGGER.fatal("The credentials creation from given cert chain and private key failed:  " + e.getMessage());
            throw e;
        } catch (NoSuchAlgorithmException e) {
            LOGGER.fatal("The credentials creation from given cert chain and private key failed:  " + e.getMessage());
            throw e;
        } catch (IOException e) {
            LOGGER.fatal("The credentials loading from given cert chain and private key failed:  " + e.getMessage());
            throw e;
        } catch (KeyStoreException e) {
            LOGGER.fatal("The keystore creation from given cert chain and private key failed:  " + e.getMessage());
            throw new CertificateException(e.getMessage());
        } catch (UnrecoverableKeyException e) {
            LOGGER.fatal("Internal error while loading credentials:  " + e.getMessage());
            throw new CertificateException(e.getMessage());
        }
    }

    /**
     * Initializes the trust anchors (CAs).
     * 
     * @throws KeyStoreException in case problems creating a keystore form the certificates.
     * @throws IOException in case problems reading the CA certificates.
     * @throws CertificateException in case problems reading the CA certificates.
     */
    void initTrustAnchors() throws KeyStoreException, IOException, CertificateException {
        FileInputStream caStoreStream = null;

        try {
            // initialize the trustAnchors
            String caStoreFile = config.getProperty(CA_STORE_FILE);

            if (caStoreFile != null) {
                String caStoreType = config.getProperty(CA_STORE_TYPE, KEYSTORE_TYPE_DEFAULT);
                String caStorePasswd = config.getProperty(CA_STORE_PASSWD);
                KeyStore caStore = KeyStore.getInstance(caStoreType);
                caStoreStream = new FileInputStream(caStoreFile);
                caStore.load(caStoreStream, caStorePasswd.toCharArray());

                Enumeration aliases = caStore.aliases();

                while (aliases.hasMoreElements()) {
                    X509Certificate cert = (X509Certificate) caStore.getCertificate((String) aliases.nextElement());
                    trustAnchors.add(new TrustAnchor(cert, null));
                }

                if (trustAnchors.size() == 0) {
                    throw new IOException("No CA store file found matching \"" + caStore);
                }
            } else {
                String caFiles = config.getProperty(CA_FILES, CA_FILES_DEFAULT);
                trustAnchors = certReader.readAnchors(caFiles);

                if (trustAnchors.size() == 0) {
                    throw new IOException("No CA files found matching \"" + caFiles);
                }
            }
        } catch (KeyStoreException e) {
            LOGGER.fatal("The trusted certificate authority certificates reading failed:  " + e.getMessage());
            throw new KeyStoreException("The trusted certificate authority certificates reading failed:  "
                    + e.toString());
        } catch (NoSuchAlgorithmException e) {
            LOGGER.fatal("The trusted certificate authority certificates reading failed:  " + e.getMessage());
            throw new CertificateException("The trusted certificate authority certificates reading failed:  "
                    + e.toString());
        } catch (IOException e) {
            LOGGER.fatal("The trusted certificate authority certificates reading failed:  " + e.getMessage());
            throw new IOException("The trusted certificate authority certificates reading failed:  " + e.toString());
        } catch (CertificateException e) {
            LOGGER.fatal("The trusted certificate authority certificates reading failed:  " + e.getMessage());
            throw e;
        } finally {
            if (caStoreStream != null) {
                caStoreStream.close();
            }
        }
    }

    /**
     * Starts the background process of updating the CRLs.
     * 
     * @throws CertificateException in case CRL reading fails.
     * @throws IOException in case CRL reading fails.
     * @throws ParseException in case the parsing of namespace files fails.
     */
    void startCRLLoop() throws CertificateException, IOException, ParseException {

        // in case of old trustmanager implementation, only start loop if the crls are enabled. With new one, start
        // always.
        if (trustManager != null) {
            String crlEnabled = config.getProperty(CRL_ENABLED, CRL_ENABLED_DEFAULT);

            if (crlEnabled.toLowerCase().startsWith("f")) { // if CRL are not enabled, return
                return;
            }
        }

        // if there is already a timer, don't start a new one.
        if (crlTimer != null) {
            return;
        }

        if (updateCRLs()) { // CRLs were found and succesfully set, start the loop
            String interval = config.getProperty(CRL_UPDATE_INTERVAL, CRL_UPDATE_INTERVAL_DEFAULT);

            long intervalSecs = getIntervalSecs(interval);

            if (intervalSecs < 1) {
                LOGGER.debug("The CRL update interval is less than 1 second, update loop not started. Value was: "
                        + intervalSecs);

                return;
            }

            // refresh the CRL caches every interval
            crlTimer = new Timer(true);
            crlTimer.schedule(new RefreshCRLs(), intervalSecs * 1000, intervalSecs * 1000);
        }
    }

    /**
     * Updates the CRLs.
     * 
     * @return true if CRL loading was successful.
     * @throws CertificateException in case CRL reading fails.
     * @throws IOException in case CRL reading fails.
     * @throws ParseException in case the parsing of namespace files fails.
     */
    boolean updateCRLs() throws CertificateException, IOException, ParseException {
        if (trustManager == null && m_trustmanager == null) {
            LOGGER.fatal("Trying to set CRLs in uninitialized ContextWrapper");
            throw new SecurityException("Trying to set CRLs in uninitialized ContextWrapper");
        }

        if (m_trustmanager != null) {
            m_trustmanager.checkUpdate();
            return true;
        }

        String crlFiles = config.getProperty(CRL_FILES, CRL_FILES_DEFAULT);

        if (crlFiles == null) {
            return false;
        }

        try {
            Vector allCRLs = certReader.readCRLs(crlFiles);
            Vector acceptedCRLs = checkCRLs(allCRLs);

            if (!acceptedCRLs.isEmpty()) {
                String crlRequired = config.getProperty(CRL_REQUIRED, CRL_REQUIRED_DEFAULT);

                boolean required = false;

                if (crlRequired.toLowerCase().startsWith("t")) {
                    required = true;
                }

                CRLCertChecker checker = new CRLCertChecker(acceptedCRLs, required);

                trustManager.setChecker(checker);

                return true;
            }

            return false;
        } catch (IOException e) {
            LOGGER.fatal("Error while setting CRLs. Tried to read " + crlFiles + " with current path "
                    + System.getProperty("user.dir") + " error was " + e.toString());

            // e.printStackTrace(System.err);
            throw new IOException("Error while setting CRLs. Tried to read " + crlFiles + " with current path "
                    + System.getProperty("user.dir") + " error was " + e.toString());
        } catch (CertificateException e) {
            LOGGER.fatal("Error while setting CRLs. Tried to read " + crlFiles + " with current path "
                    + System.getProperty("user.dir") + " error was " + e.toString());

            // e.printStackTrace(System.err);
            throw new CertificateException("Error while setting CRLs. Tried to read " + crlFiles
                    + " with current path " + System.getProperty("user.dir") + " error was " + e.toString());
        }
    }

    /**
     * Check that the CRLs are valid.
     * 
     * @param uncheked the unchecked CRLs.
     * @return the Vector of checked CRLs.
     * @throws SecurityException in case of unrecoverable error.
     */
    Vector checkCRLs(Vector uncheked) throws SecurityException {
        if (trustAnchors == null) {
            LOGGER.fatal("Trying to check CRLs without setting trustanchors first");
            throw new SecurityException("Trying to check CRLs without setting trustanchors first");
        }

        Iterator crlIter = uncheked.iterator();

        while (crlIter.hasNext()) { // go through the crls

            X509CRL crl = (X509CRL) crlIter.next();

            Iterator caIter = trustAnchors.iterator();

            boolean cheked = false;

            while (caIter.hasNext()) { // go through the trustAnchors

                TrustAnchor anchor = (TrustAnchor) caIter.next();

                X509Certificate caCert = anchor.getTrustedCert();

                if (caCert.getSubjectDN().equals(crl.getIssuerDN())) { // if

                    try {
                        crl.verify(caCert.getPublicKey()); // check the

                        // signature in crl
                        cheked = true; // flag that the crl is cheked

                        break;
                    } catch (Exception e) {
                        LOGGER.error("Invalid signature in CRL from " + crl.getIssuerDN().toString());

                        break;
                    }
                }
            }

            // if the crl was not from known source or the signature was
            // invalid, remove it
            if (cheked == false) {
                LOGGER.error("Rejecting a CRL from " + crl.getIssuerDN().toString()
                        + " because corresponding ca not found or invalid signature");
                crlIter.remove();
            }
        }

        return uncheked; // return what was left from the unchecked crls
    }

    /**
     * Parses a string representation of an interval into seconds. Format: n{s,m,h,d} (s=seconds, m=minutes, h=hours,
     * d=days)
     * 
     * @param intervalBlob String defining the interval.
     * @return the seconds calculated from the interval.
     */
    public static long getIntervalSecs(String intervalBlob) {
        int n = 0;

        // find first non-digit char
        while (n < intervalBlob.length() && Character.isDigit(intervalBlob.charAt(n))) {
            n++;
        }

        long number;

        if (n == 0) { // only day or minute etc given
            number = 1;
        } else {
            // read the number given
            String numberString = intervalBlob.substring(0, n);
            number = Long.parseLong(numberString);

            if (number == 0) {
                return 0;
            }
        }

        // get the unit definition for the number
        String unit = intervalBlob.substring(n).toLowerCase().trim();

        // input as seconds
        if (unit.charAt(0) == 's') {
            return number;
        }

        // input as minutes
        if (unit.charAt(0) == 'm') {
            return number * 60;
        }

        // input as hours
        if (unit.charAt(0) == 'h') {
            return number * 60 * 60;
        }

        // input as days
        if (unit.charAt(0) == 'd') {
            return number * 24 * 60 * 60;
        }

        LOGGER.fatal("invalid time unit definition in \"" + intervalBlob + "\" should start either with s, m, h or d");
        throw new IllegalArgumentException("invalid unit definition in \"" + intervalBlob
                + "\" should start either with s, m, h or d");
    }

    /**
     * Returns the internal key managers, only for debugging.
     * 
     * @return the internal key manager in use.
     */
    public X509KeyManager getKeyManager() {
        return (X509KeyManager) identityKeyManagers[0];
    }

    /**
     * Stops runing updater threads if there is any.
     */
    public void stop() {
        if (crlTimer != null) {
            crlTimer.cancel();
            crlTimer = null;
        }
        
        // in case the identity key manager is set and it is updating key manager, stop it. 
        if (identityKeyManagers != null && identityKeyManagers[0] != null) {
            if (identityKeyManagers[0] instanceof UpdatingKeyManager) {
                ((UpdatingKeyManager) identityKeyManagers[0]).stop();
            }
        }
    }

    /**
     * A simple utility class that implements the timer that updates the CRLs.
     */
    class RefreshCRLs extends TimerTask {
        /**
         * The actual method run by the timer.
         */
        public void run() {
            LOGGER.debug("refreshing CRLs.\n");

            try {
                updateCRLs();
            } catch (Exception e) {
                LOGGER.fatal("The CRL updating failed");
            }
        }
    }
}

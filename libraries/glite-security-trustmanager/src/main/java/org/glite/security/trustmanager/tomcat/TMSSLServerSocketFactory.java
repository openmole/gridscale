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

package org.glite.security.trustmanager.tomcat;

import org.apache.tomcat.util.net.ServerSocketFactory;

import org.glite.security.trustmanager.ContextWrapper;

import java.io.IOException;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Vector;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;


/**
 * The Tomcat glue ServerSocketFactory class. This class works as a glue
 * interface that interfaces the TrustManager SSL implementation with the
 * Tomcat.
 *
 * @author Joni Hahkala
 */
public class TMSSLServerSocketFactory extends ServerSocketFactory {
    /**
     * The logging facility.
     */
    private static final org.apache.commons.logging.Log LOGGER = org.apache.commons.logging.LogFactory.getLog(TMSSLServerSocketFactory.class);
    /**
     * The default algorithm if none is defined.
     */
    static String defaultAlgorithm = "SunX509";
    /**
     * The internal serversocket instance.
     */
    protected SSLServerSocketFactory sslProxy = null;
    /**
     * The internal context wrapper for generating the server sockets.
     */
    protected ContextWrapper contextWrapper = null;
    /**
     * The array of enabled ciphers.
     */
    protected String[] enabledCiphers;

    /**
     * Flag to state that we require client authentication.
     */
    protected boolean requireClientAuth = false;

    /**
     * Flag to state that we would like client authentication.
     */
    protected boolean wantClientAuth = false;
    /**
     * Flag to allow the renegotiation, and thus exposing the connection to man in the middle attack.
     */
    private boolean m_allowUnsafeLegacyRenegotiation = false;

    /*
     * (non-Javadoc)
     *
     * @see org.apache.tomcat.util.net.ServerSocketFactory#acceptSocket(java.net.ServerSocket)
     */
    public Socket acceptSocket(ServerSocket sSocket) throws IOException {
        LOGGER.debug("TMSSLServerSocketFactory.acceptSocket:");

        SSLSocket asock = null;

        try {
            asock = (SSLSocket) sSocket.accept();
            configureClientAuth(asock);
        } catch (SSLException e) {
            throw new SocketException("SSL handshake error" + e.toString());
        }

        return asock;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.tomcat.util.net.ServerSocketFactory#createSocket(int,
     *      int, java.net.InetAddress)
     */
    public ServerSocket createSocket(int port, int backlog, InetAddress ifAddress) throws IOException, InstantiationException {
        LOGGER.debug("TMSSLServerSocketFactory.createSocket3:");

        if (sslProxy == null) {
            init();
        }

        ServerSocket socket = sslProxy.createServerSocket(port, backlog, ifAddress);
        initServerSocket(socket);

        return socket;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.tomcat.util.net.ServerSocketFactory#createSocket(int,
     *      int)
     */
    public ServerSocket createSocket(int port, int backlog) throws IOException, InstantiationException {
        LOGGER.debug("TMSSLServerSocketFactory.createSocket2:");

        if (sslProxy == null) {
            init();
        }

        ServerSocket socket = sslProxy.createServerSocket(port, backlog);
        initServerSocket(socket);

        return socket;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.tomcat.util.net.ServerSocketFactory#createSocket(int)
     */
    public ServerSocket createSocket(int port) throws IOException, InstantiationException {
        LOGGER.debug("TMSSLServerSocketFactory.createSocket1:");

        if (sslProxy == null) {
            init();
        }

        ServerSocket socket = sslProxy.createServerSocket(port);
        initServerSocket(socket);

        return socket;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.tomcat.util.net.ServerSocketFactory#handshake(java.net.Socket)
     */
    public void handshake(Socket socket) throws IOException {
        LOGGER.debug("TMSSLServerSocketFactory.handshake:");
        // We do getSession instead of startHandshake() so we can call this multiple times
        SSLSession session = ((SSLSocket) socket).getSession();
        if (session.getCipherSuite().equals("SSL_NULL_WITH_NULL_NULL"))
            throw new IOException("SSL handshake failed. Ciper suite in SSL Session is SSL_NULL_WITH_NULL_NULL");

        if (!m_allowUnsafeLegacyRenegotiation) {
            // Prevent futher handshakes by removing all cipher suites
            ((SSLSocket) socket).setEnabledCipherSuites(new String[0]);
        }
    }

    /**
     * Reads the keystore and initializes the SSL socket factory.
     * 
     * @throws IOException in case the ssl context initialization fails.
     */
    void init() throws IOException {
        LOGGER.debug("TMSSLServerSocketFactory.init:");

        try {
            String clientAuthStr = (String) attributes.get("clientauth");

            if ("true".equalsIgnoreCase(clientAuthStr) || "yes".equalsIgnoreCase(clientAuthStr)) {
                requireClientAuth = true;
            } else if ("want".equalsIgnoreCase(clientAuthStr)) {
                wantClientAuth = true;
            }

            // SSL protocol variant (e.g., TLS, SSL v3, etc.)
            String protocol = (String) attributes.get("protocol");

            if (protocol == null) {
                protocol = ContextWrapper.SSL_PROTOCOL_DEFAULT;
            }

            // Certificate encoding algorithm (e.g., SunX509)
            String algorithm = (String) attributes.get("algorithm");

            if (algorithm == null) {
                algorithm = defaultAlgorithm;
            }

            String keystoreType = (String) attributes.get("keystoreType");

            if (keystoreType == null) {
                keystoreType = ContextWrapper.KEYSTORE_TYPE_DEFAULT;
            }

            String trustAlgorithm = (String) attributes.get("truststoreAlgorithm");

            if (trustAlgorithm == null) {
                trustAlgorithm = algorithm;
            }

            // Create and init SSLContext
            initProxy();

            // Determine which cipher suites to enable
            String requestedCiphers = (String) attributes.get("ciphers");

            if (requestedCiphers != null) {
                enabledCiphers = getEnabledCiphers(requestedCiphers, sslProxy.getSupportedCipherSuites());
            }
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }

            throw new IOException(e.getMessage());
        }
    }

    /**
     * Determines the SSL cipher suites to be enabled.
     * 
     * @param requestedCiphers Comma-separated list of requested ciphers @param
     * @param supportedCiphers Array of supported ciphers
     * @return Array of SSL cipher suites to be enabled, or null if none of the requested ciphers are supported
     */
    protected String[] getEnabledCiphers(String requestedCiphers, String[] supportedCiphers) {
        LOGGER.debug("TMSSLServerSocketFactory.getEnabledCiphers: enabling:" + requestedCiphers);

        String[] acceptedCiphers = null;

        if (requestedCiphers != null) {
            Vector vec = null;
            String cipher = requestedCiphers;
            int index = requestedCiphers.indexOf(',');

            if (index != -1) {
                int fromIndex = 0;

                while (index != -1) {
                    cipher = requestedCiphers.substring(fromIndex, index).trim();

                    if (cipher.length() > 0) {
                        /*
                         * Check to see if the requested cipher is among the
                         * supported ciphers, i.e., may be enabled
                         */
                        for (int i = 0; (supportedCiphers != null) && (i < supportedCiphers.length); i++) {
                            if (supportedCiphers[i].equals(cipher)) {
                                if (vec == null) {
                                    vec = new Vector();
                                }

                                vec.addElement(cipher);

                                break;
                            }
                        }
                    }

                    fromIndex = index + 1;
                    index = requestedCiphers.indexOf(',', fromIndex);
                } // while
                cipher = requestedCiphers.substring(fromIndex);
            }

            if (cipher != null) {
                cipher = cipher.trim();

                if (cipher.length() > 0) {
                    /*
                     * Check to see if the requested cipher is among the
                     * supported ciphers, i.e., may be enabled
                     */
                    for (int i = 0; (supportedCiphers != null) && (i < supportedCiphers.length); i++) {
                        if (supportedCiphers[i].equals(cipher)) {
                            if (vec == null) {
                                vec = new Vector();
                            }

                            vec.addElement(cipher);

                            break;
                        }
                    }
                }
            }

            if (vec != null) {
                acceptedCiphers = new String[vec.size()];
                vec.copyInto(acceptedCiphers);
            }
        }

        return acceptedCiphers;
    }

    /**
     * Initialize the SSL socket factory.
     * 
     * @exception IOException if an input/output error occurs
     */
    private void initProxy() throws IOException {
        LOGGER.debug("TMSSLServerSocketFactory.initProxy:");

        try {
            Properties props = new Properties();
            props.putAll(attributes);
            LOGGER.debug(props);
            
            String crlsEnabled = props.getProperty(ContextWrapper.CRL_ENABLED, ContextWrapper.CRL_ENABLED_DEFAULT);
            
            // only set the default if the crls are not disabled.
            if (crlsEnabled.toLowerCase().startsWith("f") != true) {
                // on tomcat use the 2h crl update interval if none is set.
                if(props.getProperty(ContextWrapper.CRL_UPDATE_INTERVAL) == null){
                	props.setProperty(ContextWrapper.CRL_UPDATE_INTERVAL, "2h");
                }
            }

            contextWrapper = new ContextWrapper(props);

            // Create the proxy and return
            sslProxy = contextWrapper.getServerSocketFactory();
        } catch (Exception e) {
            LOGGER.fatal("Server socket factory creation failed:  " + e);
//            e.printStackTrace(System.out);
            throw new IOException(e.toString());
        }
    }

    /**
     * Configures the given SSL server socket with the requested cipher suites, protocol versions, and need for client
     * authentication.
     * 
     * @param ssocket the server socket to initialize.
     */
    private void initServerSocket(ServerSocket ssocket) {
        LOGGER.debug("TMSSLServerSocketFactory.initServerSocket:");

        SSLServerSocket socket = (SSLServerSocket) ssocket;

        if (attributes.get("ciphers") != null) {
            socket.setEnabledCipherSuites(enabledCiphers);
        }else{
            String[] ciphers;
            ArrayList<String> newCiphers;
            int i;

            // disable RC4 ciphers (Java x Globus problems)
            ciphers = socket.getEnabledCipherSuites();
            newCiphers = new ArrayList<String>(ciphers.length);
            for (i = 0; i < ciphers.length; i++) {
                if (ciphers[i].indexOf("RC4") == -1 && ciphers[i].indexOf("ECDH") == -1){
                    LOGGER.debug("Enabling cipher: " + ciphers[i]);
                    newCiphers.add(ciphers[i]);
                }else{
                    LOGGER.debug("Disabling cipher: " + ciphers[i]);
                }
            }
            socket.setEnabledCipherSuites(newCiphers.toArray(new String[] {}));                    
            
        }

        String requestedProtocols = (String) attributes.get("protocols");
        setEnabledProtocols(socket, getEnabledProtocols(socket, requestedProtocols));

        // we don't know if client auth is needed -
        // after parsing the request we may re-handshake
        configureClientAuth(socket);
    }

    /**
     * DOCUMENT ME!
     *
     * @param socket DOCUMENT ME!
     * @param protocols DOCUMENT ME!
     */
    protected void setEnabledProtocols(SSLServerSocket socket, String[] protocols) {
        LOGGER.debug("TMSSLServerSocketFactory.setEnabledProtocols:");

        if (protocols != null) {
            socket.setEnabledProtocols(protocols);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param socket DOCUMENT ME!
     * @param requestedProtocols DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    protected String[] getEnabledProtocols(SSLServerSocket socket, String requestedProtocols) {
        LOGGER.debug("TMSSLServerSocketFactory.getEnabledProtocols:");

        String[] supportedProtocols = socket.getSupportedProtocols();

        String[] enabledProtocols = null;

        if (requestedProtocols != null) {
            Vector vec = null;
            String protocol = requestedProtocols;
            int index = requestedProtocols.indexOf(',');

            if (index != -1) {
                int fromIndex = 0;

                while (index != -1) {
                    protocol = requestedProtocols.substring(fromIndex, index).trim();

                    if (protocol.length() > 0) {
                        /*
                         * Check to see if the requested protocol is among the
                         * supported protocols, i.e., may be enabled
                         */
                        for (int i = 0; (supportedProtocols != null) && (i < supportedProtocols.length); i++) {
                            if (supportedProtocols[i].equals(protocol)) {
                                if (vec == null) {
                                    vec = new Vector();
                                }

                                vec.addElement(protocol);

                                break;
                            }
                        }
                    }

                    fromIndex = index + 1;
                    index = requestedProtocols.indexOf(',', fromIndex);
                }
                 // while

                protocol = requestedProtocols.substring(fromIndex);
            }

            if (protocol != null) {
                protocol = protocol.trim();

                if (protocol.length() > 0) {
                    /*
                     * Check to see if the requested protocol is among the
                     * supported protocols, i.e., may be enabled
                     */
                    for (int i = 0; (supportedProtocols != null) && (i < supportedProtocols.length); i++) {
                        if (supportedProtocols[i].equals(protocol)) {
                            if (vec == null) {
                                vec = new Vector();
                            }

                            vec.addElement(protocol);

                            break;
                        }
                    }
                }
            }

            if (vec != null) {
                enabledProtocols = new String[vec.size()];
                vec.copyInto(enabledProtocols);
            }
        }

        return enabledProtocols;
    }

    /**
     * DOCUMENT ME!
     *
     * @param socket DOCUMENT ME!
     */
    protected void configureClientAuth(SSLServerSocket socket) {
        LOGGER.debug("TMSSLServerSocketFactory.configureClientAuth:");

        if (wantClientAuth) {
            socket.setWantClientAuth(wantClientAuth);
        } else {
            socket.setNeedClientAuth(requireClientAuth);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param socket DOCUMENT ME!
     */
    protected void configureClientAuth(SSLSocket socket) {
        LOGGER.debug("TMSSLServerSocketFactory.configureClientAuth:");

        // Per JavaDocs: SSLSockets returned from 
        // SSLServerSocket.accept() inherit this setting.
    }
}

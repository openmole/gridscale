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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Properties;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.log4j.Logger;


/**
 * A wrapper for the SSLSocketFactory to work aroung jdk1.4 ignoring the timeout system settings.
 * Also support timeout config values.
 *
 * @author Steve Hicks S.J.C.Hicks@rl.ac.uk, Joni Hahkala joni.hahkala@cern.ch, Frantisek Dvorak
 */
public class TimeoutSSLSocketFactory extends SSLSocketFactory {
    /** the logger instance */
    private static final Logger LOGGER = Logger.getLogger(TimeoutSSLSocketFactory.class.getName());

    /** System property defining the connect timeout in milliseconds. */
    private static final String CONNECT_TIMEOUT_SYSTEM_PROPERTY = "sun.net.client.defaultConnectTimeout";

    /** System property defining the read timeout in milliseconds. */
    private static final String READ_TIMEOUT_SYSTEM_PROPERTY = "sun.net.client.defaultReadTimeout";

    /** Factory that does the real work. */
    private SSLSocketFactory m_realFactory;

    /** The default connect timeout to use in milliseconds. */
    private int m_connectTimeout = 0;

    /** The default read timeout to use in milliseconds. */
    private int m_readTimeout = 0;

    /**
     * Creates a new TimedOutSSLSocketFactory.
     * 
     * @param realFactory Factory used to create sockets.
     * @param config the configuration parameters for this socket factory.
     */
    public TimeoutSSLSocketFactory(SSLSocketFactory realFactory, Properties config) {
        m_realFactory = realFactory;

        /* config parameters override */
        String readTimeoutStr = config.getProperty(ContextWrapper.SSL_TIMEOUT_SETTING);

        /* use system properties, fix for jdk 1.4 */
        if (readTimeoutStr == null) {
            readTimeoutStr = System.getProperty(READ_TIMEOUT_SYSTEM_PROPERTY);
        }

        if (readTimeoutStr != null) {
            try {
                m_readTimeout = Integer.parseInt(readTimeoutStr);
            } catch (NumberFormatException e) {
                LOGGER.error("Invalid value for property \"" + READ_TIMEOUT_SYSTEM_PROPERTY +
                    "\": " + readTimeoutStr);
            }
        }

        /* config parameters override */
        String connectTimeoutStr = System.getProperty(ContextWrapper.CONNECT_TIMEOUT);

        /* use system properties, fix for jdk 1.4 */
        if (connectTimeoutStr == null) {
            connectTimeoutStr = System.getProperty(CONNECT_TIMEOUT_SYSTEM_PROPERTY);
        }

        if (connectTimeoutStr != null) {
            try {
                m_connectTimeout = Integer.parseInt(connectTimeoutStr);
            } catch (NumberFormatException e) {
                LOGGER.error("Invalid value for property \"" + CONNECT_TIMEOUT_SYSTEM_PROPERTY +
                    "\": " + connectTimeoutStr);
            }
        }
    }

    /**
     * Sets the socket read timeout value and removes RC4 cipher that causes some problems.
     *
     * @param socket the socket to configure, must be SSLSocket.
     *
     * @throws SocketException thrown in case of error.
     */
    public void setTimeout(Socket socket) throws SocketException {
    	if(!(socket instanceof SSLSocket)){
    		throw new IllegalArgumentException("Non SSLSocket given to the setTimeout method.");
    	}
    	
        if (m_readTimeout > 0) {
            LOGGER.debug("Read timeout set to:" + m_readTimeout);
            socket.setSoTimeout(m_readTimeout);
        }
        
        String[] ciphers;
        ArrayList<String> newCiphers;
        int i;

        // disable RC4 ciphers (Java x Globus problems)
        ciphers = ((SSLSocket) socket).getEnabledCipherSuites();
        newCiphers = new ArrayList<String>(ciphers.length);
        for (i = 0; i < ciphers.length; i++) {
            if (ciphers[i].indexOf("RC4") == -1)
                newCiphers.add(ciphers[i]);
        }
        ((SSLSocket) socket).setEnabledCipherSuites(newCiphers.toArray(new String[] {}));                    
    }

    /**
     * @see SSLSocketFactory#getDefaultCipherSuites()
     */
    public String[] getDefaultCipherSuites() {
        return m_realFactory.getDefaultCipherSuites();
    }

    /**
     * @see SSLSocketFactory#getSupportedCipherSuites()
     */
    public String[] getSupportedCipherSuites() {
        return m_realFactory.getSupportedCipherSuites();
    }

    /**
     * Creates socket with read timeout defined by either
     * <code>sslTimeout</code>
     * <code>sun.net.client.defaultReadTimeout</code>.
     *
     * @see javax.net.SocketFactory#createSocket()
     */
    public Socket createSocket() throws IOException {
        Socket socket = m_realFactory.createSocket();

        setTimeout(socket);

        return socket;
    }

    /**
     * Creates socket with timeout defined by either
     * <code>sslTimeout</code>
     * <code>sun.net.client.defaultReadTimeout</code>.

     * @see SSLSocketFactory#createSocket(java.net.Socket, java.lang.String,
     *      int, boolean)
     */
    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
        throws IOException {
//        System.out.println("createsocket(socket, host(" + host +"), port("+port+"))");
        Socket socket = m_realFactory.createSocket(s, host, port, autoClose);

        setTimeout(socket);

        return socket;
    }

    /**
     * Creates socket and connects with timeouts defined by either
     * <code>sslConnectTimeout</code> and
     * <code>sslTimeout</code> or
     * <code>sun.net.client.defaultConnectTimeout</code> and
     * <code>sun.net.client.defaultReadTimeout</code>.
     *
     * @see javax.net.SocketFactory#createSocket(java.lang.String, int)
     */
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        Socket socket;

        socket = m_realFactory.createSocket();

        setTimeout(socket);

        if (m_connectTimeout > 0) {
            socket.connect(new InetSocketAddress(host, port), m_connectTimeout);
        } else {
            socket.connect(new InetSocketAddress(host, port));
        }

        return socket;
    }

    /**
     * Creates socket and connects with timeouts defined by either
     * <code>sslConnectTimeout</code> and
     * <code>sslTimeout</code> or
     * <code>sun.net.client.defaultConnectTimeout</code> and
     * <code>sun.net.client.defaultReadTimeout</code>.
     *
     * @see javax.net.SocketFactory#createSocket(java.net.InetAddress, int)
     */
    public Socket createSocket(InetAddress host, int port)
        throws IOException {
        Socket socket;

        socket = m_realFactory.createSocket();

        setTimeout(socket);

        if (m_connectTimeout > 0) {
            socket.connect(new InetSocketAddress(host, port), m_connectTimeout);
        } else {
            socket.connect(new InetSocketAddress(host, port));
        }

        return socket;
    }

    /**
     * Creates socket with timeout defined by either
     * <code>sslTimeout</code>
     * <code>sun.net.client.defaultReadTimeout</code>.

     * @see javax.net.SocketFactory#createSocket(java.lang.String, int,
     *      java.net.InetAddress, int)
     */
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
        throws IOException, UnknownHostException {
        Socket socket = m_realFactory.createSocket(host, port, localHost, localPort);

        setTimeout(socket);

        return socket;
    }

    /**
     * Creates socket with timeout defined by either
     * <code>sslTimeout</code>
     * <code>sun.net.client.defaultReadTimeout</code>.

     * @see javax.net.SocketFactory#createSocket(java.net.InetAddress, int,
     *      java.net.InetAddress, int)
     */
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
        int localPort) throws IOException {
        Socket socket = m_realFactory.createSocket(address, port, localAddress, localPort);

        setTimeout(socket);

        return socket;
    }
}

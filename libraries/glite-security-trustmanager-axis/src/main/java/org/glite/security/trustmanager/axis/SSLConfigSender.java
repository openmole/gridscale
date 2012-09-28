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

package org.glite.security.trustmanager.axis;

import org.apache.axis.AxisFault;
import org.apache.axis.Handler;
import org.apache.axis.MessageContext;
import org.apache.axis.SimpleChain;
import org.apache.axis.SimpleTargetedChain;
import org.apache.axis.components.net.BooleanHolder;
import org.apache.axis.configuration.SimpleProvider;
import org.apache.axis.transport.http.HTTPSender;
import org.apache.axis.transport.http.HTTPTransport;
import org.apache.axis.transport.http.SocketHolder;

import org.glite.security.trustmanager.ContextWrapper;
import org.glite.security.util.HostNameChecker;

import java.io.IOException;

import java.net.InetSocketAddress;

import java.security.GeneralSecurityException;

import java.util.Properties;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;


/**
 * A HTTPSender that can be used to configure the trustmanager per message basis.
 *
 * @author Paolo Andreetto paolo.andreetto@pd.infn.it
 * @author Joni Hahkala joni.hahkala@cern.ch
 *
 */
public class SSLConfigSender extends HTTPSender {
    /**
	 * The serial number of the class.
	 */
	private static final long serialVersionUID = 9079875049535134492L;
	/** The internal holder of the configuration parameters. */
    private Properties m_sslConfig = null;
    private boolean m_log4jConfig = true;

    /**
     * Creates a new SSLConfigSender object.
     *
     * @param properties The configuration parameters to use for the connections.
     *
     * @throws AxisFault thrown incase of problems.
     */
    public SSLConfigSender(Properties properties) throws AxisFault {
        m_sslConfig = properties;
    }

    /**
     * Creates a new SSLConfigSender object.
     *
     * @param properties The configuration parameters to use for the connections.
     *
     * @throws AxisFault thrown incase of problems.
     */
    public SSLConfigSender(Properties properties, boolean log4jConfig) throws AxisFault {
        m_sslConfig = properties;
        m_log4jConfig = log4jConfig;
    }

    /**
     * Utility function to simplify the use of this class.
     *
     * @param config the configuration parameters for the ssl.
     *
     * @return the SimpleProvider to use for the service locator.
     *
     * @throws AxisFault thrown in case or problems.
     */
    public static SimpleProvider getTransportProvider(Properties config)
        throws AxisFault {
        SimpleProvider transportProvider = new SimpleProvider();
        Handler sslHandler = new SSLConfigSender(config);
        Handler transport = new SimpleTargetedChain(new SimpleChain(), sslHandler, new SimpleChain());
        transportProvider.deployTransport(HTTPTransport.DEFAULT_TRANSPORT_NAME, transport);

        return transportProvider;
    }

    /**
     * Utility function to simplify the use of this class.
     *
     * @param config the configuration parameters for the ssl.
     *
     * @return the SimpleProvider to use for the service locator.
     *
     * @throws AxisFault thrown in case or problems.
     */
    public static SimpleProvider getTransportProvider(Properties config, boolean log4jConfig)
        throws AxisFault {
        SimpleProvider transportProvider = new SimpleProvider();
        Handler sslHandler = new SSLConfigSender(config, log4jConfig);
        Handler transport = new SimpleTargetedChain(new SimpleChain(), sslHandler, new SimpleChain());
        transportProvider.deployTransport(HTTPTransport.DEFAULT_TRANSPORT_NAME, transport);

        return transportProvider;
    }

    /**
     * The method Axis calls to get a socket.
     *
     * @param sockHolder The holder that holds the Socket
     * @param msgContext the message context for the messages.
     * @param protocol the protocol to use, for example 'http' or 'https'.
     * @param host The host to connect to.
     * @param port The port to connect to.
     * @param timeout The timeout in milliseconds for the connection.
     * @param otherHeaders possible other headers.
     * @param useFullURL whether to use full headers or not.
     *
     * @throws IOException in case the credential reading fails.
     * @throws GeneralSecurityException in case there is other problems witht he credentials.
     * @throws Exception thrown in case the protocol is http and there is problems creating the socket.
     */
    protected void getSocket(SocketHolder sockHolder, MessageContext msgContext, String protocol,
        String host, int port, int timeout, StringBuffer otherHeaders, BooleanHolder useFullURL)
        throws IOException, GeneralSecurityException, Exception {
        if (protocol.equalsIgnoreCase("https")) {
            final ContextWrapper contextWrapper = new ContextWrapper(m_sslConfig, m_log4jConfig);
            final SSLSocketFactory fac = contextWrapper.getSocketFactory();

            SSLSocket socket = (SSLSocket) fac.createSocket();

            socket.setEnabledProtocols(new String[] { contextWrapper.getContext().getProtocol() });
            socket.setUseClientMode(true);

            // if timeout is given use it overriding the defaults and system settings and contextfactory properties
            if (timeout >= 0) {
                socket.connect(new InetSocketAddress(host, port), timeout);
                socket.setSoTimeout(timeout);
            } else { // if no timeout is given let the contextwrapper set them
                socket.connect(new InetSocketAddress(host, port));
            }
            
            String checkHostname = m_sslConfig.getProperty(ContextWrapper.HOSTNAME_CHECK, ContextWrapper.HOSTNAME_CHECK_DEFAULT).trim().toLowerCase();
            
            if(checkHostname.startsWith("t") || checkHostname.startsWith("y")){
                HostNameChecker.checkHostname(host, socket);
            }

            sockHolder.setSocket(socket);
        } else {
            super.getSocket(sockHolder, msgContext, protocol, host, port, timeout, otherHeaders,
                useFullURL);
        }
    }
}

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

import org.apache.axis.components.net.BooleanHolder;
import org.apache.axis.components.net.SecureSocketFactory;

import org.apache.log4j.Logger;

import org.glite.security.trustmanager.ContextWrapper;
import org.glite.security.util.HostNameChecker;

import java.util.Hashtable;
import java.util.Properties;

import javax.net.ssl.SSLSocketFactory;
import java.net.InetSocketAddress;

/**
 * The Axis socketfactory used for interfacing with axis 1.1 and 1.2.
 *
 * @author  Joni Hahkala
 * Created on September 3, 2002, 9:04 PM
 */
public class AXISSocketFactory implements SecureSocketFactory {
    /**
     * The logging facility.
     */
    private static final Logger LOGGER = Logger.getLogger("org.glite.security.trustmanager.axis.AXISSocketFactory");

    /** Thread local storage for the thread specific client properties. */
    private static ThreadLocal theAXISSocketFactoryProperties = new ThreadLocal();

    /**
     * Returns the current configuration properties.
     *  
     * @return java.util.Properties with the settings of the current thread. 
     */
    public static Properties getCurrentProperties () {
        Properties thisProperties = (Properties) theAXISSocketFactoryProperties.get();

        // if nothing was set, then fall back to global settings
        if (thisProperties == null) {
            thisProperties = System.getProperties();
        }

        return thisProperties;
    }

    /** 
     * Clears the thread specific properties. 
     */
    public static void clearCurrentProperties() {
        theAXISSocketFactoryProperties.set(null);
    }

    /**
     * Sets the current properties for the socket factory.
     *  
     * @param cp the Properties associated with the current thread 
     */
    public static void setCurrentProperties(Properties cp) {
        theAXISSocketFactoryProperties.set(cp);
    }

    /**
     * Creates a new instance of AxisSocketFactory
     * 
     * @param attributes
     */
    public AXISSocketFactory(final Hashtable attributes) {
        // TODO: ignore the attributes for now. But should act on them somehow.
    }

    /**
     * Creates a socket and connects to the given host, verifying the certificate and possibly checking it against the hostname (depends on the hostname check variable in configuration). 
     *
     * @param host The host to connect to.
     * @param port The port to connect to.
     * @param otherHeaders ignored.
     * @param useFullURL ignored.
     *
     * @return The open ssl socket.
     *
     * @throws Exception in case connection open fails, ssl handshaking fails or hostname verification fails.
     */
    public java.net.Socket create(final java.lang.String host, final int port, final StringBuffer otherHeaders, final BooleanHolder useFullURL) throws Exception {
        try {
            final ContextWrapper contextWrapper = new ContextWrapper(getCurrentProperties());
            final SSLSocketFactory fac = contextWrapper.getSocketFactory();

            javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket)fac.createSocket();
            socket.setEnabledProtocols(new String[] { contextWrapper.getContext().getProtocol() });
            String timeoutString = getCurrentProperties ().getProperty(ContextWrapper.SSL_TIMEOUT_SETTING, ContextWrapper.TIMEOUT_DEFAULT);
            int timeout = Integer.parseInt(timeoutString);
            socket.setSoTimeout(timeout);
            socket.connect(new InetSocketAddress(host,port), timeout);
            String checkHostname = getCurrentProperties().getProperty(ContextWrapper.HOSTNAME_CHECK, ContextWrapper.HOSTNAME_CHECK_DEFAULT).trim().toLowerCase();
            
            if(checkHostname.startsWith("t") || checkHostname.startsWith("y")){
                HostNameChecker.checkHostname(host, socket);
            }

            return socket;
        } catch (Exception e) {
            LOGGER.fatal("create() : SSL socket creation failed : " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Creates a server factory.
     *
     * @return the created server socketfactory.
     *
     * @throws java.lang.Exception in case the socket creation failed.
     */
    public Object createServerFactory() throws java.lang.Exception {
        LOGGER.info("createServerFactory() : Creating a server factory");

        try {
            final ContextWrapper contextWrapper = new ContextWrapper(getCurrentProperties());

            return contextWrapper.getServerSocketFactory();
        } catch (Exception e) {
            LOGGER.fatal("SSL socket factory creation failed : " + e.getMessage(), e);
            throw e;
        }
    }
}

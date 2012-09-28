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

import org.apache.tomcat.util.net.SSLImplementation;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.ServerSocketFactory;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.util.Properties;

import javax.net.ssl.SSLSession;


/**
 * @author Joni Hahkala
 *
 * The main Tomcat 5 (+?) glue class

 * Created on Sep 15, 2004
 */
public class TMSSLImplementation extends SSLImplementation {
    /**
     * The logging facility.
     */
    // It seems that when this class is called no loggers are configured...
    private static org.apache.commons.logging.Log LOGGER = org.apache.commons.logging.LogFactory.getLog(TMSSLImplementation.class);

    /**
     * The constructor for the class, does nothing except checks that the actual
     * ssl implementation TrustManager is present.
     * @throws ClassNotFoundException in case the util-java is not installed and thus ContextWrapper class isn't found.
     */
    public TMSSLImplementation() throws ClassNotFoundException {
        // Reading resources of JAR file
        InputStream in = null;
        Properties props = null;
        try {
            in = getClass().getClassLoader().getResourceAsStream("TMTversion.properties");
            props = new Properties();
            props.load(in);
            // ugly, but no loggers are configured, so this seems to be the only way to get the info out.
            System.out.println("Trustmanager-tomcat v" + props.getProperty("module.version") + " starting.");
        } catch (Exception e) {
            // ugly, but no loggers are configured, so this seems to be the only way to get the info out.
            System.out.println("Trustmanager-tomcat starting, trustmanager-tomcat version information loading failed. " + in + ", " + props);
        }
        try {
            in = getClass().getClassLoader().getResourceAsStream("TMversion.properties");
            props = new Properties();
            props.load(in);
            // ugly, but no loggers are configured, so this seems to be the only way to get the info out.
            System.out.println("Using trustmanager library v" + props.getProperty("module.version") + ".");
        } catch (Exception e) {
            // ugly, but no loggers are configured, so this seems to be the only way to get the info out.
            System.out.println("Trustmanager-tomcat starting, trustmanager library version information loading failed. " + in + ", " + props);
        }
        // Check to see if glite-security-util-java is floating around somewhere, will fail if it is not found throwing
        // an exception, this forces early failure in case there is no hope of it working anyway.
        Class.forName("org.glite.security.trustmanager.ContextWrapper");
    }

    /*
     * The Method that returns the name of the SSL implementation
     *
     * The string "TM-SSL" is returned (shorthand for TrustManager SSL)
     *
     * @see org.apache.tomcat.util.net.SSLImplementation#getImplementationName()
     */
    public String getImplementationName() {
        return "TM-SSL";
    }

    /*
     * The method used by Tomcat to get the actual SSLServerSocketFactory to use
     * to create the ServerSockets.
     *
     * @see org.apache.tomcat.util.net.SSLImplementation#getServerSocketFactory()
     */
    public ServerSocketFactory getServerSocketFactory() {
        return new TMSSLServerSocketFactory();
    }

    /*
     * The method used to get the class that provides the SSL support functions.
     * Current implementation reuses Tomcat's own JSSE SSLSupport class as we
     * use JSSE internally too (with modifications to the certificate path
     * checking of course.
     *
     * @see org.apache.tomcat.util.net.SSLImplementation#getSSLSupport(java.net.Socket)
     */
    public SSLSupport getSSLSupport(Socket arg0) {
        try {
            JSSEImplementation impl = new JSSEImplementation();

            return impl.getSSLSupport(arg0);
        } catch (ClassNotFoundException e) {
            LOGGER.fatal("Internal server error, JSSEImplementation class creation failed:", e);

            return null;
        }
    }

    /*
     * The method used to get the class that provides the SSL support functions.
     * Current implementation reuses Tomcat's own JSSE SSLSupport class as we
     * use JSSE internally too (with modifications to the certificate path
     * checking of course.
     *
     * @see org.apache.tomcat.util.net.SSLImplementation#getSSLSupport(java.net.ssl.SSLSession)
     */
	public SSLSupport getSSLSupport(SSLSession arg0) {
	    try {
            JSSEImplementation impl = new JSSEImplementation();
            // hack to get past tomcat5 missing this method and tomcat6 requiring it.
            java.lang.reflect.Method method;
            
            try {
				method=impl.getClass().getMethod("getSSLSupport", arg0.getClass());
			} catch (NoSuchMethodException e) {
				// this is tomcat5, so no action.
				return null;
			}

            try {
				return (SSLSupport)method.invoke(impl, arg0);
			} catch (IllegalArgumentException e) {
	            LOGGER.fatal("Internal server error, JSSEImplementation class creation failed:", e);
			} catch (IllegalAccessException e) {
	            LOGGER.fatal("Internal server error, JSSEImplementation class creation failed:", e);
			} catch (InvocationTargetException e) {
	            LOGGER.fatal("Internal server error, JSSEImplementation class creation failed:", e);
			}
			return null;
        } catch (ClassNotFoundException e) {
            LOGGER.fatal("Internal server error, JSSEImplementation class creation failed:", e);

            return null;
        }
	}
}

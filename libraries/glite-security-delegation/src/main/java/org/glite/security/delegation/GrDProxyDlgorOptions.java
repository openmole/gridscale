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

package org.glite.security.delegation;

import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.util.Properties;


/**
 * Options manager for Delegator (client) side
 */
public class GrDProxyDlgorOptions {
    static Logger logger = Logger.getLogger(GrDProxyDlgorOptions.class);
//    private Properties props = null;
    private String issuerCertFile = null;
    private String issuerKeyFile = null;
    private String issuerPass = null;
    private String issuerProxyFile = null;
    private String delegationStorage = null;

    /**
     * Constructor of class
     * @param filename file containing delegator options
     */
    public GrDProxyDlgorOptions(String filename) throws IOException {
    	logger.debug("Loading client options form: " + filename);

    	IOException saveException = null;
    	
    	InputStream st = null;
    	try {
    		st = new FileInputStream(filename);
    	} catch(FileNotFoundException e) {
    	    saveException = e;
    	}
    	
    	if(st == null) {
    		st = GrDProxyDlgorOptions.class.getClassLoader().getResourceAsStream(filename);
    	}
    	
    	if(st == null){
    	    // throw the exception from file search if it was something fishy.
    	    if(saveException != null && !(saveException instanceof FileNotFoundException)){
    	        throw saveException;
    	    }
    	    // otherwise throw generic file not found error.
    	    throw new IOException("No file nor resource named '" + filename + "' found.");
    	}
    	
    	Properties props = new Properties();
    	props.load(st);
    	init(props);
    }

    /**
     * The default constructor.
     */
    public GrDProxyDlgorOptions() {
        // empty.
    }

    /**
     * Initializer
     */
    private void init(Properties props) {
        this.issuerCertFile = props.getProperty("issuerCertFile");
        this.issuerKeyFile = props.getProperty("issuerKeyFile");
        this.issuerPass = props.getProperty("issuerPass");
        this.issuerProxyFile = props.getProperty("issuerProxyFile");
        this.delegationStorage = props.getProperty("delegationStorage");
    }

    /**
     * Getting Delegator certificate file
     * @return Certificate
     */
    public String getDlgorCertFile() {
        if (this.issuerCertFile == null) {
            return (GrDPX509Util.getDefaultCertFile());
        }
        return this.issuerCertFile;
    }

    /**
     * Getting Delegator key file
     * @return KeyFile
     */
    public String getDlgorKeyFile() {
        if (this.issuerKeyFile == null) {
            return (GrDPX509Util.getDefaultKeyFile());
        }
        return this.issuerKeyFile;
    }

    /**
     * Getting Delegator proxy file
     * @return ProxyFile
     */
    public String getDlgorProxyFile() {
        if (this.issuerProxyFile == null) {
            return (GrDPX509Util.getDefaultProxyFile());
        }
        return this.issuerProxyFile;
    }

    
    /**
     * Getting password of private key
     * @return Private Key
     */
    public String getDlgorPass() {
        return this.issuerPass;
    }

    /**
     * Getting delegation storage directory
     * @return location of stored credentials
     */
    public String getDlgorStorage() {
        if (this.delegationStorage == null) {
            return ("\tmp");
        }
        return this.delegationStorage;
    }

    /**
     * Setting Delegator certificate file
     * @param cf Certificate File
     */
    public void setDlgorCertFile(String cf) {
        this.issuerCertFile = cf;
    }

    /**
     * Setting Delegator key file
     * @param kf KeyFile
     */
    public void setDlgorKeyFile(String kf) {
        this.issuerKeyFile = kf;
    }
    
    /**
     * Setting Delegator proxy file
     * @param proxy Proxy file
     */
    public void setDlgorProxyFile(String proxy) {
        this.issuerProxyFile = proxy;
    }

    /**
     * Setting password of private key
     * @param pass Private Key decryption password
     */
    public void setDlgorPass(String pass) {
        this.issuerPass = pass;
    }

    /**
     * Setting delegation storage directory
     * @param strg location of stored credentials
     */
    public void setDlgorStorage(String strg) {
        this.delegationStorage = strg;
    }
}

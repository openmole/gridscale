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

//import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.util.Properties;


/**
 * Options manager for Delegatee (service) side
 */
public class GrDProxyDlgeeOptions {
	
	// The local logger object
//    private static Logger logger = Logger.getLogger(GrDProxyDlgeeOptions.class);
	
    private String dlgeeDN = null;
    private String dlgeePass = null;
    private String delegationStorage = null;
    private String dlgeeStorageFactory = null;
    private String dlgeeStorageDbPool = null;
    private String proxyFile = null;
    private int dlgeeKeySize = -1;

    /**
     * Constructor of class
     * @param filename file containing delegatee options
     */
    public GrDProxyDlgeeOptions(String filename) throws IOException {
    	
    	InputStream st = null;
    	try {
    		st = new FileInputStream(filename);
    	} catch(FileNotFoundException e) {
    	    // fail silently, try resource next.
    		
    	}
    	
    	if(st == null) {
    		st = GrDProxyDlgeeOptions.class.getClassLoader().getResourceAsStream(filename);
    	}
    	
    	Properties props = new Properties();
    	props.load(st);
    	init(props);
    }
    
    /**
     * Constructor of class
     * @param props Properties object containing necessary values
     */
    public GrDProxyDlgeeOptions(Properties props) {
    	init(props);
    }

    /**
     * The constructor of the class.
     */
    public GrDProxyDlgeeOptions() {
        // empty.
    }

    /**
     * Initializer
     */
    public void init(Properties props) {
        this.dlgeeDN = props.getProperty("dlgeeDN");

        this.dlgeePass = props.getProperty("dlgeePass");
        this.proxyFile = props.getProperty("proxyFile");
        this.delegationStorage = props.getProperty("delegationStorage");
        this.dlgeeStorageFactory = props.getProperty("dlgeeStorageFactory");
        this.dlgeeStorageDbPool = props.getProperty("dlgeeStorageDbPool");
        this.dlgeeKeySize = Integer.parseInt(props.getProperty("dlgeeKeySize"));
    }

    /**
     * Getting delegatee's DN
     * @return the DN
     */
    public String getDlgeeDN() {
        return this.dlgeeDN;
    }

    /**
     * Getting delegatee's password
     * @return password assigned to delegatee
     */
    public String getDlgeePass() {
        return this.dlgeePass;
    }

    /**
     * Getting the name of proxy file
     * @return certificat proxy file name
     */
    public String getDlgeeProxyFile() {
        if (this.proxyFile == null)
            return (GrDPX509Util.getDefaultProxyFile());

        return this.proxyFile;
    }

    /**
     * Getting path to the storage of Proxy certificates
     * @return path to proxy certificates
     */
    public String getDlgeeStorage() {
        if (this.delegationStorage == null)
            return ("\tmp");
        
        return this.delegationStorage;
    }
    
    /**
     * Getting the type of Storage Type used by the DLGEE
     * @return type of Storage Type used by the DLGEE
     */
    public String getDlgeeStorageFactory() {
        return this.dlgeeStorageFactory;
    }

    /**
     * Getting the pool name of the db storage
     * @return pool name of the db storage
     */
    public String getDlgeeStorageDbPool() {
        return this.dlgeeStorageDbPool;
    }
    
    /**
     * Get the key size to be used
     * @return Key size to be used
     */
    public int getDlgeeKeySize() {
        return this.dlgeeKeySize;
    }
    
    /**
     * setting delegatee's DN
     * @param dn DN
     */
    public void setDlgeeDN(String dn) {
        this.dlgeeDN = dn;
    }

    /**
     * setting delegatee's password
     * @param dgp delegatee password
     */
    public void setDlgeePass(String dgp) {
        this.dlgeePass = dgp;
    }

    /**
     * setting the name of proxy file
     * @param pf proxy file
     */
    public void setDlgeeProxyFile(String pf) {
        this.proxyFile = pf;
    }

    /**
     * setting path to the storage of Proxy certificates
     * @param stg storage
     */
    public void setDlgeeStorage(String stg) {
        this.delegationStorage = stg;
    }
    
    /**
     * Setting the storage type being used by the DLGEE
     * @param stgType storage type
     */
    public void setDlgeeStorageFactory(String stgType) {
        this.dlgeeStorageFactory = stgType;
    }
    
    /**
     * Setting the storage db pool name
     * @param stgDbPool storage pool name
     */
    public void setDlgeeStorageDbPool(String stgDbPool) {
        this.dlgeeStorageDbPool = stgDbPool;
    }
    
    /**
     * Setting generated delegation key size.
     * @param keySize   the key size in bits
     */
    public void setDlgeeKeySize(int keySize) {
        this.dlgeeKeySize = keySize;
    }
}

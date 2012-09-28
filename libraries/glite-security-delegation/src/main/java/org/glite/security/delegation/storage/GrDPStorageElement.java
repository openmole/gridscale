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

package org.glite.security.delegation.storage;

import java.util.Date;
import org.glite.security.util.DNHandler;

/**
 * Representation of a delegated proxy entry in storage.
 *
 * <p>A delegated proxy entry in storage contains the following properties:</p>
 * <ul>
 * <li><i>delegationID</i>: The delegation ID assigned to the delegated proxy</li>
 * <li><i>dn</i>: The DN associated with the delegated proxy</li>
 * <li><i>vomsAttributes</i>: The list of voms attributes contained inside the delegated proxy</li>
 * <li><i>certificate</i>: The actual delegated proxy, including its private key.</li>
 * <li><i>terminationTime</i>: The termination time of the delegated proxy</li>
 * </ul>
 */
public class GrDPStorageElement {

	private String delegationID = null;
	
	private String DN = null;
	
	private String[] vomsAttributes = null;
	
	private String certificate = null;
	
    private Date terminationTime = null;
    
	/**
	 * Retrieves the delegation id of the delegated proxy.
	 *
	 * @return The delegation id of the delegated proxy.
	 */
	public String getDelegationID() {
		return this.delegationID;
	}
	
	/**
	 * Retrieves the dn of the owner of the delegated proxy.
	 *
	 * @return The dn of the owner of the delegated proxy.
	 */
	public String getDN() {
		return this.DN;
	}
    
    /**
     * Retrieves the dn of the owner of the delegated proxy in X500 format.
     * 
     * @return The DN in X500 format.
     */
    public String getDNasX500() {
        return DNHandler.getDNRFC2253(this.DN).getX500();
    }
	
	/**
	 * Retrieves the list of voms attributes contained in the delegated proxy.
	 *
	 * @return The list of voms attributes in the delegated proxy.
	 */
	public String[] getVomsAttributes() {
		return this.vomsAttributes;
	}
	
	/**
	 * Retrieves the delegated proxy, including its private key.
	 *
	 * @return The delegated proxy.
	 */
	public String getCertificate() {
		return this.certificate;
	}
    
    /**
     * Retrieves the termination time of the delegated proxy.
     *
     * @return The termination time of the delegated proxy.
     */
    public Date getTerminationTime() {
        return this.terminationTime;
    }
	
	/**
	 * Sets the delegation id of the delegated proxy.
	 *
	 * @param delegationID The delegation id to be assigned to the delegated proxy.
	 */
	public void setDelegationID(String delegationID) {
		this.delegationID = delegationID;
	}
	
	/**
	 * Sets the dn associated with the delegated proxy.
	 *
	 * @param dn The dn to be associated with the delegated proxy.
	 */
	public void setDN(String dn) {
		this.DN = dn;
	}
    
    /**
     * Set the DN associated of the owner of the delegated proxy from X500 format.
     * 
     * @param dn    The DN in X500 format.
     */
    public void setDNasX500(String dn) {
        this.DN = DNHandler.getDNRFC2253(dn).getRFCDN();
    }
	
	/**
	 * Sets the list of voms attributes inside the delegated proxy.
	 *
	 * @param vomsAttributes The list of voms attributes inside the delegated proxy.
	 */
	public void setVomsAttributes(String[] vomsAttributes) {
		this.vomsAttributes = vomsAttributes;
	}
	
	/**
	 * Sets the delegated proxy.
	 *
	 * @param certificate The delegated proxy.
	 */
	public void setCertificate(String certificate) {
		this.certificate = certificate;
	}
	    
    /**
     * Sets the termination time of the delegated proxy.
     *
     * @param terminationTime The termination time of the delegated proxy.
     */
    public void setTerminationTime(Date terminationTime) {
        this.terminationTime = terminationTime;
    }
}

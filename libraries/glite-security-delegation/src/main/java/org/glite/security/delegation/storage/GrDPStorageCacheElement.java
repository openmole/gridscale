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

import org.glite.security.util.DNHandler;

/**
 * Representation of a delegation request entry in storage cache.
 *
 * <p>A delegation request entry in storage cache contains the following properties:</p>
 * <ul>
 * <li><i>delegationID</i>: The delegation ID assigned to the delegation request</li>
 * <li><i>dn</i>: The DN associated with the delegation request</li>
 * <li><i>vomsAttributes</i>: The list of voms attributes to be contained inside the delegated proxy</li>
 * <li><i>certificateRequest</i>: The actual delegated proxy request</li>
 * <li><i>privateKey</i>: The private key associated with the delegation request</li>
 * </ul>
 */
public class GrDPStorageCacheElement {

	private String delegationID = null;
	
	private String DN = null;
	
	private String[] vomsAttributes = null;
	
	private String certificateRequest = null;
	
	private String privateKey = null;
	
	/**
	 * Retrieves the delegation id of the delegation request.
	 *
	 * @return The delegation id of the delegation request.
	 */
	public String getDelegationID() {
		return this.delegationID;
	}
	
	/**
	 * Retrieves the dn of the owner of the delegation request.
	 *
	 * @return The dn of the owner of the delegation request.
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
	 * Retrieves the list of voms attributes to be contained in the delegated proxy.
	 *
	 * @return The list of voms attributes to be contained in the delegated proxy.
	 */
	public String[] getVomsAttributes() {
		return this.vomsAttributes;
	}
	
	/**
	 * Retrieves the delegated proxy request.
	 *
	 * @return The delegated proxy request.
	 */
	public String getCertificateRequest() {
		return this.certificateRequest;
	}
	
	/**
	 * Retrieves the private key associated with the delegation request.
	 *
	 * @return The private key associated with the delegation request.
	 */
	public String getPrivateKey() {
		return this.privateKey;
	}
	
	/**
	 * Sets the delegation id of the delegation request.
	 *
	 * @param delegationID The delegation id to be assigned to the delegation request.
	 */
	public void setDelegationID(String delegationID) {
		this.delegationID = delegationID;
	}
	
	/**
	 * Sets the dn associated with the delegation request.
	 *
	 * @param dn The dn to be associated with the delegation request.
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
	 * Sets the list of voms attributes to be contained inside the delegated proxy.
	 *
	 * @param vomsAttributes The list of voms attributes to be contained inside the delegated proxy.
	 */
	public void setVomsAttributes(String[] vomsAttributes) {
		this.vomsAttributes = vomsAttributes;
	}
	
	/**
	 * Sets the delegated proxy (credential) request.
	 *
	 * @param certificate The delegated proxy (credential) request.
	 */
	public void setCertificateRequest(String certificate) {
		this.certificateRequest = certificate;
	}
	
	/**
	 * Sets the private key associated with the delegation request.
	 *
	 * @param privateKey The private key associated with the delegation request.
	 */
	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}
}

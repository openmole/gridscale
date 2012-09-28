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

/**
 * Defines the internal interface to information storage in the delegation service.
 * <br/>
 * 
 * <p>It is assumed that two different storage areas exist:<br/>
 * <ul>
 * <li><i>storage</i>: holds the delegated proxies information: proxy certificate, private key</li>
 * <li><i>storage cache</i>: holds the certificate requests information (certificate request, private key). This 
 * is transitional information kept between the proxy request and the put proxy operations from the client.</li>
 * </ul>
 * Both areas also hold additional information about the request/proxy: list of VOMS attributes, client DN.</p>
 *
 * <p>The exposed operations match the ones that exist in a normal relational database: 
 * insert / update / delete / find.</p>
 *
 */
public interface GrDPStorage {
	
	/**
	 * Insert new delegation request into storage cache area.
	 *
	 * @param elem Object containing the information about the delegation request.
	 * @throws GrDPStorageException Failed to store new delegation request in storage cache area.
	 */	
	public void insertGrDPStorageCacheElement(GrDPStorageCacheElement elem) 
		throws GrDPStorageException;

	/**
	 * Updates existing delegation request in storage cache area.
	 *
	 * @param elem Object containing the information about the delegation request.
	 * @throws GrDPStorageException Failed to storage new delegation request in storage cache area.
	 */	
	public void updateGrDPStorageCacheElement(GrDPStorageCacheElement elem) 
		throws GrDPStorageException;
	
	/**
	 * Retrieves an existing delegation request from the storage cache area.
	 *
	 * @param delegationID The id of the delegation request to be returned.
	 * @param DN The dn of the user owning the delegation request.
	 * @return The object containing the information on the delegation request.
	 * @throws GrDPStorageException Could not retrieve a delegation request because either it does not
	 * exist or an error occured while tried to access it.
	 */	
	public GrDPStorageCacheElement findGrDPStorageCacheElement(String delegationID, String DN)
		throws GrDPStorageException;
	
	/**
	 * Deletes an existing delegation request.
	 *
	 * @param delegationID The id of the delegation request to be deleted.
	 * @param DN The dn of the owner of the delegation request.
	 * @throws GrDPStorageException Failed to delete the delegation request as either it does not exist
	 * or could not be accessed.
	 */	
	public void deleteGrDPStorageCacheElement(String delegationID, String DN)
		throws GrDPStorageException;
	
	/**
	 * Insert new delegated proxy into storage area.
	 *
	 * @param elem Object containing the information about the delegation proxy.
	 * @throws GrDPStorageException Failed to storage new delegation proxy in storage area.
	 */	
	public void insertGrDPStorageElement(GrDPStorageElement elem) 
		throws GrDPStorageException;

	/**
	 * Updates existing delegated proxy information in storage area.
	 *
	 * @param elem Object containing the information about the delegated proxy.
	 * @throws GrDPStorageException Failed to store new delegated proxy in storage area.
	 */	
	public void updateGrDPStorageElement(GrDPStorageElement elem) 
		throws GrDPStorageException;
	
	/**
	 * Retrieves an existing delegated proxy from the storage area.
	 *
	 * @param delegationID The id of the delegated proxy to be returned.
	 * @param DN The dn of the user owning the delegated proxy.
	 * @return The object containing the information on the delegated proxy.
	 * @throws GrDPStorageException Could not retrieve a delegated proxy because either it does not
	 * exist or an error occured while tried to access it.
	 */	
	public GrDPStorageElement findGrDPStorageElement(String delegationID, String DN) 
		throws GrDPStorageException;
	
	/**
	 * Deletes an existing delegated proxy.
	 *
	 * @param delegationID The id of the delegated proxy to be deleted.
	 * @param DN The dn of the owner of the delegated proxy.
	 * @throws GrDPStorageException Failed to delete the delegated proxy as either it does not exist
	 * or could not be accessed.
	 */	
	public void deleteGrDPStorageElement(String delegationID, String DN)
		throws GrDPStorageException;
	
}

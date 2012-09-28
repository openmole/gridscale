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

import org.glite.security.delegation.GrDProxyDlgeeOptions;

/**
 * Allows creates of new instances of the database implementation of the GrDPStorage interface.
 */
public class GrDPStorageDatabaseFactory extends GrDPStorageFactory {
	
	/**
	 * Creates a new GrDPStorage instance and returns it to the user.
	 *
	 * The actual instance created is a GrDPStorageDatabase object.
	 *
	 * @return The storage object that interfaces the storage backend.
	 */
	public GrDPStorage createGrDPStorage(
                    GrDProxyDlgeeOptions dlgeeOptions) throws GrDPStorageException {
		// Make sure the class is loaded
		try {
			Class.forName(GrDPStorageDatabase.class.getName());
		} catch(ClassNotFoundException e) {
			throw new GrDPStorageException("Failed to find '" +
					GrDPStorageDatabase.class.getName() + "' in classpath.");
		}
		// Return a new instance of the class
		return new GrDPStorageDatabase(dlgeeOptions);
	}
	
}

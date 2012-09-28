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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

import org.apache.log4j.Logger;
import org.glite.security.delegation.GrDPConstants;
import org.glite.security.delegation.GrDPX509Util;
import org.glite.security.delegation.GrDProxyDlgeeOptions;

/**
 * This is the filesystem based implementation of the GrDPStorage interface.
 * <br/>
 *
 * <p>It provides a local filesystem archival mechanism for delegated proxies related information.</p>
 *
 * <p>Storage is separated into two different areas: storage cache and actual storage. The first one
 * holds the temporary information during a delegation request, including the certificate request,
 * the private key and the list of voms attributes expected in the proxy. The second holds the actual
 * delegated proxy information, including the private key and list of voms attributes (as above) and
 * the actual proxy.</p>
 *
 * <p>The format inside the storage is: <br/>
 * 	<pre>
 * 	&lt;storage-base-path&gt;/&lt;user-dn&gt;/&lt;dlg-id&gt;/usercert.pem
 *	&lt;storage-base-path&gt;/&lt;user-dn&gt;/&lt;dlg-id&gt;/userkey.pem
 *	&lt;storage-base-path&gt;/&lt;user-dn&gt;/&lt;dlg-id&gt;/voms.attributes
 * 	</pre>
 * </p>
 *	
 * <p>And for the storage cache:</br>
 * 	<pre>
 * 	&lt;storage-base-path&gt;/cache/&lt;user-dn&gt;/&lt;dlg-id&gt;/userreq.pem
 *	&lt;storage-base-path&gt;/cache/&lt;user-dn&gt;/&lt;dlg-id&gt;/userkey.pem
 *	&lt;storage-base-path&gt;/cache/&lt;user-dn&gt;/&lt;dlg-id&gt;/voms.attributes
 * 	</pre>
 * </p>
 *
 * <p>&lt;storage-base-path&gt; is taken from the delegationStorage property inside 
 * dlgee.properties.</p>

 * Authors: Ricardo Rocha <ricardo.rocha@cern.ch>
 */
public class GrDPStorageFilesystem implements GrDPStorage {
	
	// Class logger
	private static Logger logger = Logger.getLogger(GrDPStorageFilesystem.class);

	// Object containing DLGEE configuration parameters
//	private GrDProxyDlgeeOptions dlgeeOpt = null;

	// Directory path for storage area 
	private String storagePath = null;
    
	// Directory path for storage cache area
	private String storageCachePath = null;
    
   	/**
	 * Class constructor.
	 */
	public GrDPStorageFilesystem(GrDProxyDlgeeOptions dlgeeOpt) throws GrDPStorageException {
		
        // Save the DLGEE properties in a local variable
//        this.dlgeeOpt = dlgeeOpt;
        
		// Initialize the storage area and make sure proper access mode is set
	    storagePath = dlgeeOpt.getDlgeeStorage();
       	if(storagePath == null) {
       	    logger.debug("Failed to get proxy storage path.");
       	    throw new GrDPStorageException("Failed to get proxy storage path.");
	    }
		storageCachePath = storagePath + "/cache";

		File storageArea = new File(storagePath);
		File storageCacheArea = new File(storageCachePath);
		if(storageArea.mkdirs()) {
            if(!GrDPX509Util.changeFileMode(storagePath, 700)) {
                throw new GrDPStorageException("Failed to update access mode (read/write for " +
                        "owner only) on storage area directory: '" + storagePath + "'");
            }            
        }
		if(storageCacheArea.mkdirs()) {
            if(!GrDPX509Util.changeFileMode(storageCachePath, 700)) {
                throw new GrDPStorageException("Failed to update access mode (read/write for " +
                        "owner only) on storage area directory: '" + storageCachePath + "'");
            }            
        }
		
        // Double check if it actually there and is writable
		if(!storageArea.exists() || !storageCacheArea.exists()) {
			throw new GrDPStorageException("Storage area or cache does not exist.");
		}
        if(!storageArea.canWrite() || !storageCacheArea.canWrite()) {
            throw new GrDPStorageException("Storage area or cache is not writable for me.");
        }
       	
	}
	
	/**
	 * Insert new delegation request into storage cache area.
	 *
	 * @param elem Object containing the information about the delegation request.
	 * @throws GrDPStorageException Failed to store new delegation request in storage cache area.
	 */
	public void insertGrDPStorageCacheElement(GrDPStorageCacheElement elem) 
		throws GrDPStorageException {
		logger.debug("Entered GrDPStorageFilesystem insertGrDPStorageCacheElement.");
		
		try {
			// Store certificate request
			writeToFile(elem.getDN(), elem.getDelegationID(), "userreq.pem",
					elem.getCertificateRequest().getBytes(), true);
			
			// Store private key
			writeToFile(elem.getDN(), elem.getDelegationID(), "userkey.pem",
					elem.getPrivateKey().getBytes(), true);
		
			// Store list of VOMS attributes into file
			writeToFile(elem.getDN(), elem.getDelegationID(), "voms.attributes",
                    GrDPX509Util.toStringVOMSAttrs(elem.getVomsAttributes()).getBytes(), true);
			
		} catch(IOException e) {
			logger.error("Failure while writing to filesystem.", e);
			throw new GrDPStorageException("Internal failure.");
		}		
	}
	
	/**
	 * Updates existing delegation request in storage cache area.
	 *
	 * In this case, as it is a filesystem based implementation, the operation is equivalent to an
	 * insertion, with the contents of certificate request, private key and list of voms attributes
	 * simply being replaced.
	 *
	 * @param elem Object containing the information about the delegation request.
	 * @throws GrDPStorageException Failed to storage new delegation request in storage cache area.
	 */
	public void updateGrDPStorageCacheElement(GrDPStorageCacheElement elem) 
		throws GrDPStorageException {
		logger.debug("Entered GrDPStorageFilesystem updateGrDPStorageCacheElement.");
		
		insertGrDPStorageCacheElement(elem);	
	}
	
	/**
	 * Retrieves an existing delegation request from the storage cache area.
	 *
	 * @param delegationID The id of the delegation request to be returned.
	 * @param dn The dn of the user owning the delegation request.
	 * @return The object containing the information on the delegation request.
	 * @throws GrDPStorageException Could not retrieve a delegation request because either it does not
	 * exist or an error occured while tried to access it.
	 */
	public GrDPStorageCacheElement findGrDPStorageCacheElement(String delegationID, String dn) 
		throws GrDPStorageException {
		logger.debug("Entered GrDPStorageFilesystem findGrDPStorageCacheElement.");
		
		logger.debug("Looking for dlg id '" + delegationID + "' and dn '" + dn + "' in cache.");
		
		// Create the basic element
		GrDPStorageCacheElement elem = new GrDPStorageCacheElement();
		elem.setDelegationID(delegationID);
		elem.setDN(dn);
					
		try {
			elem.setCertificateRequest(readFromFile(dn, delegationID, "userreq.pem", true));
			elem.setPrivateKey(readFromFile(dn, delegationID, "userkey.pem", true));
			elem.setVomsAttributes(GrDPX509Util.fromStringVOMSAttrs(
                            readFromFile(dn, delegationID, "voms.attributes", true)));
		} catch(FileNotFoundException fnfe) {
		    logger.debug("Could not find entry in cache. DN '" + dn + "'; DLG ID '" + delegationID + "'.");
			return null;
		} catch(IOException ioe) {
			logger.error("Failure accessing filesystem.");
			throw new GrDPStorageException("Internal failure.");
		}

		return elem;

	}
	
	/**
	 * Deletes an existing delegation request.
	 *
	 * @param delegationID The id of the delegation request to be deleted.
	 * @param dn The dn of the owner of the delegation request.
	 * @throws GrDPStorageException Failed to delete the delegation request as either it does not exist
	 * or could not be accessed.
	 */
	public void deleteGrDPStorageCacheElement(String delegationID, String dn) 
		throws GrDPStorageException {
		logger.debug("Entered GrDPStorageFilesystem deleteGrDPStorageCacheElement.");
		
        try {
            removeFile(dn, delegationID, null, true);
        } catch(FileNotFoundException e) {
            logger.debug("Could not find entry in storage. DN '" + dn + "'; DLG ID '" + delegationID + "'.");
            throw new GrDPStorageException("Failed to find credential in storage.");
        } catch(IOException e) {
            logger.error("Failure accessing filesystem. Exception:" + e);
            throw new GrDPStorageException("Internal Failure.");
        }
	}

	/**
	 * Insert new delegated proxy into storage area.
	 *
	 * @param elem Object containing the information about the delegation proxy.
	 * @throws GrDPStorageException Failed to storage new delegation proxy in storage area.
	 */
	public void insertGrDPStorageElement(GrDPStorageElement elem) 
		throws GrDPStorageException {
		logger.debug("Entered GrDPStorageFilesystem insertGrDPStorageElement.");
		
		try {
			// Store certificate
			writeToFile(elem.getDN(), elem.getDelegationID(), "userproxy.pem",
					elem.getCertificate().getBytes(), false);
		
			// Store list of VOMS attributes into file
			writeToFile(elem.getDN(), elem.getDelegationID(), "voms.attributes",
					GrDPX509Util.toStringVOMSAttrs(elem.getVomsAttributes()).getBytes(), false);
            
            // Store the proxy termination time into file
            writeToFile(elem.getDN(), elem.getDelegationID(), "termination.time",
                    DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)
                        .format(elem.getTerminationTime()).getBytes(), false);
            
		} catch(IOException e) {
			logger.error("Failure while writing to filesystem.", e);
			throw new GrDPStorageException("Internal failure.");
		}		
	}
	
	/**
	 * Updates existing delegated proxy information in storage area.
	 *
	 * In this case, as it is a filesystem based implementation, the operation is equivalent to an
	 * insertion, with the contents of delegated certificate, private key and list of voms attributes
	 * simply being replaced.
	 *
	 * @param elem Object containing the information about the delegated proxy.
	 * @throws GrDPStorageException Failed to store new delegated proxy in storage area.
	 */
	public void updateGrDPStorageElement(GrDPStorageElement elem) 
		throws GrDPStorageException {
		logger.debug("Entered GrDPStorageFilesystem updateGrDPStorageElement.");
		
		insertGrDPStorageElement(elem);	
	}
	
	/**
	 * Retrieves an existing delegated proxy from the storage area.
	 *
	 * @param delegationID The id of the delegated proxy to be returned.
	 * @param dn The dn of the user owning the delegated proxy.
	 * @return The object containing the information on the delegated proxy.
	 * @throws GrDPStorageException Could not retrieve a delegated proxy because either it does not
	 * exist or an error occured while tried to access it.
	 */
	public GrDPStorageElement findGrDPStorageElement(String delegationID, String dn) 
		throws GrDPStorageException {
		logger.debug("Entered GrDPStorageFilesystem findGrDPStorageElement.");
		
		logger.debug("Looking for dlg id '" + delegationID + "' and dn '" + dn + "' in storage");
		
		// Create the basic element
		GrDPStorageElement elem = new GrDPStorageElement();
		elem.setDelegationID(delegationID);
		elem.setDN(dn);
					
		try {
			elem.setCertificate(readFromFile(dn, delegationID, "userproxy.pem", false));
            elem.setVomsAttributes(GrDPX509Util.fromStringVOMSAttrs(
                            readFromFile(dn, delegationID, "voms.attributes", false)));
            Date terminationTime = null;
            try {
                terminationTime = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)
                                .parse(readFromFile(dn, delegationID, "termination.time", false));
            } catch(ParseException e) {
                logger.error("Failed to parse the termination time from file. Will be null.");
            }              
            elem.setTerminationTime(terminationTime);
		} catch(FileNotFoundException fnfe) {
            logger.debug("Could not find entry in storage. DN '" + dn + "'; DLG ID '" + delegationID + "'.");
			return null;
		} catch(IOException ioe) {
			logger.error("Failure accessing filesystem. Exception:" + ioe);
			throw new GrDPStorageException("Internal failure.");
		}

		return elem;

	}
	
	/**
	 * Deletes an existing delegated proxy.
	 *
	 * @param delegationID The id of the delegated proxy to be deleted.
	 * @param dn The dn of the owner of the delegated proxy.
	 * @throws GrDPStorageException Failed to delete the delegated proxy as either it does not exist
	 * or could not be accessed.
	 */
	public void deleteGrDPStorageElement(String delegationID, String dn) 
		throws GrDPStorageException {
		logger.debug("Entered GrDPStorageFilesystem deleteGrDPStorageElement.");
		
        try {
            removeFile(dn, delegationID, null, false);
        } catch(FileNotFoundException e) {
            logger.debug("Could not find entry in storage. DN '" + dn + "'; DLG ID '" + delegationID + "'.");
            throw new GrDPStorageException("Failed to find credential in storage.");
        } catch(IOException e) {
            logger.error("Failure accessing filesystem. Exception:" + e);
            throw new GrDPStorageException("Internal Failure.");
        }
	}

	/**
	 * Retrieves the contents of a given file from the storage/storage cache area.
	 *
	 * Represents an abstraction from the actual filesystem structure for the storage areas.
	 *
	 * @param dn The dn of the owner of the wanted delegation request/proxy information.
	 * @param dlgID The delegation ID of the wanted delegation request/proxy information.
	 * @param fileName The actual file to be accessed (userproxy.pem, userreq.pem, userkey.pem, voms.attributes).
	 * @param cache True if storage cache area should be accessed. False if storage area should be accessed.
	 *
	 * @return The contents of the requested file.
	 * 
	 * @throws IOException An error occurred while reading the contents of the file.
	 */
	private String readFromFile(String dn, String dlgID, String fileName, boolean cache)
			throws IOException {

		String contents = "";
		
		// Get directory names
		String storageArea = storagePath;
		if(cache)
			storageArea = storageCachePath;
		String dnDir = URLEncoder.encode(dn, "UTF-8");

		// Return file contents
		String filePath = storageArea + "/" + dnDir + "/" + dlgID + "/" + fileName;
		logger.debug("Reading contents from file: " + filePath);
		BufferedReader file = new BufferedReader(new FileReader(filePath));
		String tmpString = "";
		while((tmpString = file.readLine()) != null)
			contents += tmpString + GrDPConstants.NEWLINE;

		return contents;
	}
	
	/**
	 * Writes the given contents to the storage/storage cache area.
	 *
	 * Represents an abstraction from the actual filesystem structure for the storage areas.
	 *
	 * @param dn The dn of the owner of the delegation request/proxy information.
	 * @param dlgID The delegation ID of the delegation request/proxy information.
	 * @param fileName The actual file where the contents should be written (userproxy.pem, userreq.pem,
	 * userkey.pem, voms.attributes).
	 * @param cache True if storage cache area should be accessed. False if storage area should be accessed.
	 * 
	 * @throws IOException An error occurred while writing the contents to the file.
	 */
	private void writeToFile(String dn, String dlgID, String fileName, 
			byte[] content, boolean cache) throws IOException, GrDPStorageException {

		// Get directory names
		String storageArea = storagePath;
		if(cache)
			storageArea = storageCachePath;
		String dnDir = URLEncoder.encode(dn, "UTF-8");
		
		// Make sure directories and file exist
        String dlgDirPath = storageArea + "/" + dnDir + "/" + dlgID;
		String filePath = dlgDirPath + "/" + fileName;
        
        // Make sure proper access mode is set on directory and file
		if(new File(dlgDirPath).mkdirs()) {
		    if(!GrDPX509Util.changeFileMode(dlgDirPath, 700)) {
		        throw new GrDPStorageException("Failed to set read/write for owner only"
                                + " on directory '" + dlgDirPath + "'");
            }
        }
		if(new File(filePath).createNewFile()) {
            if(!GrDPX509Util.changeFileMode(filePath, 600)) {
                throw new GrDPStorageException("Failed to set read/write for owner only"
                                + " on file '" + filePath + "'");
            }
        }

		// Write to file
        logger.debug("Writing contents to file: " + filePath);
		FileOutputStream file = new FileOutputStream(filePath);
		file.write(content);
		file.close();
		
	}
    
    /**
     * Removes the given file from storage/storage cache area.
     * 
     * If fileName given is null, the actual directory containing all
     * the related stored credential files is removed.
     * 
     * @param dn The dn of the owner of the delegation request/proxy information.
     * @param dlgID The delegation ID of the delegation request/proxy information.
     * @param fileName The actual file where the contents should be written (userproxy.pem, userreq.pem,
     * userkey.pem, voms.attributes).
     * @throws IOException An error occurred while trying to remove the file.
     */
    private void removeFile(String dn, String dlgID, String fileName, boolean cache) throws IOException {
        
        // Get directory names
        String storageArea = storagePath;
        if(cache)
            storageArea = storageCachePath;
        String dnDir = URLEncoder.encode(dn, "UTF-8");
        
        // Make sure directories and file exist
        String filePath = storageArea + "/" + dnDir + "/" + dlgID;
        if(fileName != null) {
            filePath += "/" + fileName;
        }
        
        File file = new File(filePath);
        logger.debug("File to remove: '" + filePath + "'");
       
        // If directory, then make sure files inside are removed first
        if(file.isDirectory()) {
            logger.debug("Attempting to remove directory.");
            File[] files = file.listFiles();
            logger.debug("Num files inside: " + files.length);
            for(int i=0; i<files.length; i++) {
                boolean result = files[i].delete();
                if(!result) {
                    throw new IOException("Failed to remove file inside directory '" 
                                    + files[i].getName() + "'. Directory could/will not be removed.");
                }
            }
        }
        
        // Remove file/dir
        boolean result = file.delete();
        if(result) {
            logger.debug("Successfully removed file/dir '" + filePath + "'");
        } else {
            throw new IOException("Failed to remove file/dir '" + filePath + "'");
        }
    }
    
}

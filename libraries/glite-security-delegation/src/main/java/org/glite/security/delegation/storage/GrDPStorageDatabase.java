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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.glite.security.util.DNHandler;
import org.glite.security.delegation.GrDPX509Util;
import org.glite.security.delegation.GrDProxyDlgeeOptions;


/**
 * This is the database based implementation of the GrDPStorage interface.
 * <br/>
 *
 * <p>It provides a database archival mechanism for delegated proxies related information.</p>
 *
 * <p>Storage is separated into two different areas: storage cache and actual storage. The first one
 * holds the temporary information during a delegation request, including the certificate request,
 * the private key and the list of voms attributes expected in the proxy. The second holds the actual
 * delegated proxy information, including the private key and list of voms attributes (as above) and
 * the actual proxy.</p>
 *
 * <p>Inside the database, there is one different table for each of the storage areas. Check the db
 * schema file for details.</p>
 *
 * Authors: Ricardo Rocha <ricardo.rocha@cern.ch>
 */
public class GrDPStorageDatabase implements GrDPStorage {
	
	// Class logger
	private static Logger logger = Logger.getLogger(GrDPStorageDatabase.class);

	// Name of JNDI property holding the DB pool
	private final String m_default_db_pool = "jdbc/dlg";
	
	// Data source object holding the db connection pool
	private final DataSource m_dataSource;
    
	// Object containing DLGEE configuration parameters
//	private GrDProxyDlgeeOptions dlgeeOpt = null;
    
	/**
	 * Class constructor.
	 */
	public GrDPStorageDatabase(GrDProxyDlgeeOptions dlgeeOpt) throws GrDPStorageException {
       	
        // Save the DLGEE properties in a local variable
//        this.dlgeeOpt = dlgeeOpt;
        
       	// Set db pool
   		String dbPoolName = dlgeeOpt.getDlgeeStorageDbPool();
       	if(dbPoolName == null) {
   			dbPoolName = m_default_db_pool;
       	}
   	
   		// Load the DB data source
   		try {
	       	Context initCtx = new InitialContext();
	       	logger.debug("Fetched initial context");
       	
       		Context envCtx = (Context) initCtx.lookup("java:comp/env");
	       	logger.debug("Fetched environment context");

	       	logger.debug("Looking up JNDI datasource: " + dbPoolName);
       		m_dataSource = (DataSource) envCtx.lookup(dbPoolName);
       	} catch(NamingException ne) {
   			logger.debug("Failed to load DB data source.", ne);
   			throw new GrDPStorageException("Failed to load DB data source.");
       	}
       	
        checkSchemaVersion(1, 2, 0);
	}

    protected void checkSchemaVersion(int major, int minor, int patch) 
        throws GrDPStorageException {
        
        logger.debug("Entered checkSchemaVersion " + major + "." + minor + "." + patch);
        
        String sql = "SELECT major, minor, patch FROM t_credential_vers";

        Connection conn = null;
        PreparedStatement p_stat = null;
        ResultSet rs = null;

        try {
            conn = getConnection();

            p_stat = conn.prepareStatement(sql);

            rs = p_stat.executeQuery();
            if(rs.next()) {
                if (major == rs.getInt(1) && minor == rs.getInt(2) && patch == rs.getInt(3)) {
                    logger.debug("Schema version is acceptable for the service.");
                    return;
                }
                throw new GrDPStorageException("DB schema version does not match the service.");
            }    
        } catch(SQLException e) {
            logger.error("Failure on db interaction.", e);
            this.rollback(conn);
            throw new GrDPStorageException("Internal failure: " + e.getMessage());
        } finally {
            this.cleanup(conn);
            this.cleanup(p_stat);
            this.cleanup(rs);
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
		logger.debug("Entered GrDPStorageDatabase insertGrDPStorageCacheElement.");
		
		String sql = "INSERT INTO t_credential_cache (dlg_id, dn, cert_request, priv_key, voms_attrs) " +
				"VALUES (?, ?, ?, ?, ?)";

		Connection conn = null;
		PreparedStatement p_stat = null;
		
		try {
			conn = getConnection();

			p_stat = conn.prepareStatement(sql);
			p_stat.setString(1, elem.getDelegationID());
			p_stat.setString(2, elem.getDNasX500());
			p_stat.setString(3, elem.getCertificateRequest());
			p_stat.setString(4, elem.getPrivateKey());
			p_stat.setString(5, GrDPX509Util.toStringVOMSAttrs(elem.getVomsAttributes()));
			
			p_stat.executeUpdate();
			
			// Commit
			this.commit(conn);
		} catch(SQLException e) {
			logger.error("Failure on db interaction.", e);
			this.rollback(conn);
			throw new GrDPStorageException("Internal failure: " + e.getMessage());
		} finally {
			this.cleanup(conn);
			this.cleanup(p_stat);
		}
		
	}
	
	/**
	 * Updates existing delegation request in storage cache area.
	 *
	 * @param elem Object containing the information about the delegation request.
	 * @throws GrDPStorageException Failed to storage new delegation request in storage cache area.
	 */	
	public void updateGrDPStorageCacheElement(GrDPStorageCacheElement elem) 
		throws GrDPStorageException {
		logger.debug("Entered GrDPStorageDatabase updateGrDPStorageCacheElement.");
		
		String sql = "UPDATE t_credential_cache C SET " +
			"cert_request = ?, priv_key = ?, voms_attrs = ? " +
			"WHERE dlg_id = ? AND dn = ?";
	
		Connection conn = null;
		PreparedStatement p_stat = null;
		
		try {
			conn = getConnection();
	
			p_stat = conn.prepareStatement(sql);
			p_stat.setString(1, elem.getCertificateRequest());
			p_stat.setString(2, elem.getPrivateKey());
			p_stat.setString(3, GrDPX509Util.toStringVOMSAttrs(elem.getVomsAttributes()));
			p_stat.setString(4, elem.getDelegationID());
			p_stat.setString(5, elem.getDNasX500());
			
			p_stat.executeUpdate();
			
			// Commit
			this.commit(conn);
		} catch(SQLException e) {
			logger.error("Failure on db interaction.", e);
			this.rollback(conn);
			throw new GrDPStorageException("Internal failure: " + e.getMessage());
		} finally {
			this.cleanup(conn);
			this.cleanup(p_stat);
		}
		
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
		logger.debug("Entered GrDPStorageDatabase findGrDPStorageCacheElement.");
		
		logger.debug("Looking for dlg id '" + delegationID + "' and dn '" + dn + "' in cache");
		
		GrDPStorageCacheElement elem = null;
		
		String sql = "SELECT dlg_id, dn, voms_attrs, cert_request, priv_key " +
				"FROM t_credential_cache " +
				"WHERE dlg_id = ? AND dn = ?";
		
		Connection conn = null;
		PreparedStatement p_stat = null;
		ResultSet rs = null;
		
		try {
			conn = getConnection();
		
			p_stat = conn.prepareStatement(sql);
			p_stat.setString(1, delegationID);
			p_stat.setString(2, DNHandler.getDNRFC2253(dn).getX500());
			
			rs = p_stat.executeQuery();
			if(rs.next()) {
				elem = new GrDPStorageCacheElement();
				elem.setDelegationID(rs.getString("dlg_id"));
				elem.setDNasX500(rs.getString("dn"));
				elem.setCertificateRequest(rs.getString("cert_request"));
				elem.setVomsAttributes(GrDPX509Util.fromStringVOMSAttrs(rs.getString("voms_attrs")));
				elem.setPrivateKey(rs.getString("priv_key"));
			}
		} catch(SQLException e) {
			logger.error("Failure on db interaction.", e);
			this.rollback(conn);
			throw new GrDPStorageException("Internal failure: " + e.getMessage());
		} finally {
			this.cleanup(conn);
			this.cleanup(p_stat);
			this.cleanup(rs);
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
		logger.debug("Entered GrDPStorageDatabase deleteGrDPStorageCacheElement.");
		
		String sql = "DELETE FROM t_credential_cache " + 
			"WHERE dlg_id = ? AND dn = ?";
	
		Connection conn = null;
		PreparedStatement p_stat = null;
		
		try {
			conn = getConnection();
	
			p_stat = conn.prepareStatement(sql);
			p_stat.setString(1, delegationID);
			p_stat.setString(2, DNHandler.getDNRFC2253(dn).getX500());
			
			p_stat.executeUpdate();
			
			// Commit
			this.commit(conn);
		} catch(SQLException e) {
			logger.error("Failure on db interaction.", e);
			this.rollback(conn);
			throw new GrDPStorageException("Internal failure: " + e.getMessage());
		} finally {
			this.cleanup(conn);
			this.cleanup(p_stat);
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
		logger.debug("Entered GrDPStorageDatabase insertGrDPStorageElement.");
		
		String sql = "INSERT INTO t_credential (dlg_id, dn, proxy, voms_attrs, termination_time) " +
				"VALUES (?, ?, ?, ?, ?)";
	
		Connection conn = null;
		PreparedStatement p_stat = null;
		
		try {
			conn = getConnection();
	
			p_stat = conn.prepareStatement(sql);
			p_stat.setString(1, elem.getDelegationID());
			p_stat.setString(2, elem.getDNasX500());
			p_stat.setString(3, elem.getCertificate());
			p_stat.setString(4, GrDPX509Util.toStringVOMSAttrs(elem.getVomsAttributes()));
            p_stat.setTimestamp(5, new java.sql.Timestamp(elem.getTerminationTime().getTime()));
			
			p_stat.executeUpdate();
			
			// Commit
			this.commit(conn);
		} catch(SQLException e) {
			logger.error("Failure on db interaction.", e);
			this.rollback(conn);
			throw new GrDPStorageException("Internal failure: " + e.getMessage());
		} finally {
			this.cleanup(conn);
			this.cleanup(p_stat);
		}
		
	}
	
	/**
	 * Updates existing delegated proxy information in storage area.
	 *
	 * @param elem Object containing the information about the delegated proxy.
	 * @throws GrDPStorageException Failed to store new delegated proxy in storage area.
	 */
	public void updateGrDPStorageElement(GrDPStorageElement elem) 
		throws GrDPStorageException {
		logger.debug("Entered GrDPStorageDatabase updateGrDPStorageElement.");
		
		String sql = "UPDATE t_credential C SET " +
			"proxy = ?, voms_attrs = ?, termination_time = ? " +
			"WHERE dlg_id = ? AND dn = ?";
	
		Connection conn = null;
		PreparedStatement p_stat = null;
		
		try {
			conn = getConnection();
	
			p_stat = conn.prepareStatement(sql);
			p_stat.setString(1, elem.getCertificate());
			p_stat.setString(2, GrDPX509Util.toStringVOMSAttrs(elem.getVomsAttributes()));
            p_stat.setTimestamp(3, new java.sql.Timestamp(elem.getTerminationTime().getTime()));
			p_stat.setString(4, elem.getDelegationID());
			p_stat.setString(5, elem.getDNasX500());
            
			p_stat.executeUpdate();
			
			// Commit
			this.commit(conn);
		} catch(SQLException e) {
			logger.error("Failure on db interaction.", e);
			this.rollback(conn);
			throw new GrDPStorageException("Internal failure: " + e.getMessage());
		} finally {
			this.cleanup(conn);
			this.cleanup(p_stat);
		}
		
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
		logger.debug("Entered GrDPStorageDatabase findGrDPStorageElement.");
		
		logger.debug("Looking for dlg id '" + delegationID + "' and dn '" + dn + "' in storage");
		
		GrDPStorageElement elem = null;
		
		String sql = "SELECT dlg_id, dn, voms_attrs, proxy, termination_time " +
				"FROM t_credential " +
				"WHERE dlg_id = ? AND dn = ?";
		
		Connection conn = null;
		PreparedStatement p_stat = null;
		ResultSet rs = null;
		
		try {
			conn = getConnection();
		
			p_stat = conn.prepareStatement(sql);
			p_stat.setString(1, delegationID);
			p_stat.setString(2, DNHandler.getDNRFC2253(dn).getX500());
			
			rs = p_stat.executeQuery();
			if(rs.next()) {
				elem = new GrDPStorageElement();
				elem.setDelegationID(rs.getString("dlg_id"));
				elem.setDNasX500(rs.getString("dn"));
				elem.setCertificate(rs.getString("proxy"));
				elem.setVomsAttributes(GrDPX509Util.fromStringVOMSAttrs(rs.getString("voms_attrs")));
                elem.setTerminationTime(rs.getTimestamp("termination_time"));
			}
		} catch(SQLException e) {
			logger.error("Failure on db interaction.", e);
			this.rollback(conn);
			throw new GrDPStorageException("Internal failure: " + e.getMessage());
		} finally {
			this.cleanup(conn);
			this.cleanup(p_stat);
			this.cleanup(rs);
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
		logger.debug("Entered GrDPStorageDatabase deleteGrDPStorageElement.");
		
		String sql = "DELETE FROM t_credential " + 
			"WHERE dlg_id = ? AND dn = ?";
	
		Connection conn = null;
		PreparedStatement p_stat = null;
		
		try {
			conn = getConnection();
	
			p_stat = conn.prepareStatement(sql);
			p_stat.setString(1, delegationID);
			p_stat.setString(2, DNHandler.getDNRFC2253(dn).getX500());
			
			p_stat.executeUpdate();
			
			// Commit
			this.commit(conn);
		} catch(SQLException e) {
			logger.error("Failure on db interaction.", e);
			this.rollback(conn);
			throw new GrDPStorageException("Internal failure: " + e.getMessage());
		} finally {
			this.cleanup(conn);
			this.cleanup(p_stat);
		}
		
	}
	
	/**
	 * Gets a connection from the pool.
	 *
	 * @return A DB connection.
	 */
	private Connection getConnection() throws SQLException {
		
		Connection conn = m_dataSource.getConnection();
		
		conn.setAutoCommit(false);
		
		return conn;
	}

	/**
	 * Commits changes done in the last session in the given connection.
	 *
	 * @param conn The connection where commit should be done.
	 */
	private void commit(Connection conn) throws SQLException {
		conn.commit();
	}
	
	/**
	 * Rollback changes done in the last session in the given connection.
	 *
	 * @param conn The connection where rollback should be done.
	 */
	private void rollback(Connection conn) {
		try {
			conn.rollback();
		} catch(SQLException e) {
			logger.error("Failed to rollback database transaction: " + e.getMessage());
		}
	}
	
	/**
	 * Close/garbage collect the given connection object.
	 *
	 * @param conn The connection to be closed.
	 */
	private void cleanup(Connection conn) {
		try {
		    if(conn != null)
		        conn.close();
		} catch(SQLException e) {
			logger.error("Failed to close connection: " + e.getMessage());
		}
	}
	
	/**
	 * Close/garbage collect the given statement object.
	 *
	 * @param conn The statement to be closed.
	 */
	private void cleanup(PreparedStatement stat) {
		try {
		    if(stat != null)
		        stat.close();
		} catch(SQLException e) {
			logger.error("Failed to close statement: " + e.getMessage());
		}
	}
	
	/**
	 * Close/garbage collect the given result set object.
	 *
	 * @param conn The result set to be closed.
	 */
	private void cleanup(ResultSet rs) {
		try {
		    if(rs != null)
		        rs.close();
		} catch(SQLException e) {
			logger.error("Failed to close result set: " + e.getMessage());
		}
	}

}

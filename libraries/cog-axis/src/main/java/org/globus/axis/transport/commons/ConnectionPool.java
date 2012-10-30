/*
 * Copyright 1999-2006 University of Chicago
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.globus.axis.transport.commons;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.httpclient.HttpConnection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ConnectionPool {

    private static Log logger =
        LogFactory.getLog(ConnectionPool.class);

    // 2 min default idle time
    private long idleTime = 1000 * 60 * 2; 
    
    private HashMap freeConnections;
    
    public ConnectionPool() {
        this.freeConnections = new HashMap();
    }
    
    public void setIdleTime(long time) {
        this.idleTime = time;
    }
    
    public synchronized ExtendedHttpConnection getPooledConnection() {
        if (this.freeConnections.isEmpty()) {
            return null;
        }
        
        long idleTimeout = System.currentTimeMillis() - this.idleTime;
        Iterator iter = this.freeConnections.entrySet().iterator();
        while(iter.hasNext()) {
            Map.Entry entry = (Map.Entry)iter.next();
            // either it's good or expired 
            iter.remove();
            ConnectionEntry connectionEntry = 
                (ConnectionEntry)entry.getValue();
            ExtendedHttpConnection connection = 
                connectionEntry.getConnection();
            if (connectionEntry.getTimeAdded() <= idleTimeout) {
                // it's expired
                connection.close();
                if (logger.isDebugEnabled()) {
                    logger.debug("closed idle connection: " + connection);
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("got connection from pool: " + connection);
                }
                return connection;
            }
        }
        
        return null;
    }
    
    public synchronized void releaseConnection(HttpConnection conn) {
        ExtendedHttpConnection extConn = (ExtendedHttpConnection)conn;
        this.freeConnections.put(conn, new ConnectionEntry(extConn));
        if (logger.isDebugEnabled()) {
            logger.debug("returned connection to pool: " + conn);
        }
    }
    
    public synchronized void closeIdleConnections() {
        long idleTimeout = System.currentTimeMillis() - this.idleTime;
        Iterator iter = this.freeConnections.entrySet().iterator();
        while(iter.hasNext()) {
            Map.Entry entry = (Map.Entry)iter.next();
            ConnectionEntry connectionEntry = 
                (ConnectionEntry)entry.getValue();
            if (connectionEntry.getTimeAdded() <= idleTimeout) {
                // it's expired - remove & close it
                iter.remove();
                connectionEntry.getConnection().close();
                if (logger.isDebugEnabled()) {
                    logger.debug("closed idle connection: " + 
                                 connectionEntry.getConnection());
                }
            }
        }
    }

    public synchronized void shutdown() {
        Iterator iter = this.freeConnections.entrySet().iterator();
        while(iter.hasNext()) {
            Map.Entry entry = (Map.Entry)iter.next();
            ConnectionEntry connectionEntry = 
                (ConnectionEntry)entry.getValue();
            connectionEntry.getConnection().close();
        }
        this.freeConnections.clear();
    }

    private static class ConnectionEntry {
        ExtendedHttpConnection connection;
        long timeAdded;
        
        public ConnectionEntry(ExtendedHttpConnection conn) {
            this.connection = conn;
            this.timeAdded = System.currentTimeMillis();
        }
        
        public ExtendedHttpConnection getConnection() {
            return this.connection;
        }

        public long getTimeAdded() {
            return this.timeAdded;
        }
    }

}


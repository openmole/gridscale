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
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.params.HostParams;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.util.IdleConnectionTimeoutThread;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CommonsHttpConnectionManager implements HttpConnectionManager {

    private static final IdleConnectionTimeoutThread IDLE_THREAD;

    static {
        IDLE_THREAD = new IdleConnectionTimeoutThread();
        IDLE_THREAD.setTimeoutInterval(1000 * 60 * 2);
        IDLE_THREAD.start();
    }

    private static Log logger =
        LogFactory.getLog(CommonsHttpConnectionManager.class);

    private String [] hostConfigurationParams;
    private HashMap hostPoolMap;
    private long idleTime = 1000 * 60 * 2; 
    private boolean staleChecking = true;
    
    private HttpConnectionManagerParams params = 
        new HttpConnectionManagerParams(); 

    public CommonsHttpConnectionManager(String [] hostConfigurationParams) {
        this.hostConfigurationParams = hostConfigurationParams;
        this.hostPoolMap = new HashMap();
        IDLE_THREAD.addConnectionManager(this);
    }
    
    public void setConnectionIdleTime(long time) {
        this.idleTime = time;
    }

    public long getConnectionIdleTime() {
        return this.idleTime;
    }

    public void setStaleCheckingEnabled(boolean staleChecking) {
        this.staleChecking = staleChecking;
    }
    
    public boolean isStaleCheckingEnabled() {
        return this.staleChecking;
    }
    
    public HttpConnection getConnection(HostConfiguration hostConfiguration) {
        return getConnectionWithTimeout(hostConfiguration, 0);
    }

    public HttpConnection getConnection(HostConfiguration hostConfiguration, 
                                        long timeout) {
        return getConnectionWithTimeout(hostConfiguration, timeout);
    }

    public HttpConnection getConnectionWithTimeout(
                   HostConfiguration hostConfiguration, long timeout) {
        ExtendedHostConfiguration extendedHostConfiguration = 
            new ExtendedHostConfiguration(hostConfiguration, 
                                          this.hostConfigurationParams);
        
        ConnectionPool pool = getConnectionPool(extendedHostConfiguration);
        ExtendedHttpConnection httpConnection = pool.getPooledConnection();
        if (httpConnection == null) {
            // not in the pool - create a new connection
            httpConnection = getNewConnection(extendedHostConfiguration);
            httpConnection.setFromPool(false);
        } else {
            httpConnection.setFromPool(true);
        }

        if (this.staleChecking) {
            // install our retry handler
            hostConfiguration.getParams().setParameter(
                              HttpMethodParams.RETRY_HANDLER,
                              httpConnection.getRetryHandler());
        }

        return httpConnection;
    }

    private ExtendedHttpConnection getNewConnection(
                           HostConfiguration hostConfiguration) {
        ExtendedHttpConnection httpConnection = 
            new ExtendedHttpConnection(hostConfiguration, this.staleChecking);
        httpConnection.setHttpConnectionManager(this);
        HttpConnectionParams connectionParams = httpConnection.getParams();
        connectionParams.setDefaults(this.params);
        
        if (this.hostConfigurationParams != null) {
            HostParams hostParams = hostConfiguration.getParams();
            for (int i=0;i<this.hostConfigurationParams.length;i++) {
                String key = this.hostConfigurationParams[i];
                Object value = hostParams.getParameter(key);
                if (value != null) {
                    connectionParams.setParameter(key, value);
                }
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("got new connection: " + httpConnection);
        }

        return httpConnection;
    }

    private ConnectionPool getConnectionPool(
                          HostConfiguration hostConfiguration) {
        ConnectionPool pool = null;
        synchronized(this.hostPoolMap) {
            pool = (ConnectionPool)this.hostPoolMap.get(hostConfiguration);
            if (pool == null) {
                pool = new ConnectionPool();
                pool.setIdleTime(this.idleTime);
                this.hostPoolMap.put(hostConfiguration, pool);
            }
        }
        return pool;
    }

    public void releaseConnection(HttpConnection conn) {
        // we only maintain list of opened connections
        // so ignore the connections that are closed
        if (conn.isOpen()) {
            ExtendedHttpConnection extendedHostConfiguration = 
                (ExtendedHttpConnection)conn;
            HostConfiguration hostConfiguration = 
                extendedHostConfiguration.getHostConfiguration();
            
            ConnectionPool pool = getConnectionPool(hostConfiguration);
            if (pool != null) {
                pool.releaseConnection(conn);
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("not releasing closed connection: "  + conn);
            }
        }
    }

    public HttpConnectionManagerParams getParams() {
        return this.params;
    }

    public void setParams(final HttpConnectionManagerParams params) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        this.params = params;
    }
    
    public void closeIdleConnections(long idleTimeout) {
        logger.debug("checking for idle connections");
        synchronized(this.hostPoolMap) {
            Iterator iter = this.hostPoolMap.entrySet().iterator();
            while(iter.hasNext()) {
                Map.Entry entry = (Map.Entry)iter.next();
                ((ConnectionPool)entry.getValue()).closeIdleConnections();
            }
        }
        logger.debug("done checking for idle connections");
    }

    public void shutdown() {
        logger.debug("shutting down connections");
        synchronized(this.hostPoolMap) {
            Iterator iter = this.hostPoolMap.entrySet().iterator();
            while(iter.hasNext()) {
                Map.Entry entry = (Map.Entry)iter.next();
                ((ConnectionPool)entry.getValue()).shutdown();
            }
        }
        this.hostPoolMap.clear();
        logger.debug("done shutting down connections");
    }
    
    public static void setStaleCheckingEnabled(
                                   CommonsHttpConnectionManager manager) {
        String staleCheckProp = 
            System.getProperty("org.globus.transport.stalecheck");
        if ("off".equalsIgnoreCase(staleCheckProp)) {
            // no stale connection checking
            // the request will be retried 3 times
            logger.debug("no stale connection checking");
            manager.getParams().setStaleCheckingEnabled(false);
            manager.setStaleCheckingEnabled(false);
        } else if ("commons".equalsIgnoreCase(staleCheckProp)) {
            // enable commons stale checking
            // connection is checked before sending a request
            // the request still might be retried 3 times
            logger.debug("commons stale connection checking");
            manager.getParams().setStaleCheckingEnabled(true);
            manager.setStaleCheckingEnabled(false);
        } else {
            // default: enable globus stale checking
            // connection is not checked before sending a request
            // the request will be retried 1 time only
            logger.debug("globus stale connection checking");
            manager.getParams().setStaleCheckingEnabled(false);
            manager.setStaleCheckingEnabled(true);
        }
    }

    public static void setConnectionIdleTime(
                                   CommonsHttpConnectionManager manager) {
        String idleTimeoutProp = 
            System.getProperty("org.globus.transport.idleTime");
        if (idleTimeoutProp != null) {
            manager.setConnectionIdleTime(Long.parseLong(idleTimeoutProp));
        }
    }

}

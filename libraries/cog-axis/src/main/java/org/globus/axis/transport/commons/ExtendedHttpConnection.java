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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;

import org.globus.common.ChainedIOException;

import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpMethod;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ExtendedHttpConnection extends HttpConnection {

    private static Log logger =
        LogFactory.getLog(ExtendedHttpConnection.class);
        
    private boolean staleChecking = true;
    private boolean fromPool = false;
    private CommonsHttpMethodRetryHandler retryHandler;
    private HostConfiguration hostConfiguration;
    
    public ExtendedHttpConnection(HostConfiguration hc,
                                  boolean staleChecking) {
        super(hc);
        this.hostConfiguration = hc;
        this.staleChecking = staleChecking;
    }
    
    protected HostConfiguration getHostConfiguration() {
        return this.hostConfiguration;
    }
    
    protected void setFromPool(boolean fromPool) {
        this.fromPool = fromPool;
    }

    protected boolean isFromPool() {
        return this.fromPool;
    }

    protected HttpMethodRetryHandler getRetryHandler() {
        if (this.retryHandler == null) {
            this.retryHandler = new CommonsHttpMethodRetryHandler();
        }
        return this.retryHandler;
    }

    public void writeLine()
        throws IOException, IllegalStateException {
        super.writeLine();
        // this just might help catching the error sooner
        if (this.staleChecking) {
            flushRequestOutputStream();
        }
    }
    
    class CommonsHttpMethodRetryHandler 
        implements HttpMethodRetryHandler {
        
        public boolean retryMethod(HttpMethod method, 
                                   IOException exception, 
                                   int executionCount) {

            if (!isFromPool()) {
                // do not recover new connections
                return false;
            }

            if (exception instanceof InterruptedIOException) {
                // Timeout
                return false;
            }

            if (exception instanceof UnknownHostException) {
                // Unknown host
                return false;
            }

            if (exception instanceof ChainedIOException) {
                // most likely a security problem
                return false;
            }

            if (!method.isRequestSent() && executionCount == 1) {
                // retry only once!
                if (logger.isDebugEnabled()) {
                    logger.debug("Retrying connection: " + 
                                 ExtendedHttpConnection.this);
                }
                return true;
            } else {
                // do not retry otherwise
                return false; 
            }
        }
    }
}

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

import java.net.URL;

import org.apache.axis.MessageContext;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.protocol.Protocol;

import org.globus.gsi.GSIConstants;
import org.globus.axis.transport.GSIHTTPTransport;

import org.apache.axis.components.net.CommonsHTTPClientProperties;
import org.apache.axis.components.net.CommonsHTTPClientPropertiesFactory;

import org.globus.axis.transport.HTTPUtils;

/**
 * Overwrites the Axis sender to use a global connection manager
 */
public class HTTPSSender 
    extends org.apache.axis.transport.http.CommonsHTTPSender {
    
    protected static final String[] PARAMS =
    { 
        GSIHTTPTransport.GSI_AUTHORIZATION,
        GSIHTTPTransport.GSI_ANONYMOUS,
        GSIHTTPTransport.GSI_CREDENTIALS,
        GSIConstants.GSI_TRANSPORT,
        GSIHTTPTransport.TRUSTED_CERTIFICATES
    };
    
    private static CommonsHttpConnectionManager globalConnectionManager;
    private static CommonsHTTPClientProperties globalClientProperties;

    static {
        // install protocol handler
        Protocol protocol = 
            new Protocol("https", new CommonsSSLSocketFactory(), 443);
        Protocol.registerProtocol("https", protocol);

        // initialize connection manager
        globalConnectionManager = new CommonsHttpConnectionManager(PARAMS);
        globalClientProperties = CommonsHTTPClientPropertiesFactory.create();
        CommonsHttpConnectionManager.setStaleCheckingEnabled(
                                              globalConnectionManager);
        CommonsHttpConnectionManager.setConnectionIdleTime(
                                              globalConnectionManager);
    }

    protected void initialize() {
        this.clientProperties = globalClientProperties;
        this.connectionManager = globalConnectionManager;
    }

    protected HostConfiguration getHostConfiguration(HttpClient client, 
                                                     MessageContext context,
                                                     URL targetURL) {
        HostConfiguration config = super.getHostConfiguration(client,
                                                              context,
                                                              targetURL);
        
        // handle disable chunking option
        Boolean prop = 
            (Boolean)context.getProperty(HTTPUtils.DISABLE_CHUNKING);
        if (prop != null) {
            client.getParams().setParameter(HTTPUtils.DISABLE_CHUNKING, prop);
        }

        for (int i=0;i<PARAMS.length;i++) {
            Object value = context.getProperty(PARAMS[i]);
            if (value != null) {
                config.getParams().setParameter(PARAMS[i], value);
            }
        }

        return config;
    }

}

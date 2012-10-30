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
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import org.globus.net.SocketFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CommonsSocketFactory implements ProtocolSocketFactory {
        
    private SocketFactory defaultSocketFactory =
        SocketFactory.getDefault();
    
    public Socket createSocket(String host, 
                               int port, 
                               InetAddress localAddress, 
                               int localPort)
        throws IOException, UnknownHostException {
        throw new IOException("not supported");
    }

    public Socket createSocket(String host, 
                               int port) 
        throws IOException, UnknownHostException {
        throw new IOException("not supported");
    }
    
    public Socket createSocket(String host, 
                               int port, 
                               InetAddress localAddress, 
                               int localPort,
                               HttpConnectionParams params)
        throws IOException, UnknownHostException, ConnectTimeoutException {
        return defaultSocketFactory.createSocket(host, port, 
                                                 localAddress, localPort);
    }
    
}

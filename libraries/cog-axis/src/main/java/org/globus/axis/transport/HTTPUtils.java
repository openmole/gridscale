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
package org.globus.axis.transport;

import java.util.Hashtable;

import javax.xml.rpc.Stub;

import org.apache.axis.MessageContext;
import org.apache.axis.transport.http.HTTPConstants;

public class HTTPUtils {
    
    public static final String DISABLE_CHUNKING = 
        "transport.http.disableChunking";

    /**
     * Sets connection timeout.
     *
     * @param stub The stub to set the property on
     * @param timeout the new timeout value in milliseconds
     */
    public static void setTimeout(Stub stub, int timeout) {
        if (stub instanceof org.apache.axis.client.Stub) {
            ((org.apache.axis.client.Stub)stub).setTimeout(timeout);
        }
    }

    /**
     * Sets on option on the stub to close the connection
     * after receiving the reply (connection will not
     * be reused).
     *
     * @param stub The stub to set the property on
     * @param close If true, connection close will be requested. Otherwise
     *              connection close will not be requested.
     */
    public static void setCloseConnection(Stub stub, boolean close) {
        Hashtable headers = getRequestHeaders(stub);
        if (close) {
            headers.put(HTTPConstants.HEADER_CONNECTION,
                        HTTPConstants.HEADER_CONNECTION_CLOSE);
        } else {
            headers.remove(HTTPConstants.HEADER_CONNECTION);
        }
    }
    
    /**
     * Sets on option on the stub to control what HTTP protocol
     * version should be used.
     *
     * @param stub The stub to set the property on
     * @param enable If true, HTTP 1.0 will be used. If false, HTTP 1.1
     *               will be used.
     */
    public static void setHTTP10Version(Stub stub, boolean enable) {
        setHTTPVersion(stub, enable);
    }

    /**
     * Sets on option on the stub to control what HTTP protocol
     * version should be used.
     *
     * @param stub The stub to set the property on
     * @param http10 If true, HTTP 1.0 will be used. Otherwise HTTP 1.1
     *               will be used.
     */
    public static void setHTTPVersion(Stub stub, boolean http10) {
        stub._setProperty(MessageContext.HTTP_TRANSPORT_VERSION,
                          (http10) 
                          ? HTTPConstants.HEADER_PROTOCOL_V10 
                          : HTTPConstants.HEADER_PROTOCOL_V11);
    }

    /**
     * Sets on option on the stub to use to enable or disable chunked encoding
     * (only if used with HTTP 1.1).
     *
     * @param stub The stub to set the property on
     * @param enable If true, chunked encoding will be enabled. If false,
     *               chunked encoding will be disabled.
     */
    public static void setChunkedEncoding(Stub stub, boolean enable) {
        setDisableChunking(stub, !enable);
    }

    /**
     * Sets on option on the stub to use to disable chunking
     * (only if used with HTTP 1.1).
     *
     * @param stub The stub to set the property on
     * @param disable If true, chunking will be disabled. Otherwise chunking
     *                will be performed (if HTTP 1.1 will be used).
     */
    public static void setDisableChunking(Stub stub, boolean disable) {
        stub._setProperty(DISABLE_CHUNKING, 
                          (disable) 
                          ? Boolean.TRUE
                          : Boolean.FALSE);
        Hashtable headers = getRequestHeaders(stub);
        headers.put(HTTPConstants.HEADER_TRANSFER_ENCODING_CHUNKED,
                    (disable) ? "false" : "true");
    }

    private static Hashtable getRequestHeaders(Stub stub) {
        Hashtable headers = 
            (Hashtable)stub._getProperty(HTTPConstants.REQUEST_HEADERS);
        if (headers == null) {
            headers = new Hashtable();
            stub._setProperty(HTTPConstants.REQUEST_HEADERS, headers);
        }
        return headers;
    }

}

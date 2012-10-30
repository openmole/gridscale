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

/**
 * Extends Transport by implementing the setupMessageContext function to
 * set HTTP-specific message context fields and transport chains.
 * May not even be necessary if we arrange things differently somehow.
 * Can hold state relating to URL properties.
 * <BR><I>This code is based on Axis HTTPTransport.java code.</I>
 */
public class HTTPSTransport extends GSIHTTPTransport
{
    public static final String DEFAULT_TRANSPORT_NAME = "https";

    public HTTPSTransport () {
        transportName = DEFAULT_TRANSPORT_NAME;
    }
    
    /**
     * helper constructor
     */
    public HTTPSTransport (String url, String action) {
        super(url, action);
        transportName = DEFAULT_TRANSPORT_NAME;
    }
}

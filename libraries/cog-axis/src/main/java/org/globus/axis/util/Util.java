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
package org.globus.axis.util;

import org.apache.axis.MessageContext;
import org.apache.axis.client.Call;

import org.globus.axis.transport.GSIHTTPTransport;
import org.globus.axis.transport.HTTPSTransport;
import org.globus.axis.gsi.GSIConstants;

import org.ietf.jgss.GSSCredential;

public class Util {
    
    private static boolean transportRegistered = false;
    
    static {
	registerTransport();
    }

    public static GSSCredential getCredentials(MessageContext msgContext) {
	return (GSSCredential)msgContext.getProperty(GSIConstants.GSI_CREDENTIALS);
    }

    public synchronized static void registerTransport() {
	if (transportRegistered) return;
        reregisterTransport();
	transportRegistered = true;
    }

    public synchronized static void reregisterTransport() {
        Call.initialize();
	Call.addTransportPackage("org.globus.net.protocol");
        Call.setTransportForProtocol("httpg", GSIHTTPTransport.class);
        Call.setTransportForProtocol("https", HTTPSTransport.class);
    }

    public static Object getProperty(MessageContext context, String property) {
        Object val = null;
        val = context.getProperty(property);
        if (val != null) {
            return val;
        }
        Call call = (Call) context.getProperty(MessageContext.CALL);
	if (call == null) {
	    return null;
	}
	return call.getProperty(property);
    }
}

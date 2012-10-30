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
package org.globus.axis.example;

import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.encoding.XMLType;
import org.apache.axis.configuration.SimpleProvider;
import org.apache.axis.utils.Options;
import org.apache.axis.SimpleTargetedChain;
import org.apache.axis.transport.http.HTTPSender;

import org.globus.axis.transport.HTTPSSender;
import org.globus.axis.transport.GSIHTTPSender;
import org.globus.axis.gsi.GSIConstants;
import org.globus.axis.util.Util;
import org.globus.gsi.gssapi.auth.SelfAuthorization;

import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;

public class Client {

    public static void main(String [] args) {

	Util.registerTransport();
        
        try {
            Options options = new Options(args);
            
            String endpointURL = options.getURL();
            String textToSend;
            
            args = options.getRemainingArgs();
            if ((args == null) || (args.length < 1)) {
                textToSend = "<nothing>";
            } else {
                textToSend = args[0];
            }

            // these transport handlers would normally
            // be configured in client-config.wsdd file
	    SimpleProvider provider = new SimpleProvider();
	    
	    SimpleTargetedChain c = null;

	    c = new SimpleTargetedChain(new GSIHTTPSender());
	    provider.deployTransport("httpg", c);

            c = new SimpleTargetedChain(new HTTPSSender());
            provider.deployTransport("https", c);
            
	    c = new SimpleTargetedChain(new HTTPSender());
	    provider.deployTransport("http", c);

            // only necessary becuase of Options.getURL()
            // re-initializes Call settings
            Util.reregisterTransport();
	    
            Service  service = new Service(provider);
            Call     call    = (Call) service.createCall();

	    // set globus credentials
            /*
	    call.setProperty(GSIConstants.GSI_CREDENTIALS,
	                     cred);
            */

	    // sets authorization type
	    call.setProperty(GSIConstants.GSI_AUTHORIZATION,
			     SelfAuthorization.getInstance());

	    // sets gsi mode
	    call.setProperty(GSIConstants.GSI_MODE,
			     GSIConstants.GSI_MODE_LIMITED_DELEG);

            call.setTargetEndpointAddress( new URL(endpointURL) );


	    call.setOperationName(new QName("MyService", 
					    "serviceMethod"));
	    
            call.addParameter( "arg1", 
			       XMLType.XSD_STRING, 
			       ParameterMode.IN);

	    call.setReturnType( XMLType.XSD_STRING );
	    
            String ret = (String) call.invoke( new Object[] { textToSend } );
            
            System.out.println("Service response : " + ret);
        } catch (Exception e) {
	    e.printStackTrace();
	}
    }
}

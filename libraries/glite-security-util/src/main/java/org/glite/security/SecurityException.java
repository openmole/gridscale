/*
Copyright (c) Members of the EGEE Collaboration. 2004. 
See http://www.eu-egee.org/partners/ for details on the copyright
holders.  

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 

    http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
*/
package org.glite.security;

import java.security.GeneralSecurityException;


/**
 * This is a security exception which can be converted
 * into an Axis Fault and thrown across a SOAP communication.
 * Based on the <code>org.edg.data.util.FaultableException</code>.
 *
 * @author Niklas Karlsson
 */
public class SecurityException extends GeneralSecurityException {
    /** the serialVersionUID */
	private static final long serialVersionUID = 1831206086348108137L;

	/**
     * Constructs an <code>SecurityException</code> with the specified
     * detail message.
     * @param message the pfn
     */
    public SecurityException(final String message) {
        super(message);
    }

    /**
     * The code for security faults.
     * @return the default code, "SECURITY".
     */
    public static String faultCode() {
        return "SECURITY";
    }
}


// EOF SecurityException.java

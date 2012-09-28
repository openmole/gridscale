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


/**
 * Container class from which the current <code>SecurityInfo</code> can be
 * retrieved. <code>SecurityInfo</code> instances are created, one per thread,
 * by special Servlet and/or SOAP plugins. See the installation guide for
 * more information about these plugins and how they are configured
 *
 * @author mulmo
 * @see SecurityInfo
 */
public class SecurityInfoContainer {
    /**
     * @return The <code>SecurityInfo</code> associated with the currently
     * running thread, or <code>null</code> if no such object have been
     * previously assigned by the special security plugins.
     *
     * @see SecurityInfo
     */
    public static SecurityInfo getSecurityInfo() {
        return SecurityContext.getCurrentContext();
    }
}

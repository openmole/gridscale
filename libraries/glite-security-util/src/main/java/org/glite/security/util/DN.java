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

package org.glite.security.util;

/**
 * An interface representing a DN, used in conjunction with DNHandler to manage
 * the DNs in an uniform way.
 * 
 * @author Joni Hahkala
 * 
 *         Created on September 8, 2003, 2:09 PM
 */
public interface DN {
    /**
     * Used to get the DN in X500 format. E.g. /C=US/O=Nerd Heaven/CN=Nerd Nerdsten
     * 
     * @return String The DN in X500 format
     */
    public String getX500();

    /**
     * Used to get the DN in RFC2253 format. E.g. "C=US,O=Nerd Heaven,CN=Nerd
     * Nerdsten", emailaddress is showed as "Email"
     * 
     * @return String the DN in RFC2253 format
     * @deprecated use the getRFCDN() instead, it produces proper reversed RFC2253 DN, e.g "CN=Nerd Nerdsten,O=Nerd Heaven,C=US".
     */
    public String getRFC2253();

    /**
     * Used to get the DN in RFC2253 format. E.g. "CN=Nerd Nerdsten,O=Nerd Heaven,C=US" (reverse=true) or "C=US,O=Nerd
     * Heaven,CN=Nerd Nerdsten" (reverse=false), emailaddress is showed as "Email"
     * 
     * @deprecated use the getRFCDNv2() instead, it produces proper reversed RFC2253 DN, e.g "CN=Nerd Nerdsten,O=Nerd Heaven,C=US".
     * @return String the DN in RFC2253 format
     */
    public String getRFC2253v2();

    /**
     * Used to get the DN in RFC2253 format. E.g. "CN=Nerd Nerdsten,O=Nerd
     * Heaven,C=US", emailaddress is showed as "Email". Also serialnumber supported.
     * 
     * @return String the DN in RFC2253 format
     */
    public String getRFCDN();

    /**
     * Used to get the DN in RFC2253 format. E.g. "CN=Nerd Nerdsten,O=Nerd
     * Heaven,C=US", emailaddress is showed as "emailAddress" and non supported RDN identifiers like serialnumber as an OID.
     * 
     * @return String the DN in RFC2253 format
     */
    public String getRFCDNv2();

    /**
     * Used to get the DN in canonical (small case) format. E.g. cn=nerd
     * nerdsten,o=nerd heaven,c=us
     * 
     * @return String the DN in canonical format
     */
    public String getCanon();

    /**
     * Used to get a DN instance of the DN without the last CN. E.g. from CN=proxy,CN=Nerd Nerdsten,O=Nerd Heaven,C=US
     * to CN=Nerd Nerdsten,O=Nerd Heaven,C=US Useful for Grid applications to get the DN that is supposed to be in the
     * previous certificate in the chain
     * 
     * @param checkProxy a switch to define whether to check that the removed part is proxy identifier
     * @return DN the DN without the last proxy
     */
    public DN withoutLastCN(boolean checkProxy);
    
    /**
     * Returns the value of the last CN value in the DN. For example for the DN
     * "C=CH, O=CERN, OU=Organic units, CN=John Doe, SN=1234" method would return "John Doe".
     * 
     * @return The value of the last CN.
     */
    public String getLastCNValue();
    
    /**
     * Method to check if the DN is empty.
     * @return returns true if the DN fields are empty.
     */
    public boolean isEmpty();
}

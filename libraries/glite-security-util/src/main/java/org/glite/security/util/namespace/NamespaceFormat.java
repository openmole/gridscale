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

package org.glite.security.util.namespace;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a namespaces file format.
 * 
 * Ex: TO Issuer"/CN=SWITCH CA/emailAddress=switch.ca@switch.ch/O=Switch - Teleinformatikdienste fuer Lehre und Forschung/C=CH"
 * PERMIT Subject "/C=CH/O=.*"
 * 
 * @author alyu
 */
public abstract class NamespaceFormat {
    /**
     * The namespace format.
     */
    private String version = null;

    /** A list of issuer, access and subject pairs */
    private List<NamespacePolicy> policyList = new ArrayList<NamespacePolicy>();

    /**
     * A constructor to create an empty policy.
     */
    public NamespaceFormat() {
    	// empty policy.
    }

    /**
     * Parses a namespaces file.
     * 
     * @param fileName fileName of the namespaces file
     * @throws IOException if unsuccessful
     * @throws ParseException if the namespaces file format is incorrect
     */
    public abstract void parse(String fileName) throws IOException, ParseException;

    /**
     * Returns a list of issuer, access and subject pairs.
     * 
     * @return a list of policies
     */
    public List<NamespacePolicy> getPolices() {
        return policyList;
    }

    /**
     * Returns the namespaces version format.
     * 
     * @return namespaces version format.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the namespaces format version.
     * 
     * @param version the format version.
     */
   public void setVersion(String version) {
        this.version = version;
    }
}

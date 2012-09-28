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
import java.util.List;
import java.security.cert.CertPathValidatorException;

import org.glite.security.util.DN;

/**
 * Interface for DN namespace/policy checker.
 * 
 * @author alyu
 */
public interface DNChecker {
    /**
     * Reads a namespaces file/policy language file.
     * 
     * @param fileName
     *            the namespaces file or policy language file.
     * @throws IOException
     *             if unsuccessful
     * @throws ParseException
     *             if unsuccessful
     */
    public void read(String fileName) throws IOException, ParseException;

    /**
     * Checks the specified subjectDN and issuerDN against previous policies.
     * 
     * @param subjectDN
     *            the subject DN
     * @param issuerDN
     *            the issuer DN
     * @param prevPolicies
     *            previous policies or null
     * @return a set of policies for the issuer and subject DN
     * @throws CertPathValidatorException
     */
    public void check(DN subjectDN, DN issuerDN, List<NamespacePolicy> prevPolicies)
            throws CertPathValidatorException;

}

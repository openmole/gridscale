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
import java.security.cert.CertPathValidatorException;
import java.text.ParseException;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.glite.security.util.DN;

/**
 * Implements DNChecker interface. Checks a DN against a policy/namespace file.
 * 
 * @author alyu
 */
public class DNCheckerImpl implements DNChecker {
    /** The logging facility. */
    private static final Logger LOGGER = Logger.getLogger(DNCheckerImpl.class);

    /** The namespace definition to check against. */
    private NamespaceFormat namespaceFormat = null;

    /**
     * Creates a default DN checker using EU grid namespace format.
     */
    public DNCheckerImpl() {
        this(false);
    }

    /**
     * Creates a default DN Checker using legacy policy format.
     * 
     * @param isLegacy true if old policy format is to be used
     */
    public DNCheckerImpl(boolean isLegacy) {
        if (isLegacy) {
            namespaceFormat = new LegacyNamespaceFormat();
        } else {
            namespaceFormat = new EUGridNamespaceFormat();
        }
    }

    /**
     * Creates a new DN checker with the specified namespace format.
     * 
     * @param namespaceFormat a namespace format
     */
    public DNCheckerImpl(NamespaceFormat namespaceFormat) {
        this.namespaceFormat = namespaceFormat;
    }

    /**
     * Reads a namespaces file/policy language file.
     * 
     * @param fileName the namespaces file or policy language file.
     * @throws IOException if unsuccessful
     * @throws ParseException if unsuccessful
     */
    public void read(String fileName) throws IOException, ParseException {
        namespaceFormat.parse(fileName);
    }

    /**
     * Checks the specified subjectDN and issuerDN against previous policies.
     * 
     * @param subjectDN the subject DN.
     * @param issuerDN the issuer DN.
     * @param prevPolicies previous policies or null.
     * @return a set of policies for the issuer and subject DN for the next rounds.
     * @throws CertPathValidatorException in case the DNs violate the namespace.
     */
    public void check(DN subjectDN, DN issuerDN, List<NamespacePolicy> prevPolicies) throws CertPathValidatorException {

        // the new policies for this round.
        List<NamespacePolicy> policyList = namespaceFormat.getPolices();
        if (prevPolicies != null) {
            // add previous policies
            for (NamespacePolicy p : prevPolicies) {
                policyList.add(p);
            }
        }
        boolean matches = false;
        NamespacePolicy failedPolicy = null;
        boolean issuerMatches = false; // for error message to distinguish whether issuer did not match or subject.

        for (NamespacePolicy policy : policyList) {
            LOGGER.debug("Checking against policy: " + policy);
            String subjectPolicyDN = policy.getSubjectDN();
            DN issuerPolicyDN = policy.getIssuerDN();

            subjectPolicyDN = cleanupDN(subjectPolicyDN);

            if (issuerPolicyDN.equals(issuerDN)) {
                LOGGER.debug("Issuer matches " + issuerPolicyDN);
                issuerMatches = true;
                if (Pattern.matches(subjectPolicyDN, cleanupDN(subjectDN.getX500().toLowerCase()))) {
                    LOGGER.debug("Subject matches " + subjectPolicyDN);
                    if (!policy.isSubjectDNPermitted()) {
                        LOGGER.debug("Policy is deny, rejecting the DN.");
                        failedPolicy = policy;
                        break;
                    }
                    matches = true;
                } else {
                    LOGGER.debug("Subject doesn't match " + subjectPolicyDN);
                }
            } else {
                LOGGER.debug("Issuer doesn't match " + issuerPolicyDN);
            }
        } // for (

        // check for explicit deny
        if (failedPolicy != null) {
            throw new CertPathValidatorException(subjectDN.getX500() + " is denied in the namespace policy line: "
                    + failedPolicy.getPolicyStatement() + " from file: " + failedPolicy.getFilename());
        }

        if (matches) {
            LOGGER.debug("DN is allowed.");
        }

        // check if CA or subject DN were out of policies in case there were any.
        if (!matches && !policyList.isEmpty()) {
            if (issuerMatches) {
                throw new CertPathValidatorException("User: " + subjectDN.getX500() + " is not listed as allowed for issuer: "
                        + issuerDN.getX500());
            }
            throw new CertPathValidatorException("Namespace policy defined, but issuer \"" + issuerDN.getX500() + "\" is not in it.");

        }
    }

    /**
     * Cleans up the DN string, replaces '*' with '.*' for regexps, removes parentheses.
     * 
     * @param dn The DN to clean up.
     * @return The cleaned DN.
     */
    private String cleanupDN(String dn) {
        String newDN = dn;
        if (newDN.contains(".*")) {
            // new format wild card is ok
        } else if (newDN.contains("*")) {
            // replace '*' with '.*' which then can be used by
            // java.util.regex.Pattern
            newDN = newDN.replace("*", ".*");
        }
        if (newDN.contains("(")) {
            newDN = newDN.replace("(", "");
        }
        if (dn.contains(")")) {
            newDN = newDN.replace(")", "");
        }
        return newDN;
    }
}

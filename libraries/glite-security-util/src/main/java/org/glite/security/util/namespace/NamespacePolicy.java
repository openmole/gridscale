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

import org.glite.security.util.DN;

/**
 * Encapsulates the policy statement; issuer DN, access rights and subject DN.
 */
public class NamespacePolicy {

    /** Issuer DN */
    private DN issuerDN = null;
    /** Subject DN */
    private String subjectDN = null;

    /** The access right for the DN */
    private String accessRights = null;

    /** The actual policy statement */
    private String policyStatement = null;

    /** The policy line number */
    private int lineNumber = 0;

    /** The policy statement file name */
    private String filename = null;

    /** Is subject DN permitted */
    private boolean isSubjectDNPermitted = true;

    /**
     * Creates an empty policy object.
     */
    public NamespacePolicy() {
    	// empty policy.
    }

    /**
     * Creates a new policy with the specified issuer and subject DN.
     * 
     * @param issuerDN the issuer DN
     * @param subjectDN the subject DN
     */
    public NamespacePolicy(DN issuerDN, String subjectDN) {
        this(issuerDN, null, subjectDN);
    }

    /**
     * Creates a new policy with the specified issuer, access rights and subject DN.
     * 
     * @param issuerDN the issuer DN
     * @param accessRights the access rights for the subject DN
     * @param subjectDN the subject DN
     */
    public NamespacePolicy(DN issuerDN, String accessRights, String subjectDN) {
        this(issuerDN, accessRights, subjectDN, null, 0, null);
    }

    /**
     * Creates a new policy with the specified issuer, access rights, subject DN, the actual policy statement, line
     * number of the policy and file name.
     * 
     * @param issuerDN the issuer DN
     * @param accessRights the access rights for the subject DN
     * @param subjectDN the subject DN
     * @param policyStatement the actual policy statement
     * @param lineNumber the policy line number
     * @param filename the policy filename
     */
    public NamespacePolicy(DN issuerDN, String accessRights, String subjectDN, String policyStatement,
            int lineNumber, String filename) {
        this.issuerDN = issuerDN;
        this.accessRights = accessRights;
        this.subjectDN = subjectDN;
        this.policyStatement = policyStatement;
        this.lineNumber = lineNumber;
        this.filename = filename;
    }

    /**
     * Returns the issuer DN.
     * 
     * @return the issuer DN
     */
    public DN getIssuerDN() {
        return issuerDN;
    }

    /**
     * Sets the issuer DN.
     * 
     * @param issuerDN the issuer DN to set
     */
    public void setIssuerDN(DN issuerDN) {
        this.issuerDN = issuerDN;
    }

    /**
     * Returns the subject DN.
     * 
     * @return the subject DN
     */
    public String getSubjectDN() {
        return subjectDN;
    }

    /**
     * Sets the subject DN.
     * 
     * @param subjectDN the subject DN to set
     */
    public void setSubjectDN(String subjectDN) {
        this.subjectDN = subjectDN;
    }

    /**
     * Returns the access right for the DN.
     * 
     * @return the access right
     */
    public String getAccessRights() {
        return accessRights;
    }

    /**
     * The access rights for the DN.
     * 
     * @param accessRights the access rights to set
     */
    public void setAccessRights(String accessRights) {
        this.accessRights = accessRights;
    }

    /**
     * Returns true if the subject DN is permitted
     * 
     * @return true if subject DN is permitted
     */
    public boolean isSubjectDNPermitted() {
        return isSubjectDNPermitted;
    }

    /**
     * Set to true if subject DN is permitted.
     * 
     * @param isPermitted true if permitted
     */
    public void subjectDNPermitted(boolean isPermitted) {
        isSubjectDNPermitted = isPermitted;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append(policyStatement);
        buf.append('\n');
        buf.append("Line number=");
        buf.append(lineNumber);
        buf.append('\n');
        buf.append("Filename=");
        buf.append(filename);
        buf.append('\n');
        return buf.toString();
    }

    /**
     * Returns the actual policy statement.
     * 
     * @return the policy statement
     */
    public String getPolicyStatement() {
        return policyStatement;
    }

    /**
     * Sets the policy statement.
     * 
     * @param policyStatement the policy statement
     */
    public void setPolicyStatement(String policyStatement) {
        this.policyStatement = policyStatement;
    }

    /**
     * Returns the line number of this policy line.
     * 
     * @return the line number
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Sets the line number of this policy line.
     * 
     * @param lineNumber the line number
     */
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
     * Returns the policy filename.
     * 
     * @return the policy filename
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Sets the policy filename.
     * 
     * @param filename the policy filename
     */
    public void setFilename(String filename) {
        this.filename = filename;
    }
}

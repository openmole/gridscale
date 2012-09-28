/*
 * Copyright (c) Members of the EGEE Collaboration. 2004. See http://www.eu-egee.org/partners/ for details on the
 * copyright holders. Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.glite.security.delegation;

/**
 * New proxy certificate request, containing the certificate request and a generated delegation ID.
 */
public class NewProxyReq {
    /** The new RFC 3280 style proxy certificate request in PEM format with Base64 encoding. */
    private String proxyRequest;
    /** The ID associated with the new delegation session. */
    private String delegationID;

    /**
     * The default constructor.
     */
    public NewProxyReq() {
        // empty.
    }

    /**
     * The constructor with given information.
     * 
     * @param proxyRequest The contents of the proxy request.
     * @param delegationID The delegation id.
     */
    public NewProxyReq(String proxyRequest, String delegationID) {
        this.proxyRequest = proxyRequest;
        this.delegationID = delegationID;
    }

    /**
     * Gets the proxyRequest value for this NewProxyReq.
     * 
     * @return proxyRequest The new RFC 3280 style proxy certificate request in PEM format with Base64 encoding.
     */
    public String getProxyRequest() {
        return proxyRequest;
    }

    /**
     * Sets the proxyRequest value for this NewProxyReq.
     * 
     * @param proxyRequest The new RFC 3280 style proxy certificate request in PEM format with Base64 encoding.
     */
    public void setProxyRequest(String proxyRequest) {
        this.proxyRequest = proxyRequest;
    }

    /**
     * Gets the delegationID value for this NewProxyReq.
     * 
     * @return delegationID The ID associated with the new delegation session.
     */
    public String getDelegationID() {
        return delegationID;
    }

    /**
     * Sets the delegationID value for this NewProxyReq.
     * 
     * @param delegationID The ID associated with the new delegation session.
     */
    public void setDelegationID(String delegationID) {
        this.delegationID = delegationID;
    }
}

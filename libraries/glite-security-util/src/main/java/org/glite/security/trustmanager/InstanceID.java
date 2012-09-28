/*
 * Copyright (c) Members of the EGEE Collaboration. 2004. See
 * http://www.eu-egee.org/partners/ for details on the copyright holders.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.glite.security.trustmanager;

/**
 * Defines the OpensslTrustmanager instance.
 * 
 * @author Joni Hahkala
 */
public class InstanceID {
    /**
     * The id of this instance.
     */
    private String m_id;
    /**
     * The trust dir path.
     */
    private String m_path;
    /**
     * The flag for CRLs being required or not.
     */
    private boolean m_crlRequired;

    /**
     * The constructor for the instanceID
     * 
     * @param id The optional id for this trustmanager, to distinguish between trustmanagers with same configuration.
     *            Can be null.
     * @param path The Trust anchor path. Can't be null.
     * @param crlRequired Whether CRLs are required or not for the trustmanager.
     */
    public InstanceID(String id, String path, boolean crlRequired) {
        super();
        this.m_id = id;
        if (path == null) {
            throw new IllegalArgumentException("Given path was null, which is not allowed.");
        }
        this.m_path = path;
        m_crlRequired = crlRequired;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (m_crlRequired ? 1231 : 1237);
        result = prime * result + ((m_id == null) ? 0 : m_id.hashCode());
        result = prime * result + ((m_path == null) ? 0 : m_path.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof InstanceID)) {
            return false;
        }
        InstanceID other = (InstanceID) obj;
        if (m_crlRequired != other.m_crlRequired) {
            return false;
        }
        if (m_id == null) {
            if (other.m_id != null) {
                return false;
            }
        } else if (!m_id.equals(other.m_id)) {
            return false;
        }
        if (m_path == null) {
            if (other.m_path != null) {
                return false;
            }
        } else if (!m_path.equals(other.m_path)) {
            return false;
        }
        return true;
    }

    /**
     * @return the ID of this trustmanager.
     */
    public String getID() {
        return m_id;
    }

    /**
     * @return the path for trust anchors of this trustmanager.
     */
    public String getPath() {
        return m_path;
    }

    /**
     * @return Whether the CRLs are required or not.
     */
    public boolean isCRLRequired() {
        return m_crlRequired;
    }
}
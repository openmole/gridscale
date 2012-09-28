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

package org.glite.security.util;

import java.security.Principal;


/**
 * Why was this class created?... For getting an X500Principal that implements 
 * Principal interface, which java original X500Principal doesn't? 
 * For deprecated java authorization framework?
 *
 * @author Joni Hahkala <joni.hahkala@cern.ch>
 * Created on Mar 23, 2005
 */
public class X500Principal implements Principal {
    /** DOCUMENT ME! */
    DN dN;

    /**
     * DOCUMENT ME!
     *
     * @param dN DOCUMENT ME!
     */
    public void setName(DN dN) {
        this.dN = dN;
    }

    /* (non-Javadoc)
     * @see java.security.Principal#getName()
     */

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public String getName() {
        // TODO Auto-generated method stub
        return dN.getX500();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */

    /**
     * DOCUMENT ME!
     *
     * @param obj DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public boolean equals(Object obj) {
        // TODO Auto-generated method stub
        return super.equals(obj);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public int hashCode() {
        // TODO Auto-generated method stub
        return super.hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public String toString() {
        // TODO Auto-generated method stub
        return "X500Principal: " + getName();
    }
}

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

package org.glite.security.delegation;

/**
 * Exception to be thrown if there is a problem during delegation handling.
 *
 */
public class DelegationException extends Exception {
    /**
     * Generated serial to be properly serializable.
     */
    private static final long serialVersionUID = -8514894331115505968L;
    
    /** The cause of the delegation exception on the server side. */
    private String msg;

    /**
     * The default constructor.
     */
    public DelegationException() {
        // empty on purpose
    }

    /**
     * Constructor with message.
     * @param msg The error message.
     */
    public DelegationException(String msg) {
        this.msg = msg;
    }


    /**
     * Gets the msg value for this DelegationException.
     * 
     * @return msg The cause of the delegation exception on the server side.
     */
    public String getMsg() {
        return msg;
    }


    /**
     * Sets the msg value for this DelegationException.
     * 
     * @param msg The cause of the delegation exception on the server side.
     */
    public void setMsg(String msg) {
        this.msg = msg;
    }

}

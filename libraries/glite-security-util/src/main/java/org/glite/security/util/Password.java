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

import org.bouncycastle.openssl.PasswordFinder;


/**
 * a copy from bouncyCastle test class
 *
 * @author Joni Hahkala <joni.hahkala@cern.ch>
 *
 * Created on April 8, 2002, 9:26 PM
 */
public class Password implements PasswordFinder {
    /** DOCUMENT ME! */
    char[] password;

    /**
     * Creates a new Password object.
     *
     * @param word DOCUMENT ME!
     */
    public Password(char[] word) {
        this.password = word;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public char[] getPassword() {
        return password;
    }
}

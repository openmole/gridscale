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

import java.security.cert.CertificateException;

/**
 * An exception class to signal that a certificate is revoked.
 * 
 * @author Joni Hahkala
 *
 */
public class CertificateRevokedException extends CertificateException {

    /** generated serialVersionUID. */
    private static final long serialVersionUID = 8653600665122229010L;
    
    /**
     * Generates new exception.
     * 
     * @param message The error message.
     */
    public CertificateRevokedException(String message){
        super(message);
    }

}

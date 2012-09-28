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

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author hahkala
 *
 */
public abstract class RevocationChecker {
    
    /**
     * @param caCert
     * @param caHash
     * @param caNumber
     * @param props
     */
    public RevocationChecker(X509Certificate caCert, String caHash, int caNumber, CaseInsensitiveProperties props) {
        // empty, this must be overridden by implementations.
    }
    
    /**
     * Checks the certificate for revocation.
     * 
     * @param cert The certificate to check.
     * @return true in case the certificate is trusted. False if the certificate is revoked.
     * @throws IOException in case the CRL reading or info loading fails. 
     * @throws CertificateException in case CRL is malformed, certificate is malformed or revocation check fails.
     */
    public abstract void check(X509Certificate cert) throws IOException, CertificateException, CertificateRevokedException;
    
    /**
     * If the implementation is file based for example, this will check whether the file has changed and if yes, reloads it.
     * 
     * @throws IOException if file or network reading fails.
     * @throws CertificateException if e.g. CRL parsing fails.
     */
    public abstract void checkUpdate() throws IOException, CertificateException;
}

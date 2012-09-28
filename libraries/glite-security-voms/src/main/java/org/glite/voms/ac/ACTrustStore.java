/*
 * Copyright (c) Members of the EGEE Collaboration. 2004.
 * See http://eu-egee.org/partners/ for details on the copyright holders.
 * For license conditions see the license file or http://eu-egee.org/license.html
 */

package org.glite.voms.ac;

import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;


/**
 * @deprecated This does not expose the necessary information.
 *
 * @author mulmo
 */
public interface ACTrustStore {
    /**
     * Returns an array of issuer candidates, by performing a name
     * comparison of the AC's issuer and the subject names of the
     * certificates in the trust store.
     * <br>
     * <b>NOTE:</b> No actual verification or validation of signature
     * takes place in this function.
     *
     * @param issuer the principal to find an issuer for.
     * If <code>null</code>, all known AAs will be returned.
     * @return an array of issuer candidates, or <code>null</code> in
     * case of an error.
     */
    public X509Certificate[] getAACandidate(X500Principal issuer);
}

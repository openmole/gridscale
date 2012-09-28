/*********************************************************************
 *
 * Authors: Vincenzo Ciaschini - Vincenzo.Ciaschini@cnaf.infn.it
 *
 * Copyright (c) 2002, 2003, 2004, 2005, 2006 INFN-CNAF on behalf of the 
 * EGEE project.
 * For license conditions see LICENSE
 *
 * Parts of this code may be based upon or even include verbatim pieces,
 * originally written by other people, in which case the original header
 * follows.
 *
 *********************************************************************/
package org.glite.voms.ac;

import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import org.glite.voms.LSCFile;


/**
 * @author Vincenzo Ciaschini
 */
public interface VOMSTrustStore {
    /**
     * Returns the LSCFile corresponding to the VO and Host specified.
     *
     * @param voName the name of the VO.
     * @param hostName the name of the issuing host.
     *
     * @return the LSCfile, or null if none is found.
     */
    public LSCFile getLSC(String voName, String hostName);

    /**
     * Returns candidates to the role of signer of an AC with he given
     * issuer and of the give VO.
     *
     * @param issuer the DN of the signer.
     * @param voName the VO to which he signer belongs.
     *
     * @return an array of issuer candidates, or null if none is found.
     */
    public X509Certificate[] getAACandidate(X500Principal issuer, String voName);

    /**
     * Stops refreshing the store.
     *
     * This method MUST be called prior to disposing of the store.
     */
    public void stopRefresh();
}


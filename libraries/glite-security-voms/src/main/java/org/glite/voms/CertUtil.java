package org.glite.voms;

import org.apache.log4j.Logger;

import java.security.cert.X509Certificate;

/**
 * Reads a DER-encode, Base64-encoded, or PEM-encoded certificate from disk
 * without using broken IAIK implementations...
 *
 * @author mulmo
 */
class CertUtil {
    /** log4j util for logging */
    static Logger logger = Logger.getLogger(org.glite.voms.CertUtil.class.getName());

    /**
     * Finds out the index of the client cert in a certificate chain.
     * @param X509Certificate[] the cert chain
     * @return the index of the client cert of -1 if no client cert was
     * found
     */
    public static int findClientCert(X509Certificate[] chain) {
        int i;
        // get the index for the first cert that isn't a CA or proxy cert
        for (i = chain.length-1 ; i >= 0 ; i--) {
            // if constrainCheck = -1 the cert is NOT a CA cert
            if (chain[i].getBasicConstraints() == -1) {
                // double check, if issuerDN = subjectDN the cert is CA
                if (!chain[i].getIssuerDN().equals(chain[i].getSubjectDN())) {
                    break;
                }
            }
        }

        // no valid client certs found, print an error message?
        if (i == chain.length) {
            logger.error("UpdatingKeymanager: invalid certificate chain, client cert missing.");

            return -1;
        } else {
            return i;
        }
    }
}

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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.apache.log4j.Logger;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.openssl.PEMReader;
import org.glite.security.util.DNHandler;
import org.glite.security.util.FileCertReader;
import org.glite.security.util.Password;
import org.glite.security.util.PrivateKeyReader;
import org.glite.security.util.proxy.ProxyCertificateGenerator;

/**
 * The delegation handler class
 */
public class DelegationHandler {
    private static final Logger LOGGER = Logger.getLogger(DelegationHandler.class);
    private X509Certificate[] m_certs = null;
//    private byte[] x509Cert = null;
    private String strX509CertChain = null;

    /**
     * Class constructor
     * 
     * @param certReq Service certificate request
     * @param delegationID Delegation identifier
     * @param propFile location of properties file
     */
    public DelegationHandler(String certReq, String delegationID, String propFile) throws Exception {
        requestHandler(certReq, delegationID, propFile);
    }

    /**
     * Handles the service certificate request and generates a proxy certificate. Stores the new proxy certificate in
     * proxyStorage
     * 
     * @param certReq Service certificate request
     * @param delegationID Delegation identifier
     * @param propFile location of properties file
     * @return Generated proxy certificate
     */
    private void requestHandler(String certReq, String delegationID, String propFile) throws Exception{
        GrDProxyDlgorOptions dlgorOpt = null;
        try {
            dlgorOpt = new GrDProxyDlgorOptions(propFile);
        } catch (IOException e2) {
            LOGGER.error("failed to read delegation options from: " + propFile + " nor from default location. Error was: " + e2.getMessage());
            return;
        }

        try {
            LOGGER.debug("User Cert/Proxy File" + dlgorOpt.getDlgorCertFile());
            LOGGER.debug("User Key/Proxy File" + dlgorOpt.getDlgorKeyFile());
            LOGGER.debug("User Password" + dlgorOpt.getDlgorPass());
            LOGGER.debug("Certificate Request" + certReq);

            FileCertReader certReader = new FileCertReader();
            X509Certificate[] certs = (X509Certificate[]) certReader.readCerts(dlgorOpt.getDlgorCertFile())
                    .toArray(new X509Certificate[]{});
            
            for (int n = 0; n < certs.length; n++){
            	LOGGER.debug("cert [" + n + "] is from " + DNHandler.getSubject(certs[n]).getRFCDN());
            }

            
            PEMReader pemReader = new PEMReader(new StringReader(certReq));
            PKCS10CertificationRequest req;
            try {
                req = (PKCS10CertificationRequest)pemReader.readObject();
            } catch (IOException e1) {
                LOGGER.error("Could not load the original certificate request from cache.");
                throw new DelegationException("Could not load the original certificate request from cache: " + e1.getMessage());
            }
            ProxyCertificateGenerator genGen = new ProxyCertificateGenerator(certs, req);
            
            Password pass = null;
            if(dlgorOpt.getDlgorPass() != null){
            	pass = new Password(dlgorOpt.getDlgorPass().toCharArray());
            }
            
            PrivateKey key = PrivateKeyReader.read(new BufferedReader(new FileReader(dlgorOpt.getDlgorKeyFile())), pass);
            
            genGen.generate(key);
            
            certs = genGen.getCertChain();
            
            strX509CertChain = genGen.getCertChainAsPEM();
        } catch (Exception e) {
            LOGGER.error("Proxy generation failed: " + e);
            throw e;
        }
    }

    /**
     * Return generated proxy certificate
     * 
     * @return Generated proxy certificate
     */
    public X509Certificate[] getProxyCertificate() throws Exception {
        return m_certs;
    }

    /**
     * Return generated proxy certificate in PEM format
     * 
     * @return Generated proxy certificate
     */
    public String getPEMProxyCertificate() {
        return strX509CertChain;
    }
}

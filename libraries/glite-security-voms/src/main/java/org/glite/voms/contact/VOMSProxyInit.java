/*********************************************************************
 *
 * Authors: 
 *      Andrea Ceccanti - andrea.ceccanti@cnaf.infn.it 
 *          
 * Copyright (c) 2006 INFN-CNAF on behalf of the EGEE project.
 * 
 * For license conditions see LICENSE
 *
 * Parts of this code may be based upon or even include verbatim pieces,
 * originally written by other people, in which case the original header
 * follows.
 *
 *********************************************************************/
package org.glite.voms.contact;

import org.apache.log4j.Logger;
import org.glite.voms.PKIVerifier;
import org.glite.voms.ac.AttributeCertificate;
import org.globus.gsi.CredentialException;
import org.globus.gsi.GSIConstants.CertificateType;
import org.globus.gsi.GSIConstants.DelegationType;
import org.globus.gsi.X509Credential;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;


/**
 *
 * This class implements the voms-proxy-init functionality.
 * 
 * @author Andrea Ceccanti
 *
 */
public class VOMSProxyInit {
    private UserCredentials userCredentials;
    private VOMSProtocol protocol = VOMSProtocol.instance();

    private int proxyLifetime = VOMSProxyBuilder.DEFAULT_PROXY_LIFETIME;

    private int proxySize = VOMSProxyBuilder.DEFAULT_PROXY_SIZE;
    
    private CertificateType proxyType = VOMSProxyBuilder.DEFAULT_PROXY_TYPE;
    
    private DelegationType delegationType = VOMSProxyBuilder.DEFAULT_DELEGATION_TYPE;

    private String policyType = null;

    public VOMSProxyInit(String privateKeyPassword) {
        userCredentials = UserCredentials.instance(privateKeyPassword);
    }

    //sreynaud
    public VOMSProxyInit(String userCertFile, String userKeyFile, String privateKeyPassword){
        userCredentials = UserCredentials.instance(userCertFile, userKeyFile, privateKeyPassword);
    }

    private VOMSProxyInit(X509Credential credentials) {
        if (credentials == null)
            throw new VOMSException("Unable to find GlobusCredentials!");

        try {
            userCredentials = UserCredentials.instance(credentials);
        } catch (CredentialException e) {
            throw new VOMSException(e);
        }
    }

    //sreynaud
    public VOMSProxyInit(File pkcs12UserCert, String pkcs12KeyPassword){
        userCredentials = UserCredentials.instance(pkcs12UserCert, pkcs12KeyPassword);
    }

    //sreynaud
    public static VOMSProxyInit instance(File pkcs12UserCert, String pkcs12KeyPassword) {
        return new VOMSProxyInit(pkcs12UserCert, pkcs12KeyPassword);
    }

    //sreynaud
    public static VOMSProxyInit instance(String userCertFile, String userKeyFile, String privateKeyPassword) {
        return new VOMSProxyInit(userCertFile, userKeyFile, privateKeyPassword);
    }

    public static VOMSProxyInit instance(String privateKeyPassword){
        return new VOMSProxyInit(privateKeyPassword);
    }
    
    public static VOMSProxyInit instance(){
        return new VOMSProxyInit((String)null);
    }

    public static VOMSProxyInit instance(X509Credential credentials) {
        return new VOMSProxyInit(credentials);
    }

    
    public synchronized AttributeCertificate getVomsAC(VOMSServerInfo serverInfo, VOMSRequestOptions requestOptions){
        VOMSResponse response = contactServer( serverInfo, requestOptions );

        if (!response.hasErrors()){
            AttributeCertificate ac = VOMSProxyBuilder.buildAC(response.getAC());
            return ac;
        } else {
            StringBuilder b = new StringBuilder();
            for(VOMSErrorMessage e: response.errorMessages()) {
                b.append(e.getMessage());
                b.append("; ");
            }
            throw new VOMSException("Error(s) in the response of the VOMS server: " + b.toString());
        }
    }
    
    public void validateACs(List ACs){
        if (ACs.isEmpty())
            throw new VOMSException("Cannot validate an empty list of Attribute Certificates!");
        
        PKIVerifier verifier;
        
        try {
            verifier = new PKIVerifier();
        } catch ( Exception e ) {
            throw new VOMSException("Error instantiating PKIVerifier: "+e.getMessage(),e);
        }
        
        Iterator i = ACs.iterator();
        
        while(i.hasNext()){
            AttributeCertificate ac = (AttributeCertificate)i.next();
            if (!verifier.verify( ac )) i.remove();
        }
    }

    protected X509Credential getGridProxy(){
        return VOMSProxyBuilder.buildProxy( userCredentials, proxyLifetime, delegationType );
    }

    public synchronized X509Credential getVomsProxy(VOMSServerInfo serverInfo, VOMSRequestOptions requestOptions){
        AttributeCertificate ac = getVomsAC( serverInfo, requestOptions );
         List ACs = new ArrayList();
        ACs.add(ac);

        return VOMSProxyBuilder.buildProxy( userCredentials,
          ACs, proxySize, proxyLifetime,
          proxyType,
          delegationType,
          policyType);

    }


    protected VOMSResponse contactServer(VOMSServerInfo sInfo, VOMSRequestOptions reqOptions) {
        VOMSSocket socket;
        
        int gridProxyType = sInfo.getGlobusVersionAsInt();
        
        if (gridProxyType > 0) {
            CertificateType pType = VOMSProxyBuilder.DEFAULT_PROXY_TYPE;
            switch(gridProxyType) {
                case 2: pType = VOMSProxyBuilder.GT2_PROXY; break;
                case 3: pType = VOMSProxyBuilder.GT3_PROXY; break;
                case 4: pType = VOMSProxyBuilder.GT4_PROXY; break;
            }
            socket = VOMSSocket.instance( userCredentials, sInfo.getHostDn(), pType );
        } else {
            socket = VOMSSocket.instance( userCredentials, sInfo.getHostDn());
        }
        
        try {
            socket.connect( sInfo.getHostName(), sInfo.getPort());
        } catch ( Exception e ) {
            throw new VOMSException("Error connecting to "+sInfo.compactString()+":"+e.getMessage() ,e);
        }

        try {
            VOMSResponse response;

            try {
                protocol.sendRequest(reqOptions, socket.getOutputStream());
                response = protocol.getResponse(socket.getInputStream());
                return response;
            } catch (IOException e) {
                throw new VOMSException("Error communicating with server " + sInfo.getHostName() + ":" + sInfo.getPort() + ":" + e.getMessage(), e);
            }
        } finally  {
            try {
                socket.close();
            } catch (IOException e){
                throw new RuntimeException("Error in close", e);
            }
        }
           
    }

    public int getProxyLifetime() {
        return proxyLifetime;
    }
    
    public void setProxyLifetime( int proxyLifetime ) {
        this.proxyLifetime = proxyLifetime;
    }

    public int getProxySize() {
        return proxySize;
    }

    public void setProxySize(int proxySize) {
        this.proxySize = proxySize;
    }

    public CertificateType getProxyType() {
        return proxyType;
    }

    
    public void setProxyType( CertificateType proxyType ) {
        this.proxyType = proxyType;
    }

    
    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType( String policyType ) {
        this.policyType = policyType;
    }

    public DelegationType getDelegationType() {
        return delegationType;
    }

    
    public void setDelegationType( DelegationType delegationType ) {
        this.delegationType = delegationType;
    }
    
    
}

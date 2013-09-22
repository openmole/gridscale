/*********************************************************************
 *
 * Authors: 
 *      
 *      Andrea Ceccanti - andrea.ceccanti@cnaf.infn.it
 *      Gidon Moont - g.moont@imperial.ac.uk  
 *          
 * Copyright (c) 2002, 2003, 2004, 2005, 2006 INFN-CNAF on behalf 
 * of the EGEE project.
 * 
 * For license conditions see LICENSE
 *
 * Parts of this code may be based upon or even include verbatim pieces,
 * originally written by other people, in which case the original header
 * follows.
 *
 *********************************************************************/
package org.glite.voms.contact;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.security.cert.X509Certificate;

import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.globus.gsi.GSIConstants;
import org.globus.gsi.GSIConstants.DelegationType;
import org.globus.gsi.X509Credential;
import org.globus.gsi.gssapi.GSSConstants;
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl;
import org.globus.gsi.gssapi.GlobusGSSManagerImpl;
import org.globus.gsi.gssapi.auth.Authorization;
import org.globus.gsi.gssapi.auth.IdentityAuthorization;
import org.globus.gsi.gssapi.net.GssSocket;
import org.globus.gsi.gssapi.net.GssSocketFactory;
import org.gridforum.jgss.ExtendedGSSContext;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;

/**
 * The {@link VOMSSocket} class is used to manage the creation of the gsi socket used for communication with
 * the VOMS server. 
 *  
 * @author Andrea Ceccanti
 * 
 *
 */
public class VOMSSocket {
    
    private static final Logger log = Logger.getLogger( VOMSSocket.class );
    
    UserCredentials cred;
    
    String hostDN;
    
    X509Credential proxy = null;
        
    //int proxyType = VOMSProxyBuilder.DEFAULT_PROXY_TYPE;
    GSIConstants.CertificateType proxyType = VOMSProxyBuilder.DEFAULT_PROXY_TYPE ;

    private GssSocket socket = null;
    
    public static VOMSSocket instance(UserCredentials cred, String hostDN, GSIConstants.CertificateType proxyType){
        return new VOMSSocket(cred, hostDN, proxyType);
        
    }
    
    public static VOMSSocket instance(UserCredentials cred, String hostDN){
        
        //return new VOMSSocket(cred, hostDN, VOMSProxyBuilder.DEFAULT_PROXY_TYPE);
        return new VOMSSocket(cred, hostDN, VOMSProxyBuilder.DEFAULT_PROXY_TYPE);

    }
    
    private VOMSSocket(UserCredentials cred, String hostDN, GSIConstants.CertificateType proxyType){
        
        this.cred = cred;
        this.hostDN = hostDN;
        this.proxyType= proxyType;
    }
    
    /**
     * 
     * Connects this socket to the voms server identified by the (host,port) passed
     * as arguments.
     * 
     * @param host
     * @param port
     * @throws GSSException
     * @throws IOException
     * @throws GeneralSecurityException
     * 
     * @author Andrea Ceccanti
     * @author Gidon Moont
     */
    protected void connect(String host, int port) throws GSSException, IOException, GeneralSecurityException{
        
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        
        
        GSSManager manager = new GlobusGSSManagerImpl();
        Authorization auth = new IdentityAuthorization(hostDN);
        log.debug( "hostDN:"+hostDN );
        GSSCredential clientCreds;
                
       // proxy = VOMSProxyBuilder.buildProxy( cred, VOMSProxyBuilder.DEFAULT_PROXY_LIFETIME, proxyType);
         proxy = VOMSProxyBuilder.buildProxy( cred, VOMSProxyBuilder.DEFAULT_PROXY_LIFETIME, proxyType);

        try {
            
            clientCreds = (GSSCredential)new GlobusGSSCredentialImpl( proxy , GSSCredential.INITIATE_ONLY ) ;
            
        } catch ( GSSException e ) {
            log.fatal( "Error creating gss credential: "+e.getMessage() );
            if (log.isDebugEnabled())
                log.debug( e.getMessage(),e );
            throw e;
        }
        
        ExtendedGSSContext context = (ExtendedGSSContext) manager.createContext(null, 
                GSSConstants.MECH_OID,
                clientCreds, 
                86400); 
        
        context.requestMutualAuth( true ) ;
        context.requestCredDeleg( false ) ;
        context.requestConf( true ) ;
        context.requestAnonymity( false ) ;

        context.setOption( GSSConstants.GSS_MODE , GSIConstants.MODE_GSI ) ;
        context.setOption( GSSConstants.REJECT_LIMITED_PROXY , new Boolean( false ) ) ;
                
        try {
            socket = (GssSocket) GssSocketFactory.getDefault().createSocket( host , port , context );
            socket.setWrapMode( GssSocket.GSI_MODE ) ;
            socket.setAuthorization( auth ) ;
        } catch ( IOException e ) {
            
            log.fatal( "Error opening GSS socket: "+e.getMessage() );
            
            if (log.isDebugEnabled())
                log.debug( e.getMessage(),e );
            throw e;
        }
        
    }
    
    public void close() throws IOException {

        socket.close();
    }

    public GSSContext getContext() {

        return socket.getContext();
    }

    public boolean isClosed() {

        return socket.isClosed();
    }

    public boolean isConnected() {

        return socket.isConnected();
    }

    public void shutdownInput() throws IOException {

        socket.shutdownInput();
    }

    public void shutdownOutput() throws IOException {

        socket.shutdownOutput();
    }

    public OutputStream getOutputStream() throws IOException{
        try {
            return socket.getOutputStream();
        } catch ( IOException e ) {
            
            log.error( "Error getting output stream from underlying socket:"+e.getMessage() );
            if (log.isDebugEnabled())
                log.error(e.getMessage(),e);
            
            throw e;
        }
    }
    
    public InputStream getInputStream() throws IOException{
        
        try {
            
            return socket.getInputStream();
            
        } catch ( IOException e ) {
            log.error( "Error getting input stream from underlying socket:"+e.getMessage() );
            if (log.isDebugEnabled())
                log.error(e.getMessage(),e);
            
            throw e;
            
        }
    }

}

package org.glite.voms.contact;



import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.TransformerException;

import org.apache.log4j.PropertyConfigurator;
import org.globus.gsi.X509Credential;
import org.ietf.jgss.GSSException;


public class Test {


    public static void main( String[] args ) throws TransformerException, GSSException, IOException, GeneralSecurityException, ParseException {
        
        PropertyConfigurator.configure( "./src/api/java/log4j.properties" );
        
        VOMSRequestOptions options = new VOMSRequestOptions();
        VOMSRequestOptions vo8Options = new VOMSRequestOptions();
        
        vo8Options.setVoName( "vo8" );
        options.setVoName( "test_oci" );
        options.addFQAN( "/test_oci/Role=CiccioPaglia" );
        options.setOrdering( "/test_oci/Role=CiccioPaglia,/test_oci" );
        
        List optLists = new ArrayList();
        
        optLists.add( options );
        optLists.add( vo8Options );
        
        VOMSProxyInit proxyInit = VOMSProxyInit.instance();
        
        X509Credential proxy = proxyInit.getVomsProxy( optLists );

    }
}

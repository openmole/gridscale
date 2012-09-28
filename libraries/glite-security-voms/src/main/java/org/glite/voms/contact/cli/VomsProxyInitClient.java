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
package org.glite.voms.contact.cli;

import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.log4j.Logger;
import org.glite.voms.contact.VOMSProxyBuilder;
import org.glite.voms.contact.VOMSProxyInit;
import org.glite.voms.contact.VOMSRequestOptions;
import org.glite.voms.contact.VOMSException;
import org.globus.gsi.GSIConstants;
import org.globus.gsi.GSIConstants.CertificateType;
import org.globus.gsi.GSIConstants.DelegationType;

/**
 * 
 * This class implements a command-line voms-proxy-init client.
 * 
 * @author Andrea Ceccanti
 *
 */
public class VomsProxyInitClient {
    
    private static final Logger log = Logger
            .getLogger( VomsProxyInitClient.class );
    
    protected VOMSProxyInit proxyInit;
    
    protected CommandLineParser parser = new GnuParser();
    protected HelpFormatter helpFormatter = new HelpFormatter();
    protected Options options;
    
    String[] fqans;
    String targets;
    String ordering;
    int lifetime;
    
    String proxyOutput;
    String keyPassword;
    String proxyType;
    String delegationType;
    String policyType;
    
    
    protected void setupUserCredentials(String userCert,String userKey){
        System.setProperty( "X509_USER_CERT", userCert);
        System.setProperty( "X509_USER_KEY", userKey);
        
    }
    
    protected void setupVomsesPath(String vomsesPath){
        
        System.setProperty( "VOMSES_LOCATION", vomsesPath);
        
    }
    
    protected void setupVomsdir(String vomsdir){
        
        System.setProperty("VOMSDIR",vomsdir);
        
    }
    
    protected void setupCaDir(String caDir){
        System.setProperty("CADIR",caDir);
        
    }
    
    
    
    protected void setupCLParser(){
        options = new Options();
        
        
        
        options.addOption( OptionBuilder.withLongOpt( "help" )
                .withDescription( "Displays helps and exits." )
                .create("h"));
        
        
        options.addOption(OptionBuilder.withLongOpt( "vomsdir" )
                .withDescription( "Specifies non-standard vomsdir directory." )
                .hasArg()             
                .create("vomsdir")); 
        
        options.addOption(OptionBuilder.withLongOpt( "cadir" )
                .withDescription( "Specifies non-standard ca certificate directory." )
                .hasArg(true)
                .create("cadir"));
        
        options.addOption( OptionBuilder.withLongOpt( "vomsesPath" )
                .withDescription( "Specifies non-standard locations where the voms-proxy-init looks for vomses files. The path is a colon (:) separated list of paths." )
                .hasArg()
                .create("vomsesPath") );
        
        
        options.addOption(OptionBuilder.withLongOpt( "usercert" )
                .withDescription( "Specifies non-standard user certificate." )
                .hasArg()
                .create("usercert"));
        
        options.addOption(OptionBuilder.withLongOpt( "userkey" )
                .withDescription( "Specifies non-standard user private key." )
                .hasArg()
                .create("userkey"));
        
                
        options.addOption( OptionBuilder.withLongOpt( "password" )
                .withDescription( "Specifies a password that is used to decrypt the user's private key." )
                .hasArg()
                .create("password") );
        
        options.addOption( OptionBuilder.withLongOpt( "lifetime" )
                .withDescription( "Specifies the lifetime for the generated proxy." )
                .hasArg()
                .create("lifetime") );
        
        options.addOption( OptionBuilder.withLongOpt( "out" )
                .withDescription( "Specifies a non-standard location for the generated proxy. The standard location is /tmp/X509_up_<username>." )
                .hasArg()
                .create("out") );
        
        
        
        options.addOption( OptionBuilder.withLongOpt( "order" )
                .withDescription( "Specifies the ordering of received attributes. The options is a comma (,) separated list of FQANs." )
                .hasArg()
                .create("order") );
        
        options.addOption( OptionBuilder.withLongOpt( "voms" )
                .withDescription( "Specifies a request FQAN in the form: <voName>:<FQAN>." )
                .hasArgs()
                .create("voms") );
        
        options.addOption( OptionBuilder.withLongOpt( "targets" )
                .withDescription( "Targets the AC against a specific comma separated list of hostnames." )
                .hasArg()
                .create("targets") );
        
        options.addOption( OptionBuilder.withLongOpt( "proxyType" )
                .withDescription( "Specifies the type of proxy that will be generated. Possible values are: GT2_PROXY, GT3_PROXY, GT4_PROXY. The default value is GT2_PROXY." )
                .hasArg()
                .create("proxyType") );

        options.addOption( OptionBuilder.withLongOpt("policyType" )
                .withDescription( "Specifies the policy type of the proxy.  Only significant with proxyType >= GT3_PROXY.")
                .hasArg()
                .create("policyType"));

        options.addOption( OptionBuilder.withLongOpt( "delegationType" )
                .withDescription( "Specifies the type of delegation requested for the generated proxy. Possible values are: NONE, LIMITED, FULL. The default value is FULL." )
                .hasArg()
                .create("delegationType") );
        
        
    }
    
    protected void printHelpMessageAndExit(int exitStatus){
        
        helpFormatter.printHelp( "VomsProxyInit", options );
        System.exit(exitStatus);
        
    }
        
    protected void getArguments(String[] args){
        
        try {
            
            CommandLine line = parser.parse( options, args );

            if (line.hasOption( "h" ))
                
                printHelpMessageAndExit( 0 );
                
            if (line.hasOption( "vomsdir" ))
                
                setupVomsdir( line.getOptionValue( "vomsdir" ) );
                
            if (line.hasOption( "cadir" ))
                
                setupCaDir( line.getOptionValue( "cadir" ) );
            
            if (line.hasOption( "vomsesPath" ))
            
                setupVomsesPath( line.getOptionValue( "vomsesPath" ) );
            
            if (line.hasOption( "usercert") && line.hasOption( "userkey" ))
                setupUserCredentials( line.getOptionValue( "usercert" ), line.getOptionValue( "userkey"));

            
            if (line.hasOption( "out" ))
                proxyOutput = line.getOptionValue( "out" );
                
            if (line.hasOption( "order" ))
                ordering = line.getOptionValue( "order" );
            
            if (line.hasOption( "targets" ))
                targets = line.getOptionValue( "targets" );
            
            if (line.hasOption( "lifetime" ))
                lifetime = Integer.parseInt( line.getOptionValue( "lifetime" ) );
            
            if (line.hasOption( "voms" ))
                fqans = line.getOptionValues( "voms");
            
            if (line.hasOption( "password" ))
                keyPassword = line.getOptionValue( "password" );
            
            if (line.hasOption( "proxyType" ))
                proxyType = line.getOptionValue( "proxyType" );

            if (line.hasOption( "policyType" ))
                policyType = line.getOptionValue( "policyType" );

            if (line.hasOption( "delegationType" ))
                delegationType = line.getOptionValue( "delegationType" );
        } catch ( ParseException e ) {
         
            System.err.println(e.getMessage());
            helpFormatter.printHelp( "VomsProxyInit", options );
            System.exit(-1);
        }   

        
    }
    
    protected void buildProxy(){
        
            
        if (keyPassword != null)
            proxyInit = VOMSProxyInit.instance(keyPassword);
        else{
         
            log.warn( "No password given to decrypt the openssl private key..." );
            proxyInit = VOMSProxyInit.instance();
        
        }
        if (proxyOutput != null)
            proxyInit.setProxyOutputFile( proxyOutput );
        
        if (proxyType != null){
            CertificateType type = VOMSProxyBuilder.GT2_PROXY;
            
            if (proxyType.equals( "GT2_PROXY" ))
                type = VOMSProxyBuilder.GT2_PROXY;
            else if (proxyType.equals( "GT3_PROXY" ))
                type = VOMSProxyBuilder.GT3_PROXY;
            else if (proxyType.equals( "GT4_PROXY" ))
                type = VOMSProxyBuilder.GT4_PROXY;
            else
                log.warn( "Unsupported proxy type specified! The default value will be used." );
            
            proxyInit.setProxyType( type );
            
        }

        if (policyType != null)
            proxyInit.setPolicyType( policyType );
        
        if (delegationType != null){
            DelegationType type = VOMSProxyBuilder.DEFAULT_DELEGATION_TYPE;
            
            if (delegationType.equals( "NONE" ))
                type = DelegationType.NONE;
            else if (delegationType.equals( "LIMITED" ))
                type = DelegationType.LIMITED;
            else if (delegationType.equals( "FULL" ))
                type = DelegationType.FULL;
            else
                log.warn( "Unsupported delegation type specified! The default value will be used." );
            
            proxyInit.setDelegationType( type );
        }
        
        log.debug("fqans:"+ToStringBuilder.reflectionToString( fqans ));
        if (fqans == null)
            proxyInit.getVomsProxy();
        else{
            
            Map options = new HashMap();
                                   
            for ( int i = 0; i < fqans.length; i++ ) {
                
                String[] opts = fqans[i].split( ":" );
                
                if (opts.length != 2)
                    throw new VOMSException("Voms FQANs must be specified according to the <voName>:<fqan> syntax (e.g., cms:/cms/Role=lcgadmin).");
            
                String voName = opts[0];
                
                VOMSRequestOptions o;
                
                if (options.containsKey( voName ))
                    o = (VOMSRequestOptions) options.get( voName );
                else{
                    
                    o = new VOMSRequestOptions();
                    o.setVoName( voName );
                    options.put(voName,o);
                    
                }
                
                o.addFQAN( opts[1] );
                
                if (ordering != null)
                    o.setOrdering( ordering );
                
            }
            
            proxyInit.getVomsProxy( options.values());
            
        }
        
        
    }
    
    public VomsProxyInitClient(String[] args) {

        setupCLParser();
        getArguments( args );
        buildProxy();
        
    }
    
    public static void main( String[] args ) {
        new VomsProxyInitClient(args);
        
    }

}

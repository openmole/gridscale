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

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;


/**
 * 
 * This class implements the XML parsing of responses produced by VOMS servers.
 * 
 * @author Andrea Ceccanti
 *
 */
public class VOMSParser {

    private static Logger log = Logger.getLogger( VOMSParser.class );
    
    protected DocumentBuilder docBuilder;
    
    private VOMSParser(){
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringComments( true );
        factory.setNamespaceAware( false );
        factory.setValidating( false );

        try {
            docBuilder = factory.newDocumentBuilder();
        } catch ( ParserConfigurationException e ) {
            
            log.fatal( "Error configuring DOM document builder." );
            if (log.isDebugEnabled()){
                log.debug( e.getMessage(), e );
            }
            
            throw new VOMSException(e.getMessage(),e);
        }
    }
    
    /**
     * 
     * Parses a voms response reading from a given input stream.
     * @param is, the input stream.
     * @return a {@link VOMSResponse} object that represents the parsed response.
     */
    public VOMSResponse parseResponse(InputStream is){
        
        
        try {
        
            VOMSResponse res = new VOMSResponse(docBuilder.parse( is ));
            return res;
            
        } catch ( SAXException e ) {
            
            log.error( "Error parsing voms server response:" +e.getMessage());
            
            if (log.isDebugEnabled())
                log.error(e.getMessage(),e);
            
            throw new VOMSException(e);
            
        } catch ( IOException e ) {
            
            log.error( "I/O error reading voms server response:" +e.getMessage());
            if (log.isDebugEnabled())
                log.error(e.getMessage(),e);
            
            throw new VOMSException(e);
        }
        
    }
    
    /**
     * @return a new VOMSParser instance.
     */
    public static VOMSParser instance(){
        return new VOMSParser();
    }
}

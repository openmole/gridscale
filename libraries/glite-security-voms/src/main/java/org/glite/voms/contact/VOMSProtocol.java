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
import java.io.OutputStream;
import java.io.StringWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;


/**
 * This class manages the client-side communication protocol with the VOMS server.
 * 
 * @author Andrea Ceccanti
 *
 */
public class VOMSProtocol {
    
    private static final Logger log = Logger.getLogger(VOMSProtocol.class);

    private VOMSRequestFactory requestFactory = VOMSRequestFactory.instance();
    private TransformerFactory transformerFactory;
    private VOMSParser parser = VOMSParser.instance();
        
    private VOMSProtocol(){
    
        transformerFactory = TransformerFactory.newInstance();
    }
    
    public static VOMSProtocol instance() {
        return new VOMSProtocol();
    }
    
    protected String xmlDocAsString(Document doc){ 
        
        Transformer transformer;
        
        try {
        
            transformer = transformerFactory.newTransformer();
        
        } catch ( TransformerConfigurationException e ) {
            
            log.error("Error creating XML transformer:"+e.getMessage());
            if (log.isDebugEnabled())
                log.error( e.getMessage(),e );
            
            throw new VOMSException("Error creating XML transformer:", e);
            
        }
        StringWriter writer = new StringWriter();
        
        DOMSource source = new DOMSource( doc );
        StreamResult res = new StreamResult(writer);
        
        try {
            
            transformer.transform( source, res );
        
        } catch ( TransformerException e ) {
            
            log.error("Error caught serializing XML :"+e.getMessage());
            if (log.isDebugEnabled())
                log.error( e.getMessage(),e );
            
            throw new VOMSException("Error caugh serializing XML :", e);
        
        }
        writer.flush();
        
        return writer.toString();
    }
    
    /**
     * 
     * This method is used to send a request to a VOMS server.
     *  
     * @param requestOptions, the request options. See {@link VOMSRequestOptions}. 
     * @param stream, an output stream.
     */
    public void sendRequest(VOMSRequestOptions requestOptions, OutputStream stream){
        
        Document request = requestFactory.buildRequest( requestOptions );
        
        if (log.isDebugEnabled())
            log.debug( "Voms request:\n"+ xmlDocAsString( request ));
        
        Transformer transformer;
        
        try {
            
            transformer = transformerFactory.newTransformer();
        
        } catch ( TransformerConfigurationException e ) {
            
            log.error("Error creating XML transformer:"+e.getMessage());
            if (log.isDebugEnabled())
                log.error( e.getMessage(),e );
            
            throw new VOMSException("Error creating XML transformer:", e);
            
        }
        
        DOMSource source = new DOMSource( request );
        StreamResult res = new StreamResult(stream );
        
        try {
            
            
            transformer.transform( source, res);
            stream.flush();
            
        } catch ( TransformerException e ) {
        
            log.error("XML request serialization error! "+e.getMessage());
            if (log.isDebugEnabled())
                log.error(e.getMessage(),e);
            
            throw new VOMSException("XML request serialization error! "+e.getMessage(),e);
            
        } catch ( IOException e ) {
            
            log.error( e.getMessage() );
            
            if (log.isDebugEnabled())
                log.error(e.getMessage(),e);
            
            throw new VOMSException("XML request serialization error! "+e.getMessage(),e);
        }
    }
    
    /**
     * This method is used to parse a VOMS response from an input stream.
     * 
     * @param stream, the input stream from which the response will be parsed.
     * @return a {@link VOMSResponse} object.
     */
    public VOMSResponse getResponse(InputStream stream){
        
        return parser.parseResponse( stream );
        
    }
    
    
}

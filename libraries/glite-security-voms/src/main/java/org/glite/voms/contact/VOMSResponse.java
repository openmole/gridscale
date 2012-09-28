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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 
 * This class is used to parse and represent VOMS server responses.
 *  
 * @author Andrea Ceccanti
 *
 */
public class VOMSResponse {

    protected Document xmlResponse;

    public boolean hasErrors() {

        return (xmlResponse.getElementsByTagName( "item" ).getLength() != 0);
    }

    /**
     * 
     * Extracts the AC from the VOMS response.
     * @return an array of bytes containing the AC. 
     */
    public byte[] getAC() {

        Element acElement = (Element) xmlResponse.getElementsByTagName( "ac" )
                .item( 0 );
        
        return VOMSDecoder.decode( acElement.getFirstChild().getNodeValue()); 

    }
    
    /**
     * Extracts the AC from the VOMS response.
     * 
     * @return a string containing the AC.
     */
    public String getACAsString(){
        
        Element acElement = (Element) xmlResponse.getElementsByTagName( "ac" )
            .item( 0 );
        
        return acElement.getFirstChild().getNodeValue();
        
    }

    /**
     * 
     * Extracts the error messages from the VOMS response.
     * 
     * @return an array of {@link VOMSErrorMessage} objects.
     */
    public VOMSErrorMessage[] errorMessages() {

        NodeList nodes = xmlResponse.getElementsByTagName( "item" );

        if ( nodes.getLength() == 0 )
            return null;

        VOMSErrorMessage[] result = new VOMSErrorMessage[nodes.getLength()];

        for ( int i = 0; i < nodes.getLength(); i++ ) {

            Element itemElement = (Element) nodes.item( i );

            Element numberElement = (Element) itemElement.getElementsByTagName(
                    "number" ).item( 0 );
            Element messageElement = (Element) itemElement
                    .getElementsByTagName( "message" ).item( 0 );

            result[i] = new VOMSErrorMessage( Integer.parseInt( numberElement
                    .getFirstChild().getNodeValue() ), messageElement
                    .getFirstChild().getNodeValue() );

        }
        
        return result;
    }

    /**
     * Builds a VOMSResponse starting from a DOM an XML document (see {@link Document}).
     * 
     * @param res
     */
    public VOMSResponse(Document res){
        this.xmlResponse = res;
    }
}

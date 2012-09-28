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


/**
 * 
 * This class is used to decode VOMS error messages contained in a VOMS 
 * response.
 * 
 * @author Andrea CEccanti
 *
 */
public class VOMSErrorMessage {
    
    int code;
    String message;
    
    public int getCode() {
    
        return code;
    }
    
    public void setCode( int code ) {
    
        this.code = code;
    }
    
    public String getMessage() {
    
        return message;
    }
    
    public void setMessage( String message ) {
    
        this.message = message;
    }
    
    public VOMSErrorMessage(int code, String message){
        
        this.code = code;
        this.message = message;
    }
    
    public String toString() {
        
        return "voms error "+code+": "+message;        
        
    }
}

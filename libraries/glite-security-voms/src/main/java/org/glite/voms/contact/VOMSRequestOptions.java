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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 
 * This class represents options that constitute VOMS requests.
 * 
 * @author Andrea Ceccanti
 *
 */
public class VOMSRequestOptions {

    
    /**
     * The default lifetime value for voms requests is 86400 seconds.
     * This default value is used if no lifetime is used with the {@link #setLifetime(int)} method.  
     */
    public static final int DEFAULT_LIFETIME=86400;
    
    // List of hostnames where the AC will be valid (comma-separated list in the request) 
    /**
     * This is a list of AC "targets", i.e., a list of hostnames where the AC will be valid.
     * 
     */
    private List targets = new ArrayList();

    // Lifetime in seconds of the AC
    private int lifetime = DEFAULT_LIFETIME;

    // List of FQANs (comma-separated list in the request) to request a specific ordering from the server
    private String ordering;
    
    // List of requested roles
    private List requestedFQANs = new ArrayList();

    // OR mask of verification flags -- unused
    private int verificationType;

    private String voName;
    
    
    /** 
     * @return the lifetime set for this {@link VOMSRequestOptions} object.
     */
    public int getLifetime() {

        return lifetime;
    }

    /**
     * Sets the lifetime for this {@link VOMSRequestOptions} object.
     * @param lifetime
     */
    public void setLifetime( int lifetime ) {

        this.lifetime = lifetime;
    }

    
    /**
     * 
     * Returns the ordering string of this {@link VOMSRequestOptions} object.
     * @return
     */
    public String getOrdering() {

        return ordering;
    }

    
    /**
     * Sets the ordering string of this {@link VOMSRequestOptions} object.
     * The ordering string is used to request a spefic order for the ACs requested 
     * from the VOMS server.
     * 
     * @param ordering
     */
    public void setOrdering( String ordering ) {

        this.ordering = ordering;
    }

    
    /**
     * 
     * Returs the list of the requested FQANs specified in this {@link VOMSRequestOptions} object.
     * 
     * @return
     */
    public List getRequestedFQANs() {

        return requestedFQANs;
    }

    
    /**
     * 
     * Sets the list of requested FQANs for this {@link VOMSRequestOptions} object.
     * 
     * @param requestedFQANs
     */
    public void setRequestedFQANs( List requestedFQANs ) {

        this.requestedFQANs = requestedFQANs;
    }

    
    /**
     * Returns the list of targets (i.e., host where the requested ACs will be valid) for this
     * {@link VOMSRequestOptions} object.
     * 
     * @return
     */
    public List getTargets() {

        return targets;
    }

    
    /**
     * Returns the list of targets (i.e., host where the requested ACs will be valid) for this
     * {@link VOMSRequestOptions} object as a string containing a a comma-separated list of host names.
     * 
     * @return
     */
    public String getTargetsAsString() {

        return asCommaSeparatedString( targets );
    }

    /**
     * 
     * Sets the list of targets (i.e., host where the requested ACs will be valid) for this
     * {@link VOMSRequestOptions} object.
     * @param targets
     */
    public void setTargets( List targets ) {

        this.targets = targets;
    }

    public int getVerificationType() {

        return verificationType;
    }

    public void setVerificationType( int verificationType ) {

        this.verificationType = verificationType;
    }

    
    public String getVoName() {

        return voName;
    }

    public void setVoName( String voName ) {

        this.voName = voName;
    }

    /**
     * 
     * Adds a FQAN to the list of requested FQANs. See {@link #getRequestedFQANs()}.
     * 
     * @param FQAN
     */
    public void addFQAN( String FQAN ) {

        getRequestedFQANs().add( FQAN );

    }

    /**
     * 
     * Adds a target to the list of targets for this {@link VOMSRequestOptions} object. See {@link #getTargets()}.
     * 
     * @param target
     */
    public void addTarget( String target ) {

        getTargets().add( target );
    }

    private String asCommaSeparatedString( List l ) {

        if ( l.isEmpty() )
            return "";

        Iterator i = l.iterator();

        StringBuffer result = new StringBuffer();

        while ( i.hasNext() ) {
            Object o = i.next();
            result.append( o.toString() );
            if ( i.hasNext() )
                result.append( "," );

        }

        return result.toString();

    }
}

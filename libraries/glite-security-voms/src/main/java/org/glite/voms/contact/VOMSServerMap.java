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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;


/**
 * 
 * A {@link VOMSServerMap} organizes voms servers found in vomses configuration files
 * in map keyed by vo. This way is easy to know which servers acts as replicas for the 
 * same vos. For more info about vomses configuration files, see {@link VOMSESFileParser}.
 * 
 * @author Andrea Ceccanti
 *
 */
public class VOMSServerMap {

    protected Map map = new TreeMap();
    
    public void add(VOMSServerInfo info){
        String key = info.getVoName();
        
        if (map.containsKey( key )){
            
            Set servers = (Set) map.get( key );
            servers.add( info );
            return;
        }
        
        Set l = new HashSet();
        l.add( info );
        map.put( key, l);
    }
    
    public Set get(String nick){
        
        return (Set) map.get( nick );
        
    }
        
    public int serverCount(String nick){
        
        if (map.containsKey( nick ))
            return ((Set)map.get( nick )).size();
        
        return 0;
    }
    
    /**
     * Merge this map with another {@link VOMSServerMap} object.
     * @param other
     */
    public void merge(VOMSServerMap other){
        
        Iterator i = other.map.entrySet().iterator();
        
        while (i.hasNext()){
            Map.Entry e = (Entry) i.next();
         
            if (map.containsKey( e.getKey() ))
                get((String)e.getKey()).addAll( (Set )e.getValue());
            else
                map.put( e.getKey(), e.getValue());
        }
    }
    
    public String toString() {
        
        if (map == null || map.isEmpty())
            return "[]";
        
        StringBuffer buf = new StringBuffer();
        
        Iterator i = map.entrySet().iterator();
        buf.append( "VOMSServerMap:[\n");
        
        while (i.hasNext()){
            Map.Entry e = (Entry) i.next();
            
            buf.append(e.getKey()+":\n");
            buf.append( "\tnum_servers: "+((Set)e.getValue()).size()+"\n" );
            buf.append( "\tserver_details: \n\t\t"+ StringUtils.join(((Set)e.getValue()).iterator(),"\n\t\t") +"\n" );
        }
        buf.append("]\n");
        
        return buf.toString(); 
        
    }
}

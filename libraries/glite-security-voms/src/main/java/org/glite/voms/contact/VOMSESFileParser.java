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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

/**
 * 
 * This class implements support for vomses configuration files and directories.
 * 
 * The vomses file search procedure is as follows:
 * 
 * <ul>
 *  <li> if the <code>GLITE_LOCATION</code> system property is set, the <code>$GLITE_LOCATION/etc/vomses</code> path is added
 *  to the search path.
 *  </li>
 *  <li>if the <code>VOMSES_LOCATION</code> system propery is set, its value its interpreted as a colon (:) separated list of paths
 *  that are added to the search path.
 *  </li>
 *  <li> if the <code>${user.home}/.globus/vomses</code> file or directory is set, it is added to the search path.</li>
 *  <li> if the <code>${user.home}/.glite/vomses</code> file or directory is set, it is added to the search path.</li>
 * </ul> 
 * 
 * @author Andrea Ceccanti
 *
 */
public class VOMSESFileParser {

    private static final Logger log = Logger.getLogger( VOMSESFileParser.class );

    private static final String splitSyntax = "\\x22[^\\x22]\\x22";

    private static final List vomsesPaths;

    static {

        String gliteLoc = System.getProperty( "GLITE_LOCATION", null );
        String vomsesLoc = System.getProperty( "VOMSES_LOCATION", null );

        List list = new ArrayList();

        if ( gliteLoc != null ) {
            File gliteLocFile = new File( gliteLoc + File.separator + "etc"
                    + File.separator + "vomses" );

            if ( gliteLocFile.exists() )
                list.add( gliteLocFile );
        }

        if ( vomsesLoc != null ) {

            String[] userLocations = vomsesLoc.split( ":" );

            for ( int i = 0; i < userLocations.length; i++ ) {

                File vomsesLocFile = new File( userLocations[i]
                        + File.separator + "vomses" );

                if ( vomsesLocFile.exists() )
                    list.add( vomsesLocFile );

            }
        }

        File globusVomses = new File( System.getProperty( "user.home" )
                + File.separator + ".globus" + File.separator + "vomses" );

        if ( globusVomses.exists() )
            list.add( globusVomses );

        File gliteVomses = new File( System.getProperty( "user.home" )
                + File.separator + ".glite" + File.separator + "vomses" );

        if ( gliteVomses.exists() )
            list.add( gliteVomses );

        vomsesPaths = list;

    }

    private VOMSESFileParser() {

    }

    private String fixQuotes( String s ) {

        if ( s.startsWith( "\"" ) )
            s = s.substring( 1 );
        if ( s.endsWith( "\"" ) )
            s = s.substring( 0, s.length() - 1 );

        return s;

    }

    private String[] splitLine( String line ) {

        String tokens[] = line.split( splitSyntax );

        for ( int i = 0; i < tokens.length; i++ )
            tokens[i] = fixQuotes( tokens[i] );

        return tokens;
    }

    private VOMSServerMap parseDir( File vomsesDir ) throws IOException {

        File[] allFiles = vomsesDir.listFiles();
        VOMSServerMap result = new VOMSServerMap();

        log.debug( "Parsing vomses dir:" + vomsesDir );

        for ( int i = 0; i < allFiles.length; i++ )
            result.merge( parse( allFiles[i] ) );

        return result;

    }

    VOMSServerMap parse( String fileName ) throws IOException {

        return parse( new File( fileName ) );
    }

    private VOMSServerMap parse( File vomsesFile ) throws IOException {

        BufferedReader reader;

        VOMSServerMap result = new VOMSServerMap();

        if ( vomsesFile.isDirectory() )
            return parseDir( vomsesFile );

        try {

            reader = new BufferedReader( new InputStreamReader(
                    new FileInputStream( vomsesFile ) ) );

        } catch ( FileNotFoundException e ) {

            log.error( "Error opening vomses file '"
                    + vomsesFile.getAbsolutePath() + "': " + e.getMessage() );

            if ( log.isDebugEnabled() )
                log.error( e.getMessage(), e );

            throw e;

        }

        log.debug( "Parsing vomses file: " + vomsesFile.getAbsolutePath() );

        String line;

        while ( ( line = reader.readLine() ) != null ) {

            // Ignore comments
            if ( line.startsWith( "#" ) )
                continue;

            // skip empty lines
            if ( line.matches( "\\s*$" ) )
                continue;

            String[] tokens = splitLine( line );

            if ( tokens.length < 5 || tokens.length > 6 )
                throw new VOMSException( "Syntax error on vomses file!" );

            result.add( VOMSServerInfo.fromStringArray( tokens ) );

        }

        return result;

    }

    /**
     * This method is used to build a {@link VOMSServerMap} object starting from
     * vomses configuration files or directories. 
     * 
     * 
     * @return a {@link VOMSServerMap} object that reflects vomses configuration files.
     * @throws IOException
     *          if a parsing error occurs, or no vomses file is found.
     * 
     */
    public VOMSServerMap buildServerMap() throws IOException {

        Iterator i = vomsesPaths.iterator();

        if ( log.isDebugEnabled() ) {

            String locations = StringUtils.join( vomsesPaths.iterator(), "," );
            log.debug( "Known vomses files: " + locations );

        }

        VOMSServerMap result = new VOMSServerMap();

        while ( i.hasNext() ) {

            result.merge( parse( (File) i.next() ) );
        }

        return result;

    }

    /**
     * @return a new instance of {@link VOMSESFileParser}.
     */
    public static VOMSESFileParser instance() {

        return new VOMSESFileParser();
    }
}

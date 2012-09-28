/*********************************************************************
 *
 * Authors: 
 *      Karoly Lorentey - lorentey@elte.hu
 *      Andrea Ceccanti - andrea.ceccanti@cnaf.infn.it 
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * This class provides utility methods that are used for parsing, matching voms
 * FQANs (Fully Qualified Attribute Names).
 * 
 * @author <a href="mailto:lorentey@elte.hu">Karoly Lorentey</a>
 * @author <a href="mailto:andrea.ceccanti@cnaf.infn.it">Andrea Ceccanti </a>
 * 
 * 
 */
public class PathNamingScheme {

    public static final Logger log = Logger.getLogger( PathNamingScheme.class );

    public static final String containerSyntax = "^(/[\\w.-]+)+|((/[\\w.-]+)+/)?(Role=[\\w.-]+)|(Capability=[\\w\\s.-]+)$";

    public static final String groupSyntax = "^(/[\\w.-]+)+$";

    public static final String roleSyntax = "^Role=[\\w.-]+$";

    public static final String qualifiedRoleSyntax = "^(/[\\w.-]+)+/Role=[\\w.-]+$";

    public static final String capabilitySyntax = "^Capability=[\\w\\s.-]+$";

    public static final Pattern containerPattern = Pattern
            .compile( containerSyntax );

    public static final Pattern groupPattern = Pattern.compile( groupSyntax );

    public static final Pattern rolePattern = Pattern.compile( roleSyntax );

    public static final Pattern qualifiedRolePattern = Pattern
            .compile( qualifiedRoleSyntax );

    public static final Pattern capabilityPattern = Pattern
            .compile( capabilitySyntax );

    
    /**
     * This methods checks that the string passed as argument complies with the voms FQAN syntax.
     *  
     * @param containerName, the string that must be checked for compatibility with FQAN syntax. 
     * @throws VOMSSyntaxException
     *         If there's an error in the FQAN syntax.
     */
    public static void checkSyntax( String containerName ) {

        if ( containerName.length() > 255 )
            throw new VOMSSyntaxException( "containerName.length() > 255" );

        if ( !containerPattern.matcher( containerName ).matches() )
            throw new VOMSSyntaxException( "Syntax error in container name: "
                    + containerName );
    }

    
    /**
     * 
     * This methods checks that the string passed as argument complies with the syntax used
     * by voms to identify groups.
     * 
     * @param groupName, the string that has to be checked.
     * @throws VOMSSyntaxException
     *          If the string passed as argument doens not comply with the voms sytax.
     */
    public static void checkGroup( String groupName ) {

        checkSyntax( groupName );

        if ( !groupPattern.matcher( groupName ).matches() )
            throw new VOMSSyntaxException( "Syntax error in group name: "
                    + groupName );
    }

    /**
     * This methods checks that the string passed as argument complies with the syntax used
     * by voms to identify roles.
     * 
     * 
     * @param roleName
     * @throws VOMSSyntaxException
     *          If the string passed as argument doens not comply with the voms sytax.
     */
    public static void checkRole( String roleName ) {

        if ( roleName.length() > 255 )
            throw new VOMSSyntaxException( "roleName.length()>255" );

        if ( !rolePattern.matcher( roleName ).matches() )
            throw new VOMSSyntaxException( "Syntax error in role name: "
                    + roleName );
    }

    /**
     * This methods checks that the FQAN passed as argument identifies a voms group.
     * 
     * @param groupName, the string to check.
     * @return  <ul><li>true, if the string passed as argument identifies a voms group.
     *          <li>false, otherwise.
     *          </ul>
     */
    public static boolean isGroup( String groupName ) {

        checkSyntax( groupName );

        return groupPattern.matcher( groupName ).matches();
    }

    /**
     * This methods checks that the FQAN passed as argument identifies a voms role.
     * 
     * @param roleName, the string to check.
     * @return <ul><li>true, if the string passed as argument identifies a voms role.
     *          <li>false, otherwise.
     *          </ul>
     */
    public static boolean isRole( String roleName ) {

        checkSyntax( roleName );
        return rolePattern.matcher( roleName ).matches();
    }

    
    /**
     * This methods checks that the FQAN passed as argument identifies a qualified voms role, i.e.,
     * a role defined in the context of a voms group.
     * 
     * @param roleName, the string to check.
     * @return <ul><li>true, if the string passed as argument identifies a qualified voms role.
     *          <li>false, otherwise.
     *          </ul>
     */
    public static boolean isQualifiedRole( String roleName ) {

        checkSyntax( roleName );
        return qualifiedRolePattern.matcher( roleName ).matches();
    }

    
    
    /**
     * This method extracts the role name information from the FQAN passed as argument.
     * 
     * @param containerName, the FQAN
     * @return <ul><li>A string containing the role name, if found</li>
     *              <li>null, if no role information is contained in the FQAN passed as argument
     *         </ul>
     */
    public static String getRoleName( String containerName ) {

        if ( !isRole( containerName ) && !isQualifiedRole( containerName ) )
            throw new VOMSSyntaxException( "No role specified in \""
                    + containerName + "\" voms syntax." );

        Matcher m = containerPattern.matcher( containerName );

        if ( m.matches() ) {

            String roleGroup = m.group( 4 );
            return roleGroup.substring( roleGroup.indexOf( "=" ) + 1, roleGroup
                    .length() );

        }

        return null;
    }

    /**
     * This method extracts group name information from the FQAN passed as argument.
     * 
     * @param containerName, the FQAN 
     * @return <ul><li>A string containing the group name, if found</li>
     *              <li>null, if no group information is contained in the FQAN passed as argument
     *         </ul>
     */
    public static String getGroupName( String containerName ) {

        checkSyntax( containerName );

        // If it's a container and it's not a role or a qualified role, then
        // it's a group!

        if ( !isRole( containerName ) && !isQualifiedRole( containerName ) )
            return containerName;

        Matcher m = containerPattern.matcher( containerName );

        if ( m.matches() ) {
            String groupName = m.group( 2 );

            if ( groupName.endsWith( "/" ) )
                return groupName.substring( 0, groupName.length() - 1 );
            else
                return groupName;
        }

        return null;
    }

    public static String toOldQualifiedRoleSyntax( String qualifiedRole ) {

        checkSyntax( qualifiedRole );

        if ( !isQualifiedRole( qualifiedRole ) )
            throw new VOMSSyntaxException(
                    "String passed as argument is not a qualified role!" );

        return getGroupName( qualifiedRole ) + ":"
                + getRoleName( qualifiedRole );

    }
}

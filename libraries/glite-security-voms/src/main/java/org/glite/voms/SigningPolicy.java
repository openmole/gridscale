/*********************************************************************
 *
 * Authors: Vincenzo Ciaschini - Vincenzo.Ciaschini@cnaf.infn.it
 *
 * Copyright (c) 2006 INFN-CNAF on behalf of the 
 * EGEE project.
 * For license conditions see LICENSE
 *
 * Parts of this code may be based upon or even include verbatim pieces,
 * originally written by other people, in which case the original header
 * follows.
 *
 *********************************************************************/

package org.glite.voms;

//import org.apache.log4j.Logger;

//import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The purpose of this class is to represent a *.signing_policy file.
 *
 * @author Vincenzo Ciaschini
 */
public class SigningPolicy {
    private static final int ACCESS_ID_CA  = 1;
    private static final int POS_RIGHTS    = 2;
    private static final int COND_SUBJECTS = 3;
    private static final Pattern access_id_ca_pattern  = Pattern.compile("access_id_CA\\s+x509\\s+(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern pos_rights_pattern    = Pattern.compile("pos_rights\\s+globus\\s+(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern cond_subjects_pattern = Pattern.compile("cond_subjects\\s+globus\\s+(['\"])(.*?)\\1\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern remove_single_quotes  = Pattern.compile("'(.*)'");
    private static final Pattern remove_double_quotes  = Pattern.compile("\"(.*)\"");
    private static final Pattern get_subject_pattern   = Pattern.compile("(['\"]?)(.*?)\\1\\s*?");
    private String access_id_ca = null;
    private String pos_rights   = null;
    private Vector subjects = null;
    private String gname = null;

    private Vector access_id_ca_list = new Vector();
    private Vector pos_rights_list   = new Vector();
    private Vector subjects_list     = new Vector();

    private int current = -1;

    private int mode = ACCESS_ID_CA;

    /**
     * Loads a *.signing_policy file.
     *
     * @param f the File from which to load the Signing Policy.
     *
     * @throws IOException if there have been problems loading the file.
     */
    public SigningPolicy(File f) throws IOException {
        parse(f);
    }

    /**
     * Gets the basename of the file from which this was loaded.
     *
     * @return the basename or null if nothign was loaded.
     */
    public String getName() {
        return gname;
    }

    /**
     * Finds the record in the signing policy which deals with the specified
     * issuer.
     *
     * @param issuer an OpenSSL-style representation of the issuer.
     *
     * @return the record number, or -1 if none is found.
     */
    public int findIssuer(String issuer) {
        return findIssuer(issuer, -1);
    }

    /**
     * Finds the record in the signing policy which deals with the specified
     * issuer, starting from a specified record.
     *
     * @param issuer an OpenSSL-style representation of the issuer.
     * @param previous the previous match, or -1 if ther was no previous match.
     *
     * @return the record number, or -1 if none is found.
     */
    public int findIssuer(String issuer, int previous) {
        //        System.out.println("previous = " + previous);
        if (previous < -1)
            return -1;

        //        System.out.println("findIssuer in");
        //        Iterator i = access_id_ca_list.iterator();

        //        System.out.println("Contentsize = " + access_id_ca_list.size());

        //        while (i.hasNext()) {
        //            System.out.println("Has: " + (String)i.next());
        //        }

        return access_id_ca_list.indexOf(issuer, previous+1);
    }

    /**
     * Sets the indicate record as the current record.
     *
     * @param index the record number
     *
     * @throws IllegalArgumentException if the record number is too great
     * or < 0.
     */
    public void setCurrent(int index) {
        if (index > access_id_ca_list.size() || index < 0)
            throw new IllegalArgumentException("Index out of bounds for SigningPolicy " + gname);
        current = index;
    }

    /**
     * Gets the AccessIDCA from the current record.
     *
     * @return the AccessIDCA.
     * @throws IllegalArgumentException if the record number has not been set.
     */
    public String getAccessIDCA() {
        if (current != -1)
            return (String)access_id_ca_list.elementAt(current);
        else
            throw new IllegalArgumentException("Current record must be set in Signing Policy object " + gname);
    }

    /**
     * Gets the PosRights from the current record.
     *
     * @return the PosRight
     * @throws IllegalArgumentException if the record number has not been set.
     */
    public String getPosRights() {
        if (current != -1)
            return (String)pos_rights_list.elementAt(current);
        else
            throw new IllegalArgumentException("Current record must be set in Signing Policy object " + gname);

    }

    /**
     * Gets the CondSubjects from the current record.
     *
     * @return a Vector of CondSubjects.  Each element is a String.
     * @throws IllegalArgumentException if the record number has not been set.
     */
    public Vector getCondSubjects() {
        if (current != -1)
            return (Vector)subjects_list.elementAt(current);
        else
            throw new IllegalArgumentException("Current record must be set in Signing Policy object " + gname);

    }

    private String parseAccessIDCA(String line) {
        String access_id_ca = null;
        //                        System.out.println("Evaluating Access_id_ca");
        Matcher m = access_id_ca_pattern.matcher(line);

        if (m.matches()) {
            //                            System.out.println("found.");
            String match = m.group(1);
            //                            System.out.println("found: " + match);
            Matcher m2 = null;
            switch(match.charAt(0)) {
            case '\'':
                //                                System.out.println("single quotes");
                m2 = remove_single_quotes.matcher(match);
                if (m2.matches()) {
                    access_id_ca = m2.group(1);
                }
                break;
            case '"':
                //                                System.out.println("double quotes");
                m2 = remove_double_quotes.matcher(match);
                if (m2.matches()) {
                    access_id_ca = m2.group(1);
                }
                break;
            default:
                access_id_ca = match;
            }
        }
        return access_id_ca;
    }

    private String parsePosRights(String line) {
        String pos_rights = null;
        Matcher m = pos_rights_pattern.matcher(line);

        //                        System.out.println("Evaluating pos_rights");
        if (m.matches())
            pos_rights = m.group(1);

        return pos_rights;
    }

    private Vector parseCondSubjects(String line) {
        Matcher subjects = cond_subjects_pattern.matcher(line);

        Vector subjectList = new Vector();

        while (subjects.find()){
            String substring = subjects.group(2);
            //            System.out.println("Subfind: " + substring);
            Matcher subject_it = get_subject_pattern.matcher(substring);

            while(subject_it.find()){
                String subject = subject_it.group(2);
                //                System.out.println("Candidate: " + subject);
                if (subject.length() != 0) {
                    //                    System.out.println("subsubfind: " + subject);
                    //                    System.out.println("subsubfindlen: " + subject.length());
                    subjectList.add(subject);
                }
            }
            if (substring.length() != 0 && subjectList.size() == 0)
                subjectList.add(substring);
        }
        return subjectList;
    }

    private SigningPolicy parse(File theFile) throws IOException {
        BufferedReader theBuffer = new BufferedReader(new FileReader(theFile));

        String s = null;
        boolean firstrun = true;

        gname = PKIUtils.getBaseName(theFile);

        access_id_ca = null;
        pos_rights   = null;
        subjects     = null;

        boolean error = false;

        s = theBuffer.readLine();

        while (s != null) {
            //            System.out.println("FOUND STRING: " + s);
            s = s.trim();
            if (!(s.length() == 0 || s.startsWith("#"))) {
                switch(mode) {
                case ACCESS_ID_CA:
                    {
                        if (!firstrun) {
                            if (access_id_ca != null &&
                                pos_rights != null &&
                                subjects != null) {
                                //                                System.out.println("Inserting " + access_id_ca);
                                access_id_ca_list.add(access_id_ca);
                                pos_rights_list.add(pos_rights);
                                subjects_list.add(subjects);
                            }
                            access_id_ca = null;
                            pos_rights = null;
                            subjects = null;
                        }
                        access_id_ca = parseAccessIDCA(s);

                        if (access_id_ca == null)
                            error = true;
                        mode = POS_RIGHTS;
                    }
                    break;

                case POS_RIGHTS:
                    {
                        pos_rights = parsePosRights(s);

                        if (pos_rights == null)
                            error = true;
                        mode = COND_SUBJECTS;
                    }
                    break;

                case COND_SUBJECTS:
                    {
                        subjects = parseCondSubjects(s);
                        //                        System.out.println("SIZE = " + subjects.size());
                        if (subjects.size() == 0)
                            error = true;

                        mode = ACCESS_ID_CA;
                        firstrun = false;
                    }
                    break;
                }

                if (error)
                    break;
            }
            s = theBuffer.readLine();
        }

        if (access_id_ca != null &&
            pos_rights != null &&
            subjects != null && !error) {
            //            System.out.println("Inserting " + access_id_ca);
            access_id_ca_list.add(access_id_ca);
            pos_rights_list.add(pos_rights);
            subjects_list.add(subjects);
        }

        theBuffer.close();
        
        if (error) {    
            throw new IOException("Error in reading format of file: " + theFile.getName());
        }
        return this;
    }
}

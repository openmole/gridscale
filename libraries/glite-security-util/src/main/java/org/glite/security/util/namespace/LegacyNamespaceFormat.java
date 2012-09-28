/*
 * Copyright (c) Members of the EGEE Collaboration. 2004. See
 * http://www.eu-egee.org/partners/ for details on the copyright holders.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.glite.security.util.namespace;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.glite.security.util.DNHandler;

/**
 * Legacy policy language format.
 * 
 * # Globus CA rights 
 * 
 * access_id_CA X509 '/C=US/O=Globus/CN=Globus Certification Authority'
 * 
 * pos_rights globus CA:sign
 * 
 * cond_subjects globus '"/C=us/O=Globus/*"  "/C=US/O=Globus/*"'
 * 
 * @author alyu
 */
public class LegacyNamespaceFormat extends NamespaceFormat {
    private static final Logger LOGGER = Logger.getLogger(LegacyNamespaceFormat.class);

    /** Issuer/CA (access identity type) */
    private static final String ACCESS_ID_CA = "access_id_ca";

    /** Subject DN positive rights */
    private static final String POS_RIGHTS = "pos_rights";

    /** Subject (access identity type) */
    private static final String COND_SUBJECTS = "cond_subjects";

    /**
     * Temp place holder used during parsing.
     */
    private String m_issuer = null;

    /**
     * Temp place holder used during parsing.
     */
    private String m_accessRights = null;
    
    /** Temp placeholder for subjects during parsing.  */
    private Subject m_subjects[] = null;

    /**
     * The constructor to create an empty namespace.
     */
    public LegacyNamespaceFormat() {
    	//empty on purpose
    }

    /**
     * Parses a namespaces file.
     * 
     * @param filename filename of the namespaces file
     * @throws IOException if unsuccessful
     * @throws ParseException if the namespaces file format is incorrect
     */
    public void parse(String filename) throws IOException, ParseException {
        File file = new File(filename);
        BufferedReader reader = null;
        m_issuer = null;
        m_accessRights = null;
        m_subjects = null;
        try {
            reader = new BufferedReader(new FileReader(file));

            String line = null;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim(); // remove any white spaces
                lineNumber++;
                // ignore comments
                if (!line.startsWith("#") && line.length() > 0) {
                    // Create a new policy pair
                    addPolicy(line, lineNumber, filename);
                }
            } // while (
            if(m_issuer == null){
                throw new ParseException("No issuer found in the signign policy in file: " + filename + ".", 0);
            }
            if(m_accessRights == null){
                throw new ParseException("No rights definition found in the signign policy in file: " + filename + ".", 0);
            }
            if(m_subjects == null){
                throw new ParseException("No subject definition found in the signign policy in file: " + filename + ".", 0);
            }
            
            for (Subject subject : m_subjects) {
                if (subject.m_subject.trim().length() > 0) {
                    NamespacePolicy policy = new NamespacePolicy(DNHandler.getDNRFC2253(m_issuer), m_accessRights, subject.m_subject, subject.m_statement,
                            subject.m_lineNumber, subject.m_filename);
                    getPolices().add(policy);
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (ParseException e) {
            throw e;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Creates a new policy pair.
     *
     * @param inputStatement policy statement to parse.
     * @param lineNumber the line number of the policy statement, for error messages.
     * @param filename The filename of the policy definitions, for error messages.
     * @throws ParseException if policy parsing is unsuccessful.
     */
    private void addPolicy(String inputStatement, int lineNumber, String filename) throws ParseException {
    	String statement = inputStatement.toLowerCase().trim(); // make everything to lower case

        if (statement.contains(ACCESS_ID_CA)) {
            // LOGGER.debug("Line-->" + line);
            statement = statement.trim();
            String[] split = statement.split(ACCESS_ID_CA + "\\s+x509\\s+");
            
            if(split.length < 2){
                LOGGER.debug(ACCESS_ID_CA + "statement \"" + statement + "\" doesnt contain x509 field or value.");
                throw new ParseException(ACCESS_ID_CA + " line is missing x509 field or value field.", lineNumber);
            }

            m_issuer = split[1].replace("'", "").replace("\"", "").trim();

        } else if (statement.contains(POS_RIGHTS)) {
            String[] split = statement.split(POS_RIGHTS + "\\s+globus\\s+");
            if(split.length < 2){
                LOGGER.debug(POS_RIGHTS + "statement \"" + statement + "\" doesnt contain globus field or value.");
                throw new ParseException(POS_RIGHTS + " line is missing globus field or value field.", lineNumber);
            }

            m_accessRights = split[1].replace("'", "").replace("\"", "").trim();
        } else if (statement.contains(COND_SUBJECTS)) {
            String[] split = statement.split(COND_SUBJECTS + "\\s+globus");

            if(split.length < 2){
                LOGGER.debug(COND_SUBJECTS + "statement \"" + statement + "\" doesnt contain globus field or value.");
                throw new ParseException(COND_SUBJECTS + " line is missing globus field or value field.", lineNumber);
            }

            String subjects[] = split[1].split("[']|[\"]");
            Vector<Subject> subjectsVect = new Vector<Subject>();
            for (String subject : subjects) {
                if (subject.trim().length() > 0) {
                    subjectsVect.add(new Subject(subject, statement, lineNumber, filename));
                }
            }
            m_subjects = subjectsVect.toArray(new Subject[]{});
        } else {
            if (!statement.equals("")){
                throw new ParseException("Unrecognized policy line: " + statement, lineNumber);
            }
        }
    }
    
    /**
     * Simple class to hold the policy info per subject.
     *
     */
    private class Subject{
        /** The name of subject. */
        String m_subject;
        /** The statement line, for error messages. */
        String m_statement;
        /** The line number, for error messages. */
        int m_lineNumber;
        /** The filename for error messages. */
        String m_filename;
        
        /**
         * Constructor to generate the object with given values.
         * 
         * @param subject The subject name, or subject wild card.
         * @param statement The policy statement line, for error messages.
         * @param lineNum The line number in the file, for error messages.
         * @param filename The filename for error messages.
         */
        public Subject(String subject, String statement, int lineNum, String filename){
           m_subject = subject;
           m_statement = statement;
           m_lineNumber = lineNum;
           m_filename = filename;
        }
    }
}

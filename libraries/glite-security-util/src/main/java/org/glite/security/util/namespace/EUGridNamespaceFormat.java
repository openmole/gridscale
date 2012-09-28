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
import java.util.List;

import org.apache.log4j.Logger;
import org.glite.security.util.DNHandler;

/**
 * Implementation of namespaces format provided by EUGridPMA and IGTF
 * distributions.
 * 
 * NAMESPACES-VERSION: 1.0
 * 
 * @author alyu
 */
public class EUGridNamespaceFormat extends NamespaceFormat {
    /**
     * The logging facility.
     */
    static final Logger LOGGER = Logger.getLogger(EUGridNamespaceFormat.class);

    /**
     * The tag in the beginning of the file for namespace file.
     */
    private static final String FORMAT_VERSION_1 = "#NAMESPACES-VERSION: 1.0";

    /**
     * The constructor to generate a namespace object.
     */
    public EUGridNamespaceFormat() {
    	// empty
    }

    /**
     * Parses a namespaces file.
     * 
     * @param filename filename of the namespaces file
     * @throws IOException if unsuccessful
     * @throws ParseException if the namespaces file format is incorrect
     */
    public void parse(String filename) throws IOException, ParseException {
        BufferedReader reader = null;
        try {
            File file = new File(filename);
            reader = new BufferedReader(new FileReader(file));

            String line = null;
            StringBuffer buf = new StringBuffer();
            List<NamespacePolicy> policyList = getPolices();
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                // LOGGER.debug("Line-->" + line);
                line = line.trim(); // remove any white spaces
                lineNumber++;
                // check format version
                if ((getVersion() == null) && line.contains(FORMAT_VERSION_1)) {
                    setVersion(FORMAT_VERSION_1);
                }

                // ignore comments
                if (!line.contains("#") && line.length() > 0) {
                    // check for a '\' at the end of the line
                    // then we need to read next line also and concatenate
                    if (line.contains("\\")) {
                        buf.append(line.substring(0, line.length() - 1));
                    } else {
                        buf.append(line);
                        buf.append('\n');

                        // TODO check format version
                        // Create a new policy pair
                        NamespacePolicy policy = createPolicy(buf.toString(), lineNumber, filename);
                        if (policy != null) {
                            policyList.add(policy);
                            buf = new StringBuffer();
                        } else {
                            LOGGER.error("Unable to create policy pair: " + line);
                        }
                    }
                }
            } // while (

            if (getVersion() == null) {
                setVersion(FORMAT_VERSION_1); // set to 1.0 if no version is
                // detected
            }
        } catch (IOException e) {
            throw e;
        } catch (ParseException e) {
            throw e;
        } finally {
            if(reader != null){
                reader.close();
            }
        }
    }

    /**
     * Creates a policy from a line in the namespace file.
     * 
     * @param inputStatement The line in the policy.
     * @param lineNumber The line number of the line, for error messages.
     * @param filename The filename of the namespace definitions, for error message.
     * @return The parsed namespace policy.
     * @throws ParseException thrown in case the policy parsing fails.
     */
    private NamespacePolicy createPolicy(String inputStatement, int lineNumber, String filename) throws ParseException {
    	String statement = inputStatement.toLowerCase(); // make everything to lower case
        // TODO how do we interpret 'SELF' in TO Issuer?
        // TODO is this reliable...4 splits always?
        String[] split = statement.split("to issuer|deny|permit|subject");

        // first one should be 'TO Issuer'
        // second 'issuer'
        // third access PERMIT|DENY
        // fourth subject
        NamespacePolicy policy = null;
        if (split.length == 4) {
            String issuer = split[1].replace("\"", "").trim();
            String subject = split[3].replace("\"", "").trim();
            policy = new NamespacePolicy(DNHandler.getDNRFC2253(issuer), null, subject, statement, lineNumber, filename);

            String access = null;
            if (statement.contains("deny")) {
                access = "deny";
                policy.subjectDNPermitted(false);
            } else if (statement.contains("permit")) {
                access = "permit";
                policy.subjectDNPermitted(true);
            } else {
                throw new ParseException("No deny or allow string found in the input statement: \"" + inputStatement + "\" in file: " + filename, 0);
            }
            policy.setAccessRights(access);

        } else {
            LOGGER.warn("Unable to parse policy statement from file: " + filename + ", statement was: " + statement);
            throw new ParseException("Unable to parse policy statement", lineNumber);
        }

        return policy;
    }
}

/*
Copyright (c) Members of the EGEE Collaboration. 2004. 
See http://www.eu-egee.org/partners/ for details on the copyright
holders.  

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 

    http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
 */

package org.glite.security.util;

import java.io.File;
import org.apache.log4j.Logger;

/**
 * Utility class to split the CA filename. E.g. file name "/etc/grid-security/certificates/12adf342.4" would be split
 * into m_baseFilename "/etc/grid-security/certificates/", m_hash "12adf342" and the m_number 4.
 * 
 * @author Joni Hahkala
 */
public class CAFilenameSplitter {
    /** The filename with path until the last dot that marks the suffix. */
    public String m_baseFilename;
    /** The hash, the filename without path and suffix or last dot. */
    public String m_hash;
    /** The number, following the last dot. */
    public int m_number;

    /** The logging facility. */
    private static final Logger LOGGER = Logger.getLogger(CAFilenameSplitter.class);

    /**
     * Splits the filename and gives three fields. The hash code, which should be the filename without the suffix, the
     * suffix number and the hash code with path and without suffix.
     * 
     * @param caFilename The CA filename to split. E.g. /etc/grid-security/certificates/12adf342.4
     * @return The CAFilenameSplitter instance holding the information.
     * @throws IllegalArgumentException Thrown in case the file name does not end with a number suffix.
     */
    public static CAFilenameSplitter splitCAFilename(String caFilename) throws IllegalArgumentException {
        int lastSlash;
        int lastDot;
        CAFilenameSplitter parts = new CAFilenameSplitter();

        // search for the last slash and after that is the hash of the CA DN until the dot.
        lastSlash = caFilename.lastIndexOf(File.separatorChar);
        lastDot = caFilename.lastIndexOf('.');

        // shouldn't restrict to the hashed names only, but hash is then wrong...
        // TODO: calculate the hash and add the CA, check that update works when doing that.
        if (lastDot < lastSlash + 9) {
            LOGGER.warn("Can't initialize a trustanchor with filename " + caFilename
                    + ", should be of format \"[path][\\/]hash.[0-9]*\", where the hash is 8 hex digits. "
                    + "The CA name is handled, but might not be loaded and used.");
        }

        if (lastSlash != -1) {
            parts.m_hash = caFilename.substring(lastSlash + 1, lastDot);
        } else { // in case the file points to current dir, which shouldn't happen, but handle it anyway.
            parts.m_hash = caFilename.substring(0, lastDot);
        }

        // Get the number of the cert from the CA we're handling now.
        parts.m_number = Integer.decode(caFilename.substring(lastDot + 1)).intValue();

        parts.m_baseFilename = caFilename.substring(0, lastDot);

        return parts;
    }

}

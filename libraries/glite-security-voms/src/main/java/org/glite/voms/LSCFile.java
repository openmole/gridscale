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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

/**
 * The job of this class is to represent a *.lsc file in the vomsdir
 * directory.
 *
 * @author Vincenzo Ciaschini.
 */
public class LSCFile {
    private String name = null;
    private Vector dnGroups = null;

    /**
     * Loads a *.lsc file from a File
     *
     * @param f the file to load from
     *
     * @throws IOException if there are problems loading the file.
     */
    public LSCFile(File f) throws IOException {
        parse(f);
    }

    /**
     * Returns the basename of the file from which this was loaded.
     *
     * @return the filename, or null if nothing was loaded.
     */
    public String getName() {
        return name;
    }

    private LSCFile parse(File theFile) throws IOException {
        BufferedReader theBuffer = null;
        try {
            dnGroups = new Vector();

            name = PKIUtils.getBaseName(theFile);
            
            theBuffer = new BufferedReader(new FileReader(theFile));

            String s = null;

            s = theBuffer.readLine();

            Vector dnList = new Vector();

            while (s != null) {
                //            System.out.println("FOUND STRING: " + s);
                s = s.trim();
                if (!(s.length() == 0 || s.startsWith("#"))) {
                    if (!s.startsWith("-")) {
                        dnList.add(s);
                    }
                    else {
                        dnGroups.add(dnList);
                        dnList = new Vector();
                    }
                }

                s = theBuffer.readLine();
            }

            dnGroups.add(dnList);
        }
        finally {
            if (theBuffer != null)
                theBuffer.close();
        }
        return this;
    }

    /**
     * Returns the allowed subject/issuer DN sequences for this file.
     *
     * @return a vector whose elements are vectors of strings describing
     * the exact sequences.
     */
    public Vector getDNLists() {
        return dnGroups;
    }
}

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

import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;


/** This class lists all the files defined in the constructor.
 * The definitions can be in three forms.
 * 1. absolute file (/tmp/test.txt)
 * 2. absolute path (/tmp)
 * 3. a wildcard file (/tmp/*.txt)
 *
 * In case 1. only the file is returned.
 * In case 2. all files in the directory are returned
 * In case 3. all the files in the directory tmp having
 * the .txt ending are returned.
 *
 * The returning means the return of the getListing method.
 *
 * @author  Joni Hahkala
 * Created on December 10, 2001, 6:50 PM
 */
public class DirectoryList {
    /**
     * The logging facility.
     */
    static Logger logger = Logger.getLogger(DirectoryList.class.getName());
    /**
     * The list of files to handle.
     */
    List<File> files = null;

    /**
     * Creates a new instance of DirectoryList
     * 
     * @param path The file definition, see class description above.
     * @throws IOException Thrown if the path was invalid
     */
    public DirectoryList(String path) throws IOException {
        // splits the absolute? filename from the wildcard
        String[] parts = path.split("\\*");

        // accept only one wildcard, so file is of the form /tmp/*.x or /tmp/a.x
        if ((parts.length < 1) || (parts.length > 2)) {
            return;
        }

        // check whether the first and only part is a file or directory
        if (parts.length == 1) {
            // open the directory or file
            File fileOrDir = new File(parts[0]);

            // Test if the path given was fully specified filename.
            // Also, check against directory as this way unknown files (directory does not have x bit set, 
            // so listing succeeds, but reading file/directory type does not) this way the file loading is attempted
            // and fails with access denied, thus with right error. Checking for file masks this problem.
            if (!fileOrDir.isDirectory()) {
                // set the file as the only member in the vector and finish
                files = new Vector<File>();
                files.add(fileOrDir);

                return;
            }

            // the path defined a directory, so get all files
            File[] fileDirArray;

            // list the files and dirs inside
            fileDirArray = fileOrDir.listFiles();

            if (fileDirArray == null) {
                logger.error("No files found matching " + path);
                throw new IOException("No files found matching " + path);
            }

            // get the array containing all the files and directories
            Iterator<File> filesAndDirs = Arrays.asList(fileDirArray).iterator();

            files = new Vector<File>();

            // add all the files to the files list and finish
            while (filesAndDirs.hasNext()) {
                File nextFile = filesAndDirs.next();

                if (nextFile.isFile()) {
                    files.add(nextFile);
                }
            }

            return;
        }
		// this is a directory+ending combination
		files = new Vector<File>();

		// get all the files matching the definition.
		FileEndingIterator iterator = new FileEndingIterator(parts[0], parts[1]);

		while (iterator.hasNext()) {
		    files.add(iterator.next());
		}

		return;
    }

    /** Used to get the file listing, the list of files matching
     * the definition in constructor.
     * @return Returns the list of files matching the definition
     * given in the constructor.
     */
    public List<File> getListing() {
        return files;
    }
}

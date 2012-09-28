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


/** Lists all the files in the given directory that end with
 * a certain ending.
 *
 * @author  Joni Hahkala
 * Created on December 3, 2001, 9:16 AM
 */
public class FileEndingIterator {
    /**
     * The logging facility.
     */
    static Logger logger = Logger.getLogger(FileEndingIterator.class.getName());

    /** The file ending.
     */
    protected String ending;

    /** A flag to show that there are more files that match.
     */
    protected boolean nextFound = false;

    /** The list of files in the directory.
     */
    protected File[] fileList;

    /** The index of the next match in the fileList.
     */
    protected int index = 0;

    /** Creates new FileIterator and searches the first match.
     * @param path The directory used for the file search.
     * @param ending The file ending to search for.
     */
    public FileEndingIterator(String path, String ending) {
        this.ending = ending;

        try {
            // open the directory
            File directory = (path.length() != 0) ? new File(path) : new File(".").getAbsoluteFile();

            // list the files and dirs inside
            fileList = directory.listFiles();

            // find the first match for the ending
            nextFound = findNext();
        } catch (Exception e) {
            logger.error("no files found from \"" + path + "\" error: " + e.getMessage());

            //            e.printStackTrace();
            return;
        }
    }

    /** Used to get the next matching file.
     * @return Returns the next matching file.
     */
    public File next() {
        if (nextFound == false) {
            return null;
        }

        File current = fileList[index++];

        nextFound = findNext();

        return current;
    }

    /** Used to check that there are more matching files to get
     * using next().
     * @return Returns true if there are more matching files.
     */
    public boolean hasNext() {
        return nextFound;
    }

    /** Finds the next matching file in the list of files.
     * @return Returns true if a matching file was found.
     */
    protected boolean findNext() {
        try {
            // search the next file with proper ending
            while ((index < fileList.length) &&
                    (fileList[index].isDirectory() || !fileList[index].getName().endsWith(ending))) {
                //               System.out.println("FileIterator::next: Skipping file " + fileList[index].getName());
                index++;
            }
        } catch (Exception e) {
            logger.error("Error while reading directory " + e.getMessage());

            //            e.printStackTrace(System.out);
            return false;
        }

        // check if the loop ended because of a match or because running out of choices.
        if (index < fileList.length) {
            return true;
        }

        return false;
    }
}

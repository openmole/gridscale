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
package org.glite.security.util;

import java.io.File;
import java.io.IOException;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * The class that handles the trust directory, currently provides a method to list all the CA certificates.
 * 
 * @author Joni Hahkala
 */
public class TrustDirHandler {
    /**
     * The logging facility.
     */
    static final Logger LOGGER = Logger.getLogger(TrustDirHandler.class.getName());

    /** The directory containing the trustanchors */
    protected String m_dir;

    /** The list of files in the directory */
    protected File[] fileList;

    /** The CA file pattern */
    public final Pattern caPattern = Pattern.compile(".*\\.[0-9]");

    /**
     * Generates a new handler for the trust directory.
     *  
     * @param dir The directory containing the trust anchors, crls and namespace files.
     */
    public TrustDirHandler (String dir){
        this.m_dir = dir; 
    }

    /**
     * Reads the directory as a preparation.
     * 
     * @throws IOException
     */
    public void init() throws IOException{
        // open the directory.
        File directory = new File(m_dir);

        // list the files and dirs inside
        fileList = directory.listFiles();
        
        if(fileList == null){
            throw new IOException("Error while opening directory \"" + m_dir + "\" it doesn't exist or isn't a directory.");
        }
        if(fileList.length == 0){
            throw new IOException("Directory \"" + m_dir + "\" is empty.");
        }

    }

    /**
     * Lists the CA files.
     * 
     * @return the array of files matching the CA file pattern ".*\.[0-9]*"
     */
    public File[] getCAs(){
        Vector<File> cas = new Vector<File>();
        int index = 0;

        while(index < fileList.length){
            File currentFile = fileList[index];
            if(!currentFile.isDirectory()){
                Matcher matcher = caPattern.matcher(currentFile.getName());
                if(matcher.matches()){
                    cas.add(currentFile);
                }
            }
            index++;
        }

        return cas.toArray(new File[0]);

    }

}

/*
 * Copyright 1999-2006 University of Chicago
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.globus.axis.transport.commons;

import org.apache.commons.httpclient.HostConfiguration;

public class ExtendedHostConfiguration extends HostConfiguration {
        
    private String [] paramList;
    
    public ExtendedHostConfiguration(ExtendedHostConfiguration host) {
        super(host);
    }

    public ExtendedHostConfiguration(HostConfiguration host,
                                     String [] paramList) {
        super(host);
        this.paramList = paramList;
    }

    public boolean equals(Object o) {
        if (o instanceof ExtendedHostConfiguration) {
            if (!super.equals(o)) {
                return false;
            }
            // check params if any
            if (this.paramList != null) {
                ExtendedHostConfiguration other = (ExtendedHostConfiguration)o;
                for (int i=0;i<this.paramList.length;i++) {
                    Object o1 = getParameter(this.paramList[i]);
                    Object o2 = other.getParameter(this.paramList[i]);
                    if (o1 == null) {
                        if (o2 == null) {
                            // they are the same - continue
                        } else {
                            return false;
                        }
                    } else {
                        if (o1.equals(o2)) {
                            // they are the same - continue
                        } else {
                            return false;
                        }
                    }
                }
            }
            
            return true;
        } else {
            return false;
        }
    }
    
    public int hashCode() {
        int hash = super.hashCode();
        if (this.paramList == null) {
            return hash;
        } else {
            for (int i=0;i<this.paramList.length;i++) {
                Object value = getParameter(this.paramList[i]);
                if (value != null) {
                    hash += (this.paramList[i].hashCode() ^ value.hashCode());
                }
            }
            return hash;
        }
    }

    public String toString() {
        String hp = super.toString();
        if (this.paramList == null) {
            return hp;
        } else {
            StringBuffer buf = new StringBuffer();
            buf.append(hp).append("\r\n");
            for (int i=0;i<this.paramList.length;i++) {
                Object value = getParameter(this.paramList[i]);
                if (value != null) {
                    buf.append(this.paramList[i]).append('=');
                    buf.append(value).append(' ');
                }
            }
            return buf.toString();
        }
    }
    
    private Object getParameter(String key) {
        return getParams().getParameter(key);
    }
    
    public Object clone() {
        return new ExtendedHostConfiguration(this);
    }    
}

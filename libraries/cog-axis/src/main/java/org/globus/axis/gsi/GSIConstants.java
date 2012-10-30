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
package org.globus.axis.gsi;

public interface GSIConstants extends org.globus.gsi.GSIConstants {

    public static final String
        GSI_CREDENTIALS = "org.globus.gsi.credentials",
        GSI_AUTHORIZATION = "org.globus.gsi.authorization",
        GSI_MODE = "org.globus.gsi.mode",
        GSI_AUTH_USERNAME = "org.globus.gsi.authorized.user.name",
        GSI_USER_DN = "org.globus.gsi.authorized.user.dn",
        GSI_ANONYMOUS = "org.globus.gsi.anonymous",
        GSI_CONTEXT = "org.globus.gsi.context";

    /* this is just a hack for now
     * something more type safe will be
     * much better */
    public static final String
        /* behaves just like a regular ssl socket */
        GSI_MODE_SSL = "ssl",
        /* send no delegation character */
        GSI_MODE_NO_DELEG = "gsi",
        /* performs full delegation */
        GSI_MODE_FULL_DELEG = "gsifull",
        /* performs limited delegation - default */
        GSI_MODE_LIMITED_DELEG = "gsilimited";

}

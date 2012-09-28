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

import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.x509.X509Name;

import java.security.Principal;

/**
 * DNImpl.java
 * 
 * @author Joni Hahkala
 * @deprecated Use DNImplRFC2253 instead for correctly formed DN strings.
 * 
 *         Created on September 8, 2003, 7:21 PM
 */
public class DNImpl extends DNImplRFC2253 {
    /**
     * Creates a new instance of DN.
     * 
     * @param newOids
     *            The array of object identifiers.
     * @param newRdns
     *            The array or relative distinguished names.
     * @param newCount
     *            The number of fields in the DN (both oids and rdns have to
     *            have this number of items).
     */
    public DNImpl(DERObjectIdentifier[] newOids, String[] newRdns, int newCount) {
        super(newOids, newRdns, newCount);
    }

    /**
     * Creates a new DNImpl object.
     * 
     * @param name
     *            Generates a new DNImpl class from the DN in the name.
     */
    public DNImpl(String name) {
        super(name);
        if (name.startsWith("/")) {
            parseX500(name);
        } else {
            parse(name);
        }
    }

    /**
     * Creates a new DNImpl object.
     * 
     * @param principal
     *            The Principal holding the information to generate the DN from.
     */
    public DNImpl(Principal principal) {
        super(principal);
    }

    /**
     * Creates a new DNImpl object.
     * 
     * @param x509Name
     *            The X509Name instance holding the information to generate the
     *            DN from.
     */
    public DNImpl(X509Name x509Name) {
        super(x509Name);
    }
}

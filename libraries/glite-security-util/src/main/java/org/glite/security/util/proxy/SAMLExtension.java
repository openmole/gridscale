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
package org.glite.security.util.proxy;

import java.io.IOException;

import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;

/**
 * A class for handling the SAML extension in the Certificate.
 * 
 * @author joni.hahkala@cern.ch
 */
public class SAMLExtension {
    /** The OID for the SAML assertion. */
    public static final String SAML_OID = "1.3.6.1.4.1.3536.1.1.1.12";
    /** The legacy OID for the SAML assertion. */
    public static final String LEGACY_SAML_OID = "1.3.6.1.4.1.3536.1.1.1.10";

    /** The ASN.1 encoded contents of the extension. */
    private DEROctetString m_string = null;

    /**
     * Generates a new SAMLExtension object form the byte array
     * 
     * @param bytes
     * @throws IOException
     */
    public SAMLExtension(byte[] bytes) throws IOException {
        m_string = (DEROctetString) ASN1Primitive.fromByteArray(bytes);

    }

    /**
     * Used to generate an instance form the SAML assertion in String format.
     * 
     * @param samlString
     */
    public SAMLExtension(String samlString) {
        m_string = new DEROctetString(samlString.getBytes());
    }

    /**
     * Used to get the SAML assertion in String format.
     * 
     * @return The SAML sertion in string format.
     */
    public String getSAML() {
        return m_string.toString();
    }
}

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
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;

/**
 * A class for generating and parsing the proxy tracing extensions. <br>
 * See <a href="http://www.eugridpma.org/documentation/OIDProxyDelegationTracing.pdf"> OIDProxyDelegationTracing
 * documentation. </a> <br>
 * The proxy tracing extension format is below. It is used to trace the proxy delegation chain by putting in each proxy
 * the url of the service accepting the delegation and the url of the client initiating it. Often the delegation is from
 * service to service, in which case it is easy to use the url of the service. If the initiator of the delegation is a
 * user, then the client should put an url containing the client program as the scheme, the host name or IP address and
 * possibly the username as the path. <br>
 * At the moment only the URI is supported.
 * 
 * <pre>
 *  iGTFProxyTracingIssuerName ::= GeneralNames
 *  iGTFProxyTracingSubjectName ::= GeneralNames
 *  
 *  GeneralNames ::= SEQUENCE SIZE (1..MAX) OF GeneralName
 *  
 *  GeneralName ::= CHOICE {
 *           otherName                       [0]     OtherName,
 *           rfc822Name                      [1]     IA5String,
 *           dNSName                         [2]     IA5String,
 *           x400Address                     [3]     ORAddress,
 *           directoryName                   [4]     Name,
 *           ediPartyName                    [5]     EDIPartyName,
 *           uniformResourceIdentifier       [6]     IA5String,
 *           iPAddress                       [7]     OCTET STRING,
 *           registeredID                    [8]     OBJECT IDENTIFIER}
 *  
 *  OtherName ::= SEQUENCE {
 *           type-id    OBJECT IDENTIFIER,
 *           value      [0] EXPLICIT ANY DEFINED BY type-id }
 *  
 *  EDIPartyName ::= SEQUENCE {
 *           nameAssigner            [0]     DirectoryString OPTIONAL,
 *           partyName               [1]     DirectoryString }
 *  
 *  DirectoryString ::= CHOICE {
 *     teletexString           TeletexString (SIZE (1..maxSize),
 *     printableString         PrintableString (SIZE (1..maxSize)),
 *     universalString         UniversalString (SIZE (1..maxSize)),
 *     bmpString               BMPString (SIZE(1..maxSIZE))
 *  }
 * </pre>
 * 
 * @author joni.hahkala@cern.ch
 */
public class ProxyTracingExtension {

    /** The OID to identify issuer proxy tracing extension. */
    public static final String PROXY_TRACING_ISSUER_EXTENSION_OID = "1.2.840.113612.5.5.1.1.1.1";
    /** The OID to identify subject proxy tracing extension. */
    public static final String PROXY_TRACING_SUBJECT_EXTENSION_OID = "1.2.840.113612.5.5.1.1.1.2";

    /** The OID to identify issuer proxy tracing type. */
    public static final int ISSUER_EXTENSION = 1;
    /** The OID to identify issuer proxy tracing type. */
    public static final int SUBJECT_EXTENSION = 2;

    /**
     * The tracing generalNames object that wraps the generalName.
     */
    private GeneralNames m_names = null;
    /**
     * The tracing generalName object.
     */
    private GeneralName m_name = null;

    /**
     * Generates a new proxy tracing item from the URL.
     * 
     * @param url The URL to identify the issuer or the subject.
     */
    public ProxyTracingExtension(String url) {
        m_name = new GeneralName(GeneralName.uniformResourceIdentifier, url);
        m_names = new GeneralNames(m_name);
    }

    /**
     * Parses the information in the byte array (GeneralNames ASN1 sequence of GeneralName) into a proxy tracing
     * extension object.
     * 
     * @param bytes The bytes of ASN1 encoded proxy tracing extension.
     * @throws IOException In case the byte array does not contain a valid ASN1 encoded proxy tracing extension.
     */
    public ProxyTracingExtension(byte[] bytes) throws IOException {
        m_names = GeneralNames.getInstance((ASN1Sequence) ASN1Primitive.fromByteArray(bytes));
        m_name = m_names.getNames()[0];
    }

    /**
     * Returns the URL inside the proxy tracing data structure.
     * 
     * @return The URL in String format.
     */
    public String getURL() {
        if (m_name.getTagNo() != GeneralName.uniformResourceIdentifier) {
            return null;
        }

        // unwrap the DERIA5String wrapping
        DERIA5String ia5String = (DERIA5String) m_name.getName();

        return ia5String.getString();
    }

    /**
     * Returns the general names structure that holds the trace information.
     * 
     * @return The generalNames object that has the trace information. 
     */
    public GeneralNames getNames() {
        return m_names;
    }
}

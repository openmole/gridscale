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

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERObjectIdentifier;

/**
 * A class representing a x509 certificate extension for easily handling them.
 * 
 * @author hahkala
 */
public class CertificateExtensionData {
    /**
     * The oid of the extension.
     */
    public DERObjectIdentifier oid;
    /**
     * The flag for whether the extension is critical. 
     */
    public boolean critical;
    /**
     * The contents of the extension.
     */
    public ASN1Encodable value;

    /**
     * Creates the extension object out of the given arguments.
     * 
     * @param oid The oid of the extension.
     * @param critical The criticality flag of the extension.
     * @param value The contents of the extension.
     */
    public CertificateExtensionData(DERObjectIdentifier oid, boolean critical, ASN1Encodable value) {
        this.oid = oid;
        this.critical = critical;
        this.value = value;
    }
}
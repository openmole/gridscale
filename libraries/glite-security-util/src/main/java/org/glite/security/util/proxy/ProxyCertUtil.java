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

import java.math.BigInteger;
import java.util.Vector;
import java.util.regex.Pattern;

import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.x509.X509Name;
import org.glite.security.util.DN;

/**
 * Utility methods to dig up information out of proxy certificates. 
 * 
 * @author Joni Hahkala
 *
 */
public class ProxyCertUtil {

    /**
     * Check whether the proxy DN is valid compared to the issuer DN, meaning that the DN or the proxy starts with the
     * DN of the parent
     * 
     * @param parent the DN of the parent.
     * @param proxy the DN of the proxy.
     * @throws IllegalArgumentException thrown in case the DN parsing fails.
     */
    public static void checkProxyDN(DN parent, DN proxy) throws IllegalArgumentException {
        // Check that the DN is composed of the parent DN and an additional CN
        // component.
        DN baseDNFromProxySubject = proxy.withoutLastCN(true);
        if (!parent.equals(baseDNFromProxySubject)) {
            throw new IllegalArgumentException("The proxy DN (" + proxy
                    + ") violates a policy that it should be constructed from parent DN (" + parent
                    + ") and an additional CN component");
        }
    }

    /**
     * Returns the serial number CN part of the DN, if present.
     * 
     * @param dn The DN that ends or starts with (to accept also reversed DNs) the "CN=<serial number>" RDN.
     * @return the BigInteger serial number if found.
     */
    public static BigInteger getSN(X509Name dn) {
        Vector oids = dn.getOIDs();
        DERObjectIdentifier oid = (DERObjectIdentifier) oids.elementAt(0);
        String sn = (String) dn.getValues().elementAt(0);
        BigInteger bi = testGetSN(oid, sn);
        if (bi != null) {
            return bi;
        }
        oid = (DERObjectIdentifier) oids.elementAt(oids.size() - 1);
        sn = (String) dn.getValues().elementAt(oids.size() - 1);
        return testGetSN(oid, sn);

    }

    /**
     * Returns the serialnumber represented by the oid and value. The oid has to be the CN oid and value only numbers.
     * 
     * @param oid The oid of the RDN, has to be CN.
     * @param value The value of the RDN.
     * @return The serial number if the oid was CN and the value just a integer number.
     */
    private static BigInteger testGetSN(DERObjectIdentifier oid, String value) {
        if (oid == X509Name.CN) {
            if (Pattern.matches("\\d*", value)) {
                return new BigInteger(value);
            }
        }
        return null;
    }
}

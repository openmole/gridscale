package org.glite.voms.contact;

import org.bouncycastle.asn1.ASN1Primitive;

/**
 *
 */
class ExtensionData {
    String oid;
    ASN1Primitive obj;
    boolean critical;

    public static org.glite.voms.contact.ExtensionData creator(String oid, boolean critical, ASN1Primitive obj) {
        org.glite.voms.contact.ExtensionData ed = new org.glite.voms.contact.ExtensionData();
        ed.obj = obj;
        ed.oid = oid;
        ed.critical = critical;

        return ed;
    }

    public static org.glite.voms.contact.ExtensionData creator(String oid, ASN1Primitive obj) {
        org.glite.voms.contact.ExtensionData ed = new org.glite.voms.contact.ExtensionData();
        ed.obj = obj;
        ed.oid = oid;
        ed.critical = false;

        return ed;
    }

    public String getOID() {
        return oid;
    }

    public ASN1Primitive getObj() {
        return obj;
    }

    public boolean getCritical() {
        return critical;
    }
}

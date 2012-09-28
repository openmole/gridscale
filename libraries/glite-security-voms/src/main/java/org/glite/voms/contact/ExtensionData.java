package org.glite.voms.contact;

import org.bouncycastle.asn1.DERObject;

/**
 *
 */
class ExtensionData {
    String oid;
    DERObject obj;
    boolean critical;

    public static org.glite.voms.contact.ExtensionData creator(String oid, boolean critical, DERObject obj) {
        org.glite.voms.contact.ExtensionData ed = new org.glite.voms.contact.ExtensionData();
        ed.obj = obj;
        ed.oid = oid;
        ed.critical = critical;

        return ed;
    }

    public static org.glite.voms.contact.ExtensionData creator(String oid, DERObject obj) {
        org.glite.voms.contact.ExtensionData ed = new org.glite.voms.contact.ExtensionData();
        ed.obj = obj;
        ed.oid = oid;
        ed.critical = false;

        return ed;
    }

    public String getOID() {
        return oid;
    }

    public DERObject getObj() {
        return obj;
    }

    public boolean getCritical() {
        return critical;
    }
}

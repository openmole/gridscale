/*
 * Copyright (c) Members of the EGEE Collaboration. 2004.
 * See http://eu-egee.org/partners/ for details on the copyright holders.
 * For license conditions see the license file or http://eu-egee.org/license.html
 */

package org.glite.voms;


/**
 * Parses and assembles Fully Qualified Attribute Names
 * (FQANs) used by VOMS.
 *
 * FQANs are defined as<br>
 * <code>&lt;group&gt;[/Role=[&lt;role&gt;][/Capability=&lt;capability&gt;]]</code>
 *
 * @author mulmo
 */
public class FQAN {
    String fqan;
    String group;
    String role;
    String capability;
    boolean split = false;

    public FQAN(String fqan) {
        this.fqan = fqan;
    }

    public FQAN(String group, String role, String capability) {
        this.group = group;
        this.role = role;
        this.capability = capability;
        this.split = true;
    }

    public String getFQAN() {
        if (fqan != null) {
            return fqan;
        }

        fqan = group + "/Role=" + ((role != null) ? role : "") +
            ((capability != null) ? ("/Capability=" + capability) : "");

        return fqan;
    }

    protected void split() {
        if (split) {
            return;
        }

        split = true;

        if (fqan == null) {
            return;
        }

        int len = fqan.length();
        int i = fqan.indexOf("/Role=");

        if (i < 0) {
            group = fqan;

            return;
        }

        group = fqan.substring(0, i);

        int j = fqan.indexOf("/Capability=", i + 6);
        String s = (j < 0) ? fqan.substring(i + 6) : fqan.substring(i + 6, j);
        role = (s.length() == 0) ? null : s;
        s = (j < 0) ? null : fqan.substring(j + 12);
        capability = ((s == null) || (s.length() == 0)) ? null : s;
    }

    public String getGroup() {
        if (!split) {
            split();
        }

        return group;
    }

    public String getRole() {
        if (!split) {
            split();
        }

        return role;
    }

    public String getCapability() {
        if (!split) {
            split();
        }

        return capability;
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (o instanceof FQAN || o instanceof String) {
            return toString().equals(o.toString());
        }

        return false;
    }

    public String toString() {
        return getFQAN();
    }
}

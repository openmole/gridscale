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
package org.glite.security.trustmanager;

import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.util.Calendar;

import org.glite.security.util.CaseInsensitiveProperties;

/**
 * A factory class for the OpensslTrustmanager. Avoids regenerating a trustmanager for each connection, provided that
 * the consecutive calls use the same configuration and same ID.
 * 
 * @author Joni Hahkala
 */
public class OpensslTrustmanagerFactory {

    /**
     * The id of the latest instance.
     */
    private static InstanceID latestID = null;
    /**
     * The latest created manager, reuse if config and id are the same.
     */
    private static OpensslTrustmanager latestManager = null;
    /**
     * The time of latest update.
     */
    private static Calendar lastUpdate = null;
    /**
     * The interval for updates, if the trustmanager is requested more than this time after the latest update, update is
     * forced.
     */
    private static final int UPDATE_INTERVAL_H = 2;

    /**
     * If no trustmanager is created already with the same inputs as calling now, a new trustmanager is created. If
     * there is already one created with same arguments (same id or same null id, same path and same value for
     * crlRequired), the existing one is returned. If the trustmanager was last updated more than 2 hours ago, the
     * checkUpdate() method of it is called.
     * 
     * @param id Optional id to allow using several trustmanagers with same configuration. Can be null.
     * @param path The trust anchor directory. Can't be null.
     * @param crlRequired set to true if CRLs are required (recommended). If set to false, failed CRLs are ignored and
     *            all certificates from the CA with failed CRL are accepted. If set to true and the CA has a bad CRL,
     *            all certificates from that CA are rejected.
     * @return The already generated Trustmanager.
     * @throws IOException If trustanchor file reading fails.
     * @throws CertificateException If CA certificate is malformed.
     * @throws NoSuchProviderException If Bouncycastle provider is not available.
     * @throws ParseException When namespace definition parsing fails.
     * @deprecated use the method with CaseInsensitiveProperties argument instead.
     */
    public static synchronized OpensslTrustmanager getTrustmanager(String id, String path, boolean crlRequired)
            throws IOException, CertificateException, NoSuchProviderException, ParseException {
        InstanceID newID = new InstanceID(id, path, crlRequired);
        if (latestID != null) {
            if (newID.equals(latestID)) {
                // check if there is more than 2 hours from the latest update check. If yes, force update.
                Calendar cutoffTime = Calendar.getInstance();
                cutoffTime.add(Calendar.HOUR_OF_DAY, -UPDATE_INTERVAL_H);
                if (cutoffTime.after(lastUpdate)) {
                    // TODO: maybe should start a thread that updates, but this should already be much better than
                    // generating a new one always.
                    latestManager.checkUpdate();
                }
                return latestManager;
            }
        }
        latestID = newID;
        latestManager = new OpensslTrustmanager(path, crlRequired, null);
        lastUpdate = Calendar.getInstance();
        return latestManager;
    }
    
    /**
     * If no trustmanager is created already with the same inputs as calling now, a new trustmanager is created. If
     * there is already one created with same arguments (same id or same null id, same path and same value for
     * crlRequired), the existing one is returned. If the trustmanager was last updated more than 2 hours ago, the
     * checkUpdate() method of it is called.
     * 
     * @param id Optional id to allow using several trustmanagers with same configuration. Can be null.
     * @param path The trust anchor directory. Can't be null.
     * @param crlRequired set to true if CRLs are required (recommended). If set to false, failed CRLs are ignored and
     *            all certificates from the CA with failed CRL are accepted. If set to true and the CA has a bad CRL,
     *            all certificates from that CA are rejected.
     * @return The already generated Trustmanager.
     * @throws IOException If trustanchor file reading fails.
     * @throws CertificateException If CA certificate is malformed.
     * @throws NoSuchProviderException If Bouncycastle provider is not available.
     * @throws ParseException When namespace definition parsing fails.
     */
    public static synchronized OpensslTrustmanager getTrustmanager(String id, String path, boolean crlRequired, CaseInsensitiveProperties props)
            throws IOException, CertificateException, NoSuchProviderException, ParseException {
            // TODO: check that props match!
        InstanceID newID = new InstanceID(id, path, crlRequired);
        if (latestID != null) {
            if (newID.equals(latestID)) {
                // check if there is more than 2 hours from the latest update check. If yes, force update. Needed for nonupdating trustmanagers that get reused.
                Calendar cutoffTime = Calendar.getInstance();
                cutoffTime.add(Calendar.HOUR_OF_DAY, -UPDATE_INTERVAL_H);
                if (cutoffTime.after(lastUpdate)) {
                    // TODO: maybe should start a thread that updates, but this should already be much better than
                    // generating a new one always.
                    latestManager.checkUpdate();
                }
                return latestManager;
            }
        }
        latestID = newID;
        latestManager = new OpensslTrustmanager(path, crlRequired, props);
        lastUpdate = Calendar.getInstance();
        return latestManager;
    }
}

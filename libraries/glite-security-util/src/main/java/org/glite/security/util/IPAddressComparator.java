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

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.x509.GeneralName;

/**
 * An utility class used to compare ip addresses and to check whether an IP address is within a address space defined by
 * IP address - netmask combination.
 * 
 * @author Joni Hahkala
 */
public class IPAddressComparator {
    /** The logging facility. */
    private static final Logger LOGGER = Logger.getLogger(IPAddressComparator.class);

    /**
     * Parses the string representation of the IP address and returns the address as a byte array. The methods returns
     * bytes of the IP address, 4 bytes for IPv4 address, 16 for the IPv6 address, 5 for IPv4 address with netmask and
     * 17 for the IPv6 address with netmask. example 137.138.125.111/24 would return bytes {137, 138, 125, 111, 24}. So
     * far only the slash-int way of defining the netmask is supported.
     * 
     * @param ip The IP address with optional netmask.
     * @return see above for explanation of the return value.
     */
    public static byte[] parseIP(String ip) {
        // TODO: maybe implement properly without using GeneralName...
        GeneralName name = new GeneralName(7, ip);
        return ASN1OctetString.getInstance(name.getName()).getOctets();
    }

    /**
     * Compares two byte arrays. Can be used to compare two IP addresses or two IP address - netmask combinations.
     * 
     * @param item1 The first array to use for comparison.
     * @param item2 The second array to use for comparison.
     * @return true if the array lengths and the items match.
     */
    public static boolean compare(byte[] item1, byte[] item2) {
        if (item1.length != item2.length) {
            // error as this is development time error.
            LOGGER.error("Illegal array sizes given for compare operation, sizes must match, sizes were: " + item1.length + " and "
                    + item2.length + ".");
            throw new IllegalArgumentException("Illegal array sizes given for compare operation, sizes must match, sizes were"
                    + item1.length + " and " + item2.length + ".");
        }
        for (int i = 0; i < item1.length; i++) {
            if (item1[i] != item2[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tests whether the ipAddress is within the address space defined by the ipAddressWithNetmask.
     * 
     * @param ipAddress The IP address bytes to compare against the address space.
     * @param ipAddressWithNetmask The 8 (IPv4) or 32 (IPv6) byte array containing in the first half the base IP address
     *            bytes and in the second half the netmask bytes.
     * @return true if
     */
    public static boolean isWithinAddressSpace(byte[] ipAddress, byte[] ipAddressWithNetmask) {
        if (!(ipAddressWithNetmask.length == 8 && ipAddress.length == 4)
                && !(ipAddressWithNetmask.length == 32 && ipAddress.length == 16)) {
            // error as this is development time error.
            LOGGER.error("IP address and IP address-netmask length mismatch, should be either (4 and 8) or (16 and 32) lengths were: "
                    + ipAddress.length + " and " + ipAddressWithNetmask.length + ".");
            throw new IllegalArgumentException(
                    "IP address and IP address-netmask length mismatch, should be either (4 and 8) or (16 and 32) lengths were: "
                            + ipAddress.length + " and " + ipAddressWithNetmask.length + ".");
        }

        byte[] comparatorIP = copyBytes(ipAddressWithNetmask, 0, ipAddressWithNetmask.length / 2);
        byte[] netmask = copyBytes(ipAddressWithNetmask, ipAddressWithNetmask.length / 2, ipAddressWithNetmask.length);

        byte[] resultComparator = andBytes(comparatorIP, netmask);
        byte[] resultIP = andBytes(ipAddress, netmask);
        return compare(resultComparator, resultIP);

    }

    /**
     * This method does bitwise and between the two byte arrays. The arrays have to have the same size.
     * 
     * @param ip The first array to use for the and operation.
     * @param netmask The second array to use for the and operation.
     * @return The resulting byte array containing the bytes after the bitwise and operation.
     */
    public static byte[] andBytes(byte[] ip, byte[] netmask) {
        if (ip.length != netmask.length) {
            // error as this is development time error.
            LOGGER.error("Illegal array sizes given for and operation, sizes must match, sizes were: " + ip.length + " and "
                    + netmask.length + ".");
            throw new IllegalArgumentException("Illegal array sizes given for and operation, sizes must match, sizes were: " + ip.length
                    + " and " + netmask.length + ".");
        }
        byte[] result = new byte[ip.length];
        for (int i = 0; i < ip.length; i++) {
            Integer integer = Integer.valueOf((ip[i] & 0xFF) & (netmask[i] & 0xFF));
            result[i] = integer.byteValue();
        }
        return result;
    }

    /**
     * Copies the items from the array to a new array starting from index start and ending at end - 1.
     * 
     * @param array The array holding the bytes to copy.
     * @param start The index where to start copying, inclusive.
     * @param end The index to stop copying, exclusive. The last item copied has index end - 1.
     * @return The newly created array containing the copied bytes.
     */
    public static byte[] copyBytes(byte[] array, int start, int end) {
        if (end < start) {
            // error as this is development time error.
            LOGGER.error("Illegal start or end index for array copy, end must be bigger than start start was: " + start + " and end: "
                    + end + ".");
            throw new IllegalArgumentException("Illegal start or end index for array copy, end must be bigger than start start was: "
                    + start + " and end: " + end + ".");
        }
        if (end > array.length) {
            // error as this is development time error.
            LOGGER.error("Illegal end index for array copy, end must be smaller than the array size end index was: " + end
                    + " and array length: " + array.length + ".");
            throw new ArrayIndexOutOfBoundsException(
                    "Illegal end index for array copy, end must be smaller than or equal to the array size. End index was: " + end
                            + " and array size: " + array.length + ".");
        }

        byte[] newBytes = new byte[end - start];
        for (int i = start; i < end; i++) {
            newBytes[i - start] = array[i];
        }
        return newBytes;
    }

    /**
     * Concatenates two arrays of arrays bytes.
     * 
     * @param first The array of arrays to begin with.
     * @param second The array of arrays to end with.
     * @return the array of arrays that contains the arrays from both argument arrays.
     */
    public static byte[][] concatArrayArrays(byte[][] first, byte[][] second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("Invalid argument, null give even though it is not allowed.");
        }
        byte[][] newByteArrays = new byte[first.length+second.length][];
        for (int i = 0; i < first.length; i++) {
            newByteArrays[i] = first[i];
        }
        for (int i = 0; i < second.length; i++) {
            newByteArrays[i + first.length] = second[i];
        }
        return newByteArrays;
    }
}

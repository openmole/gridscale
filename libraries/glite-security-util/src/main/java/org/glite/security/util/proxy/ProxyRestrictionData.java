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
import java.util.Enumeration;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralSubtree;

/**
 * An utility class for defining the allowed address space, used both to define the source and target restrictions. The
 * format is:
 * 
 * <pre>
 * iGTFProxyRestrictFrom ::= NameConstraints
 * iGTFProxyRestrictTarget ::= NameConstraints
 *  
 * NameConstraints::= SEQUENCE {
 *            permittedSubtrees       [0]     GeneralSubtrees OPTIONAL,
 *            excludedSubtrees        [1]     GeneralSubtrees OPTIONAL }
 * 
 * GeneralSubtrees ::= SEQUENCE SIZE (1..MAX) OF GeneralSubtree
 * 
 * GeneralSubtree ::= SEQUENCE {
 *            base                    GeneralName,
 *            minimum         [0]     BaseDistance DEFAULT 0,
 *            maximum         [1]     BaseDistance OPTIONAL }
 * 
 * BaseDistance ::= INTEGER (0..MAX)
 * 
 * GeneralName ::= CHOICE {
 *         otherName                       [0]     OtherName,
 *         rfc822Name                      [1]     IA5String,
 *         dNSName                         [2]     IA5String,
 *         x400Address                     [3]     ORAddress,
 *         directoryName                   [4]     Name,
 *         ediPartyName                    [5]     EDIPartyName,
 *         uniformResourceIdentifier       [6]     IA5String,
 *         iPAddress                       [7]     OCTET STRING,
 *         registeredID                    [8]     OBJECT IDENTIFIER }
 * 
 * OtherName ::= SEQUENCE {
 *         type-id    OBJECT IDENTIFIER,
 *         value      [0] EXPLICIT ANY DEFINED BY type-id }
 * 
 * EDIPartyName ::= SEQUENCE {
 *         nameAssigner            [0]     DirectoryString OPTIONAL,
 *         partyName               [1]     DirectoryString }
 * </pre>
 * 
 * And in this class only the IPAddress as a IP address - netmask combination is supported.
 * 
 * @author joni.hahkala@cern.ch
 */
public class ProxyRestrictionData {
    /** The logging facility */
    private static final Logger LOGGER = Logger.getLogger(ProxyRestrictionData.class);

    /** The permitted subtrees in the restriction */
    private Vector<GeneralSubtree> m_permittedGeneralSubtrees = new Vector<GeneralSubtree>();
    /** The excluded subtrees in the restriction */
    private Vector<GeneralSubtree> m_excludedGeneralSubtrees = new Vector<GeneralSubtree>();

    /** The OID for the proxy source restriction */
    public static final String SOURCE_RESTRICTION_OID = "1.2.840.113612.5.5.1.1.2.1";
    /** The OID for the proxy target Restriction */
    public static final String TARGET_RESTRICTION_OID = "1.2.840.113612.5.5.1.1.2.2";

    /**
     * Parses the restriction data from byte array.
     * 
     * @param bytes The byte array to parse.
     * @throws IOException In case there is a problem parsing the structure.
     */
    public ProxyRestrictionData(byte[] bytes) throws IOException {
        ASN1Sequence nameSpaceRestrictionsSeq = (ASN1Sequence) ASN1Primitive.fromByteArray(bytes);
        switch (nameSpaceRestrictionsSeq.size()) {
        case 0:
            return;
        case 1:
            DERTaggedObject taggedSequence = (DERTaggedObject) nameSpaceRestrictionsSeq.getObjectAt(0);
            if (taggedSequence.getTagNo() == 0) {
                copyCondSequenceToVector((DERSequence) taggedSequence.getObject(), m_permittedGeneralSubtrees);
            } else {
                if (taggedSequence.getTagNo() == 1) {
                    copyCondSequenceToVector((DERSequence) taggedSequence.getObject(), m_excludedGeneralSubtrees);
                } else {
                    LOGGER.error("Illegal tag number in the proxy restriction NameConstraints data structure: "
                            + taggedSequence.getTagNo() + ", should have been 0 or 1");
                    throw new IllegalArgumentException(
                            "Illegal tag number in the proxy restriction NameConstraints data structure: "
                                    + taggedSequence.getTagNo() + ", should have been 0 or 1");
                }
            }
            break;
        case 2:
            taggedSequence = (DERTaggedObject) nameSpaceRestrictionsSeq.getObjectAt(0);
            if (taggedSequence.getTagNo() == 0) {
                copyCondSequenceToVector((DERSequence) taggedSequence.getObject(), m_permittedGeneralSubtrees);
            } else {
                LOGGER.error("Illegal tag number in the proxy restriction NameConstraints data structure: "
                        + taggedSequence.getTagNo() + ", should have been 0");
                throw new IllegalArgumentException(
                        "Illegal tag number in the proxy restriction NameConstraints data structure: "
                                + taggedSequence.getTagNo() + ", should have been 0");
            }
            taggedSequence = (DERTaggedObject) nameSpaceRestrictionsSeq.getObjectAt(1);
            if (taggedSequence.getTagNo() == 1) {
                copyCondSequenceToVector((DERSequence) taggedSequence.getObject(), m_excludedGeneralSubtrees);
            } else {
                LOGGER.error("Illegal tag number in the proxy restriction NameConstraints data structure: "
                        + taggedSequence.getTagNo() + ", should have been 1");
                throw new IllegalArgumentException(
                        "Illegal tag number in the proxy restriction NameConstraints data structure: "
                                + taggedSequence.getTagNo() + ", should have been 1");
            }
            break;
        default:
            LOGGER.error("Illegal number of items in the proxy restriction NameConstraints data structure: "
                    + nameSpaceRestrictionsSeq.size() + ", should have been 0 to 2");
            throw new IllegalArgumentException(
                    "Illegal number of items in the proxy restriction NameConstraints data structure: "
                            + nameSpaceRestrictionsSeq.size() + ", should have been 0 to 2");
        }
    }

    /**
     * Constructor to generate an empty ProxyRestrictionData object for creating new restrictions. Notice that putting
     * an empty proxy restriction into a certificate means that there are no permitted IP spaces, meaning the proxy
     * should be rejected everywhere.
     */
    public ProxyRestrictionData() {
        // creates empty restriction data object.
    }

    /**
     * This method copies the contents of a generalSubtrees sequence into the given vector. Static to protect the
     * internal data structures from access.
     * 
     * @param subSeq the subsequence to copy.
     * @param vector The target to copy the parsed GeneralSubtree objects.
     */
    private static void copyCondSequenceToVector(DERSequence subSeq, Vector<GeneralSubtree> vector) {
        Enumeration<ASN1Primitive> subTreeEnum = subSeq.getObjects();
        while (subTreeEnum.hasMoreElements()) {
            ASN1Primitive object = subTreeEnum.nextElement();
            vector.add(GeneralSubtree.getInstance((ASN1Sequence) object));
        }
    }

    /**
     * Adds a new permitted IP addressSpace to the data structure.
     * 
     * @param address The address space to add to the allowed ip address space. Example of the format: 192.168.0.0/16.
     *            Which equals a 192.168.0.0 with a net mask 255.255.0.0. A single IP address can be defined as
     *            xxx.xxx.xxx.xxx/32. <br>
     *            See <a href="http://www.ietf.org/rfc/rfc4632.txt"> RFC 4632.</a> The restriction is of the format used
     *            for NameConstraints, meaning GeneralName with 8 octets for ipv4 and 32 octets for ipv6 addresses.
     */
    public void addPermittedIPAddressWithNetmask(String address) {
        m_permittedGeneralSubtrees.add(new GeneralSubtree(new GeneralName(GeneralName.iPAddress, address), null, null));
    }

    /**
     * Adds a new excluded IP addressSpace to the data structure.
     * 
     * @param address The address space to add to the allowed ip address space. Example of the format: 192.168.0.0/16.
     *            Which equals a 192.168.0.0 with a net mask 255.255.0.0. A single IP address can be defined as
     *            xxx.xxx.xxx.xxx/32. <br>
     *            See <a href="http://www.ietf.org/rfc/rfc4632.txt"> RFC 4632.</a> The restriction is of the format used
     *            for NameConstraints, meaning GeneralName with 8 octets for ipv4 and 32 octets for ipv6 addresses.
     */
    public void addExcludedIPAddressWithNetmask(String address) {
        m_excludedGeneralSubtrees.add(new GeneralSubtree(new GeneralName(GeneralName.iPAddress, address), null, null));
    }

    /**
     * Returns the NameConstraints structure of the restrictions.
     * 
     * @return The DERSequence containing the NameConstraints structure.
     */
    public DERSequence getNameConstraints() {
        // The NameConstraints sequence
        ASN1EncodableVector nameConstraintsSequenceVector = new ASN1EncodableVector();

        addTaggedSequenceOfSubtrees(0, m_permittedGeneralSubtrees, nameConstraintsSequenceVector);
        addTaggedSequenceOfSubtrees(1, m_excludedGeneralSubtrees, nameConstraintsSequenceVector);

        return new DERSequence(nameConstraintsSequenceVector);
    }

    /**
     * Adds, with the given tag, a DER sequence object that contains the GeneralSubtree objects into the ASN1Vector.
     * 
     * @param tagNo The tag to tag the object.
     * @param subtrees The Vector of GeneralSubtree objects. Null will throw NullPointerException. An empty Vector will
     *            not be added.
     * @param asn1Vector The vector to add the subtrees sequence with the given tag.
     */
    private static void addTaggedSequenceOfSubtrees(int tagNo, Vector<GeneralSubtree> subtrees,
            ASN1EncodableVector asn1Vector) {
        if (!subtrees.isEmpty()) {
            ASN1EncodableVector subtreesSequenceVector = new ASN1EncodableVector();

            Enumeration<GeneralSubtree> generalSubtreesEnum = subtrees.elements();
            while (generalSubtreesEnum.hasMoreElements()) {
                subtreesSequenceVector.add(generalSubtreesEnum.nextElement());
            }
            asn1Vector.add(new DERTaggedObject(tagNo, new DERSequence(subtreesSequenceVector)));
        }
    }

    /**
     * Returns a Vector of Vectors of IP address spaces as defined in rfc 4632.
     * 
     * @see #addExcludedIPAddressWithNetmask(String)
     * @return The array of arrays of string representation of address spaces defined in this structure. The first
     *         element in the array lists the permitted IP address spaces and the second the excluded IP spaces. In
     *         format ipaddress/netmask bytes. Example {137,138,0,0,255,255,0,0}. Array always contains two items, but
     *         they can be of length 0.
     */
    public byte[][][] getIPSpaces() {
        byte allowedIPSpaces[][] = subtreesIntoArray(m_permittedGeneralSubtrees);
        byte excludedIPSpaces[][] = subtreesIntoArray(m_excludedGeneralSubtrees);

        return new byte[][][] { allowedIPSpaces, excludedIPSpaces };
    }

    /**
     * Generates a string array of IP address spaces from the vector of GeneralSubtrees.
     * 
     * @param subtrees The vector of GeneralSubtrees to parse. Null as input will return null.
     * @return the array of IP address spaces.
     */
    private static byte[][] subtreesIntoArray(Vector<GeneralSubtree> subtrees) {
        if (subtrees == null) {
            return null;
        }

        Vector<byte[]> ips = new Vector<byte[]>();
        Enumeration<GeneralSubtree> enumGeneralNames = subtrees.elements();
        while (enumGeneralNames.hasMoreElements()) {
            GeneralName item = enumGeneralNames.nextElement().getBase();
            if (item.getTagNo() == GeneralName.iPAddress) {
                ASN1OctetString octets = (ASN1OctetString) item.getName();
                byte[] bytes = octets.getOctets();
                ips.add(bytes);
            }
        }
        return ips.toArray(new byte[0][0]);

    }

}

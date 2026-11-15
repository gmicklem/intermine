package org.intermine.web.security;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.security.auth.x500.X500Principal;

/**
 * Issue certificates for keys we trust.
 *
 * The certificates are plain X.509 v3 certificates without extensions. They are assembled as DER
 * with the JDK classes only (this used to need the Bouncy Castle provider just for that).
 *
 * @author Alex Kalderimis
 *
 */
public class KeySigner
{

    /**
     * Errors thrown when signing certificates.
     * @author Alex Kalderimis
     *
     */
    public final class SigningException extends Exception
    {

        private SigningException(Throwable cause) {
            super(cause);
        }

        private SigningException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    /** The default algorithm to use **/
    public static final String DEFAULT_ALGORITHM = "SHA256withRSA";

    /** DER encoded AlgorithmIdentifier of each signature algorithm we can write into a cert. */
    private static final Map<String, byte[]> ALGORITHM_IDS = new HashMap<String, byte[]>();

    static {
        // sha*WithRSAEncryption, 1.2.840.113549.1.1.x, parameters NULL
        addRsa("SHA1withRSA", 5);
        addRsa("SHA256withRSA", 11);
        addRsa("SHA384withRSA", 12);
        addRsa("SHA512withRSA", 13);
        addRsa("SHA224withRSA", 14);
        // ecdsa-with-SHA*, 1.2.840.10045.4.x, no parameters
        ALGORITHM_IDS.put("SHA1withECDSA", der(0x30, der(0x06, oid(1, 2, 840, 10045, 4, 1))));
        ALGORITHM_IDS.put("SHA224withECDSA", der(0x30, der(0x06, oid(1, 2, 840, 10045, 4, 3, 1))));
        ALGORITHM_IDS.put("SHA256withECDSA", der(0x30, der(0x06, oid(1, 2, 840, 10045, 4, 3, 2))));
        ALGORITHM_IDS.put("SHA384withECDSA", der(0x30, der(0x06, oid(1, 2, 840, 10045, 4, 3, 3))));
        ALGORITHM_IDS.put("SHA512withECDSA", der(0x30, der(0x06, oid(1, 2, 840, 10045, 4, 3, 4))));
    }

    private PrivateKey signingKey;
    private String issuer;
    private int days;
    private String algorithm;

    /**
     * Build a new signer.
     * @param signingKey Our key that we use to sign the other keys with.
     * @param dn the X.509 Distinguished Name, eg "CN=Test, L=London, C=GB"
     * @param days The number of days we issue certificates for.
     * @param algorithm The signing algorithm we use.
     */
    public KeySigner(PrivateKey signingKey, String dn, int days, String algorithm) {
        this.signingKey = signingKey;
        this.issuer = dn;
        this.days = days;
        this.algorithm = algorithm;
    }

    /**
     * Create a self-signed X.509 Certificate
     *
     * @param subject Who we trust.
     * @param key The key we are asserting that we trust.
     * @return A certificate wrapping the key, signed by us.
     * @throws SigningException If we cannot generate the certificate for some reason.
     */
    public X509Certificate generateCertificate(String subject, PublicKey key)
        throws SigningException {
        byte[] algorithmId = ALGORITHM_IDS.get(algorithm);
        if (algorithmId == null) {
            throw new SigningException("Unknown algorithm", null);
        }
        try {
            long now = System.currentTimeMillis();
            byte[] tbs = der(0x30, concat(
                    der(0xA0, der(0x02, new byte[] {2})),               // version: v3
                    der(0x02, BigInteger.valueOf(1).toByteArray()),     // serial number
                    algorithmId,
                    new X500Principal(issuer).getEncoded(),
                    der(0x30, concat(
                            utcTime(new Date(now)),                     // not before
                            utcTime(new Date(now + 1000L * 60L * 60L * 24L * days)))),
                    new X500Principal("CN=" + subject).getEncoded(),
                    key.getEncoded()));                                 // SubjectPublicKeyInfo

            Signature signature = Signature.getInstance(algorithm);
            signature.initSign(signingKey);
            signature.update(tbs);
            byte[] signed = signature.sign();

            byte[] bitString = new byte[signed.length + 1];             // no unused bits
            System.arraycopy(signed, 0, bitString, 1, signed.length);
            byte[] encoded = der(0x30, concat(tbs, algorithmId, der(0x03, bitString)));
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(encoded));
        } catch (GeneralSecurityException e) {
            throw new SigningException(e);
        } catch (IllegalArgumentException e) {
            throw new SigningException(e);
        }
    }

    private static void addRsa(String name, int id) {
        ALGORITHM_IDS.put(name, der(0x30,
                concat(der(0x06, oid(1, 2, 840, 113549, 1, 1, id)), new byte[] {0x05, 0x00})));
    }

    /** UTCTime up to the year 2049, GeneralizedTime after that (RFC 5280, 4.1.2.5). */
    private static byte[] utcTime(Date date) {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Calendar calendar = Calendar.getInstance(utc);
        calendar.setTime(date);
        boolean generalized = calendar.get(Calendar.YEAR) >= 2050;
        SimpleDateFormat format = new SimpleDateFormat(
                generalized ? "yyyyMMddHHmmss'Z'" : "yyMMddHHmmss'Z'");
        format.setTimeZone(utc);
        return der(generalized ? 0x18 : 0x17,
                format.format(date).getBytes(Charset.forName("US-ASCII")));
    }

    private static byte[] oid(int... arcs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(arcs[0] * 40 + arcs[1]);
        for (int i = 2; i < arcs.length; i++) {
            int arc = arcs[i];
            int groups = 1;
            while ((arc >> (7 * groups)) != 0) {
                groups++;
            }
            for (int g = groups - 1; g >= 0; g--) {
                out.write(((arc >> (7 * g)) & 0x7F) | (g > 0 ? 0x80 : 0));
            }
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    /** One DER type-length-value triple. */
    private static byte[] der(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int length = content.length;
        if (length < 0x80) {
            out.write(length);
        } else {
            int octets = length > 0xFFFF ? 3 : (length > 0xFF ? 2 : 1);
            out.write(0x80 | octets);
            for (int i = octets - 1; i >= 0; i--) {
                out.write((length >> (8 * i)) & 0xFF);
            }
        }
        out.write(content, 0, length);
        return out.toByteArray();
    }
}

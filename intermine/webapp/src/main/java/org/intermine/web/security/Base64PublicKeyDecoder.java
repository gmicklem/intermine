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

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * A public key decoder that reads public keys encoded as base64 strings.
 * @author Alex Kalderimis
 *
 */
public class Base64PublicKeyDecoder implements KeyDecoder
{

    Base64.Decoder decoder;
    KeyFactory fact;

    /**
     * Construct a new decoder.
     */
    public Base64PublicKeyDecoder() {
        // MIME: ignores line breaks (PEM bodies) and other characters outside the alphabet
        decoder = Base64.getMimeDecoder();
        try {
            fact = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("This JVM cannot create RSA keys.");
        }
    }

    @Override
    public PublicKey decode(String input) throws DecodingException {
        byte[] decoded;
        try {
            decoded = decoder.decode(input);
        } catch (IllegalArgumentException e) {
            throw new DecodingException(e);
        }
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(decoded);

        try {
            return fact.generatePublic(x509KeySpec);
        } catch (InvalidKeySpecException e) {
            throw new DecodingException(e);
        }
    }

}

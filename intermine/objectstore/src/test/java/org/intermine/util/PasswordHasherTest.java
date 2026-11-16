package org.intermine.util;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import junit.framework.TestCase;
import net.iharder.Base64;

public class PasswordHasherTest extends TestCase
{
    public PasswordHasherTest(String arg1) {
        super(arg1);
    }

    public void test1() throws Exception {
        String hashed = PasswordHasher.hashPassword("flibble");
        assertTrue(PasswordHasher.checkPassword("flibble", hashed));
        assertFalse(PasswordHasher.checkPassword("flibble", "hhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhhh"));
        assertTrue(PasswordHasher.checkPassword("flibble", "flibble"));
        assertFalse(PasswordHasher.checkPassword("flibble", hashed + " "));
        assertFalse(PasswordHasher.checkPassword("flibble", "flobble"));
    }

    private static String hashWith(String salt, String password, Charset charset) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update((salt + password).getBytes(charset));
        return salt + Base64.encodeBytes(md.digest());
    }

    // 32 bytes of salt, Base64 encoded: 44 characters ending in '='
    private static final String SALT = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    public void testAsciiHashesAreCharsetIndependent() throws Exception {
        String ascii = hashWith(SALT, "flibble", StandardCharsets.UTF_8);
        assertEquals(ascii, hashWith(SALT, "flibble", StandardCharsets.ISO_8859_1));
        assertEquals(ascii, hashWith(SALT, "flibble", StandardCharsets.US_ASCII));
        assertTrue(PasswordHasher.checkPassword("flibble", ascii, StandardCharsets.ISO_8859_1));
    }

    public void testNonAsciiPasswordIsHashedAsUtf8() throws Exception {
        String hashed = PasswordHasher.hashPassword("p\u00e4ssw\u00f6rd\u20ac");
        assertTrue(PasswordHasher.checkPassword("p\u00e4ssw\u00f6rd\u20ac", hashed,
                StandardCharsets.UTF_8));
        assertTrue(PasswordHasher.checkPassword("p\u00e4ssw\u00f6rd\u20ac", hashed,
                StandardCharsets.ISO_8859_1));
        assertFalse(PasswordHasher.checkPassword("password", hashed, StandardCharsets.UTF_8));
    }

    public void testLegacyPlatformCharsetHashStillMatches() throws Exception {
        String legacy = hashWith(SALT, "p\u00e4ssw\u00f6rd", StandardCharsets.ISO_8859_1);
        assertTrue(PasswordHasher.checkPassword("p\u00e4ssw\u00f6rd", legacy,
                StandardCharsets.ISO_8859_1));
        assertFalse(PasswordHasher.checkPassword("p\u00e4ssw\u00f6rd", legacy,
                StandardCharsets.UTF_8));
        assertFalse(PasswordHasher.checkPassword("passw\u00f6rd", legacy,
                StandardCharsets.ISO_8859_1));
    }
}

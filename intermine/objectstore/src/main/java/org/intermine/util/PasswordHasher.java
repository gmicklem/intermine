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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import net.iharder.Base64;

/**
 * Utility methods for managing hashed passwords
 *
 * @author Matthew Wakeling
 */
public final class PasswordHasher
{
    private PasswordHasher() {
        // don't
    }

    /**
     * Converts a password into a hashed password, with a salt.
     *
     * @param password the password to hash
     * @return a 88-character String containing the salt and the hash
     */
    public static String hashPassword(String password) {
        if ((password.length() == 88) && (password.charAt(43) == '=')
                && (password.charAt(87) == '=')) {
            return password;
        }
        try {
            byte[] salt = new byte[32];
            SecureRandom sr = new SecureRandom();
            sr.nextBytes(salt);
            String saltString = Base64.encodeBytes(salt);
            return saltString + digest(saltString, password, StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks to see if a password matches an optionally hashed password entry.
     *
     * @param password the given password
     * @param hash the hashed password entry
     * @return true if the password matches
     */
    public static boolean checkPassword(String password, String hash) {
        return checkPassword(password, hash, Charset.defaultCharset());
    }

    /**
     * The salted SHA-256 of a password, Base64 encoded.
     * @throws NoSuchAlgorithmException if the SHA-256 algorithm is not available
     */
    private static String digest(String saltString, String password, Charset charset)
        throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update((saltString + password).getBytes(charset));
        return Base64.encodeBytes(md.digest());
    }

    /**
     * Checks a password against a hash.  Hashes are made from UTF-8 bytes.  Before 2026 the
     * password was converted to bytes with the platform's default charset, so a stored hash of a
     * password with non-ASCII characters may have been made with a different charset (for ASCII
     * passwords, which are nearly all of them, every charset gives the same bytes and the two
     * agree).  To keep such users able to log in, if the UTF-8 hash does not match and the
     * legacy charset differs, the hash is tried with the legacy charset too.
     *
     * @param password the given password
     * @param hash the hashed password entry
     * @param legacyCharset the charset old hashes may have been made with
     * @return true if the password matches
     */
    static boolean checkPassword(String password, String hash, Charset legacyCharset) {
        if (password == null && hash == null) {
            return true;
        }
        try {
            if ((hash.length() == 88) && (hash.charAt(43) == '=') && (hash.charAt(87) == '=')) {
                String saltString = hash.substring(0, 44);
                String expected = hash.substring(44);
                if (digest(saltString, password, StandardCharsets.UTF_8).equals(expected)) {
                    return true;
                }
                if (!StandardCharsets.UTF_8.equals(legacyCharset)
                        && digest(saltString, password, legacyCharset).equals(expected)) {
                    return true;
                }
            } else {
                if (password != null) {
                    return password.equals(hash);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return false;
    }
}

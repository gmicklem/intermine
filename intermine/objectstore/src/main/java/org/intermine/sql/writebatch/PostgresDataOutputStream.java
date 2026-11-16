package org.intermine.sql.writebatch;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.intermine.model.StringConstructor;
import org.intermine.util.SensibleByteArrayOutputStream;

/**
 * A subclass of DataOutputStream that has extra methods useful for reducing the memory
 * footprint of the Postgres database write operations.
 *
 * @author Matthew Wakeling
 */
public class PostgresDataOutputStream extends DataOutputStream
{
    /**
     * @see DataOutputStream
     *
     * @param out the OutputStream to write to
     */
    public PostgresDataOutputStream(OutputStream out) {
        super(out);
    }

    /**
     * Writes the given String to the stream in UTF-8 format, following its length in bytes
     * as a four-byte integer.
     *
     * @param str the String
     * @return the number of bytes written to the OutputStream
     * @throws IOException if there is an error writing to the underlying OutputStream
     */
    public int writeLargeUTF(String str) throws IOException {
        return writeLargeUTF(Collections.singletonList(str));
    }

    /**
     * Writes the given StringConstructor to the stream in UTF-8 format, following its
     * length in bytes as a four-byte integer.
     *
     * @param str the StringConstructor
     * @return the number of bytes written to the OutputStream
     * @throws IOException if there is an error writing to the underlying OutputStream
     */
    public int writeLargeUTF(StringConstructor str) throws IOException {
        return writeLargeUTF(str.getStrings());
    }

    /**
     * Writes the given Collection of Strings to the stream in UTF-8 format, following its
     * length in bytes as a four-byte integer.
     *
     * @param strs the Collection of Strings
     * @return the number of bytes written to the OutputStream
     * @throws IOException if there is an error writing to the underlying OutputStream
     */
    protected int writeLargeUTF(Collection<String> strs) throws IOException {
        // The bytes are encoded once, as UTF-8 and never in the platform's default charset (with
        // LANG=C or POSIX that is ASCII, which wrote a ? for every non-ASCII character, so that
        // the length written first no longer matched the bytes and the COPY failed), and the
        // length is the length of those bytes.
        List<byte[]> encoded = new ArrayList<byte[]>(strs.size());
        int utflen = 0;
        for (String str : strs) {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            encoded.add(bytes);
            utflen += bytes.length;
        }

        if (out instanceof SensibleByteArrayOutputStream) {
            ((SensibleByteArrayOutputStream) out).assureSize(((SensibleByteArrayOutputStream) out)
                .size() + 4 + utflen);
        }

        writeInt(utflen);

        for (byte[] bytes : encoded) {
            write(bytes);
        }
        return utflen + 4;
    }
}

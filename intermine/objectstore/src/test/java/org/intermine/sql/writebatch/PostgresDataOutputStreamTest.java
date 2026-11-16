package org.intermine.sql.writebatch;

import java.io.IOException;
import junit.framework.TestCase;

public class PostgresDataOutputStreamTest extends TestCase {

    public void testWriteUTFWithCaractersWithOneByte() {
        String input = "ab";
        PostgresByteArrayOutputStream baos = new PostgresByteArrayOutputStream();
        PostgresDataOutputStream dos = new PostgresDataOutputStream(baos);
        try {
            dos.writeLargeUTF(input);
            dos.flush();
            dos.close();
            byte[] output = baos.getBuffer();
            assertEquals((byte) 2, output[3]);
            assertEquals((byte) 97, output[4]);
            assertEquals((byte) 98, output[5]);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void testWriteUTFWithCaractersWithThreeBytes() {
        ////Lao Letter Ko U+0E81 (https://unicode-table.com)
        String input = "abກ";
        PostgresByteArrayOutputStream baos = new PostgresByteArrayOutputStream();
        PostgresDataOutputStream dos = new PostgresDataOutputStream(baos);
        try {
            dos.writeLargeUTF(input);
            dos.flush();
            dos.close();
            byte[] output = baos.getBuffer();
            assertEquals((byte) 5, output[3]);
            assertEquals((byte) 97, output[4]);
            assertEquals((byte) 98, output[5]);
            assertEquals((byte) 224, output[6]);
            assertEquals((byte) 186, output[7]);
            assertEquals((byte) 129, output[8]);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void testWriteUTFWithSupplementayCaracters() {
        String input = "ab𝜅";
        PostgresByteArrayOutputStream baos = new PostgresByteArrayOutputStream();
        PostgresDataOutputStream dos = new PostgresDataOutputStream(baos);
        try {
            dos.writeLargeUTF(input);
            dos.flush();
            dos.close();
            byte[] output = baos.getBuffer();
            assertEquals((byte) 6, output[3]);
            assertEquals((byte) 97, output[4]);
            assertEquals((byte) 98, output[5]);
            assertEquals((byte) 240, output[6]);
            assertEquals((byte) 157, output[7]);
            assertEquals((byte) 156, output[8]);
            assertEquals((byte) 133, output[9]);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    /**
     * The bytes are UTF-8 whatever the platform's default charset is (im-jqz.62: the testmodel
     * data has an en dash, which became a ? under LANG=C and broke the length that precedes it).
     */
    public void testWriteUTFIsUTF8WhateverTheDefaultCharset() throws IOException {
        String input = "Robert\u2013 caf\u00e9 \ud835\udf05";
        byte[] expected = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        PostgresByteArrayOutputStream baos = new PostgresByteArrayOutputStream();
        PostgresDataOutputStream dos = new PostgresDataOutputStream(baos);
        int written = dos.writeLargeUTF(input);
        dos.flush();
        dos.close();
        byte[] output = baos.toByteArray();
        assertEquals(expected.length + 4, written);
        assertEquals(expected.length + 4, output.length);
        assertEquals(expected.length, java.nio.ByteBuffer.wrap(output, 0, 4).getInt());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("byte " + i, expected[i], output[i + 4]);
        }
    }
}

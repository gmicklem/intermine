package org.intermine.objectstore.intermine;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

/**
 * Tests for DdlGenerator. The snapshot tests compare the generated DDL with the SQL that the
 * Torque 3.2 chain of InterMine 5.1 (TorqueModelOutput, then torque-gen's PostgreSQL templates)
 * made for the same objectstores, checked in under torque-5.1-ddl/: any change to the tables of
 * a mine's database shows up here.
 */
public class DdlGeneratorTest
{
    private static List<String> statements(String osName) throws Exception {
        return new DdlGenerator(DdlGenerator.getDatabaseSchema(osName)).getStatements();
    }

    private static String table(List<String> statements, String name) {
        for (String statement : statements) {
            if (statement.startsWith("CREATE TABLE " + name + "\n")) {
                return statement;
            }
        }
        return null;
    }

    @Test
    public void testClassTable() throws Exception {
        String employee = table(statements("os.unittest"), "Employee");
        assertEquals("CREATE TABLE Employee\n"
                + "(\n"
                + "    id INTEGER NOT NULL,\n"
                + "    age INTEGER NOT NULL,\n"
                + "    name TEXT,\n"
                + "    intermine_end TEXT,\n"
                + "    fullTime BOOLEAN NOT NULL,\n"
                + "    employmentPeriodId INTEGER,\n"
                + "    addressId INTEGER,\n"
                + "    departmentId INTEGER,\n"
                + "    departmentThatRejectedMeId INTEGER,\n"
                + "    class TEXT,\n"
                + "    CONSTRAINT Employee_pkey UNIQUE (id)\n"
                + ")", employee);
    }

    @Test
    public void testInterMineObjectAndNotXml() throws Exception {
        assertTrue(table(statements("os.unittest"), "InterMineObject")
                .contains("    OBJECT TEXT,\n    id INTEGER NOT NULL,\n    class TEXT,\n"));
        assertFalse(table(statements("os.unittest"), "Employee").contains("OBJECT"));
        // noNotXml=false: every class table has the serialised object
        assertTrue(table(statements("os.notxmlunittest"), "Employee").contains("    OBJECT TEXT,\n"));
    }

    @Test
    public void testFixedTables() throws Exception {
        List<String> statements = statements("os.unittest");
        assertEquals("CREATE TABLE intermine_metadata\n(\n    key TEXT,\n    value TEXT,\n"
                + "    blob_value BYTEA,\n    CONSTRAINT intermine_metadata_key UNIQUE (key)\n)",
                table(statements, "intermine_metadata"));
        assertEquals("CREATE TABLE osbag_int\n(\n    bagid INTEGER,\n    value INTEGER,\n"
                + "    CONSTRAINT osbag_int_index1 UNIQUE (bagid, value)\n)",
                table(statements, "osbag_int"));
        assertTrue(statements.contains("CREATE INDEX osbag_int_index2 ON osbag_int (value, bagid)"));
        assertEquals("CREATE TABLE clob\n(\n    clobid INTEGER,\n    clobpage INTEGER,\n"
                + "    value TEXT,\n    CONSTRAINT clob_index UNIQUE (clobid, clobpage)\n)",
                table(statements, "clob"));
    }

    @Test
    public void testIndirectionTable() throws Exception {
        assertEquals("CREATE TABLE HasSecretarysSecretarys\n(\n    HasSecretarys INTEGER NOT NULL,\n"
                + "    Secretarys INTEGER NOT NULL\n)",
                table(statements("os.unittest"), "HasSecretarysSecretarys"));
    }

    @Test
    public void testTruncatedAndFlatMode() throws Exception {
        List<String> truncated = statements("os.truncunittest");
        assertTrue(table(truncated, "InterMineObject").endsWith(
                "    class TEXT,\n    tableclass TEXT,\n"
                + "    CONSTRAINT InterMineObject_pkey UNIQUE (id, tableclass)\n)"));
        assertEquals(null, table(truncated, "Employee"));
        // flat mode: no InterMineObject (a missing table), no OBJECT and no class column
        List<String> flat = statements("os.flatmodeunittest");
        assertEquals(null, table(flat, "InterMineObject"));
        assertEquals(null, table(flat, "Thing"));
        assertFalse(table(flat, "Company").contains("OBJECT"));
        assertFalse(table(flat, "Company").contains("class TEXT"));
    }

    @Test
    public void testSqlType() {
        assertEquals("INT2", DdlGenerator.getSqlType("short"));
        assertEquals("INTEGER", DdlGenerator.getSqlType("java.lang.Integer"));
        assertEquals("INT8", DdlGenerator.getSqlType("long"));
        assertEquals("INT8", DdlGenerator.getSqlType("java.util.Date"));
        assertEquals("TEXT", DdlGenerator.getSqlType("java.lang.String"));
        assertEquals("TEXT", DdlGenerator.getSqlType("org.intermine.objectstore.query.ClobAccess"));
        assertEquals("BOOLEAN", DdlGenerator.getSqlType("boolean"));
        assertEquals("REAL", DdlGenerator.getSqlType("java.lang.Float"));
        assertEquals("DOUBLE PRECISION", DdlGenerator.getSqlType("double"));
        assertEquals("NUMERIC", DdlGenerator.getSqlType("java.math.BigDecimal"));
        try {
            DdlGenerator.getSqlType("java.lang.Object");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testSnapshotUnittest() throws Exception {
        assertSameAsTorque("os.unittest");
    }

    @Test
    public void testSnapshotTruncated() throws Exception {
        assertSameAsTorque("os.truncunittest");
    }

    @Test
    public void testSnapshotNotXml() throws Exception {
        assertSameAsTorque("os.notxmlunittest");
    }

    @Test
    public void testSnapshotFlatMode() throws Exception {
        assertSameAsTorque("os.flatmodeunittest");
    }

    @Test
    public void testSnapshotFulldata() throws Exception {
        assertSameAsTorque("os.fulldatatest");
    }

    /**
     * Compares the generated statements with the Torque output, statement by statement. Torque
     * wrote "--" comment banners and a DROP TABLE before each CREATE TABLE, and the indirection
     * tables in hash order: comments and DROPs are left out, whitespace is collapsed and the
     * statements are sorted. Column order is compared as it is.
     */
    private static void assertSameAsTorque(String osName) throws Exception {
        String resource = "torque-5.1-ddl/" + osName + "-schema.sql";
        String torque;
        try (InputStream in = DdlGeneratorTest.class.getResourceAsStream(resource)) {
            torque = IOUtils.toString(in, "UTF-8");
        }
        List<String> expected = new ArrayList<String>();
        StringBuilder sql = new StringBuilder();
        for (String line : torque.split("\n")) {
            if (!line.trim().startsWith("--")) {
                sql.append(line).append('\n');
            }
        }
        for (String statement : sql.toString().split(";")) {
            String normalised = normalise(statement);
            if (!normalised.isEmpty() && !normalised.startsWith("DROP TABLE ")) {
                expected.add(normalised);
            }
        }
        List<String> actual = new ArrayList<String>();
        for (String statement : statements(osName)) {
            actual.add(normalise(statement));
        }
        Collections.sort(expected);
        Collections.sort(actual);
        assertEquals(String.join("\n", expected), String.join("\n", actual));
    }

    private static String normalise(String statement) {
        return statement.trim().replaceAll("\\s+", " ").replace("( ", "(").replace(" )", ")");
    }
}

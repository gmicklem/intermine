package org.intermine.dataloader;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import junit.framework.TestCase;

import org.intermine.sql.Database;
import org.intermine.sql.DatabaseFactory;

public class DataTrackingTest extends TestCase {
    protected DataTracker dt;
    protected Source source1, source2;

    public void setUp() throws Exception {
        dt = new DataTracker(DatabaseFactory.getDatabase("db.unittest"), 30, 10);
        source1 = dt.stringToSource("Source1");
        source2 = dt.stringToSource("Source2");
    }

    public void tearDown() throws Exception {
        try {
            dt.close();
        } catch (Exception e) {
            // a test may have left the tracker unusable; its connections are released anyway
        } finally {
            Database db = DatabaseFactory.getDatabase("db.unittest");
            Connection c = db.getConnection();
            try {
                c.setAutoCommit(true);
                c.createStatement().execute("DROP TABLE IF EXISTS tracker");
            } finally {
                c.close();
            }
        }
    }

    public void testSetSourceNullIds() throws Exception {
        try {
            dt.setSource(null, "name", source1);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
        }

        try {
            dt.setSource(Integer.valueOf(46), "name", new Source("dummy"));
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
        }

        try {
            dt.setSource(Integer.valueOf(46), null, source1);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
        }
    }

    public void testGetSource() throws Exception {
        dt.setSource(Integer.valueOf(13), "name", source1);
        assertEquals(source1.getName(), dt.getSource(Integer.valueOf(13), "name").getName());

        for (int i = 100; i < 200; i++) {
            dt.setSource(Integer.valueOf(i), "name", source2);
        }

        assertEquals(source1.getName(), dt.getSource(Integer.valueOf(13), "name").getName());
    }
    
    // This is to investigate a possible bug where the version numbers are initialised from zero
    // for each data source, instead of continuing.
    public void testWrongOrderBug() throws Exception {
        dt.setSource(Integer.valueOf(13), "name", source1);
        dt.flush();
        dt.setSource(Integer.valueOf(14), "name", source1);
        dt.flush();
        DataTracker dt2 = new DataTracker(DatabaseFactory.getDatabase("db.unittest"), 30, 10);
        try {
            dt2.setSource(Integer.valueOf(14), "name", dt2.stringToSource("Source2"));
        } finally {
            dt2.close();
        }
        dt2 = new DataTracker(DatabaseFactory.getDatabase("db.unittest"), 30, 10);
        try {
            assertEquals(source2.getName(), dt2.getSource(Integer.valueOf(14), "name").getName());
        } finally {
            dt2.close();
        }
    }

    // A failed flush must say what failed, must not forget the entries it could not store, and
    // must not leave the tracker unusable for good.
    public void testFailedFlushIsReportedAndRetried() throws Exception {
        Database db = DatabaseFactory.getDatabase("db.unittest");
        execute(db, "DROP TABLE tracker");
        for (int i = 100; i < 105; i++) {
            dt.clearObj(Integer.valueOf(i));
            dt.setSource(Integer.valueOf(i), "name", source1);
        }
        try {
            dt.flush();
            fail("Expected the flush to fail while the tracker table is missing");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
            assertTrue(e.getMessage(), e.getMessage().contains("DataTracker"));
            assertTrue(e.getCause() instanceof SQLException);
        }
        createTrackerTable(db);
        // The failure was reported; the entries that could not be stored are still there
        dt.flush();
        assertStored(db, 100, 105);
    }

    // The background write of a batch fails: the failure is reported to the next caller (with a
    // message and the SQLException as cause), the batch is kept, and the tracker works again.
    public void testFailedBackgroundWriteIsReportedAndRecovered() throws Exception {
        Database db = DatabaseFactory.getDatabase("db.unittest");
        execute(db, "DROP TABLE tracker");
        IllegalArgumentException reported = null;
        int next = 100;
        long deadline = System.currentTimeMillis() + 30000L;
        // clearObj() does not read the (missing) tracker table
        while (reported == null && System.currentTimeMillis() < deadline) {
            try {
                dt.clearObj(Integer.valueOf(next));
                dt.setSource(Integer.valueOf(next), "name", source1);
                next++;
            } catch (IllegalArgumentException e) {
                reported = e;
            }
            if (next >= 130 && reported == null) {
                Thread.sleep(10);
            }
        }
        assertNotNull("the failed background write was not reported", reported);
        assertNotNull(reported.getMessage());
        assertTrue(reported.getMessage(), reported.getMessage().contains("DataTracker"));
        assertTrue(String.valueOf(reported.getCause()), reported.getCause() instanceof SQLException);

        createTrackerTable(db);
        // Reports of failures that happened before the table came back are given once each
        boolean flushed = false;
        for (int attempt = 0; attempt < 10 && !flushed; attempt++) {
            try {
                dt.flush();
                flushed = true;
            } catch (IllegalArgumentException e) {
                assertNotNull(e.getMessage());
            }
        }
        assertTrue("the tracker did not recover after the failed write", flushed);
        assertStored(db, 100, next);
    }

    // A failed read cannot be recovered from, but it does say what failed.
    public void testFailedReadHasMessage() throws Exception {
        Database db = DatabaseFactory.getDatabase("db.unittest");
        execute(db, "DROP TABLE tracker");
        try {
            dt.getSource(Integer.valueOf(4000), "name");
            fail("Expected the read to fail while the tracker table is missing");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
            assertTrue(e.getCause() instanceof SQLException);
        }
        try {
            dt.getSource(Integer.valueOf(4000), "name");
            fail("Expected the tracker to stay unusable after a failed read");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
            assertTrue(e.getCause() instanceof SQLException);
        }
    }

    private void execute(Database db, String sql) throws SQLException {
        Connection c = db.getConnection();
        try {
            c.setAutoCommit(true);
            Statement s = c.createStatement();
            s.execute(sql);
            s.close();
        } finally {
            c.close();
        }
    }

    private void createTrackerTable(Database db) throws SQLException {
        execute(db, "create table tracker (objectid int, fieldname text,"
                + " sourcename text, version int)");
        execute(db, "create index tracker_objectid on tracker (objectid)");
    }

    // Checks, through a second tracker, that ids from (inclusive) to to (exclusive) are stored
    private void assertStored(Database db, int from, int to) throws Exception {
        DataTracker check = new DataTracker(db, 30, 10);
        try {
            for (int i = from; i < to; i++) {
                assertEquals("id " + i, source1.getName(),
                        check.getSource(Integer.valueOf(i), "name").getName());
            }
        } finally {
            check.close();
        }
    }
}

package org.intermine.sql;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import junit.framework.AssertionFailedError;

import java.sql.ResultSet;
import java.sql.Types;


public class DatabaseTestCaseTest extends DatabaseTestCase
{
    public DatabaseTestCaseTest(String arg1) {
        super(arg1);
    }

    protected Database getDatabase() throws Exception {
        return DatabaseFactory.getDatabase("db.unittest");
    }

    private boolean failed;
    private ResultSet mrs1, mrs2, mrs3, mrs4;
    private ResultSet mrs11, mrs12, mrs13, mrs14;

    public void setUp() throws Exception {
        super.setUp();
        failed = false;
        String[] cols1 = new String[] {"field1", "field2", "field3", "field4"};
        String[] cols3 = new String[] {"field1", "field2", "field3", "field5"};
        int[] types = new int[] {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR};

        mrs1 = RowsResultSet.create(cols1, types,
                new Object[][] {{"test1", "test2", "test3", "test4"}});
        mrs2 = RowsResultSet.create(cols1, types,
                new Object[][] {{"test1", "test2", "test3", "test4"}});
        mrs3 = RowsResultSet.create(cols1, types,
                new Object[][] {{"test1", "test2", "test3", "test5"}});
        mrs4 = RowsResultSet.create(cols3, types,
                new Object[][] {{"test1", "test2", "test3", "test4"}});

        mrs11 = RowsResultSet.create(cols1, types, new Object[][] {
            {"test1", "test2", "test3", "test4"}, {"test5", "test6", "test7", "test8"}});
        mrs12 = RowsResultSet.create(cols1, types, new Object[][] {
            {"test1", "test2", "test3", "test4"}, {"test5", "test6", "test7", "test8"}});
        mrs13 = RowsResultSet.create(cols1, types, new Object[][] {
            {"test1", "test2", "test3", "test4"}, {"test5", "TEST6", "test7", "test8"}});
        mrs14 = RowsResultSet.create(cols3, types, new Object[][] {
            {"test1", "test2", "test3", "test4"}, {"test5", "test6", "test7", "test8"}});
    }

    public void testEqualsSingleRow() throws Exception {
        assertEquals(mrs1, mrs2);
    }

    public void testEqualsMultiRows() throws Exception {
        assertEquals(mrs11, mrs12);
    }

    public void testEqualsSingleRowDifferentData() throws Exception {
        try {
            assertEquals(mrs1, mrs3);
            failed = true;
        }
        catch (AssertionFailedError e) {
        }
        finally {
            if (failed) {
                fail("mrs1 and mrs3 should not be equal");
            }
        }
    }

    public void testEqualsSingleRowDifferentColumns() throws Exception {
        try {
            assertEquals(mrs1, mrs4);
            failed = true;
        }
        catch (AssertionFailedError e) {
        }
        finally {
            if (failed) {
                fail("mrs1 and mrs4 should not be equal");
            }
        }
    }

    public void testEqualsMultiRowDifferentData() throws Exception {
        try {
            assertEquals(mrs11, mrs13);
            failed=true;
        }
        catch (AssertionFailedError e) {
        }
        finally {
            if (failed) {
                fail("mrs11 and mrs13 should not be equal");
            }
        }
    }

    public void testEqualsMultiRowDifferentColumns() throws Exception {
        try {
            assertEquals(mrs11, mrs14);
            failed = true;
        }
        catch (AssertionFailedError e) {
        }
        finally {
            if (failed) {
                fail("mrs11 and mrs14 should not be equal");
            }
        }
    }

}

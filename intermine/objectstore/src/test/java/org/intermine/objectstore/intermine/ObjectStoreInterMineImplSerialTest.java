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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.intermine.objectstore.ObjectStoreWriterFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the id allocation of ObjectStoreInterMineImpl: ids are unique across threads that
 * share one instance, and never repeat ids of rows that were stored without using the 'serial'
 * sequence.
 */
public class ObjectStoreInterMineImplSerialTest
{
    private static final String PROBE_TABLE = "serialprobe_test";
    private ObjectStoreWriterInterMineImpl writer;
    private ObjectStoreInterMineImpl os;

    @Before
    public void setUp() throws Exception {
        writer = (ObjectStoreWriterInterMineImpl)
            ObjectStoreWriterFactory.getObjectStoreWriter("osw.unittest");
        os = (ObjectStoreInterMineImpl) writer.getObjectStore();
        dropProbeTable();
    }

    @After
    public void tearDown() throws Exception {
        dropProbeTable();
        writer.close();
    }

    @Test
    public void testIdsAboveRowsInsertedWithoutTheSequence() throws Exception {
        long sequence = currentSequenceValue();
        // a row whose id lies several batches ahead of the sequence, as written by plain SQL
        long foreignId = (sequence + 3) * ObjectStoreInterMineImpl.SEQUENCE_MULTIPLE + 17;
        execute("CREATE TABLE " + PROBE_TABLE + " (id integer PRIMARY KEY, name text)");
        execute("INSERT INTO " + PROBE_TABLE + " VALUES (" + foreignId + ", 'external')");

        // make the writer start a new batch, as a freshly started writer does
        writer.sequenceOffset = ObjectStoreInterMineImpl.SEQUENCE_MULTIPLE;
        int first = writer.getSerial().intValue();
        int second = writer.getSerial().intValue();

        Assert.assertTrue("id " + first + " must be above the stored id " + foreignId,
                first > foreignId);
        Assert.assertEquals(first + 1, second);
        Assert.assertTrue("the sequence must have moved past the stored id",
                currentSequenceValue() * ObjectStoreInterMineImpl.SEQUENCE_MULTIPLE
                >= foreignId);

        // another instance starting a batch now gets ids clear of both
        os.sequenceOffset = ObjectStoreInterMineImpl.SEQUENCE_MULTIPLE;
        int other = os.getSerial().intValue();
        Assert.assertTrue("id " + other + " must be above " + second, other > second);
    }

    @Test
    public void testNewBatchWithoutForeignRowsUsesNextValue() throws Exception {
        // a first refill moves the sequence past anything earlier tests left behind
        writer.sequenceOffset = ObjectStoreInterMineImpl.SEQUENCE_MULTIPLE;
        writer.getSerial();
        long before = currentSequenceValue();
        writer.sequenceOffset = ObjectStoreInterMineImpl.SEQUENCE_MULTIPLE;
        int id = writer.getSerial().intValue();
        long after = currentSequenceValue();
        Assert.assertEquals(before + 1, after);
        Assert.assertEquals(after * ObjectStoreInterMineImpl.SEQUENCE_MULTIPLE + 1, id);
    }

    @Test
    public void testIdsUniqueAcrossThreadsDuringBatchRefill() throws Exception {
        final int threads = 8;
        final int perThread = 500;
        // start near the end of a batch so that the threads race over a refill
        os.sequenceOffset = ObjectStoreInterMineImpl.SEQUENCE_MULTIPLE - 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<List<Integer>>> tasks = new ArrayList<Callable<List<Integer>>>();
            for (int t = 0; t < threads; t++) {
                tasks.add(new Callable<List<Integer>>() {
                    public List<Integer> call() throws Exception {
                        List<Integer> ids = new ArrayList<Integer>();
                        for (int i = 0; i < perThread; i++) {
                            ids.add(os.getSerial());
                        }
                        return ids;
                    }
                });
            }
            Set<Integer> all = Collections.synchronizedSet(new HashSet<Integer>());
            for (Future<List<Integer>> f : pool.invokeAll(tasks)) {
                for (Integer id : f.get()) {
                    Assert.assertTrue("id " + id + " handed out twice", all.add(id));
                }
            }
            Assert.assertEquals(threads * perThread, all.size());
        } finally {
            pool.shutdownNow();
        }
    }

    private long currentSequenceValue() throws Exception {
        Connection c = os.getConnection();
        try {
            Statement s = c.createStatement();
            ResultSet r = s.executeQuery("SELECT last_value FROM serial");
            r.next();
            return r.getLong(1);
        } finally {
            os.releaseConnection(c);
        }
    }

    private void dropProbeTable() throws Exception {
        execute("DROP TABLE IF EXISTS " + PROBE_TABLE);
    }

    private void execute(String sql) throws Exception {
        Connection c = os.getConnection();
        try {
            c.createStatement().execute(sql);
        } finally {
            os.releaseConnection(c);
        }
    }
}

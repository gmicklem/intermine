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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.intermine.sql.Database;

/**
 * Interface providing access to data tracking.
 *
 * This class is almost a generic map. However, it particularly maps from an Integer and a String
 * to a String and a boolean, where entries are grouped by the Integer.
 *
 * @author Matthew Wakeling
 */
public class DataTracker
{
    private static final Logger LOG = Logger.getLogger(DataTracker.class);

    /* We need a Map or two to store the entries. Each entry can be in several states:
     * 1. Recently-used and new - must be written to the database eventually.
     * 2. Recently-used and dirty - must be written back to the database eventually.
     * 3. Recently-used and clean - need not be written, but should be kept in memory.
     * 4. Not recently-used, and clean. These can be thrown away whenever the GC fancies it.
     *
     * This class will store types 1, 2 and 3 in a LinkedHashMap, ordered by access, and type 4 in a
     * WeakHashMap or CacheMap, or may not even store them at all.
     *
     * The LinkedHashMap has a threshold size. When it grows bigger than maxSize, a database write
     * occurs, which stores commitSize least-recently-used entries in the database, which then
     * become type 4.
     */
    private int maxSize;
    private int commitSize;
    private LinkedHashMap<Integer, ObjectDescription> cache;
    private HashMap<Integer, ObjectDescription> writeBack =
        new HashMap<Integer, ObjectDescription>();
    private HashMap<String, Source> nameToSource = new HashMap<String, Source>();
    private HashMap<Source, String> sourceToName = new HashMap<Source, String>();
    private Connection conn;
    private Connection storeConn;
    /**
     * The failure that made the tracker refuse further operations, or null. It is written by the
     * CacheStorer thread (a failed background write) as well as by the caller's thread, hence
     * volatile. A failed write is reported to the next caller by checkBroken() and then cleared
     * (the batch that failed is put back in the cache, so a later flush() retries it); a failed
     * read is never cleared.
     */
    protected volatile Exception broken = null;
    // Set (with broken) when the failure came from a write that was rolled back and can be retried
    private volatile boolean brokenRecoverable = false;
    private final Object failureLock = new Object();
    private CacheStorer cacheStorer;
    private int version = 0;
    // This reference is here so that the Database doesn't get garbage collected.
    private Database db;

    private int ops = 0;
    private int misses = 0;
    private int batched = 0;
    private long timeSpentReading = 0;
    private long timeSpentPrefetching = 0;

    /**
     * Constructor for DataTracker.
     *
     * @param db a Database to back the tracker
     * @param maxSize maximum number of cache entries
     * @param commitSize number of entries to write to the database at a time
     */
    public DataTracker(Database db, int maxSize, int commitSize) {
        this.maxSize = maxSize;
        this.commitSize = commitSize;
        this.db = db;
        cache = new LinkedHashMap<Integer, ObjectDescription>(maxSize * 14 / 10, 0.75F, true);
        try {
            conn = db.getConnection();
            conn.setAutoCommit(true);
            storeConn = db.getConnection();
            storeConn.setAutoCommit(false);
            Statement s = conn.createStatement();
            try {
                s.executeQuery("SELECT * FROM tracker LIMIT 1");
            } catch (SQLException e2) {
                clear();
            }
        } catch (SQLException e) {
            IllegalArgumentException e2 = new IllegalArgumentException(
                    "Could not access SQL database");
            e2.initCause(e);
            throw e2;
        }
        cacheStorer = new CacheStorer();
        Thread cacheStorerThread = new Thread(cacheStorer, "DataTracker CacheStorer");
        cacheStorerThread.setDaemon(true);
        cacheStorerThread.start();
    }

    /**
     * Clears the data tracker of all entries, in memory and in the tracker table.
     *
     * This may be called at any time. It holds the same locks as flush() (and so as the
     * CacheStorer thread's doWrite()), so no batch can be written to the tracker table while it
     * is dropped and re-created; previously a background write landing between the DROP and the
     * CREATE failed with "relation tracker does not exist" and left the tracker broken. The DROP
     * and CREATE run in one transaction, so other connections never see the table missing.
     *
     * @throws SQLException sometimes
     */
    public void clear() throws SQLException {
        // Synchronise in the same order as flush() to prevent deadlocks.
        synchronized (writeBack) {
            synchronized (this) {
                cache.clear();
                writeBack.clear();
                boolean autoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    Statement s = conn.createStatement();
                    s.execute("DROP TABLE IF EXISTS tracker");
                    s.execute("create table tracker (objectid int, fieldname text,"
                            + " sourcename text, version int)");
                    s.execute("create index tracker_objectid on tracker (objectid)");
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(autoCommit);
                }
            }
        }
    }

    /**
     * Prefetches data for a specified set of object ids.
     *
     * @param ids a Set of Integers
     */
    public void prefetchIds(Set<Integer> ids) {
        Connection prefetchConn = null;
        try {
            prefetchConn = db.getConnection();
            prefetchConn.setAutoCommit(true);
            prefetchConn.createStatement().execute("SET enable_seqscan = off;");
            long startTime = System.currentTimeMillis();
            Set<Integer> toFetch = new HashSet<Integer>();
            synchronized (this) {
                checkBroken();
                for (Integer id : ids) {
                    ObjectDescription desc = cache.get(id);
                    if (desc == null) {
                        desc = writeBack.get(id);
                        cache.put(id, desc);
                    }
                    if (desc == null) {
                        toFetch.add(id);
                    }
                }
            }
            Map<Integer, ObjectDescription> idsFetched = new HashMap<Integer, ObjectDescription>();
            int highestVersionSeen = Integer.MIN_VALUE;
            if (!toFetch.isEmpty()) {
                int count = 0;
                StringBuffer sql = new StringBuffer();
                boolean needComma = false;
                Iterator<Integer> idIter = toFetch.iterator();
                while (idIter.hasNext()) {
                    count++;
                    Integer id = idIter.next();
                    if (needComma) {
                        sql.append(", ");
                    } else {
                        sql.append("SELECT objectid, fieldname, sourcename, version"
                                + " FROM tracker WHERE objectid IN (");
                    }
                    needComma = true;
                    sql.append("" + id);
                    idsFetched.put(id, new ObjectDescription());
                    if ((count % 500 == 0) || (!idIter.hasNext())) {
                        sql.append(") ORDER BY version");
                        try {
                            Statement s = prefetchConn.createStatement();
                            //long beforeExecute = System.currentTimeMillis();
                            ResultSet r = s.executeQuery(sql.toString());
                            //LOG.info("Prefetching " + (((count + 499) % 500) + 1)
                            //        + " tracks took " + (System.currentTimeMillis()
                            //            - beforeExecute) + " ms");
                            while (r.next()) {
                                ObjectDescription objectDescription =
                                    idsFetched.get(new Integer(r.getInt(1)));
                                highestVersionSeen = Math.max(highestVersionSeen, r.getInt(4));
                                objectDescription.putClean(r.getString(2).intern(),
                                                           stringToSource(r.getString(3)));
                            }
                        } catch (SQLException e) {
                            throw readFailed("prefetching tracker entries", e);
                        }
                        needComma = false;
                        sql = new StringBuffer();
                    }
                }
            }
            synchronized (this) {
                if (version <= highestVersionSeen) {
                    version = highestVersionSeen + 1;
                }
                cache.putAll(idsFetched);
                maybePoke();
                batched += idsFetched.size();
            }
            timeSpentPrefetching += System.currentTimeMillis() - startTime;
        } catch (SQLException e) {
            throw readFailed("prefetching tracker entries", e);
        } finally {
            if (prefetchConn != null) {
                try {
                    prefetchConn.close();
                } catch (SQLException e) {
                    LOG.warn("Error while closing prefetch connection", e);
                }
            }
        }
    }

    /**
     * Retrieve the Source for a specified field of an Object stored in the database.
     *
     * @param id the ID of the object
     * @param field the name of the field
     * @return the Source
     */
    public synchronized Source getSource(Integer id, String field) {
        if (id == null) {
            throw new NullPointerException("id cannot be null");
        }
        checkBroken();
        ObjectDescription desc = getDesc(id, false);
        return desc.getSource(field);
    }

    /**
     * Gets the object descriptor for a given object id
     *
     * @param id the ID
     * @param forWrite true if the returned value is going to be modified
     * @return an ObjectDescriptor
     */
    private ObjectDescription getDesc(Integer id, boolean forWrite) {
        long startTime = System.currentTimeMillis();
        ObjectDescription desc = cache.get(id);
        if (desc == null) {
            desc = writeBack.get(id);
            if (forWrite && (desc != null)) {
                desc = new ObjectDescription(desc);
            }
            cache.put(id, desc);
        }
        if (desc == null) {
            desc = new ObjectDescription();
            try {
                long start = System.currentTimeMillis();
                Statement s = conn.createStatement();
                ResultSet r = s.executeQuery("select fieldname, sourcename, version from tracker"
                        + " where objectid = " + id + " ORDER BY version");
                while (r.next()) {
                    desc.putClean(r.getString(1).intern(), stringToSource(r.getString(2)));
                    int rowVersion = r.getInt(3);
                    if (version <= rowVersion) {
                        version = rowVersion + 1;
                    }
                }
                long now = System.currentTimeMillis();
                //LOG.debug("Fetched entry from DB (time = " + (now - start) + " ms)");
                if (now - start > 2000) {
                    LOG.warn("Query on tracker table took too long (" + (now - start) + " ms) "
                            + "- switching off sequential scans. You should analyse the database");
                    conn.createStatement().execute("SET enable_seqscan = off;");
                }
            } catch (SQLException e) {
                throw readFailed("reading a tracker entry", e);
            }
            cache.put(id, desc);
            maybePoke();
            misses++;
        }
        timeSpentReading += System.currentTimeMillis() - startTime;
        ops++;
        if (ops % 1000000 == 0) {
            LOG.info("Operations: " + ops + ", cache misses: " + misses + ", time spent reading: "
                    + timeSpentReading);
        }
        return desc;
    }

    /**
     * Set the Source for a field of an object in the database
     *
     * @param id the ID of the object
     * @param field the name of the field
     * @param source the Source of the field
     */
    public synchronized void setSource(Integer id, String field, Source source) {
        if (id == null) {
            throw new NullPointerException("id cannot be null");
        }
        if (!sourceToName.containsKey(source)) {
            throw new NullPointerException("Could not find given source (" + source
                    + ") in tracker. sourceToName = " + sourceToName);
        }
        checkBroken();
        ObjectDescription desc = getDesc(id, true);
        desc.put(field.intern(), source);
        // Lastly, we put the description into the cache, just in case we got it out of the
        // write-back cache. This guarantees that we won't lose data by forgetting to write it to
        // the database.
        cache.put(id, desc);
        maybePoke();
    }

    /**
     * Clears the cache for a particular object, in preparation for writing all the data for that
     * object. This allows the data tracker to cache the writes that are about to happen. This
     * method should only be called with Ids that this DataTracker has never seen before, otherwise
     * it will get the data wrong.
     *
     * @param id the ID of the object
     */
    public synchronized void clearObj(Integer id) {
        checkBroken();
        ObjectDescription desc = new ObjectDescription();
        cache.put(id, desc);
        maybePoke();
    }

    /**
     * Performs maintenance of the cache, writing stuff to the backing database.
     *
     * @return true if some action was performed
     */
    public boolean doWrite() {
        checkBroken();
        return writeNextBatch(false);
    }

    /**
     * Writes a batch for the CacheStorer thread. A failure is recorded, to be reported to the next
     * caller of the tracker (checkBroken()), rather than lost in the thread's log; until then the
     * thread writes nothing more.
     */
    private boolean doBackgroundWrite() {
        Exception failure = broken;
        if (failure != null) {
            throw new IllegalArgumentException("DataTracker is unusable after an earlier failure: "
                    + failure, failure);
        }
        return writeNextBatch(true);
    }

    private boolean writeNextBatch(boolean background) {
        synchronized (writeBack) {
            int cacheSize = cache.size();
            Map<Integer, ObjectDescription> writeBatch = getWriteBatch();
            if (writeBatch != null) {
                LOG.info("Writing cache batch - batch size: " + writeBatch.size()
                        + ", cache size: " + cacheSize + "->" + cache.size());
                try {
                    writeMap(writeBatch, false);
                } catch (SQLException e) {
                    restoreWriteBatch(writeBatch);
                    throw writeFailed("writing a batch of tracker entries to the database", e,
                            background);
                }
                clearWriteBack();
                return true;
            } else {
                LOG.debug("Not writing cache batch - no dirty entries");
                return false;
            }
        }
    }

    /**
     * Puts the entries of a batch that could not be written back in the cache (unless a newer
     * version of an entry is already there), so that they are written by a later batch or flush()
     * instead of being lost, and empties the write-back cache.
     *
     * @param writeBatch the batch that failed
     */
    private synchronized void restoreWriteBatch(Map<Integer, ObjectDescription> writeBatch) {
        for (Map.Entry<Integer, ObjectDescription> entry : writeBatch.entrySet()) {
            if (!cache.containsKey(entry.getKey())) {
                cache.put(entry.getKey(), entry.getValue());
            }
        }
        writeBack.clear();
    }

    /**
     * Throws the exception reporting an earlier failure of this tracker, if there was one. A
     * failure of a write is reported once and then forgotten: the data of the failed batch is
     * still in the cache, so the tracker stays usable and the next flush() tries again. A failure
     * of a read leaves the tracker unusable.
     */
    private void checkBroken() {
        synchronized (failureLock) {
            Exception failure = broken;
            if (failure != null) {
                boolean recoverable = brokenRecoverable;
                if (recoverable) {
                    brokenRecoverable = false;
                    broken = null;
                }
                throw new IllegalArgumentException("DataTracker is unusable after an earlier "
                        + "failure" + (recoverable ? " (reported once; the failed write will be "
                            + "retried)" : "") + ": " + failure, failure);
            }
        }
    }

    private Exception takeRecoverableFailure() {
        synchronized (failureLock) {
            Exception failure = broken;
            if (failure != null && brokenRecoverable) {
                brokenRecoverable = false;
                broken = null;
                return failure;
            }
            return null;
        }
    }

    private IllegalArgumentException readFailed(String action, SQLException e) {
        synchronized (failureLock) {
            brokenRecoverable = false;
            broken = e;
        }
        return new IllegalArgumentException("DataTracker failed while " + action + ": " + e, e);
    }

    /**
     * The exception for a failed write. The data is still in the cache and the batch was rolled
     * back, so nothing is lost: if the write was in the CacheStorer thread the failure is recorded
     * for the next caller, otherwise the exception thrown here is the report.
     */
    private IllegalArgumentException writeFailed(String action, SQLException e,
            boolean background) {
        synchronized (failureLock) {
            // Never turn an earlier read failure (which cannot be recovered) into a recoverable one
            if (background && broken == null) {
                brokenRecoverable = true;
                broken = e;
            }
        }
        return new IllegalArgumentException("DataTracker failed while " + action + ": " + e, e);
    }


    /**
     * Flushes everything to the backing database.
     */
    public void flush() {
        checkBroken();
        LOG.info("Flushing cache - size: " + cache.size());
        // Synchronise in this order to prevent deadlocks.
        synchronized (writeBack) {
            synchronized (this) {
                try {
                    writeMap(cache, true);
                } catch (SQLException e) {
                    throw writeFailed("flushing the tracker to the database", e, false);
                }
            }
        }
    }

    /**
     * Closes this DataTracker, releasing both connections to the database. No further operations
     * can be performed on the tracker.
     */
    public void close() {
        LOG.info("Closing DataTracker. Operations: " + ops + ", cache misses: " + misses
                + ", time spent reading: " + timeSpentReading + ", prefetched: " + batched
                + ", time spent prefetching: " + timeSpentPrefetching);
        cacheStorer.die();
        try {
            Exception pending = takeRecoverableFailure();
            if (pending != null) {
                LOG.warn("Retrying the write that failed earlier before closing", pending);
            }
            flush();
        } finally {
            // Release the connections even if the final flush failed
            synchronized (this) {
                try {
                    conn.close();
                    storeConn.close();
                    conn = null;
                    storeConn = null;
                } catch (SQLException e) {
                    throw new IllegalArgumentException("DataTracker could not close its "
                            + "connections: " + e, e);
                }
            }
        }
    }

    /**
     * Returns a Map created from cache, containing the entries that should be flushed to the
     * backing database. The entries are removed from the cache and put in a special write-back
     * cache before this method terminates. Once you have finished storing the entries, you should
     * call clearWriteBack() to clear this write-back cache.
     * This method will return null if it does not recommend flushing any entries to the backing
     * database. The Map will probably only contain those entries that are dirty, however the method
     * that uses this method should not rely on this fact, because such a method may be passed the
     * cache instead in the instance of a flush().
     *
     * @return a Map from Integer to ObjectDescription
     */
    private synchronized Map<Integer, ObjectDescription> getWriteBatch() {
        if (cache.size() > maxSize) {
            Map<Integer, ObjectDescription> retval = new HashMap<Integer, ObjectDescription>();
            int count = 0;
            Iterator<Map.Entry<Integer, ObjectDescription>> iter = cache.entrySet().iterator();
            while ((count < commitSize) && iter.hasNext()) {
                Map.Entry<Integer, ObjectDescription> iterEntry = iter.next();
                Integer id = iterEntry.getKey();
                ObjectDescription desc = iterEntry.getValue();
                if (desc.isDirty()) {
                    retval.put(id, desc);
                    writeBack.put(id, desc);
                }
                iter.remove();
                count++;
            }
            return retval;
        } else {
            return null;
        }
    }

    /**
     * Clears the write-back cache. This should be called after the data has been committed to the
     * database. Note that some of the entries may have been altered since they were put in the
     * write-back cache. Data loss is avoided by the fact that such entries are placed back in the
     * main cache.
     */
    private synchronized void clearWriteBack() {
        writeBack.clear();
    }

    /**
     * Writes the contents of the given Map to the backing database. Attempts to make use of all the
     * SQL tricks to speed this operation up.
     *
     * @param map a Map from Integer to ObjectDesciption
     * @param clean true if this method should call clean() on all the entries in the given Map, or
     * false if the given Map is going to be thrown away.
     * @throws SQLException on any error with the backing database
     */
    private void writeMap(Map<Integer, ObjectDescription> map, boolean clean) throws SQLException {
        long start = System.currentTimeMillis();
        // Entries are only cleaned once the batch is committed, so that a failed write does not
        // make the tracker forget what was never stored.
        List<ObjectDescription> toClean = new ArrayList<ObjectDescription>();
        try {
            org.postgresql.copy.CopyManager copyManager = null;
            ByteArrayOutputStream baos = null;
            DataOutputStream dos = null;
            Statement s = null;
            if (storeConn.isWrapperFor(org.postgresql.PGConnection.class)) {
                copyManager = (storeConn.unwrap(org.postgresql.PGConnection.class)).getCopyAPI();
                baos = new ByteArrayOutputStream();
                dos = new DataOutputStream(baos);
                dos.writeBytes("PGCOPY\n");
                dos.writeByte(255);
                dos.writeBytes("\r\n");
                dos.writeByte(0); // Signature done
                dos.writeInt(0); // Flags - we aren't supplying OIDS
                dos.writeInt(0); // Length of header extension
            }
            if (copyManager == null) {
                s = storeConn.createStatement();
                LOG.warn("Using slow portable writing method");
            }
            for (Map.Entry<Integer, ObjectDescription> entry : map.entrySet()) {
                Integer id = entry.getKey();
                ObjectDescription desc = entry.getValue();
                if (desc.isDirty()) {
                    Map<String, Source> orig = desc.getOrig();
                    Map<String, Source> newData = desc.getNewData();
                    for (Map.Entry<String, Source> fieldEntry : newData.entrySet()) {
                        String field = fieldEntry.getKey();
                        Source source = fieldEntry.getValue();
                        if (!orig.containsKey(field) || (!orig.get(field).equals(source))) {
                            // Insert required
                            if (s == null) {
                                dos.writeShort(4); // Number of fields
                                dos.writeInt(4); // Length of an integer
                                dos.writeInt(id.intValue()); // objectid
                                dos.writeInt(field.length()); // Length of fieldname
                                dos.writeBytes(field); // Field name
                                String sourceName = sourceToString(source);
                                dos.writeInt(sourceName.length()); // Length of source name
                                dos.writeBytes(sourceName); // Source name
                                dos.writeInt(4); // Length of an integer
                                dos.writeInt(version); // version
                            } else {
                                s.addBatch("INSERT INTO tracker (objectid, fieldname, sourcename,"
                                        + " version) VALUES (" + id + ", '" + field + "', '"
                                        + sourceToString(source) + "', " + version + ")");
                            }
                        }
                    }
                    if (clean) {
                        toClean.add(desc);
                    }
                }
            }
            if (s == null) {
                dos.writeShort(-1); // No more tuples
                dos.flush();
                copyManager.copyIn("COPY tracker FROM STDIN BINARY",
                        new ByteArrayInputStream(baos.toByteArray()));
            } else {
                s.executeBatch();
            }
            version++;
            storeConn.commit();
            for (ObjectDescription desc : toClean) {
                desc.clean();
            }
        } catch (IOException e) {
            rollbackQuietly();
            throw new SQLException(e.toString());
        } catch (SQLException e) {
            rollbackQuietly();
            throw e;
        } catch (RuntimeException e) {
            rollbackQuietly();
            throw e;
        }
        long now = System.currentTimeMillis();
        LOG.debug("Finished storing batch (time = " + (now - start) + " ms)");
    }

    private void rollbackQuietly() {
        try {
            storeConn.rollback();
        } catch (SQLException e) {
            LOG.warn("Could not roll back the failed tracker write", e);
        }
    }

    /**
     * Pokes the CacheStorer thread if there are too many entries in the cache.
     */
    private void maybePoke() {
        if (cache.size() > maxSize) {
            cacheStorer.poke();
        }
    }

    /**
     * Converts a string sourcename to a Source.
     *
     * @param name a string source name
     * @return a Source
     */
    public synchronized Source stringToSource(String name) {
        return stringToSource(name, null);
    }

    /**
     * Converts a string sourcename to a Source if the type is known.
     *
     * @param name a string source name
     * @param type the source type
     * @return a Source
     */
    public synchronized Source stringToSource(String name, String type) {
        Source retval = nameToSource.get(name);
        if (retval == null) {
            if (name.startsWith("skel_")) {
                retval = new Source(name.substring(5), type, true);
            } else {
                retval = new Source(name, type, false);
            }
            nameToSource.put(name, retval);
            sourceToName.put(retval, name);
        }
        return retval;
    }

    /**
     * Converts a Source to a source name.
     *
     * @param source a Source
     * @return the name
     */
    public synchronized String sourceToString(Source source) {
        String retval = sourceToName.get(source);
        if (retval == null) {
            throw new NullPointerException("Could not find given source in tracker");
        }
        return retval;
    }

    private class CacheStorer implements Runnable
    {
        private boolean needAction = false;
        private boolean dontQuit = true;
        private boolean notDead = true;

        public CacheStorer() {
        }

        public synchronized void poke() {
            needAction = true;
            notify();
        }

        public synchronized void die() {
            needAction = true;
            dontQuit = false;
            notify();
            while (notDead) {
                try {
                    wait(100000L);
                } catch (InterruptedException e) {
                }
            }
        }

        private synchronized boolean dontQuitNow() {
            return needAction || dontQuit;
        }

        public void run() {
            while (dontQuitNow()) {
                synchronized (this) {
                    while (!needAction) {
                        try {
                            wait(100000L);
                        } catch (InterruptedException e) {
                        }
                    }
                }
                while (needAction) {
                    try {
                        synchronized (this) {
                            needAction = false;
                        }
                        boolean tempNeedAction = doBackgroundWrite();
                        synchronized (this) {
                            needAction = needAction || tempNeedAction;
                        }
                    } catch (Exception e) {
                        LOG.error("CacheStorer received exception: " + e);
                    }
                }
            }
            synchronized (this) {
                notDead = false;
                notifyAll();
            }
        }
    }
}

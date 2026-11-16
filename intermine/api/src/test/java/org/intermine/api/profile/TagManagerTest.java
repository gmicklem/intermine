package org.intermine.api.profile;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.intermine.api.InterMineAPITestCase;
import org.intermine.api.profile.TagManager.TagNameException;
import org.intermine.api.profile.TagManager.TagNamePermissionException;
import org.intermine.api.tag.TagTypes;
import org.intermine.model.InterMineObject;
import org.intermine.api.userprofile.Tag;
import org.intermine.api.userprofile.UserProfile;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.metadata.ConstraintOp;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryValue;
import org.intermine.objectstore.query.SimpleConstraint;
import org.intermine.objectstore.query.SingletonResults;

public class TagManagerTest extends InterMineAPITestCase
{

    private Profile bobProfile;
    private ProfileManager pm;
    private TagManager manager;
    public TagManagerTest(String arg) {
        super(arg);
    }

    public void setUp() throws Exception {
        super.setUp();
        pm = im.getProfileManager();
        bobProfile = pm.createBasicLocalProfile("bob", "bob_pass", null);
        // bobProfile = new Profile(pm, "bob", 101, "bob_pass", new HashMap(), new HashMap(),
        //                         new HashMap(), true, false);
        // pm.createProfile(bobProfile);
        manager = im.getTagManager();
    }

    public void testDeleteTag() throws TagNameException, TagNamePermissionException {
        String tagName = "list1Tag";
        assertEquals(0, manager.getTagsByName(tagName, bobProfile, TagTypes.BAG).size());
        // Add tag.
        manager.addTag(tagName, "list1", TagTypes.BAG, bobProfile);
        // test that tag was added successfully
        assertEquals(1, manager.getTagsByName(tagName, bobProfile, TagTypes.BAG).size());
        manager.deleteTag(tagName, "list1", TagTypes.BAG, bobProfile.getUsername());
        // test that tag was deleted
        assertEquals(0, manager.getTagsByName(tagName, bobProfile, TagTypes.BAG).size());
    }

    public void testGetTags() throws Exception {
        manager.addTag("list1Tag", "list1", "bag", "bob");
        manager.addTag("list2Tag", "list2", "bag", "bob");
        manager.addTag("list3Tag", "list3", "bag", "bob");
        manager.addTag("list4Tag", "list_", "bag", "bob");

        List<Tag> tags = manager.getUserTags(bobProfile);
        assertEquals(4, tags.size());
        assertTrue("Tag added to database but not retrieved.", tagExists(tags, "list1Tag", "list1", "bag", "bob"));
        assertTrue("Tag added to database but not retrieved.", tagExists(tags, "list2Tag", "list2", "bag", "bob"));
        assertTrue("Tag added to database but not retrieved.", tagExists(tags, "list3Tag", "list3", "bag", "bob"));

        assertEquals(1, manager.getTags(null, "list_", "bag", "bob").size());
    }

    public void testAddTag() {
        Tag createdTag = manager.addTag("wowTag", "list1", "bag", "bob");
        Tag retrievedTag = manager.getTags("wowTag", "list1", "bag", "bob").get(0);
        assertEquals(createdTag, retrievedTag);
        assertEquals(createdTag.getTagName(), "wowTag");
        assertEquals(createdTag.getObjectIdentifier(), "list1");
        assertEquals(createdTag.getType(), "bag");
        assertEquals(createdTag.getUserProfile().getUsername(), "bob");
    }

    public void testAddWithProfile() throws Exception {
        Tag createdTag = manager.addTag("wowTag", "list1", "bag", bobProfile);
        Tag retrievedTag = manager.getTags("wowTag", "list1", "bag", "bob").get(0);
        assertEquals(createdTag, retrievedTag);
        assertEquals(createdTag.getTagName(), "wowTag");
        assertEquals(createdTag.getObjectIdentifier(), "list1");
        assertEquals(createdTag.getType(), "bag");
        assertEquals(createdTag.getUserProfile().getUsername(), "bob");
    }

    public void testAddSameTagTwiceStoresOnce() {
        Tag first = manager.addTag("wowTag", "list1", "bag", "bob");
        Tag second = manager.addTag("wowTag", "list1", "bag", "bob");
        assertEquals(first.getId(), second.getId());
        assertEquals(1, manager.getTags("wowTag", "list1", "bag", "bob").size());
    }

    // Several web requests tagging the same list with the same tag at once must give one tag
    // and no exception (a duplicate row, or a duplicate key error, used to be possible).
    public void testAddSameTagFromTwoThreads() throws Exception {
        final int threads = 2;
        final java.util.concurrent.CyclicBarrier start =
                new java.util.concurrent.CyclicBarrier(threads);
        final List<Throwable> failures = java.util.Collections.synchronizedList(
                new java.util.ArrayList<Throwable>());
        List<Thread> workers = new java.util.ArrayList<Thread>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread() {
                @Override
                public void run() {
                    try {
                        start.await();
                        for (int j = 0; j < 5; j++) {
                            manager.addTag("raceTag" + j, "list1", "bag", bobProfile);
                        }
                    } catch (Throwable e) {
                        failures.add(e);
                    }
                }
            };
            workers.add(t);
            t.start();
        }
        for (Thread t : workers) {
            t.join(60000);
        }
        assertTrue("Tagging from several threads failed: " + failures, failures.isEmpty());
        for (int j = 0; j < 5; j++) {
            assertEquals("raceTag" + j, 1, manager.getTags("raceTag" + j, "list1", "bag", "bob")
                    .size());
        }
    }

    // If the writer hands out an id that is already in the tag table (tag_pkey violation), the
    // tag is stored again with a new id rather than failing the request.
    public void testAddTagRetriesOnDuplicateKey() throws Exception {
        TagManager flakyManager = new TagManager(failingWriter(2, "23505"));
        Tag tag = flakyManager.addTag("retryTag", "list1", "bag", "bob");
        assertNotNull(tag.getId());
        assertEquals(1, manager.getTags("retryTag", "list1", "bag", "bob").size());
    }

    // Other store failures are still reported, as is a duplicate key that keeps happening.
    public void testAddTagReportsOtherFailures() throws Exception {
        try {
            new TagManager(failingWriter(1, "08006")).addTag("t1", "list1", "bag", "bob");
            fail("Expected exception");
        } catch (RuntimeException e) {
            // expected
        }
        try {
            new TagManager(failingWriter(100, "23505")).addTag("t2", "list1", "bag", "bob");
            fail("Expected exception");
        } catch (RuntimeException e) {
            // expected
        }
        assertEquals(0, manager.getTags("t1", "list1", "bag", "bob").size());
        assertEquals(0, manager.getTags("t2", "list1", "bag", "bob").size());
    }

    /** A writer whose first 'failures' calls to store throw an ObjectStoreException. */
    private ObjectStoreWriter failingWriter(final int failures, final String sqlState) {
        final int[] failuresLeft = {failures};
        final ObjectStoreWriter real = uosw;
        return (ObjectStoreWriter) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[] {ObjectStoreWriter.class},
                new java.lang.reflect.InvocationHandler() {
                    public Object invoke(Object proxy, java.lang.reflect.Method m, Object[] args)
                        throws Throwable {
                        if ("store".equals(m.getName()) && failuresLeft[0]-- > 0) {
                            throw new ObjectStoreException("insert failed",
                                    new java.sql.SQLException("simulated", sqlState));
                        }
                        try {
                            return m.invoke(real, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
                });
    }

    public void testAddPermissionProblems() throws Exception {
        try {
            manager.addTag("im:wowTag", "list1", "bag", bobProfile);
            fail("Expected exception");
        } catch (TagManager.TagNamePermissionException e) {
            //Bob is not allowed to add im tags, because he is not the superuser
        }
    }

    public void testAddNameProblems() throws Exception {
        try {
            manager.addTag("An illegal tag name!", "list1", "bag", bobProfile);
            fail("Expected exception");
        } catch (TagManager.TagNameException e) {
            //That name is illegal!
        }
    }

    public void testGetTagById() {
        Tag tag = manager.addTag("list1Tag", "list1", "bag", "bob");
        Tag retrievedTag = manager.getTagById(tag.getId());
        assertEquals(tag, retrievedTag);
    }

    // Verifies that tag name can only contain A-Z, a-z, 0-9, '_', '-', ' ', ':', '.', ','
    public void testIsValidTagName() {
        assertTrue(TagManager.isValidTagName("validTagName_.,- :1"));
        assertFalse(TagManager.isValidTagName("'; drop table userprofile;"));
        assertFalse(TagManager.isValidTagName(null));
        assertFalse(TagManager.isValidTagName("invalidTagName@"));
    }

    private boolean tagExists(List<Tag> tags, String name, String taggedObject, String type,
            String userName) {
        for (Tag tag : tags) {
            if (tag.getTagName().equals(name)
                    && tag.getObjectIdentifier().equals(taggedObject)
                    && tag.getType().equals(type)
                    && tag.getUserProfile().getUsername().equals(userName)) {
                return true;
            }
        }
        return false;
    }
}

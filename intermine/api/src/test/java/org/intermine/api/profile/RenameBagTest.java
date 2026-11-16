package org.intermine.api.profile;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.intermine.api.InterMineAPITestCase;

/**
 * Concurrent uploads of one list name: each request fills a temporary list of its own and renames
 * it at the end, of which only one can win (im-jqz.57).
 */
public class RenameBagTest extends InterMineAPITestCase {

    private Profile bob;

    public RenameBagTest(String arg) {
        super(arg);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        ProfileManager pm = im.getProfileManager();
        bob = new Profile(pm, "bob", 101, "bob_pass",
                new HashMap(), new HashMap(), new HashMap(), true, false);
        pm.createProfile(bob);
    }

    public void testConcurrentRenamesToOneName() throws Exception {
        final int threads = 8;
        for (int i = 0; i < threads; i++) {
            bob.createBag("same_temp_" + i, "Employee", "", im.getClassKeys());
        }
        final CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            Future<String>[] results = new Future[threads];
            for (int i = 0; i < threads; i++) {
                final String tempName = "same_temp_" + i;
                results[i] = pool.submit(new Callable<String>() {
                    public String call() throws Exception {
                        start.await();
                        try {
                            bob.renameBag(tempName, "same");
                            return "renamed";
                        } catch (ProfileAlreadyExistsException e) {
                            return "in use";
                        }
                    }
                });
            }
            int renamed = 0;
            int inUse = 0;
            for (int i = 0; i < threads; i++) {
                String result = results[i].get(60, TimeUnit.SECONDS);
                if ("renamed".equals(result)) {
                    renamed++;
                } else {
                    inUse++;
                }
            }
            assertEquals("exactly one rename wins", 1, renamed);
            assertEquals(threads - 1, inUse);
        } finally {
            pool.shutdownNow();
        }
        assertTrue(bob.getSavedBags().containsKey("same"));
        // the renames that lost left their temporary lists, which the request deletes
        int left = 0;
        for (int i = 0; i < threads; i++) {
            if (bob.getSavedBags().containsKey("same_temp_" + i)) {
                left++;
            }
        }
        assertEquals(threads - 1, left);
    }

    public void testRenameOfMissingBag() throws Exception {
        try {
            bob.renameBag("no_such_temp", "whatever");
            fail("expected BagDoesNotExistException");
        } catch (BagDoesNotExistException e) {
            // expected
        }
    }
}

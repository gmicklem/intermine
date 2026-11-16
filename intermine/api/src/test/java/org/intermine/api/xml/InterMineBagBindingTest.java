package org.intermine.api.xml;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.intermine.api.InterMineAPITestCase;
import org.intermine.api.profile.BagState;
import org.intermine.api.profile.BagValue;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;

/**
 * Tests for marshalling lists to XML, as done when a user account is deleted
 * (DELETE /service/user exports the whole profile first).
 */
public class InterMineBagBindingTest extends InterMineAPITestCase
{
    public InterMineBagBindingTest(String arg) {
        super(arg);
    }

    public void testMarshalWithoutDescription() throws Exception {
        Profile profile = im.getProfileManager().getProfile("testUser");
        InterMineBag bag = profile.createBag("nodesc", "Company", null, im.getClassKeys());
        assertNull(bag.getDescription());

        String xml = new InterMineBagBinding().marshal(bag);

        assertTrue(xml, xml.contains("<bag name=\"nodesc\" type=\"Company\""));
        assertFalse(xml, xml.contains("description"));
    }

    public void testMarshalWithDescription() throws Exception {
        Profile profile = im.getProfileManager().getProfile("testUser");
        InterMineBag bag = profile.createBag("withdesc", "Company", "some words",
                im.getClassKeys());

        String xml = new InterMineBagBinding().marshal(bag);

        assertTrue(xml, xml.contains("description=\"some words\""));
    }

    /** A list value with no extra value (eg. a gene whose organism has no name) used to NPE. */
    public void testMarshalValueWithoutExtra() throws Exception {
        InterMineBag bag = new InterMineBag("noextra", "Company", null, new Date(),
                BagState.CURRENT, os, 1, uosw, Arrays.asList("name")) {
            @Override
            public List<BagValue> getContents() {
                return Collections.singletonList(new BagValue("CompanyA", null));
            }
        };

        String xml = new InterMineBagBinding().marshal(bag);

        assertTrue(xml, xml.contains("<bagValue value=\"CompanyA\" extra=\"\"/>"));
    }
}

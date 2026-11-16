package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.intermine.xml.full.Item;

import junit.framework.TestCase;
import psidev.psi.mi.jami.model.ModelledParticipant;
import psidev.psi.mi.jami.utils.RangeUtils;

import static org.intermine.bio.dataconversion.JamiTestModels.bind;
import static org.intermine.bio.dataconversion.JamiTestModels.byRefId;
import static org.intermine.bio.dataconversion.JamiTestModels.complexOf;
import static org.intermine.bio.dataconversion.JamiTestModels.itemsOf;
import static org.intermine.bio.dataconversion.JamiTestModels.participant;
import static org.intermine.bio.dataconversion.JamiTestModels.protein;

/**
 * What the PSI-MI XML of the swr1 complex does not have: ranges that are determined,
 * participants that come after the one an interaction is made for, stoichiometries.
 */
public class PsiComplexesProcessingTest extends TestCase
{
    private Set<Item> convert(psidev.psi.mi.jami.model.Complex complex) throws Exception {
        return new JamiTestModels().convert(complex);
    }

    /**
     * a binds b and b binds c: the detail of a-b is made when only a has been read.
     */
    public void testInteractionDetailListsAllInteractors() throws Exception {
        ModelledParticipant a = participant(protein("P00001"));
        ModelledParticipant b = participant(protein("P00002"));
        ModelledParticipant c = participant(protein("P00003"));
        bind(a, b, RangeUtils.createCertainRange(10, 20));
        bind(b, c, RangeUtils.createCertainRange(30, 40));
        Set<Item> items = convert(complexOf(a, b, c));

        Item complex = itemsOf(items, "Complex").get(0);
        Set<String> all = new HashSet<String>(complex.getCollection("allInteractors").getRefIds());
        assertEquals(3, all.size());
        List<Item> details = itemsOf(items, "InteractionDetail");
        assertEquals(2, details.size());
        for (Item detail : details) {
            assertEquals(all, new HashSet<String>(detail.getCollection("allInteractors")
                    .getRefIds()));
        }
    }

    /**
     * A determined range is a Location on the binder; an undetermined one (jami reads "?-?" as
     * 0..0) is no Location and no InteractionRegion.
     */
    public void testUndeterminedRangesAreSkipped() throws Exception {
        ModelledParticipant a = participant(protein("P00001"));
        ModelledParticipant b = participant(protein("P00002"));
        bind(a, b, RangeUtils.createCertainRange(10, 20), RangeUtils.createUndeterminedRange());
        Set<Item> items = convert(complexOf(a, b));

        List<Item> locations = itemsOf(items, "Location");
        assertEquals(1, locations.size());
        Item location = locations.get(0);
        assertEquals("10", location.getAttribute("start").getValue());
        assertEquals("20", location.getAttribute("end").getValue());
        Item locatedOn = byRefId(items, location.getReference("locatedOn").getRefId());
        assertEquals("P00002", locatedOn.getAttribute("primaryIdentifier").getValue());
        Item feature = byRefId(items, location.getReference("feature").getRefId());
        assertEquals("P00001", feature.getAttribute("primaryIdentifier").getValue());
        assertEquals(1, itemsOf(items, "InteractionRegion").size());
        // the interaction itself is there, undetermined range or not
        assertEquals(1, itemsOf(items, "Interaction").size());
        assertEquals(1, itemsOf(items, "InteractionDetail").size());
    }

    public void testOnlyUndeterminedRangesGiveNoRegion() throws Exception {
        ModelledParticipant a = participant(protein("P00001"));
        ModelledParticipant b = participant(protein("P00002"));
        bind(a, b, RangeUtils.createUndeterminedRange());
        Set<Item> items = convert(complexOf(a, b));

        assertEquals(0, itemsOf(items, "Location").size());
        assertEquals(0, itemsOf(items, "InteractionRegion").size());
        assertEquals(1, itemsOf(items, "InteractionDetail").size());
    }

    public void testStoichiometry() throws Exception {
        ModelledParticipant two = participant(protein("P00001"), 2, 2);
        ModelledParticipant range = participant(protein("P00002"), 1, 3);
        ModelledParticipant none = participant(protein("P00003"));
        Set<Item> items = convert(complexOf(two, range, none));

        List<Item> interactors = itemsOf(items, "Interactor");
        assertEquals(3, interactors.size());
        int found = 0;
        for (Item interactor : interactors) {
            Item protein = byRefId(items, interactor.getReference("participant").getRefId());
            String accession = protein.getAttribute("primaryAccession").getValue();
            if ("P00001".equals(accession)) {
                assertEquals("2", interactor.getAttribute("stoichiometry").getValue());
                found++;
            } else if ("P00002".equals(accession)) {
                // the largest of the range
                assertEquals("3", interactor.getAttribute("stoichiometry").getValue());
                found++;
            } else {
                assertNull(interactor.getAttribute("stoichiometry"));
                found++;
            }
        }
        assertEquals(3, found);
    }

    /**
     * jami gives a null interaction type for a complex that has none; that is no reason to stop
     * the load.
     */
    public void testComplexWithoutInteractionType() throws Exception {
        ModelledParticipant a = participant(protein("P00001"));
        ModelledParticipant b = participant(protein("P00002"));
        bind(a, b, RangeUtils.createCertainRange(1, 5));
        psidev.psi.mi.jami.model.Complex complex = complexOf(a, b);
        complex.setInteractionType(null);
        Set<Item> items = convert(complex);

        List<Item> details = itemsOf(items, "InteractionDetail");
        assertEquals(1, details.size());
        assertNull(details.get(0).getAttribute("relationshipType"));
        assertEquals("physical", details.get(0).getAttribute("type").getValue());
    }

    public void testRelationshipTypeIsTheInteractionType() throws Exception {
        ModelledParticipant a = participant(protein("P00001"));
        ModelledParticipant b = participant(protein("P00002"));
        bind(a, b, RangeUtils.createCertainRange(1, 5));
        Set<Item> items = convert(complexOf(a, b));

        assertEquals("physical association", itemsOf(items, "InteractionDetail").get(0)
                .getAttribute("relationshipType").getValue());
    }
}

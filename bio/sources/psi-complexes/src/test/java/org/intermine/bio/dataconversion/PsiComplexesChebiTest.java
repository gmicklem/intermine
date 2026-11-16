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

import java.util.List;
import java.util.Set;

import org.intermine.xml.full.Item;

import junit.framework.TestCase;
import psidev.psi.mi.jami.model.ModelledParticipant;

import static org.intermine.bio.dataconversion.JamiTestModels.complexOf;
import static org.intermine.bio.dataconversion.JamiTestModels.itemsOf;
import static org.intermine.bio.dataconversion.JamiTestModels.participant;
import static org.intermine.bio.dataconversion.JamiTestModels.protein;
import static org.intermine.bio.dataconversion.JamiTestModels.smallMolecule;

/**
 * The names of small molecules, from a local stand-in for the ChEBI REST API (ChebiNameLookupTest
 * tests the client itself).
 */
public class PsiComplexesChebiTest extends TestCase
{
    private RecordedChebiService chebi;
    private JamiTestModels models;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        chebi = new RecordedChebiService();
        models = new JamiTestModels();
        models.converter.setChebiUrl(chebi.url());
    }

    @Override
    protected void tearDown() throws Exception {
        chebi.stop();
        super.tearDown();
    }

    public void testSmallMoleculeGetsItsChebiName() throws Exception {
        ModelledParticipant a = participant(protein("P00001"));
        ModelledParticipant water = participant(smallMolecule("water", "CHEBI:15377"));
        ModelledParticipant glucose = participant(smallMolecule("glucose", "CHEBI:17925"));
        ModelledParticipant unknown = participant(smallMolecule("unknown", "CHEBI:99999999"));
        Set<Item> items = models.convert(complexOf(a, water, glucose, unknown));

        List<Item> molecules = itemsOf(items, "SmallMolecule");
        assertEquals(3, molecules.size());
        for (Item molecule : molecules) {
            String id = molecule.getAttribute("primaryIdentifier").getValue();
            if ("CHEBI:15377".equals(id)) {
                assertEquals("water", molecule.getAttribute("name").getValue());
            } else if ("CHEBI:17925".equals(id)) {
                assertEquals("alpha-D-glucose", molecule.getAttribute("name").getValue());
            } else {
                assertEquals("CHEBI:99999999", id);
                assertNull(molecule.getAttribute("name"));
            }
        }
        // proteins are not looked up
        assertNull(itemsOf(items, "Protein").get(0).getAttribute("name"));
        assertEquals(3, chebi.requests().size());
    }

    /**
     * A ChEBI that cannot be reached must not stop the load: the small molecules are stored
     * without a name.
     */
    public void testChebiDownDoesNotFailTheConversion() throws Exception {
        chebi.setForcedStatus(500);
        ModelledParticipant a = participant(protein("P00001"));
        ModelledParticipant water = participant(smallMolecule("water", "CHEBI:15377"));
        ModelledParticipant glucose = participant(smallMolecule("glucose", "CHEBI:17925"));
        Set<Item> items = models.convert(complexOf(a, water, glucose));

        assertEquals(1, itemsOf(items, "Complex").size());
        assertEquals(3, itemsOf(items, "Interactor").size());
        List<Item> molecules = itemsOf(items, "SmallMolecule");
        assertEquals(2, molecules.size());
        for (Item molecule : molecules) {
            assertNull(molecule.getAttribute("name"));
            assertNotNull(molecule.getAttribute("primaryIdentifier"));
        }
    }

    public void testChebiLookupCanBeSwitchedOff() throws Exception {
        models.converter.setChebiUrl("");
        ModelledParticipant water = participant(smallMolecule("water", "CHEBI:15377"));
        Set<Item> items = models.convert(complexOf(water));

        assertNull(itemsOf(items, "SmallMolecule").get(0).getAttribute("name"));
        assertEquals(0, chebi.requests().size());
    }
}

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

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.intermine.dataconversion.ItemsTestCase;
import org.intermine.dataconversion.MockItemWriter;
import org.intermine.metadata.Model;
import org.intermine.model.fulldata.Item;

public class PsiConverterTest extends ItemsTestCase
{

    PsiConverter converter;
    MockItemWriter itemWriter;

    public PsiConverterTest(String arg) {
        super(arg);
    }

    public void setUp() throws Exception {
        itemWriter = new MockItemWriter(new HashMap<String, Item>());
        converter = new PsiConverter(itemWriter,  Model.getInstanceByName("genomic"));
        converter.setIntactOrganisms("7227");
        converter.rslv = IdResolverService.getMockIdResolver("Gene");
        converter.rslv.addResolverEntry("7227", "FBgn001", Collections.singleton("FBgn001"));
        converter.rslv.addResolverEntry("7227", "FBgn002", Collections.singleton("FBgn002"));
        super.setUp();
    }

    public void testProcess() throws Exception {

        Reader reader = new InputStreamReader(getClass().getClassLoader()
                                            .getResourceAsStream("PsiConverterTest_src.xml"));
        converter.process(reader);
        converter.close();

        // uncomment to write out a new target items file
        //writeItemsFile(itemWriter.getItems(), "psi-tgt-items.xml");

        Set<org.intermine.xml.full.Item> expected = readItemSet("PsiConverterTest_tgt.xml");

        assertEquals(expected, itemWriter.getItems());
    }

    /**
     * InteractionExperiment.hostOrganism is the fullName of the experiment's hostOrganism, also
     * for "in vitro" (ncbiTaxId -1); a participant's own hostOrganismList must not replace it.
     * Cut from IntAct human.zip of 2026-10-24, see intact_host_organism_test.README.
     */
    public void testHostOrganism() throws Exception {
        processIntactFixture();

        Map<String, String> expected = new HashMap<String, String>();
        expected.put("wang-2011-4", "In vitro");
        expected.put("wang-2011-5", "Homo sapiens HEK293T embryonic kidney cell");

        Map<String, String> actual = new HashMap<String, String>();
        for (org.intermine.xml.full.Item item : itemWriter.getItems()) {
            if ("InteractionExperiment".equals(item.getClassName())) {
                String host = item.hasAttribute("hostOrganism")
                        ? item.getAttribute("hostOrganism").getValue() : null;
                actual.put(item.getAttribute("name").getValue(), host);
            }
        }
        assertEquals(expected, actual);
    }

    private void processIntactFixture() throws Exception {
        converter.setIntactOrganisms("9606");
        converter.rslv.addResolverEntry("9606", "ENSG00000003400",
                Collections.singleton("ENSG00000003400.17"));
        converter.rslv.addResolverEntry("9606", "ENSG00000101782",
                Collections.singleton("ENSG00000101782.16"));
        Reader reader = new InputStreamReader(getClass().getClassLoader()
                .getResourceAsStream("intact_host_organism_test.xml"));
        converter.process(reader);
        converter.close();
    }
}

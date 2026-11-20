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

    /**
     * IntAct states its data (PSI-MI XML, MITAB, ...) are CC BY 4.0; Apache 2.0 covers only its
     * software (https://www.ebi.ac.uk/intact/about#license_privacy, read 18 Nov 2026).
     */
    public void testLicence() throws Exception {
        processIntactFixture();

        String licence = null;
        for (org.intermine.xml.full.Item item : itemWriter.getItems()) {
            if ("DataSet".equals(item.getClassName())) {
                licence = item.getAttribute("licence").getValue();
            }
        }
        assertEquals("https://creativecommons.org/licenses/by/4.0/", licence);
    }

    /**
     * An experiment's bibref is written as a Publication only with the experiment, and an
     * experiment only when one of its interactions is kept. Here the entry keeps its two
     * experiments but none of its interactions (what a gene-panel cut of IntAct's file leaves),
     * so no experiment and no Publication are written: the paper stood in the mine with nothing
     * citing it (62 such PubMed ids on the 20 Nov 2026 HumanMine testbed).
     */
    public void testNoPublicationForAnExperimentThatIsNotStored() throws Exception {
        converter.setIntactOrganisms("9606");
        java.util.Scanner s = new java.util.Scanner(getClass().getClassLoader()
                .getResourceAsStream("intact_host_organism_test.xml"), "UTF-8").useDelimiter("\\A");
        String xml = s.next();
        s.close();
        xml = xml.replaceAll("(?s)<interactionList>.*</interactionList>", "");
        assertTrue(xml.contains("<bibref>"));
        converter.process(new java.io.StringReader(xml));
        converter.close();

        for (org.intermine.xml.full.Item item : itemWriter.getItems()) {
            assertFalse("unexpected " + item.getClassName(),
                    "InteractionExperiment".equals(item.getClassName())
                    || "Publication".equals(item.getClassName()));
        }
    }

    /**
     * When the experiments are stored, their one paper is written once and both cite it.
     */
    public void testPublicationOfStoredExperiments() throws Exception {
        processIntactFixture();

        String pubId = null;
        int pubs = 0;
        Set<String> cited = new java.util.HashSet<String>();
        for (org.intermine.xml.full.Item item : itemWriter.getItems()) {
            if ("Publication".equals(item.getClassName())) {
                pubs++;
                pubId = item.getIdentifier();
                assertEquals("21988832", item.getAttribute("pubMedId").getValue());
            } else if ("InteractionExperiment".equals(item.getClassName())) {
                cited.add(item.getReference("publication").getRefId());
            }
        }
        assertEquals(1, pubs);
        assertEquals(new java.util.HashSet<String>(Collections.singleton(pubId)), cited);
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

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

import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.intermine.dataconversion.ItemsTestCase;
import org.intermine.dataconversion.MockItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;
import org.intermine.xml.full.ItemFactory;


public class GoConverterTest extends ItemsTestCase
{
    private File goOboFile;
    Model model;
    GoConverter converter;
    MockItemWriter writer;

    public GoConverterTest(String arg) {
        super(arg);
    }

    public void setUp() throws Exception {
        model = Model.getInstanceByName("genomic");
        goOboFile = File.createTempFile("go-tiny", ".obo");
        Reader goOboReader = new InputStreamReader(
            getClass().getClassLoader().getResourceAsStream("go-tiny.obo"));
        writeTempFile(goOboFile, goOboReader);
        writer = new MockItemWriter(new LinkedHashMap<String, org.intermine.model.fulldata.Item>());
        converter = new GoConverter(writer, model);
        converter.setLicence("https://creativecommons.org/licenses/by/4.0/legalcode");

        converter.rslv = IdResolverService.getMockIdResolver("Gene");
        converter.rslv.addResolverEntry("7227", "FBgn0004168", Collections.singleton("FBgn0020002"));
        converter.rslv.addResolverEntry("7227", "FBgn0015567", Collections.singleton("FBgn0015567"));
        converter.rslv.addResolverEntry("7227", "FBgn0026430", Collections.singleton("FBgn0026430"));
        converter.rslv.addResolverEntry("7227", "FBgn0001612", Collections.singleton("FBgn0001612"));

        converter.rslv = IdResolverService.getMockIdResolver("Go");
        converter.rslv.addResolverEntry("0", "GO:1234567", Collections.singleton("GO:9999999"));
        converter.rslv.addResolverEntry("0", "GO:0000011:", Collections.singleton("GO:0000011"));
        converter.rslv.addResolverEntry("0", "GO:0000004", Collections.singleton("GO:0000004"));
        converter.rslv.addResolverEntry("0", "GO:0000005", Collections.singleton("GO:0000005"));
        converter.rslv.addResolverEntry("0", "GO:0000001", Collections.singleton("GO:0000001"));
    }

    private void writeTempFile(File outFile, Reader srcFileReader) throws Exception {
        FileWriter fileWriter = new FileWriter(outFile);
        int c;
        while ((c = srcFileReader.read()) > 0) {
            fileWriter.write(c);
        }
        fileWriter.close();
    }

    public void tearDown() throws Exception {
        goOboFile.delete();
    }

    public void testProcess() throws Exception {
        Reader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("synbio_test"));
        converter.process(reader);
        //System.out.println("productWrapperMap: " + converter.productMap.keySet());
        converter.close();

        // uncomment to write a new target items file
        //writeItemsFile(writer.getItems(), "go-tgt-items.xml");

        assertEquals(readItemSet("GoConverterOboTest_tgt.xml"), writer.getItems());
    }

    public void testCreateWithObjects() throws Exception {
        ItemFactory tgtItemFactory = new ItemFactory(Model.getInstanceByName("genomic"));
        Item organism = tgtItemFactory.makeItem("3_1", "Organism", "");
        organism.setAttribute("taxonId", "7227");

        Set<String> expected = new HashSet<String>();
        expected.add("4_1");
        expected.add("4_2");
        converter.initialiseMapsForFile();
        assertEquals(expected, new HashSet<String>(converter.createWithObjects(
                "FLYBASE:Grip84; FB:FBgn0026430, FLYBASE:l(1)dd4; FB:FBgn0001612",
                organism)));
    }

    /**
     * GAF 2.2 writes UniProtKB:ACC in column 8 (With/From). Each such entry should become a
     * Protein (primaryAccession ACC) in GOEvidence.with; other prefixes stay text only.
     * Rows cut from goa_human.gaf of 2026-05-21, see goa_human_with_test.README.
     */
    public void testWithUniProtKB() throws Exception {
        Reader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("goa_human_with_test.gaf"));
        converter.process(reader);
        converter.close();

        Map<String, Set<String>> expected = new HashMap<String, Set<String>>();
        expected.put("UniProtKB:Q8NFP7", new TreeSet<String>(Arrays.asList("Q8NFP7")));
        expected.put("UniProtKB:P41271-2", new TreeSet<String>(Arrays.asList("P41271-2")));
        expected.put("UniProtKB:O43914|UniProtKB:Q9UBK5",
                new TreeSet<String>(Arrays.asList("O43914", "Q9UBK5")));
        expected.put("UniProtKB-SubCell:SL-0086", new TreeSet<String>());
        expected.put("MGI:MGI:1929185", new TreeSet<String>());

        assertEquals(expected, withAccessions(writer.getItems()));
    }

    /**
     * The older spelling UniProt:ACC must still make a with Protein.
     */
    public void testCreateWithObjectsUniProt() throws Exception {
        ItemFactory tgtItemFactory = new ItemFactory(Model.getInstanceByName("genomic"));
        Item organism = tgtItemFactory.makeItem("3_1", "Organism", "");
        organism.setAttribute("taxonId", "9606");
        converter.initialiseMapsForFile();
        List<String> with = converter.createWithObjects("UniProt:P12345", organism);
        assertEquals(1, with.size());
        Item protein = null;
        for (Item item : writer.getItems()) {
            if (item.getIdentifier().equals(with.get(0))) {
                protein = item;
            }
        }
        assertEquals("Protein", protein.getClassName());
        assertEquals("P12345", protein.getAttribute("primaryAccession").getValue());
    }

    // withText of each GOEvidence -> primaryAccessions of the Proteins in its with collection
    private Map<String, Set<String>> withAccessions(Set<Item> items) {
        Map<String, Item> byId = new HashMap<String, Item>();
        for (Item item : items) {
            byId.put(item.getIdentifier(), item);
        }
        Map<String, Set<String>> result = new HashMap<String, Set<String>>();
        for (Item item : items) {
            if (!"GOEvidence".equals(item.getClassName()) || !item.hasAttribute("withText")) {
                continue;
            }
            Set<String> accessions = new TreeSet<String>();
            if (item.hasCollection("with")) {
                for (String refId : item.getCollection("with").getRefIds()) {
                    Item with = byId.get(refId);
                    assertEquals("Protein", with.getClassName());
                    accessions.add(with.getAttribute("primaryAccession").getValue());
                }
            }
            result.put(item.getAttribute("withText").getValue(), accessions);
        }
        return result;
    }
}

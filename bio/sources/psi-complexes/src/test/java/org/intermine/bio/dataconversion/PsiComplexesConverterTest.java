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

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.intermine.dataconversion.ItemsTestCase;
import org.intermine.dataconversion.MockItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.FullParser;
import org.intermine.xml.full.FullRenderer;
import org.intermine.xml.full.Item;

/**
 * Converts swr1_yeast-1.xml, a PSI-MI XML 2.5.4 file with one complex of 14 yeast proteins (the
 * Swr1 chromatin remodelling complex of IntAct / Complex Portal).
 */
public class PsiComplexesConverterTest extends ItemsTestCase
{
    public PsiComplexesConverterTest(String arg) {
        super(arg);
    }

    private Set<Item> convert() throws Exception {
        MockItemWriter itemWriter = new MockItemWriter(new HashMap());
        PsiComplexesConverter converter = new PsiComplexesConverter(itemWriter,
                Model.getInstanceByName("genomic"));
        converter.setComplexesSource("sgd");
        Reader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("swr1_yeast-1.xml"), "UTF-8");
        converter.process(reader);
        converter.close();
        return new HashSet<Item>(itemWriter.getItems());
    }

    public void testProcess() throws Exception {
        Set<Item> items = convert();

        // uncomment to write out a new target items file (and read the items before keeping it)
        //writeItemsFile(items, "PsiComplexesConverterTest_tgt.xml");

        // The participants are the point of the converter. If the XML binding of jami drops them
        // (see JamiParsingTest) only the Complex, DataSet, DataSource and Ontology items are left,
        // which is what this test used to accept.
        assertEquals(1, countItemByClass(items, "Complex"));
        assertEquals(14, countItemByClass(items, "Interactor"));
        assertEquals(14, countItemByClass(items, "Protein"));
        assertEquals(52, countItemByClass(items, "Interaction"));
        assertEquals(52, countItemByClass(items, "InteractionDetail"));
        assertEquals(52, countItemByClass(items, "InteractionRegion"));
        assertEquals(52, countItemByClass(items, "Location"));
        assertEquals(6, countItemByClass(items, "GOAnnotation"));
        assertEquals(6, countItemByClass(items, "GOTerm"));
        assertEquals(1, countItemByClass(items, "Publication"));
        assertEquals(1, countItemByClass(items, "Organism"));
        assertEquals(254, items.size());

        Item complex = null;
        for (Item item : items) {
            if ("Complex".equals(item.getClassName())) {
                complex = item;
            }
        }
        assertEquals("EBI-8790722", complex.getAttribute("identifier").getValue());
        assertEquals("Swr1 chromatin remodelling complex",
                complex.getAttribute("name").getValue());
        assertEquals("swr1:ruvb1:ruvb2:swc5:swc4:swc7:act1:arp4:bdf1:yaf9:arp6:swc3:vps71:vps72",
                complex.getAttribute("systematicName").getValue());
        assertEquals(14, complex.getCollection("allInteractors").getRefIds().size());

        // the Vps72 / Swc2 subunit, by its UniProt accession and its SGD identifier
        Item vps72 = null;
        for (Item item : items) {
            if ("Protein".equals(item.getClassName())
                    && "Q03388".equals(item.getAttribute("primaryAccession").getValue())) {
                vps72 = item;
            }
        }
        assertEquals("S000002893", vps72.getAttribute("primaryIdentifier").getValue());
        assertEquals("559292", getTaxonId(items, vps72.getReference("organism").getRefId()));

        // every item, with the references between them, as read by eye from a conversion. The
        // items are compared as they are written to the items XML (that has no empty collections).
        Set<Item> written = new LinkedHashSet<Item>(FullParser.parse(new ByteArrayInputStream(
                FullRenderer.render(items).getBytes("UTF-8"))));
        assertEquals(readItemSet("PsiComplexesConverterTest_tgt.xml"), written);
    }

    private String getTaxonId(Set<Item> items, String refId) {
        for (Item item : items) {
            if (refId.equals(item.getIdentifier())) {
                return item.getAttribute("taxonId").getValue();
            }
        }
        return null;
    }
}

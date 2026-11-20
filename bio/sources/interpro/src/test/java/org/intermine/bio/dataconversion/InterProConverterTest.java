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
import java.util.HashMap;
import java.util.Set;

import org.intermine.dataconversion.ItemsTestCase;
import org.intermine.dataconversion.MockItemWriter;
import org.intermine.metadata.Model;

public class InterProConverterTest extends ItemsTestCase
{
    public InterProConverterTest(String arg) {
        super(arg);
    }

    public void setUp() throws Exception {
        super.setUp();
    }

    public void testProcess() throws Exception {

        Reader reader = new InputStreamReader(getClass().getClassLoader()
                                              .getResourceAsStream("InterproConverterTest_src.xml"));

        MockItemWriter itemWriter = new MockItemWriter(new HashMap());
        InterProConverter converter = new InterProConverter(itemWriter,
                                                          Model.getInstanceByName("genomic"));
        converter.process(reader);
        converter.close();

        // uncomment to write out a new target items file
        //writeItemsFile(itemWriter.getItems(), "interpro-tgt-items.xml");

        Set expected = readItemSet("InterproConverterTest_tgt.xml");

        assertEquals(expected, itemWriter.getItems());
    }

    /**
     * interpro.xml names the member database "PFAM"; the DataSource is "Pfam", as Pfam, UniProt's
     * dbxref.txt, protein2ipr and the GO annotation files spell it (decision of Gos Micklem,
     * 20 Nov 2026), so that its CrossReferences share one DataSource with theirs. The other
     * member databases keep the name interpro.xml gives.
     */
    public void testPfamMemberDatabaseName() throws Exception {
        String xml = "<?xml version=\"1.0\"?>\n<interprodb>\n"
                + "  <interpro id=\"IPR000008\" type=\"Domain\" short_name=\"C2_dom\" protein_count=\"1\">\n"
                + "    <name>C2 domain</name>\n"
                + "    <member_list>\n"
                + "      <db_xref protein_count=\"1\" db=\"PFAM\" dbkey=\"PF00168\" name=\"C2\" />\n"
                + "      <db_xref protein_count=\"1\" db=\"SMART\" dbkey=\"SM00239\" name=\"C2\" />\n"
                + "    </member_list>\n"
                + "  </interpro>\n</interprodb>\n";
        MockItemWriter itemWriter = new MockItemWriter(new HashMap());
        InterProConverter converter = new InterProConverter(itemWriter,
                Model.getInstanceByName("genomic"));
        converter.process(new java.io.StringReader(xml));
        converter.close();

        java.util.Map<String, String> sourceNameById = new HashMap<String, String>();
        for (Object o : itemWriter.getItems()) {
            org.intermine.xml.full.Item item = (org.intermine.xml.full.Item) o;
            if ("DataSource".equals(item.getClassName())) {
                sourceNameById.put(item.getIdentifier(), item.getAttribute("name").getValue());
            }
        }
        java.util.Map<String, String> sourceOfXref = new HashMap<String, String>();
        for (Object o : itemWriter.getItems()) {
            org.intermine.xml.full.Item item = (org.intermine.xml.full.Item) o;
            if ("CrossReference".equals(item.getClassName())) {
                sourceOfXref.put(item.getAttribute("identifier").getValue(),
                        sourceNameById.get(item.getReference("source").getRefId()));
            }
        }
        assertEquals("Pfam", sourceOfXref.get("PF00168"));
        assertEquals("SMART", sourceOfXref.get("SM00239"));
        assertFalse(sourceNameById.containsValue("PFAM"));
    }
}

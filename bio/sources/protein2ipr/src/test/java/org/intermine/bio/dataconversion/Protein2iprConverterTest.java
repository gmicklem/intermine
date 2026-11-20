package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2014 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.intermine.dataconversion.ItemsTestCase;
import org.intermine.dataconversion.MockItemWriter;
import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.model.bio.Chromosome;
import org.intermine.model.bio.Organism;
import org.intermine.model.bio.Protein;
import org.intermine.model.fulldata.Item;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.ObjectStoreWriterFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.SingletonResults;
import org.intermine.util.DynamicUtil;
import org.intermine.xml.full.ItemFactory;

public class Protein2iprConverterTest extends ItemsTestCase
{
    Model model = Model.getInstanceByName("genomic");
    Protein2iprConverter converter;
    MockItemWriter itemWriter;
    private final String currentFile = "protein2ipr.dat";
    private ObjectStoreWriter osw;
    private Chromosome chromosome = null;
    private ItemFactory itemFactory;

    public Protein2iprConverterTest(String arg) {
        super(arg);
    }

    public void setUp() throws Exception {
        super.setUp();
        itemWriter = new MockItemWriter(new HashMap<String, Item>());
        converter = new Protein2iprConverter(itemWriter, model);
        converter.setOsAlias("os.bio-test");

        osw = ObjectStoreWriterFactory.getObjectStoreWriter("osw.bio-test");
        osw.getObjectStore().flushObjectById();
        model = Model.getInstanceByName("genomic");
        itemFactory = new ItemFactory(model);
        createProtein();
    }

    private void createProtein() throws ObjectStoreException {

        Organism organism = (Organism) DynamicUtil.createObject(Collections.singleton(Organism.class));
        organism.setId(Integer.valueOf(102));
        organism.setTaxonId("7227");
        osw.store(organism);

        Protein protein = (Protein) DynamicUtil.createObject(Collections.singleton(Protein.class));
        protein.setId(Integer.valueOf(101));
        protein.setPrimaryAccession("P02833");
        protein.setOrganism(organism);
        osw.store(protein);
    }

    public void tearDown() throws Exception {
        if (osw.isInTransaction()) {
            osw.abortTransaction();
        }
        Query q = new Query();
        QueryClass qc = new QueryClass(InterMineObject.class);
        q.addFrom(qc);
        q.addToSelect(qc);
        SingletonResults res = osw.getObjectStore().executeSingleton(q);

        Iterator resIter = res.iterator();
        osw.beginTransaction();
        while (resIter.hasNext()) {
            InterMineObject o = (InterMineObject) resIter.next();
            osw.delete(o);
        }
        osw.commitTransaction();
        osw.close();
    }

    public void testProcess() throws Exception {

        Reader reader = new InputStreamReader(getClass().getClassLoader()
                                            .getResourceAsStream(currentFile));
        converter.setCurrentFile(new File(currentFile));
        converter.setProtein2iprOrganisms("7227");
        converter.process(reader);
        converter.close();

        // uncomment to write out a new target items file
        //writeItemsFile(itemWriter.getItems(), "protein2ipr-tgt-items.xml");

        Set<org.intermine.xml.full.Item> expected = readItemSet("Protein2iprConverterTest_tgt.xml");

        assertEquals(expected, itemWriter.getItems());
    }

    /**
     * NCBIfam (NF...) and AntiFam (ANF...) signatures are loaded under their own database names;
     * a row with a prefix the converter does not know is skipped, not fatal.
     */
    public void testNcbifamAntifamAndUnknownPrefix() throws Exception {
        Protein protein = (Protein) DynamicUtil.createObject(Collections.singleton(Protein.class));
        protein.setId(Integer.valueOf(103));
        protein.setPrimaryAccession("A0A009PJF4");
        protein.setOrganism((Organism) osw.getObjectStore().getObjectById(Integer.valueOf(102)));
        osw.store(protein);

        String file = "protein2ipr_ncbifam.dat";
        Reader reader = new InputStreamReader(getClass().getClassLoader()
                                            .getResourceAsStream(file));
        converter.setCurrentFile(new File(file));
        converter.setProtein2iprOrganisms("7227");
        converter.process(reader);
        converter.close();

        Map<String, String> databases = new HashMap<String, String>();
        for (org.intermine.xml.full.Item item : itemWriter.getItems()) {
            if ("ProteinDomainRegion".equals(item.getClassName())) {
                databases.put(item.getAttribute("identifier").getValue(),
                        item.getAttribute("database").getValue());
            }
        }
        assertEquals("NCBIfam", databases.get("NF033085"));
        assertEquals("AntiFam", databases.get("ANF00001"));
        assertEquals("Pfam", databases.get("PF00144"));
        assertEquals("PROSITE", databases.get("PS00336"));
        assertEquals("Gene3D", databases.get("G3DSA:3.40.710.10"));
        assertEquals("SUPERFAMILY", databases.get("SSF56601"));
        assertEquals("PANTHER", databases.get("PTHR46825"));
        assertFalse("row with an unknown prefix must be skipped",
                databases.containsKey("XYZ00001"));
        assertEquals(7, databases.size());
    }

    public void testProcessNoOrganismSet() throws Exception {

        Reader reader = new InputStreamReader(getClass().getClassLoader()
                                            .getResourceAsStream(currentFile));
        converter.setCurrentFile(new File(currentFile));
        converter.process(reader);
        converter.close();

        Set<org.intermine.xml.full.Item> expected = readItemSet("Protein2iprConverterTest_tgt.xml");

        assertEquals(expected, itemWriter.getItems());
    }
}

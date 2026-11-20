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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.activation.DataSource;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.intermine.dataconversion.ItemsTestCase;
import org.intermine.metadata.Model;
import org.intermine.model.bio.Organism;
import org.intermine.objectstore.ObjectStore;
import org.intermine.xml.full.FullParser;

/**
 * Tests for DataSourceUpdater.
 */
public class DataSourceUpdaterTest extends ItemsTestCase
{
    public DataSourceUpdaterTest(String arg) {
        super(arg);
    }

    public void testDataSourceUpdaterTest() throws Exception {
        DataSourceUpdater converter = new TestDataSourceUpdater();
        converter.setOsAlias("os.bio-test");

        // Create temp file.
        File temp = File.createTempFile("DataSourceUpdater", ".tmp");
        // Delete temp file when program exits.
        temp.deleteOnExit();

        converter.setOutputFile(temp.getPath());
        
        File dbrefs = File.createTempFile("dbxrefs", "");
        FileOutputStream out = new FileOutputStream(dbrefs);
        IOUtils.copy(getClass().getClassLoader().getResourceAsStream("dbxref.txt"), out);
        out.close();
        dbrefs.deleteOnExit();
        
        converter.setDataSourceFile(dbrefs.getPath());
        converter.execute();

        Set expected = readItemSet("DataSourceUpdaterTest_tgt.xml");
        Collection actual = FullParser.parse(new FileInputStream(temp));

        Assert.assertEquals(expected, new HashSet(actual));
    }

    /**
     * Every kind of item the updater writes needs a primary key in update-data-sources_keys.properties,
     * made of attributes the item has: without a Publication key each run adds the papers the mine
     * already holds a second time.
     */
    public void testEveryWrittenItemHasAKey() throws Exception {
        DataSourceUpdater converter = new TestDataSourceUpdater();
        File temp = File.createTempFile("DataSourceUpdater", ".tmp");
        temp.deleteOnExit();
        converter.setOutputFile(temp.getPath());
        File dbrefs = File.createTempFile("dbxrefs", "");
        FileOutputStream out = new FileOutputStream(dbrefs);
        IOUtils.copy(getClass().getClassLoader().getResourceAsStream("dbxref.txt"), out);
        out.close();
        dbrefs.deleteOnExit();
        converter.setDataSourceFile(dbrefs.getPath());
        converter.execute();

        Properties keys = new Properties();
        InputStream in = getClass().getClassLoader()
                .getResourceAsStream("update-data-sources_keys.properties");
        assertNotNull("update-data-sources_keys.properties not found", in);
        keys.load(in);
        in.close();

        Collection<org.intermine.xml.full.Item> items = FullParser.parse(new FileInputStream(temp));
        Set<String> classes = new HashSet<String>();
        for (org.intermine.xml.full.Item item : items) {
            classes.add(item.getClassName());
            boolean keyed = false;
            for (String name : keys.stringPropertyNames()) {
                if (name.startsWith(item.getClassName() + ".key_")) {
                    keyed = true;
                    for (String field : keys.getProperty(name).split(",")) {
                        assertNotNull(item.getClassName() + " has no attribute " + field.trim()
                                + " for key " + name, item.getAttribute(field.trim()));
                    }
                }
            }
            assertTrue("no primary key for " + item.getClassName(), keyed);
        }
        assertTrue(classes.contains("Publication"));
        assertTrue(classes.contains("DataSource"));
        assertEquals("pubMedId", keys.getProperty("Publication.key_pubmed").trim());
    }

    /**
     * Two databases of dbxref.txt may cite one paper (release 2026_04: NDEx and CIViC both give
     * PubMed=28138153). The file is loaded in one go, and the loader does not merge two items of
     * the same load that share a key, so the updater must write one Publication per PubMed id
     * and point both DataSources at it: two items made two Publications with one pubMedId, and
     * update-publications then stopped on an item that matched both.
     */
    public void testOnePublicationPerPubMedIdWhenTwoDatabasesCiteIt() throws Exception {
        DataSourceUpdater converter = new TestDataSourceUpdater("NDEx", "CIViC");
        File temp = File.createTempFile("DataSourceUpdater", ".tmp");
        temp.deleteOnExit();
        converter.setOutputFile(temp.getPath());
        File dbrefs = File.createTempFile("dbxrefs", "");
        dbrefs.deleteOnExit();
        java.io.Writer w = new java.io.FileWriter(dbrefs);
        w.write("AC    : DB-0247\n"
                + "Abbrev: CIViC\n"
                + "Name  : Clinical Interpretation of Variants in Cancer\n"
                + "Ref   : Nat. Genet. 49:170-174(2017); PubMed=28138153; DOI=10.1038/ng.3774;\n"
                + "Server: https://civicdb.org/\n"
                + "\n"
                + "AC    : DB-0245\n"
                + "Abbrev: NDEx\n"
                + "Name  : The NDEx Project\n"
                + "Ref   : Nat. Genet. 49:170-174(2017); PubMed=28138153; DOI=10.1038/ng.3774;\n"
                + "Server: https://www.ndexbio.org/\n");
        w.close();
        converter.setDataSourceFile(dbrefs.getPath());
        converter.execute();

        Collection<org.intermine.xml.full.Item> items = FullParser.parse(new FileInputStream(temp));
        Map<String, String> pubIdByPubMedId = new HashMap<String, String>();
        int publications = 0;
        Set<String> cited = new HashSet<String>();
        for (org.intermine.xml.full.Item item : items) {
            if ("Publication".equals(item.getClassName())) {
                publications++;
                pubIdByPubMedId.put(item.getAttribute("pubMedId").getValue(), item.getIdentifier());
            } else if ("DataSource".equals(item.getClassName())) {
                cited.addAll(item.getCollection("publications").getRefIds());
            }
        }
        assertEquals("one Publication item for PubMed=28138153", 1, publications);
        assertEquals(new HashSet<String>(Collections.singleton(pubIdByPubMedId.get("28138153"))),
                cited);
    }

    class TestDataSourceUpdater extends DataSourceUpdater
    {
        private final Set<String> names = new HashSet<String>();

        public TestDataSourceUpdater(String... names) {
            super();
            setOsAlias("os.bio-test");
            setOutputFile("TestDataSourceUpdater_dummy");
            if (names.length == 0) {
                this.names.add("InterPro");
                this.names.add("UniProt");
            } else {
                this.names.addAll(java.util.Arrays.asList(names));
            }
        }

        protected Set getDataSources(ObjectStore os) {
            return new HashSet<String>(names);
        }
    }
}

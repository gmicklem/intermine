package org.intermine.bio.dataconversion;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.intermine.bio.util.OrganismRepository;
import org.intermine.sql.RowsResultSet;


/**
 * FlyBaseIdResolverFactory Unit Tests
 *
 * @author Fengyuan Hu
 *
 */
public class FlyBaseIdResolverFactoryTest extends TestCase {

    FlyBaseIdResolverFactory factory;
    List<String> resultHeader;
    List<List<Object>> dataList = new ArrayList<List<Object>>();
    ResultSet res;


    public FlyBaseIdResolverFactoryTest() {
    }

    public FlyBaseIdResolverFactoryTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        factory = new FlyBaseIdResolverFactory();
        IdResolverFactory.resolver = new IdResolver();

        // prepare mocked results
        resultHeader = new ArrayList<String>(Arrays.asList("uniquename",
                                                            "accession",
                                                            "abbreviation",
                                                            "name",
                                                            "is_current"));

        List<Object> line1 = new ArrayList<Object>();

        line1.add("pid1");
        line1.add("syn1");
        line1.add("Dmel");
        line1.add("FlyBase Annotation IDs");
        line1.add(Boolean.TRUE);

        dataList.add(line1);

        List<Object> line2 = new ArrayList<Object>();

        line2.add("pid2");
        line2.add("syn2");
        line2.add("Dmel");
        line2.add("FlyBase Annotation IDs");
        line2.add(Boolean.FALSE);

        dataList.add(line2);

        res = mockResultSet(resultHeader, dataList);
    }

    /**
    *
    * @param headers a list of strings
    * @param data a list of list of objects
    * @return ResultSet mocked ResultSet
    * @throws Exception
    */
   public ResultSet mockResultSet(List<String> headers, List<List<Object>> data) throws Exception {

       // empty set
       if (headers == null || data == null) {
           return RowsResultSet.create(new String[0], new Object[0][]);
       }

       // validation
       if (headers.size() != data.get(0).size()) {
           throw new Exception("column sizes are not equal");
       }

       Object[][] rows = new Object[data.size()][];
       for (int i = 0; i < rows.length; i++) {
           rows[i] = data.get(i).toArray();
       }
       return RowsResultSet.create(headers.toArray(new String[0]), rows);
   }

    public void testAddIdsFromResultSet() throws Exception {
        int count = factory.addIdsFromResultSet(res, OrganismRepository.getOrganismRepository(), "gene");
        assertEquals(2, count);
        assertEquals(1, IdResolverFactory.resolver.getTaxons().size());
        assertEquals("7227", IdResolverFactory.resolver.getTaxons().iterator().next());
        assertEquals(1, IdResolverFactory.resolver.getClassNames().size());
        assertEquals("gene", IdResolverFactory.resolver.getClassNames().iterator().next());
        assertTrue(IdResolverFactory.resolver.isPrimaryIdentifier("7227", "gene", "pid1"));
        assertFalse(IdResolverFactory.resolver.isPrimaryIdentifier("7227", "gene", "pid3"));
        assertEquals("pid1", IdResolverFactory.resolver.resolveId("7227", "gene", "syn1").iterator().next());

        try {
            IdResolverFactory.resolver.resolveId("7227", "exon", "syn3");
            fail("Expected to Fail to assert");
        } catch(IllegalArgumentException ex) {
            assertEquals("Catched exception", "exon IdResolver has no data for taxonId: '7227'.", ex.getMessage());
        }

        try {
            IdResolverFactory.resolver.resolveId("101", "gene", "syn1");
            fail("Expected to Fail to assert");
        } catch(IllegalArgumentException ex) {
            assertEquals("Catched exception", "gene IdResolver has no data for taxonId: '101'.", ex.getMessage());
        }
    }
}

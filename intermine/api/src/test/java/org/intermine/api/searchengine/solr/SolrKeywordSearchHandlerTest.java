package org.intermine.api.searchengine.solr;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.solr.client.solrj.SolrQuery;
import org.intermine.api.searchengine.KeywordSearchFacetData;
import org.intermine.api.searchengine.KeywordSearchFacetType;

import junit.framework.TestCase;

/**
 * Tests for the building of the Solr filter queries, no Solr server needed.
 */
public class SolrKeywordSearchHandlerTest extends TestCase
{
    private Vector<KeywordSearchFacetData> facets;

    @Override
    protected void setUp() {
        facets = new Vector<KeywordSearchFacetData>();
        facets.add(new KeywordSearchFacetData("Category", "Category",
                KeywordSearchFacetType.SINGLE));
        facets.add(new KeywordSearchFacetData("organism.shortName", "Organism",
                KeywordSearchFacetType.SINGLE));
    }

    public void testQuotePhrase() {
        assertEquals("\"H. sapiens\"", SolrKeywordSearchHandler.quotePhrase("H. sapiens"));
        assertEquals("\"a:b\"", SolrKeywordSearchHandler.quotePhrase("a:b"));
        assertEquals("\"(x) [y] {z} +-!~*?^&&||\"",
                SolrKeywordSearchHandler.quotePhrase("(x) [y] {z} +-!~*?^&&||"));
        assertEquals("\"say \\\"hi\\\"\"", SolrKeywordSearchHandler.quotePhrase("say \"hi\""));
        assertEquals("\"a\\\\b\"", SolrKeywordSearchHandler.quotePhrase("a\\b"));
        assertEquals("\"trailing \\\\\\\"\"",
                SolrKeywordSearchHandler.quotePhrase("trailing \\\""));
        assertEquals("\"\"", SolrKeywordSearchHandler.quotePhrase(""));
    }

    public void testFacetFilterQuery() {
        assertEquals("facet_organism.shortName:\"H. sapiens\"",
                SolrKeywordSearchHandler.facetFilterQuery("organism.shortName", "H. sapiens"));
    }

    public void testFilterQueriesForFacetWithSpace() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("organism.shortName", "H. sapiens");
        assertEquals(Arrays.asList("facet_organism.shortName:\"H. sapiens\""),
                SolrKeywordSearchHandler.buildFilterQueries(facets, values, null));
    }

    public void testFilterQueriesForSeveralFacets() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("organism.shortName", "H. sapiens");
        values.put("Category", "Gene: \"x\" (y)");
        assertEquals(Arrays.asList("facet_organism.shortName:\"H. sapiens\"",
                "facet_Category:\"Gene: \\\"x\\\" (y)\""),
                SolrKeywordSearchHandler.buildFilterQueries(facets, values,
                        Collections.<Integer>emptyList()));
    }

    public void testUnknownFacetsAndNullsAreIgnored() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("id", "1 OR *:*");
        values.put("Category", null);
        values.put(null, "x");
        assertTrue(SolrKeywordSearchHandler.buildFilterQueries(facets, values, null).isEmpty());
        assertTrue(SolrKeywordSearchHandler.buildFilterQueries(facets, null, null).isEmpty());
    }

    public void testFilterQueryForIds() {
        List<Integer> ids = Arrays.asList(1000001, 22, 3);
        assertEquals(Arrays.asList("{!terms f=id}1000001,22,3"),
                SolrKeywordSearchHandler.buildFilterQueries(facets,
                        Collections.<String, String>emptyMap(), ids));
    }

    public void testFilterQueriesReachSolrQuery() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("organism.shortName", "H. sapiens");
        SolrQuery query = new SolrQuery("*");
        for (String fq : SolrKeywordSearchHandler.buildFilterQueries(facets, values,
                Arrays.asList(5))) {
            query.addFilterQuery(fq);
        }
        assertEquals(Arrays.asList("facet_organism.shortName:\"H. sapiens\"",
                "{!terms f=id}5"), Arrays.asList(query.getFilterQueries()));
    }
}

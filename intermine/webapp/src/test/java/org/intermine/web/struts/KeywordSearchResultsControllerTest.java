package org.intermine.web.struts;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import org.intermine.api.searchengine.KeywordSearchFacetData;
import org.intermine.api.searchengine.KeywordSearchFacetType;
import org.junit.Test;

public class KeywordSearchResultsControllerTest {

    private Map<String, String> facetValues(Map<String, String[]> parameters) {
        Vector<KeywordSearchFacetData> facets = new Vector<KeywordSearchFacetData>();
        facets.add(new KeywordSearchFacetData("Category", "Category",
                KeywordSearchFacetType.SINGLE));
        facets.add(new KeywordSearchFacetData("organism.shortName", "Organism",
                KeywordSearchFacetType.SINGLE));
        HttpServletRequest request = createMock(HttpServletRequest.class);
        expect(request.getParameter("searchSubmit")).andReturn(null).anyTimes();
        expect(request.getParameter("searchSubmitRestricted")).andReturn(null).anyTimes();
        expect(request.getParameterMap()).andReturn(parameters).anyTimes();
        replay(request);
        return KeywordSearchResultsController.getFacetValues(request, facets);
    }

    @Test
    public void facetValuesAreNotHtmlEscaped() {
        Map<String, String[]> parameters = new HashMap<String, String[]>();
        parameters.put("facet_Category", new String[] {"Gene & <Protein> \"x\" 'y'"});
        Map<String, String> values = facetValues(parameters);
        // the value goes to the Solr filter and back into links: it must be what the user clicked
        assertEquals("Gene & <Protein> \"x\" 'y'", values.get("Category"));
    }

    @Test
    public void onlyConfiguredFacetsAreKept() {
        Map<String, String[]> parameters = new HashMap<String, String[]>();
        parameters.put("facet_organism.shortName", new String[] {"H. sapiens"});
        parameters.put("facet_Unknown", new String[] {"x"});
        parameters.put("facet_Category", new String[] {" "});
        Map<String, String> values = facetValues(parameters);
        assertEquals(1, values.size());
        assertEquals("H. sapiens", values.get("organism.shortName"));
        assertFalse(values.containsKey("Unknown"));
    }
}

package org.intermine.bio.web.displayer;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.intermine.api.InterMineAPI;
import org.intermine.api.query.PathQueryExecutor;
import org.intermine.metadata.Model;
import org.intermine.model.InterMineObject;
import org.intermine.pathquery.PathQuery;
import org.junit.Test;

public class MetabolicGeneSummaryDisplayerTest {

    @Test
    public void testHasAtlasExpression() {
        assertFalse(MetabolicGeneSummaryDisplayer.hasAtlasExpression(
                Model.getInstanceByName("genesummary")));
        assertTrue(MetabolicGeneSummaryDisplayer.hasAtlasExpression(
                Model.getInstanceByName("genesummaryatlas")));
    }

    /** A count query over paths that the mine's model lacks is left out, not run and logged. */
    @Test
    public void testCountsOnlyQueriesThatAreValidForTheModel() throws Exception {
        Model model = Model.getInstanceByName("genesummary");
        InterMineAPI im = createMock(InterMineAPI.class);
        PathQueryExecutor executor = createMock(PathQueryExecutor.class);
        HttpServletRequest request = createMock(HttpServletRequest.class);
        HttpSession session = createMock(HttpSession.class);
        InterMineObject gene = createMock(InterMineObject.class);
        expect(request.getSession()).andReturn(session);
        expect(session.getAttribute("PROFILE")).andReturn(null);
        expect(im.getPathQueryExecutor(null)).andReturn(executor);
        PathQuery valid = new PathQuery(model);
        valid.addView("Gene.pathways.name");
        PathQuery invalid = new PathQuery(model);
        invalid.addView("Gene.goAnnotation.ontologyTerm.name");
        // only the valid one reaches the executor
        expect(executor.count(valid)).andReturn(7);
        replay(im, executor, request, session, gene);

        MetabolicGeneSummaryDisplayer displayer = new MetabolicGeneSummaryDisplayer(null, im);
        MetabolicGeneSummaryDisplayer.GeneSummary summary =
                displayer.new GeneSummary(gene, request);
        summary.addCollectionCount("Pathways", "x", valid, "pathways");
        summary.addCollectionCount("Gene Ontology", "x", invalid, "GeneOntologyDisplayer");

        assertEquals(1, summary.getFields().size());
        assertEquals(7, summary.getFields().get("Pathways").get("data"));
    }
}

package org.intermine.web.logic.widget;

import java.util.List;
import java.util.Map;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import org.intermine.api.profile.InterMineBag;
import org.intermine.pathquery.Constraints;
import org.intermine.pathquery.PathConstraint;
import org.intermine.pathquery.PathQuery;
import org.intermine.web.logic.widget.config.EnrichmentWidgetConfig;
import org.intermine.web.logic.widget.config.GraphWidgetConfig;
import org.intermine.web.logic.widget.config.WidgetConfig;
import org.junit.Ignore;

public class GraphWidgetTest extends WidgetConfigTestCase
{
    private final class GraphOptions implements WidgetOptions {
        @Override
        public String getFilter() {
            return null;
        }
    }
    private GraphWidget widget;
    private InterMineBag bag;
    private WidgetConfig config;
    private WidgetOptions options;

    public void setUp() throws Exception {
        super.setUp();
        config = webConfig.getWidgets().get("age_groups");
        InterMineBag employeeList = createEmployeeLongList();
        bag = employeeList;
        options = new GraphOptions();
        widget = new GraphWidget((GraphWidgetConfig) config, bag, os, options, null);
    }

    public void testContraintIntegerType() throws Exception {
        widget.process();
        List<List<Object>> results = widget.getResults();

        //first element contain the range label Count
        //[25][1]
        assertEquals("25", results.get(1).get(0).toString());
        assertEquals("1", results.get(1).get(1).toString());
        //[35][1]
        assertEquals("35", results.get(2).get(0).toString());
        assertEquals("2", results.get(2).get(1).toString());
        //[50][1]
        assertEquals("50", results.get(3).get(0).toString());
        assertEquals("1", results.get(3).get(1).toString());
    }

   public void testGetPathQuery() {
        PathQuery q = new PathQuery(os.getModel());
        q.addView("Employee.name");
        q.addView("Employee.age");
        q.addView("Employee.fullTime");
        q.addView("Employee.department.name");
        // bag constraint
        q.addConstraint(Constraints.in(config.getStartClass(), bag.getName()));
        q.addConstraint(Constraints.eq("Employee.age", "%category"));
        q.addConstraint(Constraints.neq("Employee.age", "40"));
        PathQuery widgetPathQuery = widget.getPathQuery();
        assertEquals(q, widgetPathQuery);
    }

    public void testCreatePathQueryView() {
        PathQuery pathQuery = new PathQuery(os.getModel());
        pathQuery.addView("Employee.name");
        pathQuery.addView("Employee.age");
        pathQuery.addView("Employee.fullTime");
        pathQuery.addView("Employee.department.name");
        assertEquals(pathQuery, widget.createPathQueryView(os,
                webConfig.getWidgets().get(("age_groups"))));
    }

    public void testValidateBagType() throws Exception {
        InterMineBag companyList = createCompanyList();
        try {
            widget = new GraphWidget((GraphWidgetConfig) config, companyList, os, options, null);
            widget.process();
            fail("Should raise a IllegalArgumentException");
        } catch (IllegalArgumentException iae){
        }
    }

    private final class FilterOptions implements WidgetOptions {
        private final String value;

        private FilterOptions(String value) {
            this.value = value;
        }

        @Override
        public String getFilter() {
            return value;
        }
    }

    private List<List<Object>> processFilteredWidget(String filterValue) throws Exception {
        GraphWidgetConfig filtered =
                (GraphWidgetConfig) webConfig.getWidgets().get("age_groups_by_department");
        GraphWidget w = new GraphWidget(filtered, bag, os, new FilterOptions(filterValue), null);
        w.process();
        return w.getResults();
    }

    /**
     * A list for which there is no value to filter by (e.g. none of its genes has an organism
     * name) used to fail with "Cannot create a QueryValue with null" [im-jqz.44]. The filter is
     * now left out and the chart is drawn for the whole list.
     */
    public void testNullFilterIsIgnored() throws Exception {
        List<List<Object>> results = processFilteredWidget(null);
        // header row, then ages 25, 35, 40, 50
        assertEquals(5, results.size());
        assertEquals("25", results.get(1).get(0).toString());
        assertEquals("1", results.get(1).get(1).toString());
        assertEquals("35", results.get(2).get(0).toString());
        assertEquals("2", results.get(2).get(1).toString());
    }

    public void testFilterStillApplied() throws Exception {
        assertEquals(5, processFilteredWidget("department").size());
        // no employee is in this department: only the header row is left
        assertEquals(1, processFilteredWidget("no such department").size());
    }
}

package org.intermine.api.xml;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.Date;
import java.util.HashMap;

import javax.xml.stream.XMLOutputFactory;

import org.intermine.api.InterMineAPITestCase;
import org.intermine.api.profile.InterMineBag;
import org.intermine.api.profile.Profile;
import org.intermine.api.profile.SavedQuery;
import org.intermine.api.template.TemplateHelper;
import org.intermine.pathquery.PathQuery;
import org.intermine.pathquery.PathQueryBinding;
import org.intermine.template.TemplateQuery;

/**
 * The methods that marshal to a String must give the same text whichever StAX implementation
 * XMLOutputFactory.newInstance() finds: the JDK's, or Woodstox, which solr-solrj puts on the
 * class path of this module and of every web application. Woodstox buffers its output, so a
 * method that did not flush the XMLStreamWriter returned an empty String (im-ee7.67; until then
 * the build forced the JDK implementation with a system property).
 */
public class StaxImplementationTest extends InterMineAPITestCase
{
    private static final String JDK = "com.sun.xml.internal.stream.XMLOutputFactoryImpl";
    private static final String WOODSTOX = "com.ctc.wstx.stax.WstxOutputFactory";
    private static final String PROPERTY = XMLOutputFactory.class.getName();

    private String savedProperty;

    public StaxImplementationTest(String arg) {
        super(arg);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        savedProperty = System.getProperty(PROPERTY);
    }

    @Override
    public void tearDown() throws Exception {
        if (savedProperty == null) {
            System.clearProperty(PROPERTY);
        } else {
            System.setProperty(PROPERTY, savedProperty);
        }
        super.tearDown();
    }

    public void testJdkImplementation() throws Exception {
        marshalEverything(JDK);
    }

    public void testWoodstox() throws Exception {
        marshalEverything(WOODSTOX);
    }

    /** With no property, the implementation found on this module's class path is Woodstox. */
    public void testDefaultImplementationIsWoodstox() throws Exception {
        System.clearProperty(PROPERTY);
        assertEquals(WOODSTOX, XMLOutputFactory.newInstance().getClass().getName());
        marshalEverything(null);
    }

    private void marshalEverything(String implementation) throws Exception {
        if (implementation != null) {
            System.setProperty(PROPERTY, implementation);
            assertEquals(implementation, XMLOutputFactory.newInstance().getClass().getName());
        }

        Profile profile = im.getProfileManager().getProfile("testUser");
        InterMineBag bag = profile.createBag("staxbag", "Company", "some words",
                im.getClassKeys());
        String xml = new InterMineBagBinding().marshal(bag);
        assertTrue(xml, xml.contains("<bag name=\"staxbag\" type=\"Company\""));
        assertTrue(xml, xml.endsWith("</bag>") || xml.endsWith("/>"));

        PathQuery pq = new PathQuery(os.getModel());
        pq.addView("Company.name");
        xml = PathQueryBinding.marshal(pq, "staxquery", os.getModel().getName(),
                PathQuery.USERPROFILE_VERSION);
        assertTrue(xml, xml.contains("<query name=\"staxquery\""));
        assertTrue(xml, xml.endsWith("/>") || xml.endsWith("</query>"));

        xml = SavedQueryBinding.marshal(new SavedQuery("staxsaved", new Date(1124276877010L), pq),
                PathQuery.USERPROFILE_VERSION);
        assertTrue(xml, xml.contains("<saved-query name=\"staxsaved\""));
        assertTrue(xml, xml.endsWith("</saved-query>"));

        xml = new SharedBagBinding().marshal(profile);
        assertTrue(xml, xml.contains("shared-bags"));

        xml = TemplateHelper.templateMapToXml(new HashMap<String, TemplateQuery>(),
                PathQuery.USERPROFILE_VERSION);
        assertTrue(xml, xml.contains("template-queries"));
    }
}

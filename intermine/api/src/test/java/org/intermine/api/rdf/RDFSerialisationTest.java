package org.intermine.api.rdf;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.StringWriter;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.intermine.metadata.ClassDescriptor;

import junit.framework.TestCase;

/**
 * Pins the RDF text the web services produce (Model.write(writer, "N-TRIPLE" or "RDF/XML"), see RDFOutput): the
 * N-Triples form is the RIOT writer's, which Jena uses only while jena-arq is on the class path, so this test fails
 * if the dependency on jena-arq is lost or a Jena upgrade changes the serialisation. Non-ASCII characters are written
 * as Java unicode escapes so that the result does not depend on the compiler's source encoding.
 */
public class RDFSerialisationTest extends TestCase
{
    private Model jenaModel;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        org.intermine.metadata.Model imModel =
                org.intermine.metadata.Model.getInstanceByName("testmodel");
        ClassDescriptor employee =
                imModel.getClassDescriptorByName("org.intermine.model.testmodel.Employee");
        jenaModel = ModelFactory.createDefaultModel();
        jenaModel.setNsPrefix("im", RDFHelper.VOC_NAMESPACE);
        Resource resource = RDFHelper.createResource("http://example.org/mine/employee:1",
                employee, jenaModel);
        resource.addProperty(RDFHelper.createIMProperty(employee.getFieldDescriptorByName("name")),
                "caf\u00e9 \"quoted\" \\ back\nnew <&> \u4e2d\u6587");
        resource.addProperty(RDFHelper.createIMProperty(employee.getFieldDescriptorByName("age")),
                "35");
    }

    private String write(String format) {
        StringWriter writer = new StringWriter();
        jenaModel.write(writer, format);
        return writer.toString();
    }

    public void testNTriples() {
        String nt = write("N-TRIPLE");
        String subject = "<http://example.org/mine/employee:1> ";
        String voc = "<" + RDFHelper.VOC_NAMESPACE;
        assertTrue(nt, nt.contains(subject + voc + "hasAge> \"35\" .\n"));
        assertTrue(nt, nt.contains(subject + voc + "hasName> \"caf\u00e9 \\\"quoted\\\" \\\\ back"
                + "\\nnew <&> \u4e2d\u6587\" .\n"));
        assertTrue(nt, nt.contains(subject + "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> "
                + voc + "Employee> .\n"));
        assertFalse(nt, nt.contains("^^"));
    }

    public void testRdfXml() {
        String xml = write("RDF/XML");
        assertTrue(xml, xml.contains("xmlns:im=\"" + RDFHelper.VOC_NAMESPACE + "\">"));
        assertTrue(xml, xml.contains("<im:Employee rdf:about="
                + "\"http://example.org/mine/employee:1\">"));
        assertTrue(xml, xml.contains("<im:hasAge>35</im:hasAge>"));
        assertTrue(xml, xml.contains("<im:hasName>caf\u00e9 \"quoted\" \\ back\nnew &lt;&amp;&gt; "
                + "\u4e2d\u6587</im:hasName>"));
    }
}

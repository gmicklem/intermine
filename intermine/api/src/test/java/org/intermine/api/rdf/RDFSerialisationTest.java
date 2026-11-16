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

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.intermine.metadata.AttributeDescriptor;
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

    private static final String SO = "http://purl.obolibrary.org/obo/SO_0000704";

    private ClassDescriptor classWithTerm(String term) {
        return new ClassDescriptor("org.intermine.model.testmodel.Thing", null, true,
                Collections.<AttributeDescriptor>emptySet(), Collections.emptySet(),
                Collections.emptySet(), term);
    }

    public void testExpandTerm() {
        assertEquals(SO, RDFHelper.expandTerm("obo:SO:0000704"));
        assertEquals(SO, RDFHelper.expandTerm("obo:SO_0000704"));
        assertEquals(SO, RDFHelper.expandTerm("http://purl.obolibrary.org/obo/SO:0000704"));
        assertEquals(SO, RDFHelper.expandTerm(" " + SO + " "));
        assertEquals("http://edamontology.org/data_0966",
                RDFHelper.expandTerm("edam:data_0966"));
        assertEquals("http://purl.org/dc/terms/title",
                RDFHelper.expandTerm("http://purl.org/dc/terms/title"));
        assertNull(RDFHelper.expandTerm(null));
        assertNull(RDFHelper.expandTerm(""));
        assertNull(RDFHelper.expandTerm("  "));
        assertNull(RDFHelper.expandTerm("GO:0000001"));
        assertNull(RDFHelper.expandTerm("not a term"));
    }

    public void testExpandTermsDropsBlanksAndDuplicates() {
        assertEquals(Arrays.asList(SO, "http://semanticscience.org/resource/SIO_010525"),
                RDFHelper.expandTerms(",http://purl.obolibrary.org/obo/SO_0000704,"
                        + "obo:SO:0000704,http://purl.obolibrary.org/obo/SO:0000704,"
                        + "http://semanticscience.org/resource/SIO_010525"));
        assertTrue(RDFHelper.expandTerms("").isEmpty());
        assertTrue(RDFHelper.expandTerms(null).isEmpty());
    }

    /**
     * Class terms that used to give a 500 in RDF/XML: an empty term (GOTerm "<>"), a leading comma
     * (Pathway) and a CURIE or OBO id with a colon in the local name (Gene).
     */
    public void testAllFormatsWithBadClassTerms() {
        String[] terms = {"", ",http://semanticscience.org/resource/SIO_010525", null,
            "obo:SO:0000704,http://purl.obolibrary.org/obo/SO:0000704"};
        for (String term : terms) {
            Model model = ModelFactory.createDefaultModel();
            model.setNsPrefix("im", RDFHelper.VOC_NAMESPACE);
            model.setNsPrefix("obo", "http://purl.obolibrary.org/obo/");
            RDFHelper.createResource("http://example.org/mine/thing:1", classWithTerm(term),
                    model);
            long expected = RDFHelper.expandTerms(term).size() + 1;
            assertEquals(term, expected, model.size());
            for (String format : new String[] {"RDF/XML", "RDF/XML-ABBREV", "N-TRIPLE",
                "TURTLE", "N3"}) {
                StringWriter writer = new StringWriter();
                model.write(writer, format);
                Model read = ModelFactory.createDefaultModel();
                read.read(new StringReader(writer.toString()), null,
                        "N3".equals(format) ? "TURTLE" : format);
                assertTrue(format + " of '" + term + "'", read.isIsomorphicWith(model));
            }
        }
    }

    public void testOboTermIsExpandedInEveryFormat() {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("obo", "http://purl.obolibrary.org/obo/");
        RDFHelper.createResource("http://example.org/mine/gene:1",
                classWithTerm("obo:SO:0000704"), model);
        StringWriter nt = new StringWriter();
        model.write(nt, "N-TRIPLE");
        assertTrue(nt.toString(), nt.toString().contains("<http://example.org/mine/gene:1> "
                + "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <" + SO + "> .\n"));
        StringWriter xml = new StringWriter();
        model.write(xml, "RDF/XML");
        assertTrue(xml.toString(), xml.toString().contains("rdf:resource=\"" + SO + "\"")
                || xml.toString().contains("<obo:SO_0000704"));
        assertFalse(xml.toString(), xml.toString().contains("SO:0000704"));
    }
}

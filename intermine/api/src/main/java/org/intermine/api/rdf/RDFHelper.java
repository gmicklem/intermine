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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.intermine.metadata.AttributeDescriptor;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.FieldDescriptor;
import org.intermine.metadata.ReferenceDescriptor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for RDF generation
 * @author Daniela Butano
 */
public final class RDFHelper
{
    /**
     * InterMine vocabulary namespace
     */
    public static final String VOC_NAMESPACE = Namespaces.getNamespaces().get("im");

    /** namespace of the OBO library, whose local names use '_' where the OBO ids use ':' */
    private static final String OBO_NAMESPACE = Namespaces.getNamespaces().get("obo");

    private static final Logger LOG = Logger.getLogger(RDFHelper.class);

    /**
     * default constructor
     */
    private RDFHelper() {
        //hidden
    }

    /**
     * Create a RDF property given the attribute
     * @param fieldDescriptor the attribute
     * @return the RDF property
     */
    public static final Property createIMProperty(FieldDescriptor fieldDescriptor) {
        return ResourceFactory.createProperty(VOC_NAMESPACE,
                "has" + StringUtils.capitalize(fieldDescriptor.getName()));
    }

    /**
     * Create a RDF property given the attribute
     * @param classDescriptor the class
     * @return the RDF resource
     */
    public static final Resource createIMResource(ClassDescriptor classDescriptor) {
        return ResourceFactory.createResource(VOC_NAMESPACE + classDescriptor.getSimpleName());
    }

    /**
     * Create a RDF resource ans setting types
     * @param  resourceURI for the subject
     * @param classDescriptor the class
     * @param model the jena model
     * @return the RDF resource
     */
    public static final Resource createResource(String resourceURI,
                ClassDescriptor classDescriptor, Model model) {
        Resource resource = model.createResource(resourceURI,
                RDFHelper.createIMResource(classDescriptor));
        for (String term : getClassTerms(classDescriptor)) {
            resource = model.createResource(resourceURI, model.createResource(term));
        }
        return resource;
    }

    /**
     * Create a RDF property given the attribute
     * @param attributeDescriptor the field Descriptor
     * @return the RDF property
     */
    public static final Property createProperty(AttributeDescriptor attributeDescriptor) {
        List<String> terms = expandTerms(attributeDescriptor.getOntologyTerm());
        return terms.isEmpty() ? createIMProperty(attributeDescriptor)
                : ResourceFactory.createProperty(terms.get(0));
    }

    /**
     * Create a RDF property given a reference
     * @param referenceDescriptor the field Descriptor
     * @return the RDF property
     */
    public static final Property createProperty(ReferenceDescriptor referenceDescriptor) {
        List<String> terms = expandTerms(referenceDescriptor.getOntologyTerm());
        return terms.isEmpty() ? createIMProperty(referenceDescriptor)
                : ResourceFactory.createProperty(terms.get(0));
    }

    /**
     * The ontology terms of a class as valid absolute IRIs, see {@link #expandTerms(String)}.
     * @param classDescriptor the class
     * @return the distinct IRIs, in model order, empty if the class has no usable term
     */
    public static List<String> getClassTerms(ClassDescriptor classDescriptor) {
        return expandTerms(classDescriptor.getOntologyTerm());
    }

    /**
     * Turn the comma separated "term" value of a model element into absolute IRIs that every RDF
     * serialisation accepts. Blank entries are dropped, a CURIE with a prefix from
     * prefix_namespaces.properties (obo:SO_0000704) is expanded to its namespace, and in the OBO
     * namespace the ':' of an OBO id is replaced by '_' (SO:0000704 becomes SO_0000704, as in
     * the OBO PURLs) because ':' is not allowed in the local name of an XML QName. Entries that
     * are neither absolute IRIs nor known CURIEs are ignored with a warning.
     * @param terms the value of the model's term attribute, may be null
     * @return the distinct IRIs, in order, never null
     */
    public static List<String> expandTerms(String terms) {
        Set<String> expanded = new LinkedHashSet<>();
        if (terms != null) {
            for (String term : terms.split(",")) {
                String iri = expandTerm(term);
                if (iri != null) {
                    expanded.add(iri);
                }
            }
        }
        return new ArrayList<>(expanded);
    }

    /**
     * Expand one term, see {@link #expandTerms(String)}.
     * @param term a full IRI or a CURIE
     * @return the IRI, or null if the term is blank or cannot be made into an absolute IRI
     */
    public static String expandTerm(String term) {
        String t = StringUtils.trimToNull(term);
        if (t == null) {
            return null;
        }
        String iri = null;
        int colon = t.indexOf(':');
        if (t.contains("://") || t.startsWith("urn:")) {
            iri = t;
        } else if (colon > 0) {
            String namespace = Namespaces.getNamespaces().get(t.substring(0, colon));
            if (namespace != null) {
                iri = namespace + t.substring(colon + 1);
            }
        }
        if (iri == null || iri.contains(" ")) {
            LOG.warn("Ignoring ontology term that is not a valid IRI or known CURIE: " + t);
            return null;
        }
        if (OBO_NAMESPACE != null && iri.startsWith(OBO_NAMESPACE)) {
            iri = OBO_NAMESPACE + iri.substring(OBO_NAMESPACE.length()).replace(':', '_');
        }
        return iri;
    }
}

package org.intermine.web.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.Model;
import org.junit.Test;

public class KeywordSearchResultTest {

    private final Model model = Model.getInstanceByName("testmodel");

    private ClassDescriptor cd(String name) {
        return model.getClassDescriptorByName("org.intermine.model.testmodel." + name);
    }

    @Test
    public void attributeAndInheritedAttributeAreInModel() {
        assertTrue(KeywordSearchResult.isFieldInModel(cd("Employee"), "name"));
        // age is declared on Employee, seniority on Manager (a subclass of Employee)
        assertTrue(KeywordSearchResult.isFieldInModel(cd("Manager"), "age"));
    }

    @Test
    public void pathThroughReferenceIsInModel() {
        assertTrue(KeywordSearchResult.isFieldInModel(cd("Employee"), "department.name"));
        assertTrue(KeywordSearchResult.isFieldInModel(cd("Employee"), "department.company.name"));
    }

    @Test
    public void missingFieldIsNotInModel() {
        // the GWAS.firstAuthor case: web config names a field the model does not have
        assertFalse(KeywordSearchResult.isFieldInModel(cd("Employee"), "firstAuthor"));
        assertFalse(KeywordSearchResult.isFieldInModel(cd("Employee"), "department.firstAuthor"));
        assertFalse(KeywordSearchResult.isFieldInModel(cd("Employee"), "noSuchReference.name"));
    }

    @Test
    public void pathThroughAnAttributeIsNotInModel() {
        assertFalse(KeywordSearchResult.isFieldInModel(cd("Employee"), "name.length"));
    }
}

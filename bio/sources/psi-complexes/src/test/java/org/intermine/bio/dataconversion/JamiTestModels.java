package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.intermine.dataconversion.MockItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

import psidev.psi.mi.jami.model.Complex;
import psidev.psi.mi.jami.model.CvTerm;
import psidev.psi.mi.jami.model.Interactor;
import psidev.psi.mi.jami.model.ModelledFeature;
import psidev.psi.mi.jami.model.ModelledParticipant;
import psidev.psi.mi.jami.model.Range;
import psidev.psi.mi.jami.model.impl.DefaultBioactiveEntity;
import psidev.psi.mi.jami.model.impl.DefaultComplex;
import psidev.psi.mi.jami.model.impl.DefaultCvTerm;
import psidev.psi.mi.jami.model.impl.DefaultModelledFeature;
import psidev.psi.mi.jami.model.impl.DefaultModelledParticipant;
import psidev.psi.mi.jami.model.impl.DefaultProtein;
import psidev.psi.mi.jami.model.impl.DefaultStoichiometry;
import psidev.psi.mi.jami.utils.XrefUtils;

/**
 * Builds complexes from jami's model classes, as jami-xml would hand them over, and runs the
 * converter on them (the swr1 file of the other tests has no determined ranges, stoichiometries
 * or small molecules).
 */
final class JamiTestModels
{
    private static final String BINDING = "binding region";
    final MockItemWriter itemWriter =
        new MockItemWriter(new HashMap<String, org.intermine.model.fulldata.Item>());
    final PsiComplexesConverter converter;

    JamiTestModels() throws Exception {
        converter = new PsiComplexesConverter(itemWriter, Model.getInstanceByName("genomic"));
        converter.setComplexesSource("sgd");
    }

    static CvTerm miTerm(String name, String mi) {
        return new DefaultCvTerm(name, name, mi);
    }

    static Interactor protein(String uniprot) {
        return new DefaultProtein(uniprot, uniprot, miTerm("protein", "MI:0326"),
                XrefUtils.createXrefWithQualifier("uniprotkb", "MI:0486", uniprot, "identity",
                        "MI:0356"));
    }

    static Interactor smallMolecule(String name, String chebiId) {
        return new DefaultBioactiveEntity(name, name, miTerm("small molecule", "MI:0328"),
                XrefUtils.createXrefWithQualifier("chebi", "MI:0474", chebiId, "identity",
                        "MI:0356"));
    }

    static ModelledParticipant participant(Interactor interactor) {
        return new DefaultModelledParticipant(interactor, miTerm("unspecified role", "MI:0499"));
    }

    static ModelledParticipant participant(Interactor interactor, int minStoichiometry,
            int maxStoichiometry) {
        return new DefaultModelledParticipant(interactor, miTerm("unspecified role", "MI:0499"),
                new DefaultStoichiometry(minStoichiometry, maxStoichiometry));
    }

    // a binds b: a has a feature that is linked to a feature of b, which has these ranges
    static void bind(ModelledParticipant a, ModelledParticipant b, Range... bRanges) {
        ModelledFeature featureOfA = new DefaultModelledFeature(miTerm(BINDING, "MI:0117"));
        ModelledFeature featureOfB = new DefaultModelledFeature(miTerm(BINDING, "MI:0117"));
        a.addFeature(featureOfA);
        b.addFeature(featureOfB);
        for (Range range : bRanges) {
            featureOfB.getRanges().add(range);
        }
        featureOfA.getLinkedFeatures().add(featureOfB);
    }

    static Complex complexOf(ModelledParticipant... participants) {
        Complex complex = new DefaultComplex("test complex");
        complex.setInteractionType(miTerm("physical association", "MI:0915"));
        for (ModelledParticipant participant : participants) {
            complex.getParticipants().add(participant);
        }
        return complex;
    }

    Set<Item> convert(Complex complex) throws Exception {
        converter.processComplex(complex);
        converter.close();
        return new HashSet<Item>(itemWriter.getItems());
    }

    static List<Item> itemsOf(Set<Item> items, String className) {
        List<Item> found = new ArrayList<Item>();
        for (Item item : items) {
            if (className.equals(item.getClassName())) {
                found.add(item);
            }
        }
        return found;
    }

    static Item byRefId(Set<Item> items, String refId) {
        for (Item item : items) {
            if (refId.equals(item.getIdentifier())) {
                return item;
            }
        }
        return null;
    }
}

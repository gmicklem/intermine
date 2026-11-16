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

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;

import javax.xml.bind.JAXBContext;

import junit.framework.TestCase;
import psidev.psi.mi.jami.commons.MIDataSourceOptionFactory;
import psidev.psi.mi.jami.commons.MIFileType;
import psidev.psi.mi.jami.commons.PsiJami;
import psidev.psi.mi.jami.datasource.InteractionStream;
import psidev.psi.mi.jami.factory.MIDataSourceFactory;
import psidev.psi.mi.jami.model.Complex;
import psidev.psi.mi.jami.model.Interaction;
import psidev.psi.mi.jami.model.InteractionCategory;
import psidev.psi.mi.jami.model.ModelledParticipant;

/**
 * Parses a PSI-MI XML 2.5 complex with the jami-commons and jami-xml parsers the way the
 * converter does, and checks that the complex has its content: a JAXB runtime that cannot bind
 * jami's classes gives an empty complex without any error.
 */
public class JamiParsingTest extends TestCase
{
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // what PsiComplexesConverter.process does before jami-xml creates its JAXB context: the
        // JAXB 2.2.7 of jami-xml cannot generate its accessors on JDK 16+ otherwise
        if (System.getProperty("com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize") == null) {
            System.setProperty("com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize", "true");
        }
    }

    public void testPsiMiXml25Complex() throws Exception {
        PsiJami.initialiseAllFactories();
        Reader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("swr1_yeast-1.xml"), "UTF-8");
        Map<String, Object> options = MIDataSourceOptionFactory.getInstance().getOptions(
                MIFileType.psimi_xml, InteractionCategory.complex, null, true, null, reader);
        InteractionStream<?> stream =
                MIDataSourceFactory.getInstance().getInteractionSourceWith(options);
        assertNotNull("no PSI-MI XML datasource for the options", stream);
        int complexes = 0;
        try {
            Iterator<?> it = stream.getInteractionsIterator();
            while (it.hasNext()) {
                // the file is a PSI-MI XML 2.5.4 entry with one complex (swr1_yeast-1); jami
                // reads it as an XmlComplex of its xml254 model
                Interaction interaction = (Interaction) it.next();
                assertTrue(interaction instanceof Complex);
                complexes++;
                Complex complex = (Complex) interaction;
                // a JAXB runtime that cannot bind jami's classes gives an empty XmlComplex (no name,
                // no identifiers, no participants) and jami only tells its listener, so look at
                // the content
                assertEquals("swr1_yeast-1", complex.getShortName());
                assertEquals("swr1:ruvb1:ruvb2:swc5:swc4:swc7:act1:arp4:bdf1:yaf9:arp6:swc3:vps71:"
                        + "vps72", complex.getSystematicName());
                assertEquals(14, complex.getParticipants().size());
                for (ModelledParticipant participant : complex.getParticipants()) {
                    assertNotNull(participant.getInteractor());
                    assertNotNull(participant.getInteractor().getPreferredIdentifier());
                }
                assertEquals("physical association",
                        complex.getInteractionType().getShortName());
            }
        } finally {
            stream.close();
        }
        assertEquals(1, complexes);
    }

    /**
     * jami-xml needs its own JAXB 2.2.7 (jaxb-impl): with a JAXB 2.3.x runtime (jaxb-runtime) on
     * the class path JAXB picks that one, and it binds none of the PSI-MI XML elements.
     */
    public void testJaxbIsTheOneOfJamiXml() throws Exception {
        Class<?> contextClass = JAXBContext.newInstance(
                psidev.psi.mi.jami.xml.model.extension.xml254.XmlComplex.class).getClass();
        String jar = new java.io.File(contextClass.getProtectionDomain().getCodeSource()
                .getLocation().getPath()).getName();
        assertEquals("jaxb-impl-2.2.7.jar", jar);
    }
}

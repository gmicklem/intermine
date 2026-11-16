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
import java.io.StringReader;
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
 * Parses a PSI-MI XML 2.5 file and a MITAB 2.7 line with the jami-commons, jami-xml and
 * jami-mitab parsers on top of jami-core, the way the converter does: the four jars have to be
 * from one jami release (the parsers call the model classes of jami-core, which changes between
 * releases).
 */
public class JamiParsingTest extends TestCase
{
    // 42 tab separated columns of MITAB 2.7: two proteins, one physical association
    private static final String MITAB_27 = String.join("\t",
        "uniprotkb:P12345", "uniprotkb:Q67890", "-", "-",
        "psi-mi:geneA(display_long)", "psi-mi:geneB(display_long)",
        "psi-mi:\"MI:0018\"(two hybrid)", "Smith et al. (2010)", "pubmed:12345678",
        "taxid:9606(human)", "taxid:9606(human)",
        "psi-mi:\"MI:0915\"(physical association)", "psi-mi:\"MI:0469\"(IntAct)",
        "intact:EBI-1", "intact-miscore:0.5", "-",
        "psi-mi:\"MI:0499\"(unspecified role)", "psi-mi:\"MI:0499\"(unspecified role)",
        "psi-mi:\"MI:0496\"(bait)", "psi-mi:\"MI:0498\"(prey)",
        "psi-mi:\"MI:0326\"(protein)", "psi-mi:\"MI:0326\"(protein)",
        "-", "-", "-", "-", "-", "-", "-", "-", "2010/01/01", "2010/01/01",
        "-", "-", "-", "false", "-", "-", "-", "-", "-", "-");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // what PsiComplexesConverter.process does before jami-xml creates its JAXB context: the
        // JAXB 2.2.7 of jami-xml cannot generate its accessors on JDK 16+ otherwise
        if (System.getProperty("com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize") == null) {
            System.setProperty("com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize", "true");
        }
    }

    public void testMitabLineHas42Columns() {
        assertEquals(42, MITAB_27.split("\t", -1).length);
    }

    public void testJamiJarsAreOneRelease() {
        Class<?>[] classes = {psidev.psi.mi.jami.model.Interaction.class,
            MIFileType.class, psidev.psi.mi.jami.xml.PsiXmlVersion.class,
            psidev.psi.mi.jami.tab.MitabVersion.class};
        String[] artifacts = {"jami-core", "jami-commons", "jami-xml", "jami-mitab"};
        String version = null;
        for (int i = 0; i < classes.length; i++) {
            String jar = new java.io.File(classes[i].getProtectionDomain().getCodeSource()
                    .getLocation().getPath()).getName();
            assertTrue(jar + " is not " + artifacts[i], jar.startsWith(artifacts[i] + "-"));
            String jarVersion = jar.substring(artifacts[i].length() + 1, jar.length() - 4);
            if (version == null) {
                version = jarVersion;
            }
            assertEquals("jami jars of different releases on the classpath", version,
                    jarVersion);
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

    public void testMitab27BinaryInteraction() throws Exception {
        PsiJami.initialiseAllFactories();
        Map<String, Object> options = MIDataSourceOptionFactory.getInstance().getOptions(
                MIFileType.mitab, InteractionCategory.evidence, null, true, null,
                new StringReader(MITAB_27 + "\n"));
        InteractionStream<?> stream =
                MIDataSourceFactory.getInstance().getInteractionSourceWith(options);
        assertNotNull("no MITAB datasource for the options", stream);
        int interactions = 0;
        try {
            Iterator<?> it = stream.getInteractionsIterator();
            while (it.hasNext()) {
                Interaction interaction = (Interaction) it.next();
                interactions++;
                assertEquals(2, interaction.getParticipants().size());
                assertEquals("physical association", interaction.getInteractionType()
                        .getShortName());
            }
        } finally {
            stream.close();
        }
        assertEquals(1, interactions);
    }
}

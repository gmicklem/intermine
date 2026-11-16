package org.intermine.bio.task;

/*
 * Copyright (C) 2002-2026 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the source code for this package and is
 * included in the file LICENSE.txt.  This file can be found in
 * the bio/core directory of the source code.
 */

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * Stand-in for bio-core's SOToModelTask in the plugin's TestKit tests (it goes into the fake bio-model jar). It has the
 * same Ant attributes, which Ant resolves against the JVM's working directory when they are relative, the same error
 * for a missing term list, and it logs through the log4j 1 API (reload4j) as InterMine code does: one INFO and one
 * WARN line.
 */
public class SOToModelTask extends Task
{
    private static final Logger LOG = Logger.getLogger(SOToModelTask.class);
    private File soTermListFile, outputFile;

    /**
     * @param soTermListFile file containing the list of SO terms
     */
    public void setSoTermListFile(File soTermListFile) {
        this.soTermListFile = soTermListFile;
    }

    /**
     * @param outputFile the additions file that will be generated
     */
    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @Override
    public void execute() {
        if (soTermListFile == null || !soTermListFile.exists()) {
            throw new BuildException("Could not find file containing SO terms to add to the model,"
                    + " check the dbmodel/build.gradle file. Property was: " + soTermListFile);
        }
        try {
            List<String> lines = new ArrayList<String>();
            lines.add("<classes>");
            int terms = 0;
            for (String term : Files.readAllLines(soTermListFile.toPath(), StandardCharsets.UTF_8)) {
                if (!term.trim().isEmpty()) {
                    lines.add("<class name=\"" + term.trim() + "\" is-interface=\"true\"/>");
                    terms++;
                }
            }
            lines.add("</classes>");
            outputFile.getParentFile().mkdirs();
            Files.write(outputFile.toPath(), lines, StandardCharsets.UTF_8);
            LOG.info("stand-in SOToModelTask read " + terms + " SO terms");
            LOG.warn("stand-in SOToModelTask warns once per run");
            System.out.println("Wrote " + outputFile.getPath());
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }
}

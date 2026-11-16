package org.intermine.task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * Stand-in for intermine-objectstore's ModelMergerTask in the plugin's TestKit tests (it goes into the fake
 * intermine-integrate and bio-model jars). It has the Ant attributes that the plugin and MergeSourceModelsTask set
 * (reflectively, so the signatures matter) and finds each additions file as a resource of the class path, as the real
 * task does, but "merges" by appending to the model a comment and the text of each additions file, in the order of the
 * list: the tests read the order and the files used from the result.
 */
public class ModelMergerTask extends Task
{
    private File inputModelFile, outputFile;
    private final List<String> additionsFiles = new ArrayList<String>();

    /**
     * @param file the model to start from
     */
    public void setInputModelFile(File file) {
        inputModelFile = file;
    }

    /**
     * @param fileName an additions file, a resource of the class path
     */
    public void setAdditionsFile(String fileName) {
        additionsFiles.add(fileName);
    }

    /**
     * @param files the additions files, resources of the class path
     */
    public void setAdditionsFiles(List<String> files) {
        additionsFiles.addAll(files);
    }

    /**
     * @param file where to write the merged model (it may be the input file)
     */
    public void setOutputFile(File file) {
        outputFile = file;
    }

    @Override
    public void execute() {
        try {
            StringBuilder merged = new StringBuilder(
                    new String(Files.readAllBytes(inputModelFile.toPath()), StandardCharsets.UTF_8));
            for (String name : additionsFiles) {
                InputStream in = ModelMergerTask.class.getClassLoader().getResourceAsStream(name);
                if (in == null) {
                    throw new BuildException("Failed to find additions file " + name + " on the class path");
                }
                merged.append("<!-- additions: ").append(name).append(" -->\n");
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    merged.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
                }
                in.close();
            }
            outputFile.getParentFile().mkdirs();
            Files.write(outputFile.toPath(), merged.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("stand-in ModelMergerTask merged " + additionsFiles.size() + " additions files");
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }
}

package org.intermine.task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * Stand-in for intermine-objectstore's AcceptanceTestTask in the plugin's TestKit tests (it goes into the fake
 * intermine-integrate jar): writes the database name and the text of the configuration file into the output file.
 */
public class AcceptanceTestTask extends Task
{
    private File configFile, outputFile;
    private String database;

    /**
     * @param configFile the acceptance test configuration
     */
    public void setConfigFile(File configFile) {
        this.configFile = configFile;
    }

    /**
     * @param outputFile the HTML report
     */
    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * @param database the database alias
     */
    public void setDatabase(String database) {
        this.database = database;
    }

    @Override
    public void execute() {
        try {
            String conf = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            Files.write(outputFile.toPath(), ("<html>database=" + database + " conf=" + conf + "</html>")
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }
}

package org.intermine.task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * Stand-in for intermine-objectstore's GenerateUpdateTriggersTask in the plugin's TestKit tests (it goes into the fake
 * intermine-integrate jar): writes the three SQL files of the real task into destDir.
 */
public class GenerateUpdateTriggersTask extends Task
{
    private File destDir;
    private String osName;

    /**
     * @param destDir the directory for the SQL files
     */
    public void setDestDir(File destDir) {
        this.destDir = destDir;
    }

    /**
     * @param osName the object store alias
     */
    public void setOsName(String osName) {
        this.osName = osName;
    }

    @Override
    public void execute() {
        try {
            for (String name : new String[] {"add-update-triggers.sql", "remove-update-triggers.sql", "key-checker.sql"}) {
                Files.write(new File(destDir, name).toPath(), ("-- " + name + " for " + osName + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }
}

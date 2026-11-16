package org.intermine.task;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

/**
 * The loader of the fake bio-source jars in the plugin's TestKit tests (named by "loader.class" of &lt;type&gt;.properties,
 * with have.file.custom.direct, so that no database is needed): reports its attributes and the files of its fileset.
 */
public class StandInDirectLoaderTask extends Task
{
    private String integrationWriterAlias, sourceName, sourceType;
    private final List<FileSet> fileSets = new ArrayList<FileSet>();

    /**
     * @param alias the object store writer alias
     */
    public void setIntegrationWriterAlias(String alias) {
        integrationWriterAlias = alias;
    }

    /**
     * @param sourceName the name of the source in project.xml
     */
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    /**
     * @param sourceType the type of the source in project.xml
     */
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    /**
     * @param fileSet the files to load
     */
    public void addFileset(FileSet fileSet) {
        fileSets.add(fileSet);
    }

    @Override
    public void execute() {
        List<String> names = new ArrayList<String>();
        for (FileSet fileSet : fileSets) {
            DirectoryScanner scanner = fileSet.getDirectoryScanner(getProject());
            for (String file : scanner.getIncludedFiles()) {
                names.add(new File(file).getName());
            }
        }
        Collections.sort(names);
        System.out.println("stand-in direct loader: source=" + sourceName + " type=" + sourceType
                + " os=" + integrationWriterAlias + " files=" + names);
    }
}

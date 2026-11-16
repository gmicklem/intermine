package org.intermine.task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * Stand-in for intermine-model's ModelOutputTask in the plugin's TestKit tests (it goes into the fake bio-model jar,
 * which generateModel puts on the Ant class path). It reads &lt;model&gt;_model.xml from the class path, as the real
 * task does, and writes one Java class, org.example.model.ModelInfo, whose constant holds the model: the tests compile
 * it and read the model back from it.
 */
public class ModelOutputTask extends Task
{
    private File destDir;
    private String type, model;

    /**
     * @param destDir the directory to write the generated classes into
     */
    public void setDestDir(File destDir) {
        this.destDir = destDir;
    }

    /**
     * @param type what to generate, "java"
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @param model the name of the model: the resource &lt;model&gt;_model.xml
     */
    public void setModel(String model) {
        this.model = model;
    }

    @Override
    public void execute() {
        if (!"java".equals(type)) {
            throw new BuildException("stand-in ModelOutputTask only writes type java, not " + type);
        }
        String resource = model + "_model.xml";
        try {
            InputStream in = ModelOutputTask.class.getClassLoader().getResourceAsStream(resource);
            if (in == null) {
                throw new BuildException("Could not find the model " + resource + " on the class path");
            }
            StringBuilder text = new StringBuilder();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) {
                text.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
            in.close();
            File dir = new File(destDir, "org/example/model");
            dir.mkdirs();
            String escaped = text.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            Files.write(new File(dir, "ModelInfo.java").toPath(), ("package org.example.model;\n"
                    + "public class ModelInfo {\n"
                    + "    public static final String NAME = \"" + model + "\";\n"
                    + "    public static final String MODEL = \"" + escaped + "\";\n"
                    + "}\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("stand-in ModelOutputTask wrote ModelInfo for " + model + " into " + destDir);
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }
}

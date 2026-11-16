package org.intermine.task;

import org.apache.tools.ant.Task;

/**
 * Stand-in for intermine-integrate's PostProcessorTask in the plugin's TestKit tests (it goes into the fake
 * intermine-integrate jar). It has the Ant attributes the plugin sets and reports what it was asked to run and the
 * Ant property "pp.prop", which the plugin sets from the post-process's user properties.
 */
public class PostProcessorTask extends Task
{
    private String clsName, osName;

    /**
     * @param clsName the post-processor class
     */
    public void setClsName(String clsName) {
        this.clsName = clsName;
    }

    /**
     * @param osName the object store writer alias
     */
    public void setOsName(String osName) {
        this.osName = osName;
    }

    @Override
    public void execute() {
        System.out.println("stand-in PostProcessorTask: class=" + clsName + " os=" + osName
                + " pp.prop=" + getProject().getProperty("pp.prop")
                + " source.name=" + getProject().getProperty("source.name"));
    }
}

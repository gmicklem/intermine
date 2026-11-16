package org.intermine.task;

import java.util.Map;
import java.util.TreeMap;

import org.apache.tools.ant.DynamicAttribute;
import org.apache.tools.ant.Task;

/**
 * The pre-retrieve task of the fake bio-source jars in the plugin's TestKit tests (named by the "classname" of
 * &lt;type&gt;-pre-retrieve.properties). It takes every attribute that the plugin passes and reports them sorted.
 */
public class StandInPreRetrieveTask extends Task implements DynamicAttribute
{
    private final Map<String, String> attributes = new TreeMap<String, String>();

    @Override
    public void setDynamicAttribute(String name, String value) {
        attributes.put(name, value);
    }

    @Override
    public void execute() {
        System.out.println("stand-in pre-retrieve: " + attributes);
    }
}

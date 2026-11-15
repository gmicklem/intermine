package org.intermine.plugin.postprocess

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.intermine.plugin.BioSourceProperties
import org.intermine.plugin.project.PostProcess
import org.intermine.plugin.project.ProjectXmlBinding
import org.intermine.plugin.project.Source

/**
 * The post-processes chosen with -Pprocess (and, for do-sources, the sources chosen with -Psource),
 * read from the mine's project.xml on first use. It replaces the initPostProcess task.
 */
class PostProcessSpec {
    final org.intermine.plugin.project.Project intermineProject
    final BioSourceProperties bioSourceProperties
    final List<String> processNames

    private final Project project

    PostProcessSpec(Project project) {
        this.project = project
        File projectXml = new File(project.getParent().getProjectDir(), "project.xml")
        intermineProject = ProjectXmlBinding.unmarshall(projectXml)
        println "Found " + intermineProject.postProcesses.size() + " post-processes"
        bioSourceProperties = new BioSourceProperties(intermineProject, project)

        String processInput = project.hasProperty('process') ? project.property('process') : ""
        List<String> names = new ArrayList<String>()
        if ("".equals(processInput) || "all".equals(processInput)) {
            println "Performing ALL " + intermineProject.postProcesses.size() + " post-processes"
            intermineProject.postProcesses.keySet().each { processName ->
                names.add(processName)
            }
        } else {
            names = Arrays.asList(processInput.split("\\s*,\\s*"))
        }
        names.each { processName ->
            PostProcess postProcess = intermineProject.postProcesses.get(processName)
            if (postProcess == null) {
                throw new InvalidUserDataException("Can't find postProcess " + processName + " in project definition file")
            }
        }
        processNames = names
    }

    /** The sources do-sources post-processes: -Psource, or every source in project.xml. */
    List<String> sourceNames() {
        List<String> sourceNames = new ArrayList<String>()
        String sourceInput = project.hasProperty('source') ? project.property('source') : ""
        if ("".equals(sourceInput)) {
            intermineProject.sources.keySet().each { sourceName ->
                sourceNames.add(sourceName)
            }
        } else {
            sourceNames = Arrays.asList(sourceInput.split("\\s*,\\s*"))
        }
        sourceNames.each { sourceName ->
            Source source = intermineProject.sources.get(sourceName)
            if (source == null) {
                throw new InvalidUserDataException("Can't find source " + sourceName + " in project definition file")
            }
        }
        sourceNames
    }
}

package org.intermine.plugin.integrate

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.intermine.plugin.BioSourceProperties
import org.intermine.plugin.dbmodel.DBModelUtils
import org.intermine.plugin.project.ProjectXmlBinding
import org.intermine.plugin.project.Source

/**
 * What an integrate run works on: the mine's project.xml and the sources and action chosen with
 * -Psource and -Paction. It replaces the initIntegration task. It is built on first use, when a
 * task runs or integrateSource is resolved, so configuring the build does not read project.xml.
 */
class IntegrationSpec {
    final org.intermine.plugin.project.Project intermineProject
    final List<String> sourceNames
    final IntegrateAction action
    /** Dependency notations for the integrateSource configuration, one per source type. */
    final List<Map> sourceDependencies = []
    final DBModelUtils dbUtils
    final IntegrateUtils integration
    final BioSourceProperties bioSourceProperties

    private final Project project
    private boolean prepared = false

    IntegrationSpec(Project project) {
        this.project = project
        File projectXml = new File(project.getParent().getProjectDir(), "project.xml")
        intermineProject = ProjectXmlBinding.unmarshall(projectXml)
        dbUtils = new DBModelUtils(project)
        integration = new IntegrateUtils(project, intermineProject)
        bioSourceProperties = new BioSourceProperties(intermineProject, project)

        String sourceInput = project.hasProperty('source') ? project.property('source') : ""
        List<String> names = new ArrayList<String>()
        if ("".equals(sourceInput) || "all".equals(sourceInput)) {
            println "Integrating ALL sources set project.xml"
            intermineProject.sources.keySet().each { sourceName ->
                names.add(sourceName)
            }
        } else {
            names = Arrays.asList(sourceInput.split("\\s*,\\s*"))
        }
        sourceNames = names

        // keep track of the versions, only take the first one encountered
        Map<String, String> typesToVersions = new HashMap<String, String>()

        sourceNames.each { sourceName ->
            Source source = intermineProject.sources.get(sourceName)
            if (source == null) {
                throw new InvalidUserDataException("Can't find source " + sourceName + " in gradleProject definition file")
            }
            String sourceType = source.type

            // use default version
            String version = System.getProperty("bioVersion")
            // check if we have a custom version
            if (typesToVersions.get(sourceType) != null) {
                // we've seen this type before, use same version
                version = typesToVersions.get(sourceType)
            } else if (source.version != null) {
                version = source.version
                typesToVersions.put(sourceType, version)
            }

            sourceDependencies.add([group: "org.intermine", name: "bio-source-" + sourceType, version: version])
        }

        //when we have more than one source we can't split the integrate in the 2 steps: retrieve and load
        if (sourceNames.size() > 1) {
            action = IntegrateAction.RETRIEVE_AND_LOAD
        } else {
            String actionInput = project.hasProperty('action') ? project.property('action') : ""
            try {
                action = IntegrateAction.getAction(actionInput)
            } catch (RuntimeException ex) {
                throw new InvalidUserDataException("Unknown action: " + actionInput + ". Possible actions: load or retrieve")
            }
        }
    }

    /**
     * Run before integrating: if the "so" source is selected, extract the SO.obo file from bio-core
     * and put it on the classpath for use by this data source. Done once per build.
     */
    synchronized void prepare() {
        if (prepared) {
            return
        }
        prepared = true
        sourceNames.each { sourceName ->
            if ("so".equals(intermineProject.sources.get(sourceName).type)) {
                // into build/im/integrate, on the Ant tasks' classpath (dirset of build/); not into build/resources/main,
                // where the dbmodel jar would pick it up
                DBModelUtils.copyFromArchive(project, project.configurations.getByName("bioCore").singleFile, "so.obo",
                        DBModelUtils.taskDir(project, "integrate").absolutePath + File.separator + "so.obo")
            }
        }
    }
}

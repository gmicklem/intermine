package org.intermine.plugin.postprocess

import groovy.ant.AntBuilder
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.intermine.plugin.dbmodel.DBModelUtils
import org.intermine.plugin.BioSourceProperties
import org.intermine.plugin.TaskConstants
import org.intermine.plugin.project.PostProcess
import org.intermine.plugin.project.ProjectXmlBinding
import org.intermine.plugin.project.Source

class PostProcessPlugin implements Plugin<Project> {
    /**
     * Used mainly in project.xml indicate when we should iterate over the source directories when
     * we are doing the postprocessing, as some postprocess tasks will have to run before the source
     * tasks and some other tasks afterwards.
     * */
    public static final String DO_SOURCES = "do-sources"
    public static final String CREATE_SEARCH_INDEX = "create-search-index"
    public static final String CREATE_AUTO_INDEX = "create-autocomplete-index"

    private PostProcessSpec spec

    /** The post-processes of this run; read from project.xml and -Pprocess/-Psource on first use. */
    synchronized PostProcessSpec spec(Project project) {
        if (spec == null) {
            spec = new PostProcessSpec(project)
        }
        spec
    }

    void apply(Project project) {
        // Resolvable only. integrateSource is shared with the integrate plugin.
        ['postProcesses', 'integrateSource'].each { name ->
            project.configurations.maybeCreate(name).canBeConsumed = false
        }
        // the bio-postprocess jars of the selected post-processes (initPostProcess used to add them)
        project.configurations.getByName('postProcesses').withDependencies { DependencySet deps ->
            PostProcessSpec spec = spec(project)
            spec.processNames.each { processName ->
                PostProcess postProcess = spec.intermineProject.postProcesses.get(processName)
                // default version
                String version = System.getProperty("bioVersion")
                // check if we have a custom version
                if (postProcess.version != null) {
                    version = postProcess.version
                }
                if (!DO_SOURCES.equals(processName)) {
                    deps.add(project.dependencies.create([group: "org.intermine", name: "bio-postprocess-" + processName, version: version]))
                }
            }
        }
        // do-sources runs each source's own post-processor, from its bio-source jar
        project.configurations.getByName('integrateSource').withDependencies { DependencySet deps ->
            if (!project.gradle.taskGraph.hasTask(project.tasks.getByName('postProcess'))) {
                return
            }
            PostProcessSpec spec = spec(project)
            if (!spec.processNames.contains(DO_SOURCES)) {
                return
            }
            spec.sourceNames().each { sourceName ->
                Source source = spec.intermineProject.sources.get(sourceName)
                String version = System.getProperty("bioVersion")
                if (source.getVersion() != null) {
                    version = source.getVersion()
                }
                String name = "bio-source-" + source.type
                // the integrate plugin may already have added it, for the same -Psource
                if (!deps.any { it.group == "org.intermine" && it.name == name }) {
                    deps.add(project.dependencies.create([group: "org.intermine", name: name, version: version]))
                }
            }
        }

        project.tasks.register('postProcess') {
            group = TaskConstants.TASK_GROUP
            description = "Post processes. Optional input parameters: process (process name) and source(if process=do-sources). E.g. postprocess -Pprocess=create-references"
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties())

            doLast{
                PostProcessSpec spec = spec(project)
                org.intermine.plugin.project.Project intermineProject = spec.intermineProject
                BioSourceProperties bioSourceProperties = spec.bioSourceProperties
                spec.processNames.each { processName ->
                    println "Performing postprocess " + processName + "."
                    if(DO_SOURCES.equals(processName)) {
                        // the sources were validated, and their jars added to integrateSource, by the spec
                        List<String> sourceNames = spec.sourceNames()
                        sourceNames.each { sourceName ->
                            if (bioSourceProperties.postprocessorExists(sourceName)) {
                                println "Performing source postprocess on " + sourceName
                                String postprocessorClassName = bioSourceProperties.getPostprocessorClassname(sourceName)
                                Source source = intermineProject.sources.get(sourceName)
                                def ant = new AntBuilder()
                                source.userProperties.each { prop ->
                                    if (prop.location != null) {
                                        ant.project.setProperty(prop.name, prop.location)//TODO resolve path
                                    } else {
                                        ant.project.setProperty(prop.name, prop.value)
                                    }
                                }
                                ant.project.setProperty("source.name", sourceName)
                                ant.taskdef(name: "sourcePostProcess", classname: "org.intermine.task.PostProcessorTask") {
                                    classpath {
                                        dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                                        pathelement(path: project.configurations.getByName("integrateSource").asPath)
                                    }
                                }
                                ant.sourcePostProcess(clsName: postprocessorClassName, osName: "osw.production")
                            }
                        }
                    } else {
                        try {
                            def ant = new AntBuilder()
                            PostProcess postProcess = intermineProject.postProcesses.get(processName)
                            postProcess.userProperties.each { prop ->
                                ant.project.setProperty(prop.name, prop.value)
                            }
                            String postprocessorClassName = bioSourceProperties.getPostProcesserClassName(processName)
                            ant.taskdef(name: "corePostProcess", classname: "org.intermine.task.PostProcessorTask") {
                                classpath {
                                dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                                pathelement(path: DBModelUtils.antClasspath(project).asPath)
                                pathelement(path: project.configurations.getByName("postProcesses").asPath)
                                }
                            }
                            ant.corePostProcess(clsName: postprocessorClassName, osName: "osw.production")
                        } catch(Exception e) {
                            println "POSTPROCESS " + processName + " FAILED."
                            println e.message
                            if(CREATE_SEARCH_INDEX.equals(processName)
                                    || CREATE_AUTO_INDEX.equals(processName)) {
                                println "Please correct the error and run again ONLY THE POSTPROCESS"
                                println "./gradlew postprocess -Pprocess=" + processName
                                println "NO NEED TO RE-RUN THE ENTIRE BUILD"
                            }
                            return
                        }
                    }
                    println "Postprocess " + processName + " completed."
                }
            }
        }
    }
}

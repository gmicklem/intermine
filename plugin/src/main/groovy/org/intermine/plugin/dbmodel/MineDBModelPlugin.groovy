package org.intermine.plugin.dbmodel

import groovy.ant.AntBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project

class MineDBModelPlugin implements Plugin<Project> {
    /** Plugin-internal configuration holding this plugin's own jar (for MergeSourceModelsTask). */
    static final String IM_PLUGIN = "imPlugin"

    void apply(Project project) {
        MineDBModelConfig config = project.extensions.create('mineDBModelConfig', MineDBModelConfig)
        DBModelConfig dbModelConfig = project.extensions.getByType(DBModelConfig)
        // Resolvable only. The plugin jar on the build script class path cannot be used instead:
        // Gradle 6+ instruments build script jars, and the copy it loads refers to Gradle internals
        // that Ant's class loader cannot see (NoClassDefFoundError org/gradle/internal/classpath/Instrumented).
        DBModelUtils.resolvableConfiguration(project, IM_PLUGIN)
        //added this dependecy otherwise ant doesn't find MergeSourceModelsTask at runtime
        DBModelPlugin.addDependency(project, IM_PLUGIN) {
            [group: "org.intermine", name: "plugin", version: System.getProperty("imVersion")]
        }

        project.tasks.register('mergeModels') {
            description = "Merges defferent source model files into an intermine XML model"
            onlyIf { DBModelPlugin.regenerateModel(project) }
            // Merges, in place, into the genomic model copied by copyGenomicModel; the additions come
            // from the mine's resources, the bio-source jars (mergeSource, resolved only when this
            // runs) and so_additions.xml (createSoModel).
            inputs.files(DBModelUtils.processedResources(project))
            inputs.files(DBModelUtils.resource(project, 'copyGenomicModel') { config.modelName + "_model.xml" })
            inputs.files(project.files({ soAdditionsFile(project, dbModelConfig) ?: [] }).builtBy('createSoModel'))

            doLast {
                String projectXmlFilePath = DBModelPlugin.projectXml(project).absolutePath
                String modelFilePath = DBModelUtils.resourcesDir(project) + File.separator + config.modelName + "_model.xml"
                def ant = new AntBuilder()
                // MergeSourceModelsTask must be loaded by Ant's class loader, with the additions files on
                // its classpath, so the plugin's own jar goes on the taskdef classpath.
                ant.taskdef(name: "mergeSourceModels", classname: "org.intermine.plugin.ant.MergeSourceModelsTask") {
                    classpath {
                        pathelement(path: project.configurations.getByName(IM_PLUGIN).asPath)
                        pathelement(path: project.configurations.getByName("mergeSource").asPath)
                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                        dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                    }
                }
                ant.mergeSourceModels(projectXmlPath: projectXmlFilePath,
                        modelFilePath: modelFilePath,
                        extraModelsStart: config.extraModelsStart,
                        extraModelsEnd: config.extraModelsEnd)
            }
        }
    }

    /**
     * Where createSoModel writes so_additions.xml. A relative path is taken from the mine's root directory
     * (DBModelUtils.rootPath), as the task itself does.
     */
    static File soAdditionsFile(Project project, DBModelConfig config) {
        String path = DBModelUtils.rootPath(project, config.soAdditionFilePath)
        path == null ? null : new File(path)
    }
}

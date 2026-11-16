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
            // Copies the genomic model of bio-model (bioModel) into its own directory, build/im/mergeModels, and merges
            // into it the additions of the mine's resources, of the bio-source jars (mergeSource, resolved only when
            // this runs) and so_additions.xml (createSoModel). It used to merge in place into the copy that
            // copyGenomicModel left in build/resources/main, processResources' output.
            inputs.files(DBModelUtils.processedResources(project))
            inputs.files({ DBModelPlugin.modelArchive(project) })
            inputs.files(project.files({ soAdditionsFile(project, dbModelConfig) ?: [] }).builtBy('createSoModel'))
            inputs.files({ DBModelPlugin.projectXml(project).exists() ? [DBModelPlugin.projectXml(project)] : [] })
            inputs.property('modelName', { config.modelName ?: '' })
            inputs.property('extraModelsStart', { config.extraModelsStart ?: '' })
            inputs.property('extraModelsEnd', { config.extraModelsEnd ?: '' })
            outputs.dir(DBModelUtils.taskDir(project, name))

            doLast {
                String projectXmlFilePath = DBModelPlugin.projectXml(project).absolutePath
                String modelFilePath = DBModelUtils.cleanTaskDir(project, name).absolutePath + File.separator +
                        config.modelName + "_model.xml"
                DBModelUtils.copyFromArchive(project, DBModelPlugin.modelArchive(project).singleFile, "genomic_model.xml",
                        modelFilePath)
                def ant = new AntBuilder()
                // MergeSourceModelsTask must be loaded by Ant's class loader, with the additions files on
                // its classpath, so the plugin's own jar goes on the taskdef classpath.
                ant.taskdef(name: "mergeSourceModels", classname: "org.intermine.plugin.ant.MergeSourceModelsTask") {
                    classpath {
                        pathelement(path: project.configurations.getByName(IM_PLUGIN).asPath)
                        pathelement(path: project.configurations.getByName("mergeSource").asPath)
                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                        dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                        pathelement(location: DBModelUtils.antLoggingDir(project).absolutePath) // log4j.properties of the Ant tasks
                        dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                    }
                }
                ant.mergeSourceModels(projectXmlPath: projectXmlFilePath,
                        modelFilePath: modelFilePath,
                        extraModelsStart: config.extraModelsStart,
                        extraModelsEnd: config.extraModelsEnd)
            }
        }
        // The merged model is part of the mine's dbmodel jar (the webapp reads it from there): its directory is a
        // main output, so the jar takes it from the task that writes it.
        DBModelUtils.sourceSets(project).getByName("main").output.dir(
                [builtBy: 'mergeModels'], { DBModelUtils.taskDir(project, 'mergeModels') })
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

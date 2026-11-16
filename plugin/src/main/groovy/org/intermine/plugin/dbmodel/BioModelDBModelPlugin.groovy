package org.intermine.plugin.dbmodel

import groovy.ant.AntBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * For bio-model, whose jar is the genomic model that the bio sources and the mines start from: core.xml merged with
 * genomic_additions.xml.
 */
class BioModelDBModelPlugin implements Plugin<Project>{

    @Override
    void apply(Project project) {
        project.tasks.register('mergeModels') {
            description = "Merges only genomic_additions.xml and so_additions.xml into an intermine XML model"
            // Reads core.xml and genomic_additions.xml from the resources (processResources puts them there; the
            // additions are found on the classpath) and writes genomic_model.xml into its own directory,
            // build/im/mergeModels. It used to merge into a copy of core.xml in build/resources/main, editing
            // processResources' output in place, so neither task was ever up to date.
            inputs.files(DBModelUtils.processedResources(project))
            outputs.dir(DBModelUtils.taskDir(project, name))

            doLast {
                String inputModelFilePath = DBModelUtils.resourcesDir(project) + File.separator + "core.xml"
                String outputModelFilePath = DBModelUtils.cleanTaskDir(project, name).absolutePath + File.separator +
                        "genomic_model.xml"
                def ant = new AntBuilder()

                ant.taskdef(name: "mergeBioCoreModels", classname: "org.intermine.task.ModelMergerTask") {
                    classpath {
                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                        dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                        dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                    }
                }

                ant.mergeBioCoreModels(inputModelFile: inputModelFilePath, additionsFile: "genomic_additions.xml",
                        outputFile: outputModelFilePath)
            }
        }
        // The merged model is what bio-model publishes: its directory is part of the main output, so the jar (and
        // whatever reads the main output) takes it from the task that writes it.
        DBModelUtils.sourceSets(project).getByName("main").output.dir(
                [builtBy: 'mergeModels'], { DBModelUtils.taskDir(project, 'mergeModels') })
    }
}

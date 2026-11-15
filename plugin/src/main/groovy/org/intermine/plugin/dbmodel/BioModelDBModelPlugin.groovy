package org.intermine.plugin.dbmodel

import org.gradle.api.Plugin
import org.gradle.api.Project

class BioModelDBModelPlugin implements Plugin<Project>{

    @Override
    void apply(Project project) {
        project.tasks.register('mergeModels') {
            description "Merges only genomic_additions.xml and so_additions.xml into an intermine XML model"
            // Merges genomic_additions.xml (from the resources) into genomic_model.xml in place. bio-model
            // disables copyGenomicModel and gets genomic_model.xml from processResources instead.
            inputs.files(DBModelUtils.processedResources(project))
            inputs.files(DBModelUtils.resource(project, 'copyGenomicModel') { "genomic_model.xml" })

            doLast {
                String inputModelFilePath = DBModelUtils.resourcesDir(project) + File.separator + "genomic_model.xml"
                def ant = new AntBuilder()

                ant.taskdef(name: "mergeBioCoreModels", classname: "org.intermine.task.ModelMergerTask") {
                    classpath {
                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                        dirset(dir: project.getBuildDir().getAbsolutePath())
                    }
                }

                ant.mergeBioCoreModels(inputModelFile: inputModelFilePath, additionsFile: "genomic_additions.xml",
                        outputFile: inputModelFilePath)
            }
        }
    }
}

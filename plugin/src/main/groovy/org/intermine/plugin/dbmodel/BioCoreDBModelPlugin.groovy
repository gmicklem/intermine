package org.intermine.plugin.dbmodel

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * For bio-core and the bio-postprocess modules: projects that do not merge a model of their own. Their unit tests
 * use the model of the bioModel dependency as it is.
 */
class BioCoreDBModelPlugin implements Plugin<Project>{
    @Override
    void apply(Project project) {
        DBModelConfig config = project.extensions.getByType(DBModelConfig)
        project.tasks.register('mergeModels') {
            description = "bio-core gets its model from bio-model: copies it as it is into build/im/mergeModels, for the unit tests"
            // Nothing is merged; the copy is the model that generateModel and the unit tests read. It goes into this
            // task's own directory, not build/resources/main: the jar must not carry it.
            inputs.files({ DBModelPlugin.modelArchive(project) })
            inputs.property('modelName', { config.modelName ?: '' })
            outputs.dir(DBModelUtils.taskDir(project, name))

            doLast {
                File dir = DBModelUtils.cleanTaskDir(project, name)
                DBModelUtils.copyFromArchive(project, DBModelPlugin.modelArchive(project).singleFile, "genomic_model.xml",
                        dir.absolutePath + File.separator + config.modelName + "_model.xml")
            }
        }
        // the unit tests read the model and the test keys (and, from dbmodel, default.intermine.properties)
        DBModelUtils.testReads(project, ['mergeModels', 'copyGenomicKeys'])
    }
}

package org.intermine.plugin.dbmodel

import org.gradle.api.Plugin
import org.gradle.api.Project

class BioCoreDBModelPlugin implements Plugin<Project>{
    @Override
    void apply(Project project) {
        DBModelConfig config = project.extensions.getByType(DBModelConfig)
        project.tasks.register('mergeModels') {
            description = "bio-core gets its model from bio-model. Skip this step. MERGE NOTHING!"
            // The model generateModel reads is the one copyGenomicModel copies from bio-model.
            inputs.files(DBModelUtils.resource(project, 'copyGenomicModel') { config.modelName + "_model.xml" })
            // do nothing

            // used by biocore and bio-postprocess. packages that do NOT generate their own
            // model but instead use ones already provided. 'mergeModels' is required however
            // so that's why you are reading this empty method.
        }
        // the unit tests read the test keys (and, from dbmodel, default.intermine.properties)
        DBModelUtils.testReads(project, ['copyGenomicKeys'])
    }
}

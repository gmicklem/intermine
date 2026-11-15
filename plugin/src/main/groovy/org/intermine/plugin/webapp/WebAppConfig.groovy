package org.intermine.plugin.webapp

import org.gradle.api.Project
import org.intermine.plugin.dbmodel.DBModelUtils

class WebAppConfig {
    String mineName
    String objectStoreName
    String userProfileObjectStoreName
    String userProfileObjectStoreWriterName
    String defaultInterminePropertiesFile
    /** Where the merged web.properties and the object store summary are written; default: build/props. */
    String propsDir

    private final Project project

    WebAppConfig(Project project) {
        this.project = project
    }

    String getPropsDir() {
        propsDir ?: DBModelUtils.buildFile(project, "props").absolutePath
    }
}

package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * unwarBioWebApp (and addStrutsConfig, which only runs it) reads the model's struts, tiles and message fragments from the
 * resources output directory of the main source set, wherever the mine's build file puts it (im-ee7.32): it used to read
 * build/resources/main.
 */
class WebAppResourcesDirTest {

    @Test
    void additionsComeFromTheResourcesOutputOfTheSourceSet() {
        TestMine mine = TestMine.create('war-resources-dir', '''\
            sourceSets {
                main {
                    // not build/resources/main
                    output.resourcesDir = layout.buildDirectory.dir('processed-resources')
                }
            }
            ''')
        // the standard test mine's fragments, plus a marker that only this mine's resources have
        mine.write('webapp/src/main/resources/model.properties', 'model.label=Model label from the moved directory\n')
        BuildResult result = mine.run(':webapp:addStrutsConfig', ':webapp:war')
        assert result.task(':webapp:unwarBioWebApp').outcome == SUCCESS
        // the resources went to the directory that the build file names, and not to the default one
        assert mine.file('webapp/build/processed-resources/model.properties').exists()
        assert !mine.file('webapp/build/resources/main').exists()

        File war = mine.file('webapp/build/libs/webapp.war')
        assert TestMine.entryText(war, 'WEB-INF/struts-config.xml') ==
                '<struts-config><action path="/modelAction"/><form-bean name="modelForm"/></struts-config>'
        assert TestMine.entryText(war, 'WEB-INF/tiles-defs.xml') ==
                '<tiles-definitions><definition name="model.tile"/></tiles-definitions>'
        assert TestMine.entryText(war, 'WEB-INF/classes/InterMineWebApp.properties') ==
                'webapp.label=InterMine\nmodel.label=Model label from the moved directory\n'
        // the merged web.properties reads intermine.properties from there too
        assert TestMine.entryText(war, 'WEB-INF/web.properties').contains('project.title=Test mine')
    }
}

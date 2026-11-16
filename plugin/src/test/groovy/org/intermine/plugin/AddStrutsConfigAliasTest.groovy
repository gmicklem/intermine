package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * addStrutsConfig is the 5.x name of the step that adds the model's struts, tiles and web.xml fragments to the
 * bio-webapp (done by unwarBioWebApp since im-ee7.41). A mine of the 5.x line still names it, and the in-repo testmine
 * does not apply the webapp plugin (im-ee7.40), so only a deployer's build exercised the name until this test.
 * The additions themselves are checked in UnwarBioWebAppTest and WarCompositionTest; this class covers the task.
 */
class AddStrutsConfigAliasTest {

    @Test
    void addStrutsConfigRunsUnwarBioWebAppAndLeavesTheAdditionsInTheExplodedDirectory() {
        TestMine mine = TestMine.create('add-struts-config')

        BuildResult result = mine.run(':webapp:addStrutsConfig')

        assert result.task(':webapp:unwarBioWebApp').outcome == SUCCESS
        assert result.task(':webapp:addStrutsConfig') != null
        File exploded = mine.file('webapp/build/explodedWebApp')
        assert new File(exploded, 'WEB-INF/struts-config.xml').text ==
                '<struts-config><action path="/modelAction"/><form-bean name="modelForm"/></struts-config>'
        assert new File(exploded, 'WEB-INF/tiles-defs.xml').text ==
                '<tiles-definitions><definition name="model.tile"/></tiles-definitions>'
    }

    /** What the war task of a 5.x build file declares: it depends on addStrutsConfig by name. */
    @Test
    void aWarThatDependsOnAddStrutsConfigByNameBuilds() {
        TestMine mine = TestMine.create('add-struts-config-war', '''\
            war {
                dependsOn 'addStrutsConfig'
            }
            ''')

        BuildResult result = mine.run(':webapp:war')

        assert result.task(':webapp:addStrutsConfig') != null
        assert result.task(':webapp:war').outcome == SUCCESS
        File war = mine.file('webapp/build/libs/webapp.war')
        assert TestMine.entryText(war, 'WEB-INF/struts-config.xml') ==
                '<struts-config><action path="/modelAction"/><form-bean name="modelForm"/></struts-config>'
    }
}

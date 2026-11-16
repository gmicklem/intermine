package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * The war of the webapp plugin: the mine's src/main/webapp, then the exploded bio-webapp with the model's additions,
 * then the merged web.properties (WebAppPlugin, im-ee7.48 and im-89g.16).
 */
class WarCompositionTest {

    @Test
    void warHasTheMineFilesFirstNoDuplicatesAndNoLegacyAnt() {
        TestMine mine = TestMine.create('war', '''\
            dependencies {
                // what a mine of the 5.x line declares
                implementation 'ant:ant:1.6.5'
            }
            ''')
        BuildResult result = mine.run(':webapp:war')
        assert result.task(':webapp:war').outcome == SUCCESS
        File war = mine.file('webapp/build/libs/webapp.war')
        List<String> entries = TestMine.entryNames(war)

        // no name twice: the first entry wins
        assert entries.size() == (entries as Set).size()
        // the mine's own JSP replaces bio-webapp's
        assert TestMine.entryText(war, 'dataCategories.jsp') == TestMine.MINE_JSP

        // the five bio-webapp files that unwarBioWebApp completes with the model's fragments
        assert TestMine.entryText(war, 'WEB-INF/web.xml') == '<web-app><servlet>bio</servlet></web-app>'
        assert TestMine.entryText(war, 'WEB-INF/web-bio.xml') == '<servlet>bio</servlet>'
        assert TestMine.entryText(war, 'WEB-INF/struts-config.xml') ==
                '<struts-config><action path="/modelAction"/><form-bean name="modelForm"/></struts-config>'
        assert TestMine.entryText(war, 'WEB-INF/tiles-defs.xml') ==
                '<tiles-definitions><definition name="model.tile"/></tiles-definitions>'
        assert TestMine.entryText(war, 'WEB-INF/classes/InterMineWebApp.properties') ==
                'webapp.label=InterMine\nmodel.label=Model label\n'

        // web.properties is the merged one, not the mine's or bio-webapp's unmerged file
        String webProperties = TestMine.entryText(war, 'WEB-INF/web.properties')
        assert webProperties.contains('webapp.only=from-web-properties')
        assert webProperties.contains('project.title=Test mine')
        assert !webProperties.contains('bio.webapp.only')

        // the jar that bio-webapp and the mine's classpath both carry is there once; the legacy ant:ant jar is left out
        assert entries.count('WEB-INF/lib/foo-1.0.jar') == 1
        assert !entries.contains('WEB-INF/lib/ant-1.6.5.jar')
        assert result.output.contains('The war leaves out ant-1.6.5.jar')
        // only out of the war: the Ant tasks of the build still have it
        assert mine.run(':webapp:dependencies', '--configuration', 'runtimeClasspath').output.contains('ant:ant:1.6.5')
    }

    /** A duplicatesStrategy that the mine sets on the war is kept (the plugin sets EXCLUDE before the build file runs). */
    @Test
    void theMineDuplicatesStrategyIsKept() {
        TestMine mine = TestMine.create('war-duplicates-strategy', '''\
            war {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
            ''')
        BuildResult result = mine.run(':webapp:war')
        assert result.task(':webapp:war').outcome == SUCCESS
        List<String> entries = TestMine.entryNames(mine.file('webapp/build/libs/webapp.war'))
        assert entries.count('dataCategories.jsp') == 2
        assert entries.count('WEB-INF/lib/foo-1.0.jar') == 2
        // no ant:ant dependency, no warning
        assert !result.output.contains('The war leaves out')
    }
}

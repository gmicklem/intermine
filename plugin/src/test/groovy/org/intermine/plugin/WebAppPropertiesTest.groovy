package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/** mergeProperties, the mine's copyMineProperties and copyDefaultInterMineProperties of the webapp plugin. */
class WebAppPropertiesTest {

    static Properties load(File file) {
        Properties properties = new Properties()
        file.withInputStream { properties.load(it) }
        properties
    }

    @Test
    void mergedWebPropertiesHaveTheMineKeysAndAreUpToDateOnARerun() {
        TestMine mine = TestMine.create('mergeProperties')

        BuildResult first = mine.run(':webapp:mergeProperties')
        [':webapp:copyMineProperties', ':webapp:copyDefaultInterMineProperties', ':webapp:processResources',
         ':webapp:mergeProperties'].each { assert first.task(it).outcome == SUCCESS }
        // the webapp needs nothing of dbmodel for its properties (im-ee7.28)
        assert !TestMine.taskPaths(first).any { it.startsWith(':dbmodel:') }

        // web.properties of the mine, then the mine's intermine.properties: a key in both has the mine's value
        File merged = mine.file('webapp/build/props/web.properties')
        Properties properties = load(merged)
        assert properties.getProperty('webapp.only') == 'from-web-properties'
        assert properties.getProperty('project.title') == 'Test mine'
        assert properties.getProperty('shared.key') == 'from-intermine-properties'
        // the defaults are a file of their own (default.intermine.properties), next to intermine.properties
        File resources = mine.file('webapp/build/resources/main')
        assert load(new File(resources, 'default.intermine.properties')).getProperty('default.only') == 'from-defaults'
        assert load(new File(resources, 'intermine.properties')).getProperty('shared.key') == 'from-intermine-properties'

        BuildResult second = mine.run(':webapp:mergeProperties')
        [':webapp:copyMineProperties', ':webapp:copyDefaultInterMineProperties', ':webapp:processResources',
         ':webapp:mergeProperties'].each { assert second.task(it).outcome == UP_TO_DATE }

        // the mine's property file is an input, through copyMineProperties and processResources
        mine.write('testmine.properties', 'project.title=Renamed mine\n')
        BuildResult third = mine.run(':webapp:mergeProperties')
        assert third.task(':webapp:mergeProperties').outcome == SUCCESS
        assert load(merged).getProperty('project.title') == 'Renamed mine'
        assert load(merged).getProperty('shared.key') == 'from-web-properties'
    }

    /** A web.properties whose last line has no line break must not run into the first key of intermine.properties. */
    @Test
    void webPropertiesWithoutAFinalLineBreak() {
        TestMine mine = TestMine.create('mergeProperties-no-final-newline')
        mine.file('webapp/src/main/webapp/WEB-INF/web.properties').text = 'webapp.only=from-web-properties'
        mine.run(':webapp:mergeProperties')
        Properties properties = load(mine.file('webapp/build/props/web.properties'))
        assert properties.getProperty('webapp.only') == 'from-web-properties'
        assert properties.getProperty('project.title') == 'Test mine'
    }

    /** `:webapp:war` alone, from a clean mine, runs no task of dbmodel (im-ee7.28). */
    @Test
    void warAloneNeedsNoDbModelTask() {
        TestMine mine = TestMine.create('war-alone')
        BuildResult result = mine.run(':webapp:war')
        assert result.task(':webapp:war').outcome == SUCCESS
        assert !TestMine.taskPaths(result).any { it.startsWith(':dbmodel:') }
        File war = mine.file('webapp/build/libs/webapp.war')
        assert load(new ByteArrayInputStream(TestMine.entryText(war, 'WEB-INF/classes/default.intermine.properties').bytes))
                .getProperty('default.only') == 'from-defaults'
        assert TestMine.entryText(war, 'WEB-INF/web.properties').contains('project.title=Test mine')
    }

    static Properties load(InputStream input) {
        Properties properties = new Properties()
        properties.load(input)
        properties
    }
}

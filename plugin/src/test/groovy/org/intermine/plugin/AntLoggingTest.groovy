package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import java.util.regex.Pattern

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * The plugin's Ant tasks log through reload4j (DBModelUtils.antLoggingDir): createSoModel runs the stand-in
 * SOToModelTask, which logs one INFO and one WARN line through the log4j 1 API.
 */
class AntLoggingTest {
    static final Pattern INFO_LINE = ~/\d\d:\d\d:\d\d INFO  SOToModelTask - stand-in SOToModelTask read 2 SO terms/
    static final Pattern WARN_LINE = ~/\d\d:\d\d:\d\d WARN  SOToModelTask - stand-in SOToModelTask warns once per run/

    @Test
    void infoAtTheDefaultLevelAndWarnOnlyWithQuiet() {
        TestMine mine = TestMine.create('antLogging')
        File config = mine.file('dbmodel/build/im/antLogging/log4j.properties')

        BuildResult result = mine.run(':dbmodel:createSoModel')
        assert result.task(':dbmodel:createSoModel').outcome == SUCCESS
        assert config.text.contains('log4j.rootLogger=INFO, console')
        assert INFO_LINE.matcher(result.output).find()
        assert WARN_LINE.matcher(result.output).find()
        assert !result.output.contains('No appenders could be found')

        // -q: the file follows Gradle's level, and the INFO line is gone while the WARN line stays
        BuildResult quiet = mine.run(':dbmodel:createSoModel', '--rerun-tasks', '-q')
        assert config.text.contains('log4j.rootLogger=WARN, console')
        assert !INFO_LINE.matcher(quiet.output).find()
        assert WARN_LINE.matcher(quiet.output).find()
        assert !quiet.output.contains('No appenders could be found')
    }

    /** A mine with its own log4j.properties in its resources keeps it: the plugin writes none. */
    @Test
    void aMineWithItsOwnLog4jConfigurationKeepsIt() {
        TestMine mine = TestMine.create('antLogging-mine-config')
        mine.write('dbmodel/src/main/resources/log4j.properties', '''\
            log4j.rootLogger=INFO, console
            log4j.appender.console=org.apache.log4j.ConsoleAppender
            log4j.appender.console.layout=org.apache.log4j.PatternLayout
            log4j.appender.console.layout.ConversionPattern=MINE-LAYOUT %p %m%n
            ''')
        // processResources first: the plugin looks for the mine's file in build/resources/main
        BuildResult result = mine.run(':dbmodel:processResources', ':dbmodel:createSoModel')
        assert result.task(':dbmodel:createSoModel').outcome == SUCCESS
        assert !mine.file('dbmodel/build/im/antLogging/log4j.properties').exists()
        // and the Ant task logs with the mine's layout
        assert result.output.contains('MINE-LAYOUT INFO stand-in SOToModelTask read 2 SO terms')
        assert !result.output.contains('No appenders could be found')
    }
}

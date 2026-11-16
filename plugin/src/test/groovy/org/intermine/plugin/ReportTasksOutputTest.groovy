package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * runAcceptanceTests and generateUpdateTriggers write into directories of their own under build/im, their declared
 * outputs, and no longer into build/resources/main, which the dbmodel jar and the war pack (im-ee7.56). The stand-in Ant
 * tasks of src/testKitTasks write what the real ones write.
 */
class ReportTasksOutputTest {

    @Test
    void theReportAndTheTriggerFilesAreNotInTheResourcesOutputOrTheJar() {
        TestMine mine = TestMine.createModelMine('report-tasks-output', '<sources/>')
        mine.file('dbmodel/build.gradle').text += '''
            dbModelConfig {
                mineName = 'testmine'
                objectStoreName = 'os.production'
            }
            '''.stripIndent()
        mine.write('dbmodel/src/main/resources/testmine_acceptance_test.conf', 'acceptance conf of the mine')

        BuildResult result = mine.run(':dbmodel:runAcceptanceTests', ':dbmodel:generateUpdateTriggers', ':dbmodel:jar')
        assert result.task(':dbmodel:runAcceptanceTests').outcome == SUCCESS
        assert result.task(':dbmodel:generateUpdateTriggers').outcome == SUCCESS

        // the files are in the directories of the tasks, which the tasks declare as their outputs
        File report = mine.file('dbmodel/build/im/runAcceptanceTests/acceptance_test.html')
        assert report.text == '<html>database=db.production conf=acceptance conf of the mine</html>'
        List<String> sql = ['add-update-triggers.sql', 'remove-update-triggers.sql', 'key-checker.sql']
        sql.each { assert mine.file('dbmodel/build/im/generateUpdateTriggers/' + it).text == "-- ${it} for os.production\n" }

        // not in the resources output, so not in the jar
        assert mine.file('dbmodel/build/resources/main/testmine_acceptance_test.conf').exists()
        assert !mine.file('dbmodel/build/resources/main/acceptance_test.html').exists()
        sql.each { assert !mine.file('dbmodel/build/resources/main/' + it).exists() }
        List<String> entries = TestMine.entryNames(mine.file('dbmodel/build/libs/dbmodel.jar'))
        assert entries.contains('testmine_acceptance_test.conf')
        assert !entries.contains('acceptance_test.html')
        assert entries.findAll { it.endsWith('.sql') } == []

        // they read the database, which is no input: each run does the work again, and leaves no file of the last run
        report.text = 'stale'
        mine.file('dbmodel/build/im/generateUpdateTriggers/old.sql').text = 'stale'
        BuildResult again = mine.run(':dbmodel:runAcceptanceTests', ':dbmodel:generateUpdateTriggers')
        assert again.task(':dbmodel:runAcceptanceTests').outcome == SUCCESS
        assert again.task(':dbmodel:generateUpdateTriggers').outcome == SUCCESS
        assert report.text.contains('database=db.production')
        assert !mine.file('dbmodel/build/im/generateUpdateTriggers/old.sql').exists()
    }
}

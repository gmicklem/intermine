package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/** createSoModel of the dbmodel plugin: the SO additions file, up to date checks and the paths of dbModelConfig. */
class CreateSoModelTest {

    @Test
    void writesTheAdditionsAndIsUpToDateWhenNothingChanged() {
        TestMine mine = TestMine.create('createSoModel')

        BuildResult first = mine.run(':dbmodel:createSoModel')
        assert first.task(':dbmodel:createSoModel').outcome == SUCCESS
        String additions = mine.file('dbmodel/build/so_additions.xml').text
        assert additions.contains('<class name="gene"')
        assert additions.contains('<class name="transcript"')
        // the log4j configuration of the Ant tasks, written when the task builds its classpath
        assert mine.file('dbmodel/build/im/antLogging/log4j.properties').isFile()

        BuildResult second = mine.run(':dbmodel:createSoModel')
        assert second.task(':dbmodel:createSoModel').outcome == UP_TO_DATE

        // the term list is an input
        mine.write('dbmodel/resources/so_terms', 'gene\nexon\n')
        BuildResult third = mine.run(':dbmodel:createSoModel')
        assert third.task(':dbmodel:createSoModel').outcome == SUCCESS
        assert mine.file('dbmodel/build/so_additions.xml').text.contains('<class name="exon"')

        // so is the file it writes: a deleted output makes it run again
        assert mine.file('dbmodel/build/so_additions.xml').delete()
        assert mine.run(':dbmodel:createSoModel').task(':dbmodel:createSoModel').outcome == SUCCESS
        assert mine.file('dbmodel/build/so_additions.xml').isFile()
    }

    /**
     * Relative paths in dbModelConfig are taken from the mine's root directory (the 6.0 behaviour), not from the
     * project directory and not from the JVM's working directory, against which Ant resolves a relative File
     * attribute: on JDK 9+ that is the daemon's own directory, and the stand-in task then fails with "Could not find
     * file containing SO terms" as SOToModelTask does.
     *
     * NOTE: only on JDK 17 (JDK 9+) does this test catch a regression to Ant's own resolution of the path against the
     * working directory. On JDK 8 Gradle sets user.dir to the directory of the project that runs the task, so a path
     * that is left relative still fails there, but only because it is then taken from dbmodel/ and not from the root: the
     * daemon's own directory plays no part. CI runs the plugin tests on both JDKs of its matrix, so the 17 cells are the
     * ones that guard this.
     */
    @Test
    void relativeSoPathsAreTakenFromTheMineRoot() {
        TestMine mine = TestMine.create('createSoModel-relative')
        BuildResult result = mine.run(':dbmodel:createSoModel')
        assert result.task(':dbmodel:createSoModel').outcome == SUCCESS
        assert result.output.contains('Wrote ' + mine.file('dbmodel/build/so_additions.xml').absolutePath)
        assert !mine.file('dbmodel/dbmodel').exists()
    }

    /** The absolute form that the 5.1.1 upgrade notes ask for ("$rootDir/dbmodel/...") still works. */
    @Test
    void absoluteSoPathsStillWork() {
        TestMine mine = TestMine.create('createSoModel-absolute')
        File build = mine.file('dbmodel/build.gradle')
        build.text = build.text
                .replace("'dbmodel/resources/so_terms'", '"$rootDir/dbmodel/resources/so_terms"')
                .replace("'dbmodel/build/so_additions.xml'", '"$rootDir/dbmodel/build/so_additions.xml"')
        assert build.text.contains('$rootDir/dbmodel/resources/so_terms')
        BuildResult result = mine.run(':dbmodel:createSoModel')
        assert result.task(':dbmodel:createSoModel').outcome == SUCCESS
        assert mine.file('dbmodel/build/so_additions.xml').text.contains('<class name="gene"')
    }
}

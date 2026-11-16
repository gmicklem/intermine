package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.intermine.plugin.webapp.WarLibraries
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * The check that the war does not carry two versions of one library in WEB-INF/lib (checkWarLibraries, im-ee7.54).
 * The fake bio-webapp war carries foo-1.0.jar; a mine whose classpath resolves to foo-1.1.jar has two versions of foo.
 */
class WarLibrariesTest {

    private static final String FOO_1_1 = '''\
            dependencies {
                // Gradle's conflict resolution takes 1.1 over the 1.0 that the standard mine declares
                implementation 'org.example:foo:1.1'
            }
            '''

    private static TestMine mineWithFoo11(String name, String extra = '') {
        TestMine mine = TestMine.create(name, FOO_1_1 + extra)
        mine.publish('org.example', 'foo', '1.1', 'jar', TestMine.zip(['foo.txt': 'foo 1.1']))
        mine
    }

    @Test
    void twoVersionsOfOneLibraryWarn() {
        TestMine mine = mineWithFoo11('war-libraries-warn')
        BuildResult result = mine.run(':webapp:war')
        assert result.task(':webapp:checkWarLibraries').outcome == SUCCESS
        assert result.task(':webapp:war').outcome == SUCCESS
        // the message names the library, both jars, where each comes from, and the switch
        assert result.output.contains('The war would carry two versions of a library in WEB-INF/lib')
        assert result.output.contains('foo: org.example:foo:1.1 (classpath), foo-1.0.jar (bio-webapp)')
        assert result.output.contains('-PimWarDuplicateLibraries=fail')
        // a warning only: the war is built with both jars
        List<String> entries = TestMine.entryNames(mine.file('webapp/build/libs/webapp.war'))
        assert entries.contains('WEB-INF/lib/foo-1.0.jar')
        assert entries.contains('WEB-INF/lib/foo-1.1.jar')
        // the warning comes again on a run that finds the war up to date
        BuildResult again = mine.run(':webapp:war')
        assert again.task(':webapp:war').outcome == org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
        assert again.output.contains('The war would carry two versions of a library')
    }

    @Test
    void failModeStopsBeforeTheWarIsBuilt() {
        TestMine mine = mineWithFoo11('war-libraries-fail')
        BuildResult result = mine.runner(':webapp:war', '-PimWarDuplicateLibraries=fail').buildAndFail()
        assert result.output.contains('The war would carry two versions of a library in WEB-INF/lib')
        assert result.output.contains('foo: org.example:foo:1.1 (classpath), foo-1.0.jar (bio-webapp)')
        assert result.task(':webapp:checkWarLibraries').outcome == org.gradle.testkit.runner.TaskOutcome.FAILED
        assert result.task(':webapp:war') == null
        assert !mine.file('webapp/build/libs/webapp.war').exists()
    }

    @Test
    void ignoreModeSaysNothing() {
        TestMine mine = mineWithFoo11('war-libraries-ignore')
        BuildResult result = mine.run(':webapp:war', '-PimWarDuplicateLibraries=ignore')
        assert !result.output.contains('two versions')
    }

    /** The same jar twice (foo-1.0.jar from the classpath and from bio-webapp) is one jar, and gives no warning. */
    @Test
    void theSameJarTwiceIsNotADuplicate() {
        TestMine mine = TestMine.create('war-libraries-same')
        BuildResult result = mine.run(':webapp:war', '-PimWarDuplicateLibraries=fail')
        assert result.task(':webapp:war').outcome == SUCCESS
        assert !result.output.contains('two versions')
        assert TestMine.entryNames(mine.file('webapp/build/libs/webapp.war')).count('WEB-INF/lib/foo-1.0.jar') == 1
    }

    /**
     * The name of a resolved library is its module id, not a guess from the file name: lib-2-2.0.jar is lib-2 in 2.0 (a
     * guess would make it lib in 2-2.0) and so clashes with lib-2-1.0.jar of bio-webapp, whose name is guessed with the
     * names of the resolved libraries.
     */
    @Test
    void theNameOfAResolvedLibraryIsItsModuleId() {
        TestMine mine = TestMine.create('war-libraries-module-id', '''\
            dependencies {
                implementation 'org.example:lib-2:2.0'
            }
            ''')
        mine.publish('org.example', 'lib-2', '2.0', 'jar', TestMine.zip(['lib.txt': 'lib-2 2.0']))
        mine.publishBioWebApp(TestMine.VERSION, ['WEB-INF/lib/lib-2-1.0.jar': TestMine.zip(['lib.txt': 'lib-2 1.0'])])
        BuildResult result = mine.run(':webapp:war')
        assert result.output.contains('lib-2: org.example:lib-2:2.0 (classpath), lib-2-1.0.jar (bio-webapp)')
    }

    /** A classifier is part of the library: foo-1.1-sources.jar next to foo-1.0.jar is no second version of foo. */
    @Test
    void aClassifierIsPartOfTheName() {
        TestMine mine = TestMine.create('war-libraries-classifier', '''\
            dependencies {
                implementation 'org.example:foo:1.0:tests'
            }
            ''')
        mine.publish('org.example', 'foo', '1.0', 'jar', TestMine.zip(['foo.txt': 'foo']))
        File dir = mine.file('repo/org/example/foo/1.0')
        new File(dir, 'foo-1.0-tests.jar').bytes = TestMine.zip(['foo.txt': 'foo tests'])
        BuildResult result = mine.run(':webapp:war')
        assert !result.output.contains('two versions')
    }

    @Test
    void libraryNamesOfJarsNoResolutionKnows() {
        def lib = { String file, Collection<String> known = [] -> WarLibraries.fromFileName(file, 'x', known) }
        assert lib('commons-lang3-3.12.0.jar').name == 'commons-lang3'
        assert lib('commons-lang3-3.12.0.jar').version == '3.12.0'
        assert lib('json-lib-2.4-jdk15.jar').name == 'json-lib'
        assert lib('json-lib-2.4-jdk15.jar').version == '2.4-jdk15'
        assert lib('log4j-api-2.17.1.jar').name == 'log4j-api'
        assert lib('noversion.jar').name == 'noversion'
        // a known name wins over the first hyphen and digit; the longest known name wins
        assert lib('lib-2-1.0.jar').name == 'lib'
        assert lib('lib-2-1.0.jar', ['lib-2']).name == 'lib-2'
        assert lib('lib-2-1.0.jar', ['lib-2']).version == '1.0'
        assert lib('lib-2-1.0.jar', ['li', 'lib-2', 'lib']).name == 'lib-2'
        // a known name that only starts the file name is not it
        assert lib('lib-two-1.0.jar', ['lib']).name == 'lib-two'
    }

    @Test
    void duplicatesAreLibrariesOfOneNameAndDifferentVersions() {
        def lib = { String name, String version, String group = null, String origin = 'o' ->
            new WarLibraries.Library(name: name, version: version, group: group, fileName: "${name}-${version}.jar", origin: origin)
        }
        assert WarLibraries.duplicates([]) == []
        assert WarLibraries.duplicates([lib('a', '1'), lib('b', '1')]) == []
        // the same file twice
        assert WarLibraries.duplicates([lib('a', '1'), lib('a', '1', 'g')]) == []
        assert WarLibraries.duplicates([lib('a', '1'), lib('a', '2'), lib('b', '1'), lib('b', '3')]).size() == 2
        // the same file name from two groups
        assert WarLibraries.duplicates([lib('a', '1', 'g1'), lib('a', '1', 'g2')]).size() == 1
    }
}

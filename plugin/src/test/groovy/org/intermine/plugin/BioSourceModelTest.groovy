package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/**
 * The biosource-dbmodel plugin, as the projects of bio/sources apply it (with dbmodel): mergeModels merges the global and
 * the source's own additions into the genomic model of bio-model, and what it writes does not go into the source's jar.
 */
class BioSourceModelTest {

    static TestMine bioSource(String name) {
        TestMine mine = TestMine.createBioSources(name, ['fasta'], '''\
            bioSourceDBModelConfig {
                globalAdditionsFile = 'global_additions.xml'
            }
            ''')
        mine.write('global_additions.xml', '<class name="Global"/>\n')
        mine.write('bio-source-fasta/src/main/resources/fasta_additions.xml', '<class name="FastaGene" extends="Gene"/>\n')
        mine
    }

    @Test
    void mergeModelsMergesTheGlobalThenTheSourceAdditionsAndIsUpToDateWhenNothingChanged() {
        TestMine mine = bioSource('bioSource')
        File merged = mine.file('bio-source-fasta/build/im/mergeModels/genomic_model.xml')

        BuildResult first = mine.run(':bio-source-fasta:mergeModels')
        assert first.task(':bio-source-fasta:mergeModels').outcome == SUCCESS
        assert merged.text.startsWith('<model name="genomic"')
        assert ModelTasksTest.additionsOf(merged.text) == ['global_additions.xml', 'fasta_additions.xml']
        assert merged.text.contains('<class name="FastaGene" extends="Gene"/>')

        assert mine.run(':bio-source-fasta:mergeModels').task(':bio-source-fasta:mergeModels').outcome == UP_TO_DATE

        // the global additions file, which the root project holds, is an input
        mine.write('global_additions.xml', '<class name="OtherGlobal"/>\n')
        assert mine.run(':bio-source-fasta:mergeModels').task(':bio-source-fasta:mergeModels').outcome == SUCCESS
        assert merged.text.contains('<class name="OtherGlobal"/>')
        assert !merged.text.contains('<class name="Global"/>')
    }

    @Test
    void theUnitTestFilesAreWrittenAndTheJarCarriesNoneOfThem() {
        TestMine mine = bioSource('bioSource-jar')

        BuildResult result = mine.run(':bio-source-fasta:mergeModels', ':bio-source-fasta:copyGenomicKeys',
                ':bio-source-fasta:createTestPrioritiesFile', ':bio-source-fasta:jar')
        assert result.task(':bio-source-fasta:copyGenomicKeys').outcome == SUCCESS
        assert result.task(':bio-source-fasta:createTestPrioritiesFile').outcome == SUCCESS
        // the keys of bio-model and the dummy priorities, each in the directory of its task, for the unit tests
        assert mine.file('bio-source-fasta/build/im/copyGenomicKeys/genomic_keyDefs.properties').text
                .contains('Gene.key_primaryidentifier')
        assert mine.file('bio-source-fasta/build/im/createTestPrioritiesFile/genomic_priorities.properties').text
                .contains('# dummy file for unit tests')

        // a mine merges its own model: the source's jar has its additions and none of the files above
        List<String> entries = TestMine.entryNames(mine.file('bio-source-fasta/build/libs/bio-source-fasta.jar'))
        assert entries.contains('fasta_additions.xml')
        assert !entries.any { it.startsWith('genomic_') || it == 'global_additions.xml' }
    }
}

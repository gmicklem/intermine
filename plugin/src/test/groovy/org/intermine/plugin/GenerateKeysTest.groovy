package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/** generateKeys of the dbmodel plugin: the keys files of the bio-source jars of project.xml, merged into one file. */
class GenerateKeysTest {
    static final String SOURCES = '''\
        <sources>
          <source name="uniprot" type="uniprot"/>
          <source name="go-human" type="go"/>
          <source name="custom" type="fasta"/>
        </sources>'''

    static TestMine keysMine(String name) {
        TestMine mine = TestMine.createModelMine(name, SOURCES)
        mine.publishBioSource('uniprot')
        // go-human has no keys file of its own: the keys of its type are used
        mine.publishBioSource('go')
        // a keys file named after the source, in the jar of its type
        mine.publishBioSource('fasta', ['fasta_keys.properties': null, 'custom_keys.properties': 'Gene.key_custom = customId\n'])
        mine
    }

    static Properties keysOf(TestMine mine) {
        Properties keys = new Properties()
        mine.file('dbmodel/build/im/generateKeys/genomic_keyDefs.properties').withInputStream { keys.load(it) }
        keys
    }

    @Test
    void mergesTheKeysOfEverySourceByNameThenByType() {
        TestMine mine = keysMine('generateKeys')

        BuildResult first = mine.run(':dbmodel:generateKeys')
        assert first.task(':dbmodel:generateKeys').outcome == SUCCESS
        assert keysOf(mine) == ['Gene.key_uniprot': 'uniprotId', 'Gene.key_go': 'goId', 'Gene.key_custom': 'customId'] as Properties
        assert first.output.contains('Processing uniprot_keys.properties')
        assert first.output.contains("Processing go_keys.properties now, as didn't find go-human_keys.properties")

        assert mine.run(':dbmodel:generateKeys').task(':dbmodel:generateKeys').outcome == UP_TO_DATE

        // project.xml is an input
        mine.write('project.xml', '''\
            <project type="bio">
              <sources>
                <source name="uniprot" type="uniprot"/>
              </sources>
            </project>
            ''')
        assert mine.run(':dbmodel:generateKeys').task(':dbmodel:generateKeys').outcome == SUCCESS
        assert keysOf(mine) == ['Gene.key_uniprot': 'uniprotId'] as Properties
    }

    @Test
    void aSourceWithoutAKeysFileFailsTheBuildAndNamesIt() {
        TestMine mine = TestMine.createModelMine('generateKeys-missing',
                '<sources><source name="nokeys" type="nokeys"/></sources>')
        mine.publishBioSource('nokeys', ['nokeys_keys.properties': null])

        String output = mine.runner(':dbmodel:generateKeys').buildAndFail().output
        assert output.contains('No keys file found for nokeys (or nokeys)')
    }

    @Test
    void isSkippedInAMineWithoutProjectXml() {
        TestMine mine = TestMine.create('generateKeys-no-project-xml')
        BuildResult result = mine.run(':dbmodel:generateKeys')
        assert result.task(':dbmodel:generateKeys').outcome == SKIPPED
    }
}

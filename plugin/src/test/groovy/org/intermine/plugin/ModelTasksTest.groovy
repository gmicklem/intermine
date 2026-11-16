package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import java.util.regex.Matcher

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/**
 * mergeModels of the mine-dbmodel plugin (with MergeSourceModelsTask) and generateModel of the dbmodel plugin, against the
 * stand-ins of ModelMergerTask and ModelOutputTask and fake bio-source jars (TestMine.createModelMine).
 */
class ModelTasksTest {
    static final String SOURCES = '''\
        <sources>
          <source name="uniprot" type="uniprot"/>
          <source name="go" type="go"/>
          <source name="fasta" type="fasta"/>
        </sources>'''

    static TestMine modelMine(String name) {
        TestMine mine = TestMine.createModelMine(name, SOURCES)
        mine.publishBioSource('uniprot')
        mine.publishBioSource('go')
        // a source that has no additions file: MergeSourceModelsTask warns and goes on
        mine.publishBioSource('fasta', ['fasta_additions.xml': null])
        mine.write('dbmodel/src/main/resources/start_additions.xml', '<class name="Start"/>\n')
        mine.write('dbmodel/src/main/resources/end_additions.xml', '<class name="End"/>\n')
        mine.file('dbmodel/build.gradle').text += '''
            mineDBModelConfig {
                extraModelsStart = 'start_additions.xml'
                extraModelsEnd = 'end_additions.xml'
            }
            '''.stripIndent()
        mine
    }

    /** The additions files of a merged model, in the order the stand-in ModelMergerTask appended them. */
    static List<String> additionsOf(String model) {
        Matcher matcher = model =~ /<!-- additions: (\S+) -->/
        matcher.collect { it[1] }
    }

    @Test
    void mergeModelsAddsTheExtraModelsAndTheSourcesInProjectXmlOrder() {
        TestMine mine = modelMine('mergeModels')
        File merged = mine.file('dbmodel/build/im/mergeModels/genomic_model.xml')

        BuildResult first = mine.run(':dbmodel:mergeModels')
        assert first.task(':dbmodel:mergeModels').outcome == SUCCESS
        // the model of bio-model, then extraModelsStart, the sources of project.xml in its order, extraModelsEnd
        assert merged.text.startsWith('<model name="genomic"')
        assert additionsOf(merged.text) ==
                ['start_additions.xml', 'uniprot_additions.xml', 'go_additions.xml', 'end_additions.xml']
        // the bio-source jars were resolved from project.xml (the mergeSource configuration), the source without
        // additions only warned
        assert first.output.contains('warning: fasta_additions.xml not found')
        assert first.output.contains('stand-in ModelMergerTask merged 4 additions files')

        assert mine.run(':dbmodel:mergeModels').task(':dbmodel:mergeModels').outcome == UP_TO_DATE

        // project.xml is an input: with one source less the task runs again, and its directory is cleaned first
        mine.write('project.xml', '''\
            <project type="bio">
              <sources>
                <source name="uniprot" type="uniprot"/>
              </sources>
            </project>
            ''')
        BuildResult second = mine.run(':dbmodel:mergeModels')
        assert second.task(':dbmodel:mergeModels').outcome == SUCCESS
        assert additionsOf(merged.text) == ['start_additions.xml', 'uniprot_additions.xml', 'end_additions.xml']
    }

    @Test
    void generateModelWritesTheClassesBeforeCompileJavaAndOnlyWhileThereIsNoBuildGen() {
        TestMine mine = modelMine('generateModel')
        File info = mine.file('dbmodel/build/gen/org/example/model/ModelInfo.java')

        BuildResult first = mine.run(':dbmodel:compileJava')
        assert first.task(':dbmodel:mergeModels').outcome == SUCCESS
        assert first.task(':dbmodel:generateModel').outcome == SUCCESS
        assert first.task(':dbmodel:compileJava').outcome == SUCCESS
        List<String> order = TestMine.taskPaths(first)
        assert order.indexOf(':dbmodel:mergeModels') < order.indexOf(':dbmodel:generateModel')
        assert order.indexOf(':dbmodel:generateModel') < order.indexOf(':dbmodel:compileJava')
        // the classes come from the merged model, and were compiled
        assert info.text.contains('additions: go_additions.xml')
        assert mine.file('dbmodel/build/classes/java/main/org/example/model/ModelInfo.class').isFile()

        // build/gen exists: the model is not generated again (deployers run clean to pick up a model change), and
        // neither is it merged again
        mine.write('project.xml', '<project type="bio"><sources/></project>\n')
        BuildResult second = mine.run(':dbmodel:compileJava')
        assert second.task(':dbmodel:mergeModels').outcome == SKIPPED
        assert second.task(':dbmodel:generateModel').outcome == SKIPPED
        assert info.text.contains('additions: go_additions.xml')

        // after clean it does, from the model of the new project.xml
        BuildResult third = mine.run(':dbmodel:clean', ':dbmodel:compileJava')
        assert third.task(':dbmodel:generateModel').outcome == SUCCESS
        assert !info.text.contains('go_additions.xml')
        assert info.text.contains('additions: start_additions.xml')
    }
}

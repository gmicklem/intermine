package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * The integrate and postprocess plugins applied to the dbmodel project of a mine with project.xml, on fake bio-source and bio-postprocess jars and the
 * stand-in Ant tasks of src/testKitTasks. The fake source type "fasta" loads directly (have.file.custom.direct), so that no
 * database is needed: the tasks resolve the source jars from project.xml and -Psource, find the properties files in
 * them and run the Ant tasks that they name.
 */
class IntegratePostProcessTest {
    static final String PROJECT_XML = '''\
        <sources>
          <source name="fasta-a" type="fasta">
            <property name="src.data.dir" location="@MINE@/data/a"/>
            <property name="fasta.includes" value="*.fa"/>
            <property name="pre.message" value="hello a"/>
          </source>
          <source name="fasta-b" type="fasta">
            <property name="src.data.dir" location="@MINE@/data/b"/>
            <property name="fasta.includes" value="*.fa"/>
            <property name="pre.message" value="hello b"/>
          </source>
        </sources>
        <post-processes>
          <post-process name="create-references">
            <property name="pp.prop" value="42"/>
          </post-process>
          <post-process name="do-sources"/>
        </post-processes>'''

    static TestMine pipelineMine(String name) {
        TestMine mine = TestMine.createModelMine(name, PROJECT_XML)
        mine.publishBioSource('fasta', [
                'fasta.properties'            : '''\
                    have.file.custom.direct=true
                    loader.class=org.intermine.task.StandInDirectLoaderTask
                    postprocessor.class=org.example.FastaPostProcess
                    ''',
                'fasta-pre-retrieve.properties': '''\
                    classname=org.intermine.task.StandInPreRetrieveTask
                    message=${pre.message}
                    literal=fixed
                    '''.stripIndent()])
        mine.write('data/a/one.fa', '>one\nACGT\n')
        mine.write('data/a/ignored.txt', 'not matched by fasta.includes\n')
        mine.write('data/b/two.fa', '>two\nACGT\n')
        mine.write('data/b/three.fa', '>three\nACGT\n')
        mine.publish('org.intermine', 'bio-postprocess-create-references', TestMine.VERSION, 'jar', TestMine.zip([
                'create-references.properties': 'postprocessor.class=org.example.CreateReferences\n']))
        // as in a mine, the integrate and postprocess plugins are applied to the dbmodel project, which has the properties
        // and the model tasks that they read (project.xml is the one of its parent)
        mine.file('dbmodel/build.gradle').text += '''
            apply plugin: 'integrate'
            apply plugin: 'postprocess'
            '''.stripIndent()
        mine
    }

    @Test
    void integrateOneSourceRunsPreRetrieveAndLoadFromTheSourceJar() {
        TestMine mine = pipelineMine('integrate-single')

        BuildResult result = mine.run(':dbmodel:integrate', '-Psource=fasta-a')
        assert result.task(':dbmodel:preRetrieveSingleSource').outcome == SUCCESS
        assert result.task(':dbmodel:loadSingleSource').outcome == SUCCESS
        // one source: the multiple-source path is not taken
        assert result.task(':dbmodel:integrateMultipleSources').outcome == SKIPPED
        // the pre-retrieve properties of the fasta jar, with ${pre.message} taken from the source's properties
        assert result.output.contains('stand-in pre-retrieve: {literal=fixed, message=hello a}')
        // the loader of fasta.properties, on the files that fasta.includes selects in src.data.dir
        assert result.output.contains(
                'stand-in direct loader: source=fasta-a type=fasta os=integration.production files=[one.fa]')
        assert !result.output.contains('fasta-b')
        assert !result.output.contains('No appenders could be found')
    }

    @Test
    void integrateAllSourcesLoadsEachOneInTheOrderOfProjectXml() {
        TestMine mine = pipelineMine('integrate-multiple')

        BuildResult result = mine.run(':dbmodel:integrate')
        assert result.task(':dbmodel:integrateMultipleSources').outcome == SUCCESS
        assert result.task(':dbmodel:integrateSingleSource').outcome == SKIPPED
        String output = result.output
        List<String> lines = [
                'stand-in pre-retrieve: {literal=fixed, message=hello a}',
                'stand-in direct loader: source=fasta-a type=fasta os=integration.production files=[one.fa]',
                'stand-in pre-retrieve: {literal=fixed, message=hello b}',
                'stand-in direct loader: source=fasta-b type=fasta os=integration.production files=[three.fa, two.fa]',
        ]
        List<Integer> positions = lines.collect { output.indexOf(it) }
        assert positions.every { it >= 0 }
        assert positions == positions.sort(false)
    }

    @Test
    void integrateRejectsASourceThatProjectXmlDoesNotHave() {
        TestMine mine = pipelineMine('integrate-unknown')
        String output = mine.runner(':dbmodel:integrate', '-Psource=nothere').buildAndFail().output
        assert output.contains("Can't find source nothere")
    }

    @Test
    void postProcessRunsEachProcessWithTheClassOfItsJar() {
        TestMine mine = pipelineMine('postprocess')

        BuildResult result = mine.run(':dbmodel:postProcess')
        assert result.task(':dbmodel:postProcess').outcome == SUCCESS
        // create-references: the class is named by create-references.properties in the bio-postprocess jar, and the user
        // properties of project.xml are Ant properties
        assert result.output.contains(
                'stand-in PostProcessorTask: class=org.example.CreateReferences os=osw.production pp.prop=42 source.name=null')
        // do-sources: the class is named by fasta.properties in the jar of each source's type
        assert result.output.contains(
                'stand-in PostProcessorTask: class=org.example.FastaPostProcess os=osw.production pp.prop=null source.name=fasta-a')
        assert result.output.contains(
                'stand-in PostProcessorTask: class=org.example.FastaPostProcess os=osw.production pp.prop=null source.name=fasta-b')
        assert result.output.contains('Postprocess do-sources completed.')

        // -Pprocess and -Psource choose
        BuildResult one = mine.run(':dbmodel:postProcess', '-Pprocess=do-sources', '-Psource=fasta-b')
        assert !one.output.contains('CreateReferences')
        assert !one.output.contains('source.name=fasta-a')
        assert one.output.contains('source.name=fasta-b')
    }

    @Test
    void postProcessRejectsAProcessThatProjectXmlDoesNotHave() {
        TestMine mine = pipelineMine('postprocess-unknown')
        String output = mine.runner(':dbmodel:postProcess', '-Pprocess=nothere').buildAndFail().output
        assert output.contains("Can't find postProcess nothere")
    }
}

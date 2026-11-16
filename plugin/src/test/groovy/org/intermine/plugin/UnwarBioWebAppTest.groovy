package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.Test

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/** unwarBioWebApp is a Sync: build/explodedWebApp holds the current bio-webapp war and nothing else (im-ee7.41). */
class UnwarBioWebAppTest {

    @Test
    void aFileOfAnEarlierBioWebAppDoesNotSurvive() {
        TestMine mine = TestMine.create('unwar')
        File exploded = mine.file('webapp/build/explodedWebApp')

        BuildResult first = mine.run(':webapp:unwarBioWebApp')
        assert first.task(':webapp:unwarBioWebApp').outcome == SUCCESS
        assert new File(exploded, 'old-version-only.jsp').isFile()
        String strutsConfig = '<struts-config><action path="/modelAction"/><form-bean name="modelForm"/></struts-config>'
        assert new File(exploded, 'WEB-INF/struts-config.xml').text == strutsConfig

        // up to date, and the directory is as the last run left it, additions included
        BuildResult second = mine.run(':webapp:unwarBioWebApp')
        assert second.task(':webapp:unwarBioWebApp').outcome == UP_TO_DATE
        assert new File(exploded, 'WEB-INF/struts-config.xml').text == strutsConfig

        // a new bio-webapp version without that file, and a stray file in the directory
        mine.publishBioWebApp('6.0.1', ['new-version-only.jsp': '<p>only in the new version</p>'])
        File properties = mine.file('gradle.properties')
        properties.text = properties.text.replace('systemProp.bioVersion=6.0.0', 'systemProp.bioVersion=6.0.1')
        new File(exploded, 'stray.txt').text = 'left by something else'

        BuildResult third = mine.run(':webapp:unwarBioWebApp')
        assert third.task(':webapp:unwarBioWebApp').outcome == SUCCESS
        assert !new File(exploded, 'old-version-only.jsp').exists()
        assert !new File(exploded, 'stray.txt').exists()
        assert new File(exploded, 'new-version-only.jsp').isFile()
        // the additions are made once, on the fresh copy
        assert new File(exploded, 'WEB-INF/struts-config.xml').text == strutsConfig
        assert new File(exploded, 'WEB-INF/classes/InterMineWebApp.properties').text ==
                'webapp.label=InterMine\nmodel.label=Model label\n'
    }
}

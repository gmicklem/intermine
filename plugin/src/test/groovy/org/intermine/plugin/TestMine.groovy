package org.intermine.plugin

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A small synthetic mine for the TestKit tests: a root project with a `dbmodel` project (dbmodel plugin) and a
 * `webapp` project (war and webapp plugins), laid out and wired like a converted HumanMine (docs/UPGRADING-build.md:
 * `copyMineProperties` into build/im/mine-properties, taken by processResources), and a file Maven repository with
 * the artifacts that the plugin resolves, faked: intermine-resources (default.intermine.properties), bio-webapp (a war
 * with web.xml, struts-config.xml, tiles-defs.xml, web-bio.xml, InterMineWebApp.properties, a jar and a JSP), bio-model
 * (the stand-in SOToModelTask of src/testKitTasks), org.example:foo:1.0 and the legacy ant:ant:1.6.5.
 *
 * Each mine is a directory of its own under build/testkit/mines, emptied when the test starts and left behind for
 * inspection. The builds run in TestKit daemons whose Gradle user home is build/testkit/gradle.
 */
class TestMine {
    static final File TESTKIT_DIR = new File(System.getProperty('testkit.dir'))
    static final String VERSION = '5.1.1'
    static final String MINE_JSP = '<p>the mine\'s own dataCategories.jsp</p>\n'
    static final String BIO_WEBAPP_JSP = '<p>dataCategories.jsp of bio-webapp</p>\n'

    final File root
    final File repo

    TestMine(String name) {
        root = new File(TESTKIT_DIR, 'mines/' + name)
        root.deleteDir()
        root.mkdirs()
        repo = new File(root, 'repo')
    }

    /** The standard mine; {@code webappBuild} is appended to webapp/build.gradle. */
    static TestMine create(String name, String webappBuild = '') {
        TestMine mine = new TestMine(name)
        mine.write('settings.gradle', """\
            rootProject.name = 'testmine'
            include 'dbmodel', 'webapp'
            """)
        mine.write('gradle.properties', """\
            systemProp.imVersion=${VERSION}
            systemProp.bioVersion=${VERSION}
            org.gradle.jvmargs=-Xmx512m -Dfile.encoding=UTF-8
            """)
        mine.write('build.gradle', """\
            plugins {
                id 'dbmodel' apply false
                id 'webapp' apply false
            }
            subprojects {
                apply plugin: 'java'
                repositories {
                    maven { url = uri('${mine.repo.toURI()}') }
                }
                tasks.register('copyMineProperties', Copy) {
                    description = 'Copies mine specific intermine.properties file'
                    from "\$rootDir/testmine.properties"
                    into layout.buildDirectory.dir('im/mine-properties')
                    rename { 'intermine.properties' }
                }
                processResources {
                    from tasks.named('copyMineProperties')
                }
            }
            """)
        // what ~/.intermine/<mine>.properties is to a mine
        mine.write('testmine.properties', """\
            project.title=Test mine
            shared.key=from-intermine-properties
            """)

        mine.write('dbmodel/build.gradle', """\
            apply plugin: 'dbmodel'
            dependencies {
                // a mine's dbmodel gets reload4j through intermine-integrate
                implementation files('${path(System.getProperty('testkit.reload4jJar'))}')
            }
            dbModelConfig {
                // relative to the mine's root, as in a mine's dbmodel/build.gradle
                soTermListFilePath = 'dbmodel/resources/so_terms'
                soAdditionFilePath = 'dbmodel/build/so_additions.xml'
            }
            """)
        mine.write('dbmodel/resources/so_terms', 'gene\ntranscript\n')

        mine.write('webapp/build.gradle', """\
            apply plugin: 'war'
            apply plugin: 'webapp'
            webappConfig {
                defaultInterminePropertiesFile = 'default.intermine.webapp.properties'
            }
            dependencies {
                // also in WEB-INF/lib of bio-webapp: the war must list it once
                implementation 'org.example:foo:1.0'
            }
            """.stripIndent() + webappBuild.stripIndent())
        mine.write('webapp/src/main/webapp/WEB-INF/web.properties', """\
            webapp.only=from-web-properties
            shared.key=from-web-properties
            """)
        mine.write('webapp/src/main/webapp/dataCategories.jsp', MINE_JSP)
        // the model's struts, tiles and message fragments that unwarBioWebApp puts into the bio-webapp files
        mine.write('webapp/src/main/resources/struts-config-model.xml', '<action path="/modelAction"/>')
        mine.write('webapp/src/main/resources/struts-config-form-model.xml', '<form-bean name="modelForm"/>')
        mine.write('webapp/src/main/resources/tiles-defs-model.xml', '<definition name="model.tile"/>')
        mine.write('webapp/src/main/resources/model.properties', 'model.label=Model label\n')

        mine.publish('org.intermine', 'intermine-resources', VERSION, 'jar', zip([
                'default.intermine.webapp.properties': 'default.only=from-defaults\nshared.key=from-defaults\n']))
        mine.publishBioWebApp(VERSION, ['old-version-only.jsp': '<p>only in this version of bio-webapp</p>'])
        mine.publish('org.intermine', 'bio-model', VERSION, 'jar', new File(System.getProperty('testkit.tasksJar')).bytes)
        mine.publish('org.example', 'foo', '1.0', 'jar', zip(['foo.txt': 'foo']))
        mine.publish('ant', 'ant', '1.6.5', 'jar', zip(['org/apache/tools/ant/legacy.txt': 'ant 1.6.5']))
        mine
    }

    /** A bio-webapp war: the files that unwarBioWebApp edits, a jar, a JSP and {@code extra}. */
    void publishBioWebApp(String version, Map<String, Object> extra) {
        Map<String, Object> entries = [
                'WEB-INF/web.xml': '<web-app><!--@MODEL_INCLUDE@--></web-app>',
                'WEB-INF/web-bio.xml': '<servlet>bio</servlet>',
                'WEB-INF/struts-config.xml': '<struts-config><!--@MODEL_INCLUDE@--><!--@MODEL_FORM_INCLUDE@--></struts-config>',
                'WEB-INF/tiles-defs.xml': '<tiles-definitions><!--@MODEL_INCLUDE@--></tiles-definitions>',
                'WEB-INF/classes/InterMineWebApp.properties': 'webapp.label=InterMine\n',
                'WEB-INF/web.properties': 'bio.webapp.only=from-bio-webapp\n',
                'WEB-INF/lib/foo-1.0.jar': zip(['foo.txt': 'foo']),
                'dataCategories.jsp': BIO_WEBAPP_JSP,
        ]
        entries.putAll(extra)
        publish('org.intermine', 'bio-webapp', version, 'war', zip(entries))
    }

    File file(String path) {
        new File(root, path)
    }

    File write(String path, String text) {
        File f = file(path)
        f.parentFile.mkdirs()
        f.text = text.stripIndent()
        f
    }

    /** Puts an artifact into the mine's file Maven repository, with a minimal POM. */
    void publish(String group, String name, String version, String ext, byte[] content) {
        File dir = new File(repo, group.replace('.', '/') + '/' + name + '/' + version)
        dir.mkdirs()
        new File(dir, "${name}-${version}.${ext}").bytes = content
        new File(dir, "${name}-${version}.pom").text = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>${group}</groupId>
  <artifactId>${name}</artifactId>
  <version>${version}</version>
  <packaging>${ext}</packaging>
</project>
"""
    }

    GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(root)
                .withTestKitDir(new File(TESTKIT_DIR, 'gradle'))
                .withPluginClasspath()
                .withArguments(['--stacktrace', '--warning-mode', 'all'] + arguments.toList())
                .forwardOutput()
    }

    /** Runs the build, which must succeed without a Gradle deprecation warning (it runs with --warning-mode all). */
    BuildResult run(String... arguments) {
        BuildResult result = runner(arguments).build()
        List<String> deprecations = result.output.readLines().findAll {
            it.contains('has been deprecated') && !it.startsWith('Executing Gradle on JVM versions 16 and lower')
        }
        assert deprecations == []
        result
    }

    /** The paths of the build's tasks that ran or were checked, e.g. [':webapp:war', ...]. */
    static List<String> taskPaths(BuildResult result) {
        result.tasks*.path
    }

    static String path(String file) {
        file.replace('\\', '/')
    }

    static byte[] zip(Map<String, Object> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ZipOutputStream(bytes).withCloseable { ZipOutputStream out ->
            entries.each { String name, Object content ->
                out.putNextEntry(new ZipEntry(name))
                out.write(content instanceof byte[] ? (byte[]) content : content.toString().getBytes('UTF-8'))
                out.closeEntry()
            }
        }
        bytes.toByteArray()
    }

    /** Every entry of a zip in order, a name that is repeated as often as it occurs (ZipFile would list it once). */
    static List<String> entryNames(File zip) {
        List<String> names = []
        new ZipInputStream(new FileInputStream(zip)).withCloseable { ZipInputStream input ->
            ZipEntry entry
            while ((entry = input.nextEntry) != null) {
                names << entry.name
            }
        }
        names
    }

    /** The text of the first entry of that name. */
    static String entryText(File zip, String name) {
        new ZipInputStream(new FileInputStream(zip)).withCloseable { ZipInputStream input ->
            ZipEntry entry
            while ((entry = input.nextEntry) != null) {
                if (entry.name == name) {
                    return new String(input.bytes, 'UTF-8')
                }
            }
            throw new AssertionError("no entry ${name} in ${zip}")
        }
    }
}

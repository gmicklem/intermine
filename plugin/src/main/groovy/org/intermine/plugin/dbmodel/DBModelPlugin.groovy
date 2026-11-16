package org.intermine.plugin.dbmodel

import groovy.ant.AntBuilder
import groovy.xml.XmlParser
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.util.PatternSet
import org.intermine.plugin.TaskConstants
import org.intermine.plugin.project.ProjectXmlBinding
import org.intermine.plugin.project.Source

/**
 * Model and database tasks shared by every project that holds an InterMine data model.
 *
 * Tasks are registered lazily and wired through their inputs. There are no init tasks: the
 * dbModelConfig extension and the configurations are created when the plugin is applied, and the
 * dependencies the plugin needs are added when the configuration is resolved (withDependencies),
 * so they still see a version set in gradle.properties or a project.xml edited before the build.
 */
class DBModelPlugin implements Plugin<Project> {
    /** Plugin-internal configuration holding antlr-complete for runIQLQuery. */
    static final String IM_ANTLR = "imAntlr"

    DBModelConfig config
    DBModelUtils dbUtils

    void apply(Project project) {
        project.pluginManager.apply(JavaPlugin)
        config = project.extensions.create('dbModelConfig', DBModelConfig)
        dbUtils = new DBModelUtils(project)

        // Resolvable only: nothing should consume them. imApiJar and imAntlr used to be called api
        // and antlr, which clash with the java-library and antlr plugins (Gradle 7's antlr plugin
        // applies java-library).
        ['bioModel', 'bioModelForSOOnly', 'commonResources', 'mergeSource', DBModelUtils.IM_API_JAR,
         IM_ANTLR].each { name ->
            DBModelUtils.resolvableConfiguration(project, name)
        }
        addDependency(project, 'commonResources') {
            [group: "org.intermine", name: "intermine-resources", version: System.getProperty("imVersion")]
        }
        addDependency(project, DBModelUtils.IM_API_JAR) {
            [group: "org.intermine", name: "intermine-api", version: System.getProperty("imVersion"), transitive: false]
        }
        // we need bio-model for the SO files. It may be on the classpath already but we can't be sure.
        addDependency(project, 'bioModelForSOOnly') {
            [group: "org.intermine", name: "bio-model", version: System.getProperty("bioVersion"), transitive: false]
        }
        addDependency(project, IM_ANTLR) {
            [group: "org.antlr", name: "antlr-complete", version: "3.5.2", transitive: false]
        }
        // The bio-source jars of every source in the mine's project.xml (this replaces the old
        // addSourceDependencies task). Read when mergeSource is resolved, by generateKeys and mergeModels.
        project.configurations.getByName('mergeSource').withDependencies { DependencySet deps ->
            File projectXmlFile = projectXml(project)
            if (!projectXmlFile.exists()) {
                return
            }
            def parser = new XmlParser()
            parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
            def projectXml = parser.parse(projectXmlFile)
            projectXml.sources.source.each { source ->
                String version = System.getProperty("bioVersion")
                if (source.'@version' != null) {
                    version = source.'@version'
                }
                if (source.@type == "intermine-items-xml-file") {
                    deps.add(dbUtils.bioSourceDependency(source.'@name', version))
                }
                deps.add(dbUtils.bioSourceDependency(source.'@type', version))
            }
        }

        // The mine's intermine.properties (database user and password) reaches build/resources/main through
        // processResources, where the Ant tasks and the tests read it from. It must not be packed into the jar,
        // which is published or copied into a war.
        project.tasks.named('jar').configure { exclude 'intermine.properties' }

        // The files below are for the plugin's own tasks and the tests, not for the jar: each task writes them into
        // a directory of its own (DBModelUtils.taskDir, build/im/<task>), which it declares as its output, so that it is
        // up to date when its inputs have not changed. build/resources/main, which the jar packs and javadoc reads,
        // holds what processResources puts there and nothing else. The Ant tasks find the directories on their
        // classpath (DBModelUtils.imDir); the tests through testReads; every other consumer names the file it reads
        // (DBModelUtils.output), which schedules the producer.
        project.tasks.register('copyDefaultInterMineProperties') {
            description = "Copies the default.intermine.properties file (defaultInterminePropertiesFile) into build/im, for the tests and the Ant tasks"
            inputs.files({ project.configurations.getByName("commonResources") })
            inputs.property('defaultInterminePropertiesFile', { config.defaultInterminePropertiesFile ?: '' })
            outputs.dir(DBModelUtils.taskDir(project, name))
            doLast {
                dbUtils.copyDefaultPropertiesFile(config.defaultInterminePropertiesFile, DBModelUtils.cleanTaskDir(project, name))
            }
        }
        DBModelUtils.testReads(project, ['copyDefaultInterMineProperties'])

        project.tasks.register('copyGenomicModel') {
            description = "Kept for build files that name it: the model now comes from mergeModels, which starts from the genomic model of bio-model"
            // lifecycle alias: it has no work of its own (it used to copy genomic_model.xml into build/resources/main,
            // where mergeModels then merged into it in place)
            dependsOn 'mergeModels'
        }

        project.tasks.register('copyGenomicKeys') {
            description = "Copies default keys for bio in the case of unit tests."
            inputs.files({ modelArchive(project) })
            inputs.property('modelName', { config.modelName ?: '' })
            outputs.dir(DBModelUtils.taskDir(project, name))
            doLast {
                File dir = DBModelUtils.cleanTaskDir(project, name)
                DBModelUtils.copyFromArchive(project, modelArchive(project).singleFile, "genomic_keyDefs.properties",
                        dir.absolutePath + File.separator + config.modelName + "_keyDefs.properties")
            }
        }

        project.tasks.register('generateKeys') {
            description = "Append keys for each source in project.xml to generated genomic_keyDefs.properties file"
            onlyIf { projectXml(project).exists() }
            // project.xml and the bio-source jars it names (mergeSource), whose keys files are merged
            inputs.files({ projectXml(project).exists() ? [projectXml(project)] : [] })
            inputs.files({ project.configurations.getByName("mergeSource") })
            inputs.property('modelName', { config.modelName ?: '' })
            outputs.dir(DBModelUtils.taskDir(project, name))

            doLast {

                // parse project XML for each data source
                org.intermine.plugin.project.Project intermineProject = ProjectXmlBinding.unmarshall(projectXml(project))
                List<String> sourceNames = new ArrayList<String>()
                intermineProject.sources.keySet().each { sourceName ->
                    sourceNames.add(sourceName)
                }

                Properties keysProperties = new Properties()

                // append each source keys file to make one big keys file
                sourceNames.each { sourceName ->

                    Source source = intermineProject.sources.get(sourceName)
                    String sourceVersion = source.getVersion()
                    String sourceType = source.getType()
                    FileTree dataSourceJar = null
                    File sourceKeysFile = null

                    // Prefix because actual value of the version string is 2.+ while the real version is 2.0.0
                    // Also versions might be strings, so can't use regular expressions (eg. RC or SNAPSHOT)
                    // have to include the version number at all because go-annotation will match go
                    String bioVersionPrefix = System.getProperty("bioVersion").substring(0, 1)
                    if (sourceVersion != null && sourceVersion != "") {
                        bioVersionPrefix = sourceVersion
                    }


                    project.configurations.getByName("mergeSource").asFileTree.each {
                        if (it.name.startsWith("bio-source-$sourceName-$bioVersionPrefix")) {
                             dataSourceJar = project.zipTree(it)
                        }
                    }

                    if (dataSourceJar == null) {
                        project.configurations.getByName("mergeSource").asFileTree.each {
                            if (it.name.startsWith("bio-source-$sourceType-$bioVersionPrefix")) {
                                dataSourceJar = project.zipTree(it)
                            }
                        }
                    }

                    if (dataSourceJar == null) {
                        throw new RuntimeException("Failed to find JAR: 'bio-source-" + sourceType + "-"
                                + bioVersionPrefix + "*.jar' OR 'bio-source-" + sourceName + "-"
                                + bioVersionPrefix + "*.jar'")
                    }

                    System.out.println("Processing ${sourceName}_keys.properties")

                    PatternSet patternSet = new PatternSet()
                    patternSet.include("${sourceName}_keys.properties")
                    if (!dataSourceJar.matching(patternSet).empty) {
                        sourceKeysFile = dataSourceJar.matching(patternSet).singleFile
                    }
                    if (sourceKeysFile == null) {
                        System.out.println("Processing ${sourceType}_keys.properties now, " +
                                "as didn't find ${sourceName}_keys.properties in 'bio-source-" + sourceType
                                + "-" + bioVersionPrefix + "*.jar'")
                        patternSet.include("${sourceType}_keys.properties")
                        if (dataSourceJar.matching(patternSet).empty) {
                            throw new RuntimeException("No keys file found for " + sourceName + " (or "
                                    + sourceType + ") in 'bio-source-" + sourceType + "-" + bioVersionPrefix
                                    + "*.jar'. Please add this file and try your build again.")
                        }
                        sourceKeysFile = dataSourceJar.matching(patternSet).singleFile
                    }

                    Properties sourceProperties = new Properties()
                    sourceProperties.load(sourceKeysFile.newDataInputStream())
                    keysProperties.putAll(sourceProperties)
                }
                File keysFile = new File(DBModelUtils.cleanTaskDir(project, name), config.modelName + "_keyDefs.properties")
                keysFile.withWriter { w -> keysProperties.store(w, null) }
            }
        }

        project.tasks.register('createSoModel') {
            description = "Reads SO OBO files and writes so_additions.xml"
            onlyIf { regenerateModel(project) }
            // the SO term list and SOToModelTask's bio-model (whose so.obo it reads), and the so_additions.xml it writes
            inputs.files({ rootFiles(project, config.soTermListFilePath) })
            inputs.files({ project.name.equals("bio-model") ? [] : project.configurations.getByName("bioModelForSOOnly") })
            outputs.files({ rootFiles(project, config.soAdditionFilePath) })

            doLast {
                def ant = new AntBuilder()
                //when we execute bio-model build, bio-core might be not installed yet
                if (project.name.equals("bio-model")) {
                    ant.taskdef(name: "createSoModel", classname: "org.intermine.bio.task.SOToModelTask") {
                        classpath {
                            dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                            pathelement(location: DBModelUtils.antLoggingDir(project).absolutePath) // log4j.properties of the Ant tasks
                            dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                            pathelement(path: DBModelUtils.antClasspath(project).asPath)
                        }
                    }
                } else {
                    ant.taskdef(name: "createSoModel", classname: "org.intermine.bio.task.SOToModelTask") {
                        classpath {
                            pathelement(path: project.configurations.getByName("bioModelForSOOnly").asPath)
                            pathelement(location: DBModelUtils.antLoggingDir(project).absolutePath) // log4j.properties of the Ant tasks
                            pathelement(path: DBModelUtils.antClasspath(project).asPath)
                        }
                    }
                }
                // Ant resolves a relative path against the working directory of the JVM, which is the Gradle daemon's own
                // directory since Gradle 8: resolve it against the mine's root directory first
                ant.createSoModel(soTermListFile: DBModelUtils.rootPath(project, config.soTermListFilePath),
                        outputFile: DBModelUtils.rootPath(project, config.soAdditionFilePath))
            }
        }

        project.tasks.register('generateModel') {
            description = "Merges different source model files into an intermine XML model"
            onlyIf { regenerateModel(project) }
            // mergeModels (added by the biocore-, biomodel-, biosource- or mine-dbmodel plugin) writes the
            // merged <modelName>_model.xml into build/im/mergeModels; this generates the Java from it.
            inputs.files(DBModelUtils.output(project, 'mergeModels') { config.modelName + "_model.xml" })
            outputs.dir(project.layout.buildDirectory.dir("gen"))

            doLast {
                dbUtils.generateModel(config.modelName)
            }
        }

        // lifecycle (kept): the generated model sources must exist before compileJava. Mines replace
        // the source dirs with srcDirs = ['build/gen'], which would drop a srcDir added lazily here.
        project.tasks.named('compileJava').configure { dependsOn 'generateModel' }

        project.tasks.register('buildDB') {
            group = TaskConstants.TASK_GROUP
            description = "Build the database for the webapp"
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties(project) +
                    [DBModelUtils.output(project, 'mergeModels') { config.modelName + "_model.xml" },
                     DBModelUtils.output(project, 'generateKeys') { config.modelName + "_keyDefs.properties" }])

            doLast {
                dbUtils.createSchema(config.objectStoreName)
                dbUtils.createTables(config.objectStoreName, config.modelName)
                dbUtils.storeMetadata(config.objectStoreName, config.modelName)
                dbUtils.createIndexes(config.objectStoreName, false)
                dbUtils.analyse(config.objectStoreName, config.modelName)

                println "************************************************************************************************************************"
                println "IF YOU CHANGE YOUR DATA MODEL BE SURE TO RUN THE clean TASK BEFORE buildDB ELSE YOU WILL NOT UPDATE THE MINE'S MODEL! :)"
                println "************************************************************************************************************************"
            }
        }

        project.tasks.register('buildUnitTestDB') {
            description = "Build the database for the webapp"
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties(project) +
                    [DBModelUtils.output(project, 'mergeModels') { config.modelName + "_model.xml" },
                     DBModelUtils.output(project, 'copyGenomicKeys') { config.modelName + "_keyDefs.properties" }])

            doLast {
                dbUtils.createSchema(config.objectStoreName)
                dbUtils.createTables(config.objectStoreName, config.modelName)
                dbUtils.storeMetadata(config.objectStoreName, config.modelName)
            }
        }

        project.tasks.register('copyUserProfileModel') {
            description = "Copies the userprofile model of intermine-api into build/im, for createUserDB"
            inputs.files({ project.configurations.getByName(DBModelUtils.IM_API_JAR) })
            inputs.property('userProfileModelName', { config.userProfileModelName ?: '' })
            outputs.dir(DBModelUtils.taskDir(project, name))

            doLast {
                File dir = DBModelUtils.cleanTaskDir(project, name)
                DBModelUtils.copyFromArchive(project, project.configurations.getByName(DBModelUtils.IM_API_JAR).singleFile,
                        "userprofile_model.xml", dir.absolutePath + File.separator + config.userProfileModelName + "_model.xml")
            }
        }

        project.tasks.register('createUserDB') {
            group = TaskConstants.TASK_GROUP
            description = "Creates empty tables in the userprofile database."
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties(project) +
                    [DBModelUtils.output(project, 'copyUserProfileModel') { config.userProfileModelName + "_model.xml" }])

            doFirst {
                dbUtils.createSchema(config.userProfileObjectStoreName)
                dbUtils.createTables(config.userProfileObjectStoreName, config.userProfileModelName)
                dbUtils.storeMetadata(config.userProfileObjectStoreName, config.userProfileModelName)
            }
        }

        project.tasks.register('buildUserDB') {
            group = TaskConstants.TASK_GROUP
            description = "Creates empty tables in the userprofile database, then loads the superuser and default templates."
            // lifecycle (kept): an aggregate task with no action of its own.
            dependsOn 'createUserDB', ':webapp:loadDefaultTemplates'
        }

        project.tasks.register('runAcceptanceTests') {
            group = TaskConstants.TASK_GROUP
            description = "Run the configured acceptance tests against the production database, uses <MINE_NAME>_acceptance_test.conf"
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties(project))

            doLast {
                def ant = new AntBuilder()

                String buildResourcesMainDir = dbUtils.buildResourcesMainDir
                String outputFilePath = buildResourcesMainDir + File.separator + "acceptance_test.html"
                String acceptanceTestFilePath = buildResourcesMainDir + File.separator + config.mineName + "_acceptance_test.conf"

                ant.taskdef(name: "acceptanceTest", classname: "org.intermine.task.AcceptanceTestTask") {
                    classpath {
                        dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                        pathelement(location: DBModelUtils.antLoggingDir(project).absolutePath) // log4j.properties of the Ant tasks
                        dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                    }
                }
                ant.acceptanceTest(database: "db.production", configFile: acceptanceTestFilePath,
                    outputFile: outputFilePath)
            }
        }

        project.tasks.register('runIQLQuery') {
            group = TaskConstants.TASK_GROUP
            description = "Run an IQL query against the database, e.g. -Pquery=SELECT DISTINCT a2_.primaryIdentifier AS a1_ FROM org.intermine.model.bio.Gene AS a2_"
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties(project))

            doLast {
                def ant = new AntBuilder()
                if (project.hasProperty('query')) {
                    def query = project.properties['query']
                    ant.taskdef(name: "runIQLQuery", classname: "org.intermine.task.RunIqlQueryTask") {
                        classpath {
                            dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                            pathelement(location: DBModelUtils.antLoggingDir(project).absolutePath) // log4j.properties of the Ant tasks
                            dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                            pathelement(path: DBModelUtils.antClasspath(project).asPath)
                            pathelement(path: project.configurations.getByName(IM_ANTLR).asPath)
                        }
                    }
                    ant.runIQLQuery(alias: config.objectStoreName, query: query)
                }
            }
        }

        project.tasks.register('generateUpdateTriggers') {
            group = TaskConstants.TASK_GROUP
            description = "Generates SQL files for manual CRUD operations on InterMine database. THIS IS AN EXPERIMENTAL FEATURE, USE AT YOUR OWN RISK."
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties(project))

            doLast {
                def ant = new AntBuilder()
                String buildResourcesMainDir = dbUtils.buildResourcesMainDir
                ant.taskdef(name: "generateUpdateTriggers", classname: "org.intermine.task.GenerateUpdateTriggersTask") {
                    classpath {
                        dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                        pathelement(location: DBModelUtils.antLoggingDir(project).absolutePath) // log4j.properties of the Ant tasks
                        dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                    }
                }
                ant.generateUpdateTriggers(osName: config.objectStoreName, destDir: buildResourcesMainDir)
            }
        }
    }

    /** Adds a dependency to a configuration when it is resolved, so that the notation is read late. */
    static void addDependency(Project project, String configuration, Closure<Map> notation) {
        project.configurations.getByName(configuration).withDependencies { DependencySet deps ->
            deps.add(project.dependencies.create(notation.call()))
        }
    }

    static File projectXml(Project project) {
        new File(project.getParent().getProjectDir(), "project.xml")
    }

    /** The file at a path of dbModelConfig (DBModelUtils.rootPath), as a list for a task input or output: empty if unset. */
    static List<File> rootFiles(Project project, String path) {
        path == null ? [] : [new File(DBModelUtils.rootPath(project, path))]
    }

    static File genDir(Project project) {
        DBModelUtils.buildFile(project, "gen")
    }

    /**
     * The model is generated only while build/gen does not exist: deployers run clean to pick up a
     * model change. Checked when each task runs (it used to be read once, by initConfig), so that
     * `clean build` in one invocation still regenerates.
     */
    static boolean regenerateModel(Project project) {
        !genDir(project).exists()
    }

    /** The archive genomic_model.xml and genomic_keyDefs.properties come from. */
    static Configuration modelArchive(Project project) {
        // testModel is not defined by any known build; kept for builds that define it.
        project.configurations.findByName("testModel") ?: project.configurations.getByName("bioModel")
    }
}

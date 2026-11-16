package org.intermine.plugin.dbmodel

import groovy.ant.AntBuilder
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.util.PatternSet

import java.nio.file.Files
import java.nio.file.StandardCopyOption

class DBModelUtils {
    /** Plugin-internal configuration holding the intermine-api jar (userprofile model and keys). */
    static final String IM_API_JAR = "imApiJar"

    Project project

    /**
     * Classpath for the InterMine Ant tasks the plugin runs: the project's runtime classpath.
     * It replaces the old `compile` configuration, which Gradle 6 deprecates for resolution and
     * Gradle 7 removes. runtimeClasspath exists in Gradle 4.9 too and is a superset of `compile`
     * (it also holds runtime-only dependencies and, for war projects, providedCompile).
     */
    static Configuration antClasspath(Project project) {
        project.configurations.getByName("runtimeClasspath")
    }

    /**
     * Creates, or gets if the build script already made it, a configuration that the plugin only
     * resolves: it is never published and no other project consumes it. Since Gradle 8.0 two
     * consumable configurations with identical attributes are an error, so it must not be consumable.
     */
    static Configuration resolvableConfiguration(Project project, String name) {
        Configuration configuration = project.configurations.maybeCreate(name)
        configuration.canBeConsumed = false
        configuration.canBeResolved = true
        configuration
    }

    /**
     * The project's build directory (layout.buildDirectory: Project.getBuildDir() is deprecated from
     * Gradle 8.3), read when called so that a build script can still move it after apply. A File,
     * because the Ant tasks take plain paths.
     */
    static File buildDirectory(Project project) {
        project.layout.buildDirectory.get().asFile
    }

    /** A file or directory under the build directory (see buildDirectory). */
    static File buildFile(Project project, String path) {
        project.layout.buildDirectory.file(path).get().asFile
    }

    /**
     * A path that the mine's build file gives to the plugin (soTermListFilePath, soAdditionFilePath), as an absolute path.
     * A relative path is taken from the mine's root directory, which is where deployers run the build from and what
     * Ant resolved it against until Gradle 8, whose daemon has its own working directory; an absolute path stays as it is.
     * Null stays null (the property is not set).
     */
    static String rootPath(Project project, String path) {
        path == null ? null : project.rootProject.file(path).absolutePath
    }

    /** The main source set's resources output directory, where processResources puts the files of the jar. */
    static String resourcesDir(Project project) {
        sourceSets(project).getByName("main").getOutput().resourcesDir.toString()
    }

    static SourceSetContainer sourceSets(Project project) {
        project.extensions.getByType(SourceSetContainer)
    }

    /**
     * The directory that the plugin's task {@code taskName} writes: build/im/<taskName>, its declared output. The
     * plugin's files that the jar must not carry (the test database configuration, the model and keys of the unit
     * tests, so.obo) used to be written into build/resources/main, processResources' output, so that the jar and javadoc
     * saw them, or not, depending on which tasks had run, and were never up to date. Each producer has a directory of
     * its own, so that it can declare it as its output without overlapping another task's.
     */
    static File taskDir(Project project, String taskName) {
        buildFile(project, "im/" + taskName)
    }

    /**
     * The parent of every taskDir. The plugin's Ant tasks put its subdirectories first on their classpath
     * ({@code dirset(dir: imDir, includes: '*')}), before the dirset of the whole build directory, whose scan order is
     * that of the file system: a file of a task (the merged model, generateKeys' genomic_keyDefs.properties) then wins
     * over one of the same name in build/resources/main or in a jar, as it did when the tasks overwrote the copy in
     * build/resources/main.
     */
    static File imDir(Project project) {
        buildFile(project, "im")
    }

    /**
     * The directory with the log4j.properties of the plugin's Ant tasks, on the classpath of each of them. The tasks
     * run InterMine code in an Ant class loader of its own with the log4j 1 API (reload4j), which finds no
     * configuration unless the classpath has one (intermine-resources only has the log4j 2 file that nothing reads
     * since the move to reload4j): every Logger then printed "log4j:WARN No appenders could be found" and the
     * integrate and postprocess messages were lost. The file has a console appender; its level follows Gradle's: INFO
     * at the default level and with -i, WARN with -q and -w, org.intermine at DEBUG with -d. A mine that has its own
     * log4j.properties or log4j.xml in its resources (build/resources/main) keeps it: then no file is written.
     * It is not a task output: it only depends on the log level of the run, which must not make the Ant tasks out of
     * date, so it is written when a task builds its classpath (a task action), when its content has changed.
     * It is a directory of its own, build/im/antLogging, which is also on the classpath through the dirset of imDir.
     * It also sets the system property log4j.ignoreTCL (see the comment in the method).
     */
    static File antLoggingDir(Project project) {
        // Gradle's own class loader, the thread context class loader of the Ant task, has the classes of log4j-over-slf4j
        // (Appender, ConsoleAppender, PatternLayout...), and reload4j in the Ant class loader loads the classes that the
        // configuration names through the context class loader first: its Appender interface is then not the one that
        // the ConsoleAppender from Gradle implements ("not assignable to a Appender variable"), the appender is dropped and
        // the warning stays. log4j.ignoreTCL makes it load them from its own class loader. Only reload4j reads it.
        System.setProperty("log4j.ignoreTCL", "true")
        File dir = taskDir(project, "antLogging")
        File config = new File(dir, "log4j.properties")
        File resources = new File(resourcesDir(project))
        if (new File(resources, "log4j.properties").exists() || new File(resources, "log4j.xml").exists()) {
            config.delete()
            return dir
        }
        LogLevel level = project.gradle.startParameter.logLevel
        String root = (level == LogLevel.INFO || level == LogLevel.DEBUG || level == LogLevel.LIFECYCLE) ? "INFO" : "WARN"
        String text = """# written by the InterMine Gradle plugin (DBModelUtils.antLoggingDir) for the Ant tasks; the level follows Gradle's
log4j.rootLogger=${root}, console
log4j.logger.org.intermine=${level == LogLevel.DEBUG ? "DEBUG" : root}
log4j.appender.console=org.apache.log4j.ConsoleAppender
log4j.appender.console.Target=System.out
log4j.appender.console.layout=org.apache.log4j.PatternLayout
log4j.appender.console.layout.ConversionPattern=%d{HH:mm:ss} %-5p %c{1} - %m%n
"""
        if (!config.exists() || config.text != text) {
            dir.mkdirs()
            config.text = text
        }
        dir
    }

    /** Empties (or creates) a task's own output directory before the task writes it, so that no stale file stays. */
    static File cleanTaskDir(Project project, String taskName) {
        File dir = taskDir(project, taskName)
        dir.deleteDir()
        dir.mkdirs()
        dir
    }

    /**
     * A file in the resources output directory (build/resources/main) that task {@code producer} puts there, as a
     * task input: the consuming task then depends on the producer through its inputs instead of an explicit
     * dependsOn. Only intermine.properties is read this way: a mine's copyMineProperties feeds processResources
     * (or, in a 5.x style build file, copies into the directory itself).
     */
    static FileCollection resource(Project project, String producer, Closure<String> fileName) {
        project.files({ new File(resourcesDir(project), fileName.call()) }).builtBy(producer)
    }

    /** A file that task {@code producer} writes into its own directory (taskDir), as a task input. */
    static FileCollection output(Project project, String producer, Closure<String> fileName) {
        project.files({ new File(taskDir(project, producer), fileName.call()) }).builtBy(producer)
    }

    /**
     * Puts the directories of the given tasks (taskDir) on the test runtime classpath: the files that the tests
     * read and the jar must not carry. They go after the test and main outputs and before the dependencies, whose
     * jars may hold a file of the same name (bio-model's unmerged genomic_model.xml), as when the files were in
     * build/resources/main. The test task depends on the producers through its classpath.
     */
    static void testReads(Project project, List<String> producers) {
        SourceSet test = sourceSets(project).getByName("test")
        SourceSet main = sourceSets(project).getByName("main")
        FileCollection dirs = project.files({ producers.collect { taskDir(project, it) } }).builtBy(producers as Object[])
        test.runtimeClasspath = project.files(test.output, main.output, dirs, test.runtimeClasspath)
    }

    /**
     * The resources output directory as the build leaves it, as a task input: processResources and, where the
     * project has one, the mine's copyMineProperties. Mine build files written for plugin 5.x define
     * copyMineProperties as a Copy into this same directory, which makes the whole directory its declared output;
     * Gradle 8 then fails every task that reads the directory without depending on it.
     */
    static FileCollection processedResources(Project project) {
        project.files({ resourcesDir(project) }).builtBy('processResources',
                { project.tasks.names.contains('copyMineProperties') ? ['copyMineProperties'] : [] })
    }

    /**
     * Declares what a task that runs InterMine code (an Ant task on this project's classpath)
     * reads: the compiled classes and resources (this replaces dependsOn 'jar' / 'compileJava';
     * the Ant tasks use build/ directly, not the jar), the runtime classpath (which also builds
     * project dependencies such as a mine's dbmodel jar), and the runtime files that other tasks
     * write (resource, output).
     */
    static void readsRuntime(Task task, List<FileCollection> files = []) {
        Project project = task.project
        task.inputs.files(sourceSets(project).getByName("main").output)
        task.inputs.files(antClasspath(project))
        files.each { FileCollection file ->
            task.inputs.files(file)
        }
    }

    /**
     * intermine.properties (the mine's own build file copies it into the resources) and default.intermine.properties
     * (copyDefaultInterMineProperties).
     */
    static List<FileCollection> mineProperties(Project project) {
        [resource(project, 'copyMineProperties') { 'intermine.properties' },
         output(project, 'copyDefaultInterMineProperties') { 'default.intermine.properties' }]
    }

    /**
     * Copies one entry of an archive to {@code destination}, creating its directory. This
     * replaces the old createNewFile + renameTo, which did nothing when the destination
     * directory did not exist yet.
     */
    static void copyFromArchive(Project project, File archive, String entry, String destination) {
        FileTree fileTree = project.zipTree(archive)
        PatternSet patternSet = new PatternSet()
        patternSet.include(entry)
        File file = fileTree.matching(patternSet).singleFile
        File dest = new File(destination)
        dest.parentFile.mkdirs()
        Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    DBModelUtils(Project project) {
        this.project = project
    }

    /** Resolved on use, so that a build script can still change the source set after apply. */
    String getBuildResourcesMainDir() {
        resourcesDir(project)
    }

    /** Copies the given file of intermine-resources as default.intermine.properties into {@code destination}. */
    protected copyDefaultPropertiesFile = { String defaultPropertiesFile, File destination ->
        copyFromArchive(project, project.configurations.getByName("commonResources").singleFile,
                defaultPropertiesFile, new File(destination, "default.intermine.properties").absolutePath)
    }


    /** The bio-source-<postfix> jar, for the mergeSource configuration. */
    Dependency bioSourceDependency(sourcePostfix, sourceVersion) {
        // This can prove useful for debugging but may be a bit too noisy in practice
        // System.out.println("Adding mergeSource configuration dependency bio-source-${sourcePostfix}")
        project.getDependencies().create(
                [group: "org.intermine", name: "bio-source-${sourcePostfix}", version: "${sourceVersion}"])
    }

    protected generateModel = { modelName ->
        def ant = new AntBuilder()
        File destinationPath = DBModelUtils.buildFile(project, "gen")
        String destination = destinationPath.absolutePath
        if (! destinationPath.exists()){
            // we might have done a clean right before. compileJava comes after this step
            destinationPath.mkdirs();
        }
        ant.taskdef(name: "modelOutputTask", classname: "org.intermine.task.ModelOutputTask") {
            classpath {
                dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                pathelement(location: DBModelUtils.antLoggingDir(project).absolutePath) // log4j.properties of the Ant tasks
                dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                pathelement(path: DBModelUtils.antClasspath(project).asPath)
                pathelement(path: project.configurations.getByName("bioModel").asPath)
            }
        }
        ant.modelOutputTask(model: modelName, destDir: destination, type: "java")
    }

    /**
     * Empties the database of the objectstore and creates its tables (BuildDbTask, which generates the DDL from the
     * model and the objectstore's properties; up to 5.1 the createSchema step wrote a Torque schema XML first). The
     * classpath has what the task reads: intermine.properties, the model XML and the compiled model classes (the
     * project's build directories, the bio model, and intermine-api for the userprofile model).
     */
    protected createTables = { objectStoreName, modelName ->
        // BuildDbTask writes the SQL it executes (<objectStoreName>-schema.sql) here, for inspection
        File tempDir = DBModelUtils.buildFile(project, "tmp/intermine-buildDB")
        tempDir.mkdirs()
        System.out.println("Creating tables in objectstore ${objectStoreName} for model ${modelName}")

        def ant = new AntBuilder()
        ant.taskdef(name: "buildDB", classname: "org.intermine.task.BuildDbTask") {
            classpath {
                dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                pathelement(location: DBModelUtils.antLoggingDir(project).absolutePath) // log4j.properties of the Ant tasks
                dirset(dir: buildResourcesMainDir) // intermine.properties
                dirset(dir: DBModelUtils.buildDirectory(project).absolutePath) // the model classes
                pathelement(path: DBModelUtils.antClasspath(project).asPath)
                // created by the dbmodel plugin; a project with the integrate plugin alone has neither
                ["bioModel", IM_API_JAR].each { name -> // IM_API_JAR: the userprofile model and its classes
                    Configuration configuration = project.configurations.findByName(name)
                    if (configuration != null) {
                        pathelement(path: configuration.asPath)
                    }
                }
            }
        }
        ant.buildDB(osname: objectStoreName, model: modelName, tempDir: tempDir.absolutePath)
    }

    protected storeMetadata = { objectStoreName, modelName ->
        System.out.println("Storing metadata in objectstore ${objectStoreName} for model ${modelName}")

        def ant = new AntBuilder()
        ant.taskdef(name: 'insertModel', classname: 'org.intermine.task.StoreMetadataTask') {
            classpath {
                dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                pathelement(location: DBModelUtils.antLoggingDir(project).absolutePath) // log4j.properties of the Ant tasks
                dirset(dir: buildResourcesMainDir) // intermine.properties
                pathelement(path: DBModelUtils.antClasspath(project).asPath)
                pathelement(path: project.configurations.getByName(IM_API_JAR).asPath) //userprofile_keyDefs.properties
            }
        }
        ant.insertModel(osname: objectStoreName, modelName: modelName)
    }

    protected analyse = { objectStoreName, modelName ->
        System.out.println("Postgres analyzing objectstore ${objectStoreName} for ${modelName} to optimize performance")

        def ant = new AntBuilder()
        ant.taskdef(name: 'analyse', classname: 'org.intermine.task.AnalyseDbTask') {
            classpath {
                dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                pathelement(location: DBModelUtils.antLoggingDir(project).absolutePath) // log4j.properties of the Ant tasks
                dirset(dir: buildResourcesMainDir) // intermine.properties
                pathelement(path: DBModelUtils.antClasspath(project).asPath)
            }
        }
        ant.analyse(osname: objectStoreName, model: modelName)
    }

    protected createIndexes = { objectStoreName, attributeIndexes ->
        System.out.println("Creating search indexes for objectstore ${objectStoreName}")

        def ant = new AntBuilder()
        ant.taskdef(name: 'createIndexes', classname: 'org.intermine.task.CreateIndexesTask') {
            classpath {
                dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                pathelement(location: DBModelUtils.antLoggingDir(project).absolutePath) // log4j.properties of the Ant tasks
                dirset(dir: buildResourcesMainDir) // intermine.properties
                dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                pathelement(path: DBModelUtils.antClasspath(project).asPath)
            }
        }
        ant.createIndexes(alias: objectStoreName, attributeIndexes: attributeIndexes)
    }

}

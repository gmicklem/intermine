package org.intermine.plugin.dbmodel

import groovy.ant.AntBuilder
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
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

    /** The main source set's resources output directory, where the InterMine runtime files live. */
    static String resourcesDir(Project project) {
        SourceSetContainer sourceSets = (SourceSetContainer) project.getProperties().get("sourceSets")
        sourceSets.getByName("main").getOutput().resourcesDir.toString()
    }

    /**
     * A file in the resources output directory that task {@code producer} writes, as a task
     * input: the consuming task then depends on the producer through its inputs instead of an
     * explicit dependsOn. The producers write into a directory shared with processResources, so
     * most of them do not declare outputs; the file name is resolved when the input is read.
     */
    static FileCollection resource(Project project, String producer, Closure<String> fileName) {
        project.files({ new File(resourcesDir(project), fileName.call()) }).builtBy(producer)
    }

    /** The resources output directory as processResources leaves it, as a task input. */
    static FileCollection processedResources(Project project) {
        project.files({ resourcesDir(project) }).builtBy('processResources')
    }

    /**
     * Declares what a task that runs InterMine code (an Ant task on this project's classpath)
     * reads: the compiled classes and resources (this replaces dependsOn 'jar' / 'compileJava';
     * the Ant tasks use build/ directly, not the jar), the runtime classpath (which also builds
     * project dependencies such as a mine's dbmodel jar), and the runtime property files that
     * the given tasks put into the resources directory.
     */
    static void readsRuntime(Task task, Map<String, Closure<String>> resources = [:]) {
        Project project = task.project
        SourceSetContainer sourceSets = (SourceSetContainer) project.getProperties().get("sourceSets")
        task.inputs.files(sourceSets.getByName("main").output)
        task.inputs.files(antClasspath(project))
        resources.each { String producer, Closure<String> fileName ->
            task.inputs.files(resource(project, producer, fileName))
        }
    }

    /** intermine.properties (the mine's own build file copies it in) and default.intermine.properties. */
    static Map<String, Closure<String>> mineProperties() {
        ['copyMineProperties': { 'intermine.properties' },
         'copyDefaultInterMineProperties': { 'default.intermine.properties' }]
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

    protected copyDefaultPropertiesFile = { String defaultPropertiesFile ->
        copyFromArchive(project, project.configurations.getByName("commonResources").singleFile,
                defaultPropertiesFile, buildResourcesMainDir + File.separator + "default.intermine.properties")
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
                dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                pathelement(path: DBModelUtils.antClasspath(project).asPath)
                pathelement(path: project.configurations.getByName("bioModel").asPath)
            }
        }
        ant.modelOutputTask(model: modelName, destDir: destination, type: "java")
    }

    protected createSchema = { objectStoreName ->
        String schemaFile = objectStoreName + "-schema.xml"
        String destination = DBModelUtils.buildFile(project, schemaFile).absolutePath
        System.out.println("Creating schema in objectstore ${objectStoreName} for ${destination}")

        def ant = new AntBuilder()
        ant.taskdef(name: "torque", classname: "org.intermine.objectstore.intermine.TorqueModelOutputTask") {
            classpath {
                dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                pathelement(path: DBModelUtils.antClasspath(project).asPath)
                pathelement(path: project.configurations.getByName("bioModel").asPath)
                pathelement(path: project.configurations.getByName(IM_API_JAR).asPath) //userprofile classes
            }
        }
        ant.torque(osname: objectStoreName, destFile: destination)
    }

    protected createTables = { objectStoreName, modelName ->
        String schemaFile = objectStoreName + "-schema.xml"
        String tempDirectory = DBModelUtils.buildFile(project, "tmp").absolutePath
        System.out.println("Creating tables in objectstore ${objectStoreName} with schema ${schemaFile}")

        def ant = new AntBuilder()
        ant.taskdef(name: "buildDB", classname: "org.intermine.task.BuildDbTask") {
            classpath {
                dirset(dir: buildResourcesMainDir)//to read default.intermine.properties
                pathelement(path: DBModelUtils.buildDirectory(project).absolutePath)//to read the schema
                pathelement(path: DBModelUtils.antClasspath(project).asPath)
            }
        }
        ant.buildDB(osname: objectStoreName, model: modelName, schemafile: schemaFile, tempDir: tempDirectory)
    }

    protected storeMetadata = { objectStoreName, modelName ->
        System.out.println("Storing metadata in objectstore ${objectStoreName} for model ${modelName}")

        def ant = new AntBuilder()
        ant.taskdef(name: 'insertModel', classname: 'org.intermine.task.StoreMetadataTask') {
            classpath {
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
                dirset(dir: buildResourcesMainDir) // intermine.properties
                dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                pathelement(path: DBModelUtils.antClasspath(project).asPath)
            }
        }
        ant.createIndexes(alias: objectStoreName, attributeIndexes: attributeIndexes)
    }

}

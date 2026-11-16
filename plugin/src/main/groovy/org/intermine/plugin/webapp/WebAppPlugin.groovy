package org.intermine.plugin.webapp

import groovy.ant.AntBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.War
import org.intermine.plugin.TaskConstants
import org.intermine.plugin.dbmodel.DBModelPlugin
import org.intermine.plugin.dbmodel.DBModelUtils

import java.nio.file.Files
import java.nio.file.StandardCopyOption

class WebAppPlugin implements Plugin<Project> {
    WebAppConfig config;
    DBModelUtils dbUtils

    void apply(Project project) {
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(WarPlugin)
        config = project.extensions.create('webappConfig', WebAppConfig, project)
        dbUtils = new DBModelUtils(project)

        // Resolvable only (see DBModelPlugin); the dependencies are added when they are resolved.
        ['commonResources', 'bioWebApp'].each { name ->
            DBModelUtils.resolvableConfiguration(project, name)
        }
        DBModelPlugin.addDependency(project, 'commonResources') {
            [group: "org.intermine", name: "intermine-resources", version: System.getProperty("imVersion")]
        }
        DBModelPlugin.addDependency(project, 'bioWebApp') {
            [group: "org.intermine", name: "bio-webapp", version: System.getProperty("bioVersion"), transitive: false, ext: "war"]
        }

        // default.intermine.properties belongs in the war (WEB-INF/classes): the task writes it into a directory of its
        // own, its declared output, and processResources takes it from there, so that it is in the resources output
        // whatever ran before (it used to be copied into build/resources/main after processResources).
        project.tasks.register('copyDefaultInterMineProperties') {
            description = "Copies the default.intermine.properties file (defaultInterminePropertiesFile) for the resources output"
            inputs.files({ project.configurations.getByName("commonResources") })
            inputs.property('defaultInterminePropertiesFile', { config.defaultInterminePropertiesFile ?: '' })
            outputs.dir(DBModelUtils.taskDir(project, name))

            doLast {
                dbUtils.copyDefaultPropertiesFile(config.defaultInterminePropertiesFile, DBModelUtils.cleanTaskDir(project, name))
            }
        }
        project.tasks.named('processResources', Copy) { Copy copy ->
            copy.from(project.tasks.named('copyDefaultInterMineProperties'))
        }

        project.tasks.register('mergeProperties') {
            description = "Append intermine.properties to web.properties file"
            String webPropertiesPath = project.projectDir.absolutePath + File.separator + "src" + File.separator + "main" +
                    File.separator + "webapp" + File.separator + "WEB-INF" + File.separator + "web.properties"
            inputs.files(webPropertiesPath)
            // intermine.properties as processResources leaves it in build/resources/main (a mine's copyMineProperties
            // feeds processResources; a 5.x style one copies into the directory itself)
            inputs.files(DBModelUtils.processedResources(project))
            outputs.file({ config.propsDir + File.separator + "web.properties" })

            doLast {
                if (!(new File(config.propsDir)).exists()) {
                    new File(config.propsDir).mkdir()
                }
                String webPropertiesBuiltPath = config.propsDir + File.separator + "web.properties"
                File webPropertiesBuilt = new File(webPropertiesBuiltPath)
                Files.copy((new File(webPropertiesPath)).toPath(), webPropertiesBuilt.toPath(), StandardCopyOption.REPLACE_EXISTING)
                String interMinePropertiesPath = DBModelUtils.resourcesDir(project) + File.separator + "intermine.properties"
                webPropertiesBuilt.append( (new File(interMinePropertiesPath)).getText())
            }
        }

        // build/explodedWebApp: the unpacked bio-webapp war with the model's struts, tiles, web.xml and message
        // additions. A Sync, so that a file of an earlier bio-webapp version or of an earlier model does not stay in the
        // directory (im-ee7.41); the additions are made in the same task, after the copy, so that a run that is
        // up to date leaves the directory as the last run finished it (Gradle snapshots the outputs after the last action).
        project.tasks.register('unwarBioWebApp', Sync) { Sync sync ->
            sync.description = "Unpack bio-webapp into build/explodedWebApp and add the struts, tiles, web.xml and model configuration"
            sync.from({ project.zipTree(project.configurations.getByName("bioWebApp").singleFile) })
            sync.into(project.layout.buildDirectory.dir("explodedWebApp"))
            // the struts, tiles and message fragments of the mine, from the resources
            sync.inputs.files(DBModelUtils.processedResources(project))

            sync.doLast {
                File buildResDir = DBModelUtils.buildFile(project, "resources/main")
                File webInf = new File(explodedWebApp(project), "WEB-INF")
                File strutsConfigModelPath = new File(buildResDir, "struts-config-model.xml")
                File strutsConfigFormPath = new File(buildResDir, "struts-config-form-model.xml")
                File strutsConfigPath = new File(webInf, "struts-config.xml")

                //struts-config.xml
                String content = strutsConfigPath.text
                strutsConfigPath.withWriter { w ->
                    w << content.replace("<!--@MODEL_INCLUDE@-->", strutsConfigModelPath.text)
                }
                content = strutsConfigPath.text
                strutsConfigPath.withWriter { w ->
                    w << content.replace("<!--@MODEL_FORM_INCLUDE@-->", strutsConfigFormPath.text)
                }

                //tiles-defs.xml
                File tilesDefsModel = new File(buildResDir, "tiles-defs-model.xml")
                File tilesDefs = new File(webInf, "tiles-defs.xml")
                content = tilesDefs.text
                tilesDefs.withWriter { w ->
                    w << content.replace("<!--@MODEL_INCLUDE@-->", tilesDefsModel.text)
                }

                // web.xml
                File webXml = new File(webInf, "web.xml")
                File webBio = new File(webInf, "web-bio.xml")
                content = webXml.text
                webXml.withWriter { w ->
                    w << content.replace("<!--@MODEL_INCLUDE@-->", webBio.text)
                }

                //internationalisation
                File modelProperties = new File(buildResDir, "model.properties")
                File intermineWebAppProperties = new File(webInf, "classes/InterMineWebApp.properties")
                intermineWebAppProperties.append(modelProperties.text)
            }
        }

        // Kept for the build files of plugin 5.x, whose war task depends on it: unwarBioWebApp makes the additions now.
        project.tasks.register('addStrutsConfig') {
            description = "Append the struts config modifications to the webapp (done by unwarBioWebApp)"
            // lifecycle alias: it has no work of its own
            dependsOn 'unwarBioWebApp'
        }

        // this plugin requires a database to exist and be populated. However this plugin is run at compile time, not runtime.
        // We have no guarantee there will be a database. Hence the try/catch
        project.tasks.register('summariseObjectStore') {
            description = "Summarise ObjectStore into objectstoresummary.properties file"
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties(project))
            // the war takes the file from here. It depends on the database, which is no input, so the task always runs
            // (and the file may be missing when there is no database yet)
            outputs.file({ config.propsDir + File.separator + "objectstoresummary.properties" })
            outputs.upToDateWhen { false }

            doLast {
                try {
                    def ant = new AntBuilder()
                    ant.taskdef(name: "summarizeObjectStore", classname: "org.intermine.task.SummariseObjectStoreTask") {
                        classpath {
                            dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                            dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                            pathelement(path: DBModelUtils.antClasspath(project).asPath)
                        }
                    }
                    ant.summarizeObjectStore(alias: config.objectStoreName, configFileName: "objectstoresummary.config.properties",
                            outputFile: config.propsDir + File.separator + "objectstoresummary.properties")
                } catch (Exception ex) {
                    println("Error: " + ex)
                }
            }
        }

        project.tasks.register('loadDefaultTemplates') {
            group = TaskConstants.TASK_GROUP
            description = "Loads default template queries from an XML file into a given user profile"
            // the runtime classpath builds the dbmodel jar (in case a clean has been run), which holds
            // class_keys.properties
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties(project))
            // lifecycle (kept): the templates go into the userprofile database createUserDB creates.
            dependsOn ':dbmodel:createUserDB'

            doLast {
                def ant = new AntBuilder()

                String buildResourcesMainDir = DBModelUtils.resourcesDir(project)
                Properties intermineProperties = new Properties();
                intermineProperties.load(new FileInputStream(buildResourcesMainDir + File.separator + "intermine.properties"));
                String superUser = intermineProperties.getProperty("superuser.account")
                String superUserPsw = intermineProperties.getProperty("superuser.initialPassword")


                ant.taskdef(name: "loadTemplates", classname: "org.intermine.web.task.LoadDefaultTemplatesTask") {
                    classpath {
                        dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                        dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                    }
                }
                ant.loadTemplates(osAlias: config.userProfileObjectStoreName, userProfileAlias: config.userProfileObjectStoreWriterName,
                        templatesXml:buildResourcesMainDir + File.separator + "default-template-queries.xml",
                        username: superUser,
                        superuserPassword: superUserPsw)
            }
        }

        project.tasks.register('dropPrecomputedTables') {
            group = TaskConstants.TASK_GROUP
            description = "Drops all precomputed tables from the database"
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties(project))

            doLast {
                def ant = new AntBuilder()
                ant.taskdef(name: "dropPrecomputedTables", classname: "org.intermine.task.DropPrecomputedTablesTask") {
                    classpath {
                        dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                        dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                    }
                }
                ant.dropPrecomputedTables(alias: config.objectStoreName)
            }
        }

        project.tasks.register('precomputeQueries') {
            group = TaskConstants.TASK_GROUP
            description = "Creates temporary tables to make querying faster -- uses 'genomic_precompute.properties'"
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties(project))

            doLast {
                def ant = new AntBuilder()
                ant.taskdef(name: "precomputeQueries", classname: "org.intermine.task.PrecomputeTask") {
                    classpath {
                        dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                        dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                    }
                }
                ant.precomputeQueries(objectStoreAlias: config.objectStoreName, minRows:0,
                        precomputePropertiesPath: "genomic_precompute.properties")
            }
        }

        project.tasks.register('precomputeTemplates') {
            group = TaskConstants.TASK_GROUP
            description = "Creates temporary tables to make querying faster -- uses the results of your template queries"
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties(project))

            doLast {
                def ant = new AntBuilder()

                String buildResourcesMainDir = DBModelUtils.resourcesDir(project)
                Properties intermineProperties = new Properties()
                intermineProperties.load(new FileInputStream(buildResourcesMainDir + File.separator + "intermine.properties"));
                String superUser = intermineProperties.getProperty("superuser.account")

                ant.taskdef(name: "precomputeTemplates", classname: "org.intermine.web.task.PrecomputeTemplatesTask") {
                    classpath {
                        dirset(dir: DBModelUtils.imDir(project).absolutePath, includes: '*', erroronmissingdir: false) // DBModelUtils.imDir
                        dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                    }
                }
                ant.precomputeTemplates(alias: config.objectStoreName,
                        userProfileAlias: config.userProfileObjectStoreWriterName, minRows:0,
                        username: superUser)
            }
        }

        // The war: the mine's own src/main/webapp (which the war task takes by itself), then the exploded bio-webapp,
        // then the merged web.properties and the object store summary. The first entry of a name wins, so the mine's own
        // files replace those of bio-webapp, and a jar that the mine's classpath and bio-webapp both carry is listed once.
        // Every part comes from the output of the task that makes it, so the war needs no dependsOn.
        project.tasks.named('war', War) { War war ->
            war.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            // the unmerged web.properties of the mine and of bio-webapp
            war.exclude "WEB-INF/web.properties"
            war.from(project.tasks.named('unwarBioWebApp'))
            war.webInf.from(project.files({ config.propsDir }).builtBy('mergeProperties', 'summariseObjectStore'))

            // A mine of the 5.x line declares compile('ant:ant:1.6.5'): a 2005 Ant under the legacy groupId `ant`, which
            // Gradle takes for a different library from org.apache.ant:ant:1.10.x that InterMine brings, so both jars
            // would go to WEB-INF/lib. Nothing at run time needs it (the classes of the old jar are in the new one).
            // It stays on the mine's other classpaths (the Ant tasks of the build); the war leaves it out.
            FileCollection legacyAnt = legacyAntJars(project)
            war.classpath = war.classpath.minus(legacyAnt)
            war.doFirst {
                if (!legacyAnt.empty) {
                    project.logger.warn("The war leaves out ${legacyAnt.files*.name.join(', ')} (legacy groupId 'ant', the " +
                            "org.apache.ant:ant jar of InterMine is in the war): delete the ant:ant:1.6.5 dependency " +
                            "from the build files of the mine (docs/UPGRADING-build.md, step 3)")
                }
            }
        }
    }

    /** The jars of the legacy `ant:ant` coordinate (pre-Apache groupId) on the mine's runtime classpath, if any. */
    static FileCollection legacyAntJars(Project project) {
        project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).incoming.artifactView { view ->
            view.componentFilter { id ->
                id instanceof ModuleComponentIdentifier && id.group == 'ant' && id.module == 'ant'
            }
        }.files
    }

    static File explodedWebApp(Project project) {
        DBModelUtils.buildFile(project, "explodedWebApp")
    }
}

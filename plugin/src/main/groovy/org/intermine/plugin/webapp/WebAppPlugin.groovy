package org.intermine.plugin.webapp

import groovy.ant.AntBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
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
        config = project.extensions.create('webappConfig', WebAppConfig)
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

        project.tasks.register('copyDefaultInterMineProperties') {
            description = "Copies default.intermine.properties file into resources output"
            // ordering only: it writes into processResources' output directory.
            mustRunAfter 'processResources'

            doLast {
                dbUtils.copyDefaultPropertiesFile(config.defaultInterminePropertiesFile)
            }
        }

        project.tasks.register('mergeProperties') {
            description = "Append intermine.properties to web.properties file"
            String webPropertiesPath = project.projectDir.absolutePath + File.separator + "src" + File.separator + "main" +
                    File.separator + "webapp" + File.separator + "WEB-INF" + File.separator + "web.properties"
            inputs.files(webPropertiesPath)
            inputs.files(DBModelUtils.resource(project, 'copyMineProperties') { "intermine.properties" })
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

        project.tasks.register('addStrutsConfig') {
            description = "Append the struts config modifications to the webapp"
            // The struts and tiles fragments come from the resources.
            inputs.files(DBModelUtils.processedResources(project))
            // lifecycle: it edits, in place, the files unwarBioWebApp unpacks (neither declares outputs), so it
            // must run after it every time. build/explodedWebApp is not declared as an input: a mine's own
            // copyWebappContent copies into the same directory, and Gradle 8 would then fail addStrutsConfig
            // for reading that task's output without depending on it. A single Sync task with filters should
            // replace the pair once the plugin composes the war.
            dependsOn 'unwarBioWebApp'

            doLast {
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

        // this plugin requires a database to exist and be populated. However this plugin is run at compile time, not runtime.
        // We have no guarantee there will be a database. Hence the try/catch
        project.tasks.register('summariseObjectStore') {
            description = "Summarise ObjectStore into objectstoresummary.properties file"
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties())

            doLast {
                try {
                    def ant = new AntBuilder()
                    ant.taskdef(name: "summarizeObjectStore", classname: "org.intermine.task.SummariseObjectStoreTask") {
                        classpath {
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

        project.tasks.register('unwarBioWebApp') {
            description = "Unwar bio-webapp under the build/explodedWebAppDir directory"

            doLast {
                String bioWebAppWar = project.configurations.getByName("bioWebApp").singleFile.absolutePath;
                String destination = explodedWebApp(project).absolutePath
                def ant = new AntBuilder()
                ant.unzip(src: bioWebAppWar, dest: destination, overwrite: "true" )
            }
        }

        project.tasks.register('loadDefaultTemplates') {
            group = TaskConstants.TASK_GROUP
            description = "Loads default template queries from an XML file into a given user profile"
            // the runtime classpath builds the dbmodel jar (in case a clean has been run), which holds
            // class_keys.properties
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties())
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
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties())

            doLast {
                def ant = new AntBuilder()
                ant.taskdef(name: "dropPrecomputedTables", classname: "org.intermine.task.DropPrecomputedTablesTask") {
                    classpath {
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
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties())

            doLast {
                def ant = new AntBuilder()
                ant.taskdef(name: "precomputeQueries", classname: "org.intermine.task.PrecomputeTask") {
                    classpath {
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
            DBModelUtils.readsRuntime(it, DBModelUtils.mineProperties())

            doLast {
                def ant = new AntBuilder()

                String buildResourcesMainDir = DBModelUtils.resourcesDir(project)
                Properties intermineProperties = new Properties()
                intermineProperties.load(new FileInputStream(buildResourcesMainDir + File.separator + "intermine.properties"));
                String superUser = intermineProperties.getProperty("superuser.account")

                ant.taskdef(name: "precomputeTemplates", classname: "org.intermine.web.task.PrecomputeTemplatesTask") {
                    classpath {
                        dirset(dir: DBModelUtils.buildDirectory(project).absolutePath)
                        pathelement(path: DBModelUtils.antClasspath(project).asPath)
                    }
                }
                ant.precomputeTemplates(alias: config.objectStoreName,
                        userProfileAlias: config.userProfileObjectStoreWriterName, minRows:0,
                        username: superUser)
            }
        }
    }

    static File explodedWebApp(Project project) {
        DBModelUtils.buildFile(project, "explodedWebApp")
    }
}

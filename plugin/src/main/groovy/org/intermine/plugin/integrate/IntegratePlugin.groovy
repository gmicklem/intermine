package org.intermine.plugin.integrate

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.DependencySet
import org.intermine.plugin.TaskConstants
import org.intermine.plugin.dbmodel.DBModelPlugin
import org.intermine.plugin.dbmodel.DBModelUtils

class IntegratePlugin implements Plugin<Project> {
    String COMMON_OS_PREFIX = "common"

    private IntegrationSpec spec

    /** The sources and action of this run; read from project.xml and -Psource/-Paction on first use. */
    synchronized IntegrationSpec spec(Project project) {
        if (spec == null) {
            spec = new IntegrationSpec(project)
        }
        spec
    }

    void apply(Project project) {
        // Resolvable only; nothing should consume them.
        ['integrateSource', 'postProcesses', 'bioCore'].each { name ->
            DBModelUtils.resolvableConfiguration(project, name)
        }
        DBModelPlugin.addDependency(project, 'bioCore') {
            [group: "org.intermine", name: "bio-core", version: System.getProperty("bioVersion"), transitive: false]
        }
        // the bio-source jars of the selected sources (initIntegration used to add them)
        project.configurations.getByName('integrateSource').withDependencies { DependencySet deps ->
            spec(project).sourceDependencies.each { Map notation ->
                deps.add(project.dependencies.create(notation))
            }
        }

        // What every task that integrates a source reads (it used to depend on initIntegration,
        // which depended on jar, copyMineProperties and copyDefaultInterMineProperties), and the
        // one-off preparation it needs before it runs.
        Closure integrates = { Task task ->
            DBModelUtils.readsRuntime(task, DBModelUtils.mineProperties())
            task.doFirst { spec(project).prepare() }
        }

        project.tasks.register('integrate') {
            group = TaskConstants.TASK_GROUP
            description = "Integrates sources into production database. Optional input properties: source (source name) and action(possible values: pre-retrieve, retrieve or load). E.g. integrate -Psource=uniprot-malaria -Paction=load"
            // lifecycle (kept): an aggregate task; onlyIf picks the single- or multi-source path.
            dependsOn 'integrateSingleSource', 'integrateMultipleSources'
        }

        project.tasks.register('integrateSingleSource') {
            description = "Integrates single source into production database."
            // lifecycle (kept): the database pipeline for one source.
            dependsOn 'preRetrieveSingleSource', 'retrieveSingleSource', 'loadSingleSource'
            onlyIf { spec(project).sourceNames.size() == 1 }
        }

        project.tasks.register('integrateMultipleSources') {
            description = "Integrates multiple sources into production database."
            integrates(it)
            onlyIf { spec(project).sourceNames.size() > 1 }

            doLast {
                IntegrationSpec spec = spec(project)
                spec.sourceNames.each { sourceName ->
                    def osName = "os.$COMMON_OS_PREFIX-tgt-items-std"
                    def modelName = 'fulldata'

                    Properties props = spec.bioSourceProperties.getBioSourceProperties(sourceName)
                    if (!props.containsKey("have.file.custom.direct")) {
                        println "Building tgt items database"
                        spec.dbUtils.createSchema(osName)
                        spec.dbUtils.createTables(osName, modelName)
                        spec.dbUtils.storeMetadata(osName, modelName)
                    }

                    spec.integration.preRetrieveSingleSource(sourceName)
                    println "Retrieving $sourceName in a tgt items database"
                    spec.integration.retrieveSingleSource(sourceName)

                    if (!props.containsKey("have.file.custom.direct")) {
                        // need to do this after data is in items DB or loading is too slow
                        spec.dbUtils.createIndexes(osName, false)
                        spec.dbUtils.analyse(osName, modelName)
                    }

                    println "Loading $sourceName tgt items into production database"
                    spec.integration.loadSingleSource(spec.intermineProject.sources.get(sourceName))
                }
            }
        }

        project.tasks.register('preRetrieveSingleSource') {
            description = "Pre-retrieves a single source"
            integrates(it)
            // lifecycle (kept): the items database must exist first.
            dependsOn 'buildTgtItems'
            onlyIf {
                IntegrateAction action = spec(project).action
                spec(project).sourceNames.size() == 1 && (action.equals(IntegrateAction.PRE_RETRIEVE) || action.equals(IntegrateAction.RETRIEVE) || action.equals(IntegrateAction.RETRIEVE_AND_LOAD))
            }

            doLast {
                String sourceName = spec(project).sourceNames.get(0)
                spec(project).integration.preRetrieveSingleSource(sourceName)
            }
        }

        project.tasks.register('retrieveSingleSource') {
            description = "Retrieves a single source into tgt items database"
            integrates(it)
            // lifecycle (kept): database pipeline order.
            dependsOn 'buildTgtItems', 'preRetrieveSingleSource'
            onlyIf {
                IntegrateAction action = spec(project).action
                spec(project).sourceNames.size() == 1 && (action.equals(IntegrateAction.RETRIEVE) || action.equals(IntegrateAction.RETRIEVE_AND_LOAD))
            }

            doLast {
                IntegrationSpec spec = spec(project)
                String sourceName = spec.sourceNames.get(0)
                println "Retrieving " + sourceName + " in a tgt items database"
                spec.integration.retrieveSingleSource(sourceName)
                Properties props = spec.bioSourceProperties.getBioSourceProperties(sourceName)
                if (!props.containsKey("have.file.custom.direct")) {
                    // need to do this after data is in items DB or loading is too slow
                    def osName = "os.$COMMON_OS_PREFIX-tgt-items-std"
                    def modelName = 'fulldata'

                    spec.dbUtils.createIndexes(osName, false)
                    spec.dbUtils.analyse(osName, modelName)
                }
            }
        }

        project.tasks.register('buildTgtItems') {
            description = "Builds tgt items database"
            integrates(it)
            onlyIf { spec(project).sourceNames.size() == 1 && !spec(project).action.equals(IntegrateAction.LOAD) }

            doLast {
                IntegrationSpec spec = spec(project)
                Properties props = spec.bioSourceProperties.getBioSourceProperties(spec.sourceNames.get(0))
                if (!props.containsKey("have.file.custom.direct")) {
                    println "Building tgt items database"
                    def osName = "os.$COMMON_OS_PREFIX-tgt-items-std"
                    def modelName = 'fulldata'

                    spec.dbUtils.createSchema(osName)
                    spec.dbUtils.createTables(osName, modelName)
                    spec.dbUtils.storeMetadata(osName, modelName)
                }
            }
        }

        project.tasks.register('loadSingleSource') {
            description = "Loads items into production database"
            integrates(it)
            // ordering (kept): with -Paction=load alone the retrieve step is not run at all.
            mustRunAfter 'retrieveSingleSource'
            onlyIf {
                IntegrateAction action = spec(project).action
                spec(project).sourceNames.size() == 1 && (action.equals(IntegrateAction.LOAD) || action.equals(IntegrateAction.RETRIEVE_AND_LOAD))
            }

            doLast {
                IntegrationSpec spec = spec(project)
                String sourceName = spec.sourceNames.get(0)
                println "Loading " + sourceName + " tgt items into production DB"
                spec.integration.loadSingleSource(spec.intermineProject.sources.get(sourceName))
            }
        }
    }
}

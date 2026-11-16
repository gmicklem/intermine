# Building InterMine

This page describes how the back-end code in this repository is built: the Gradle builds, how they find each other, the generated build files and the dependency conventions. For tests see [TESTING.md](TESTING.md), for releases [RELEASING.md](RELEASING.md), for mines (humanmine, flymine, ...) that use the InterMine Gradle plugin see [UPGRADING-build.md](UPGRADING-build.md).

## Requirements

- JDK 17. JDK 8 still works until 6.0.0 is released (the CI unit tests run on both; the web service tests and Checkstyle run on 17); JDK 21 passes the full gate too (its javac warns that `--release 8` is obsolete: some later JDK will no longer build Java 8 bytecode). The JDK that runs Gradle, i.e. `JAVA_HOME` (`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=$JAVA_HOME/bin:$PATH`), compiles the code, runs the tests and runs the plugin's Ant tasks (inside the Gradle daemon). No Java toolchain is declared, so `org.gradle.java.installations.*` settings are not needed: a toolchain would compile and test on one JDK while the daemon runs InterMine code on another, and a machine with only JDK 8 could no longer build.
- Whatever the build JDK, the jars are Java 8 bytecode (class file version 52), so they run on a JDK 8 Tomcat. On JDK 9+ the code is compiled with `--release 8`, i.e. against the Java 8 API; this is set in one place, `config/gradle/java-target.gradle`, which every build applies. No JVM option is needed on any JDK: the tests and Tomcat run without `--add-opens` (until 6.0 cglib 3.1 needed `--add-opens=java.base/java.lang=ALL-UNNAMED` on JDK 16+; Byte Buddy, which replaced it in `org.intermine.util.DynamicBean`, loads its classes through a class loader of its own). Gradle 8.14.6 runs on JDK 8 to 24; on JDK 8 it prints one deprecation in every build ("Executing Gradle on JVM versions 16 and lower has been deprecated"), because Gradle 9 needs JDK 17 to run.
- Python 3 for `config/lib/install_intermine.py` (it uses `dataclasses` and `glob(root_dir=...)`, so Python 3.10 or later).
- PostgreSQL, but only to run tests (see [TESTING.md](TESTING.md)). Compiling and publishing do not need it.
- `export LANG=C.UTF-8 LC_ALL=C.UTF-8` before building: javadoc fails on non-ASCII sources under an ASCII locale.

## Seven separate Gradle builds

There is no root build. Each directory below has its own `settings.gradle`, `build.gradle` and Gradle wrapper, and is built by running `./gradlew` inside it.

| Directory | `rootProject.name` | What it contains | Published |
|---|---|---|---|
| `plugin/` | `plugin` | the Groovy Gradle plugin used by the `bio` builds below and by every mine | yes (`org.intermine:plugin`) |
| `intermine/` | `intermine` | `intermine-model`, `-objectstore`, `-api`, `-integrate`, `-webapp` (war), `-webtasks`, `-resources`, `-testresources`, ... | yes |
| `bio/` | `bio` | `bio-core`, `bio-model`, `bio-tools`, `bio-webapp` (war), `bio-testresources` | yes |
| `bio/sources/` | `bio-sources` | the `bio-source-*` data source converters | yes |
| `bio/postprocess/` | `bio-postprocess` | the `bio-postprocess-*` steps | yes |
| `bio/postprocess-test/` | `bio-postprocess-test` | tests of the postprocess steps | no |
| `testmine/` | `testmine` | the test model mine (`dbmodel`, `webapp`), used for the web service tests | no |

Which modules are published, and under which names, is in [RELEASING.md](RELEASING.md).

### How the builds depend on each other

The builds are built in this order, and each needs the jars of the ones before it:

```
plugin -> intermine -> bio -> bio/sources -> bio/postprocess -> bio/postprocess-test -> testmine
```

The jars are not found through one repository but through three mechanisms, all configured in the build files:

1. **Local file (ivy) repositories of `build/libs`.** Each consuming build adds one `ivy { url "file://<dir>" }` repository per module directory, with the layout `[artifact]-[revision](-[classifier]).[ext]` and `metadataSources { artifact() }` (the jar alone, no descriptor). The directories come from `gradle.properties`: `intermine.development.intermineRootDir` plus `intermine.development.intermineModules` (for example `api,integrate,model,...`), and for the later builds `intermine.development.bioRootDir` plus `intermine.development.bioModules`. So `intermine-objectstore:6.0.0` is resolved from `<checkout>/intermine/objectstore/build/libs/intermine-objectstore-6.0.0.jar`. This is what `bio/build.gradle`, `bio/sources/build.gradle`, `bio/postprocess/build.gradle`, `bio/postprocess-test/build.gradle` and `testmine/build.gradle` do. `intermine/` does not need it: it only depends on its own subprojects.
2. **The plugin from `plugin/build/libs`.** `bio/core`, `bio/model`, `bio/sources`, `bio/postprocess` and `bio/postprocess-test` load the plugin in a `buildscript` block, from an ivy repository at `intermine.plugin.repo` (layout `[artifact]-[revision].[ext]`, so `plugin-6.0.0.jar`), with Maven Central as fallback, at the catalog version (`classpath libs.intermine.plugin`; `buildscript` blocks can read the catalog on Gradle 8.14). `testmine` does not use the plugin.
3. **Maven Central** (`intermine.maven.central`), plus the EBI repository (`intermine.maven.ebi`) and Clojars (`intermine.maven.clojars`, testmine only), for everything that is not built here, and for released InterMine artifacts when no local jar exists.

No build file uses `mavenLocal()`. `publishToMavenLocal` still matters for two reasons: it depends on the jar, war, sources and javadoc tasks, so running it is what fills the `build/libs` directories that mechanisms 1 and 2 read; and it installs the artifacts into `~/.m2` (or into the directory given with `-Dmaven.repo.local=<dir>`) for builds outside this repository, such as a mine that adds `mavenLocal()`. That is why `config/lib/install_intermine.py`, `testmine/setup.sh` and `config/ci/run.sh` all call it.

The version of the InterMine release that is built (the `version` of every published module) comes from the version catalog: `libs.versions.intermine` in `gradle/libs.versions.toml` (the plugin too: `version = libs.versions.intermine.get()` in `plugin/build.gradle`). The same version is how the build files name the *other* InterMine builds' jars: the catalog has a library entry for every `org.intermine` module that a build consumes (`libs.intermine.objectstore`, `libs.bio.model`, `libs.bio.postprocess.create.references`, `libs.intermine.plugin`, ... all at `libs.versions.intermine`), used in `dependencies { }` and in the `classpath` of `buildscript { }` blocks. A release edits the catalog only; `config/lib/install_intermine.py` copies `libs.versions.intermine` into `systemProp.imVersion` and `systemProp.bioVersion` of every generated `gradle.properties` (run it again after a version change; see "Version bump" in [RELEASING.md](RELEASING.md)). Those system properties are no longer read by InterMine's own build files; they are for the deployers' builds (the plugin reads `imVersion` and `bioVersion` to find the jars of a mine's dependencies, and `UPGRADING-build.md` tells a mine to set them). `systemProp.flymineVersion` (the version of the `bio-source-flymine-static` test dependency, in `bio/`, `bio/sources/` and `bio/postprocess-test/`) is the one version that is still only a system property.

## Generated files: `config/lib/install_intermine.py`

`gradle.properties` and `gradle/wrapper/gradle-wrapper.properties` are not committed (they are in `.gitignore`). Each build has `gradle.properties.in` (plugin, intermine, bio, bio/sources, bio/postprocess, bio/postprocess-test, testmine, and `testmine/webapp`) and `gradle/wrapper/gradle-wrapper.properties.in` (all seven builds). `config/lib/install_intermine.py` fills in the `@@key@@` placeholders and writes the real files, each starting with `# FILE AUTOMATICALLY GENERATED FROM ... DO NOT EDIT!`. Edit the `.in` file, then run the script again.

The script does four things, in this order:

1. writes every `gradle.properties` from its `.in` file (checkout path, repository URLs);
2. writes every `gradle-wrapper.properties` from its `.in` file (distribution URL and SHA-256);
3. copies `config/ci.properties`, `config/testmodel.properties` and `config/ci-bio.properties` to `~/.intermine/intermine-test.properties`, `~/.intermine/testmodel.properties` and `~/.intermine/intermine-bio-test.properties`, replacing `PSQL_HOST`, `PSQL_USER` and `PSQL_PWD`. An existing file is never overwritten ("Skipping ... as it already exists"), so delete it to regenerate it;
4. runs `./gradlew publishToMavenLocal --stacktrace` in `plugin`, `intermine`, `bio`, `bio/sources` and `bio/postprocess`, in that order (with `./gradlew clean --stacktrace` first when `--clean` is given). `bio/postprocess-test` and `testmine` are not built by it.

It exits with an error unless the environment variables `PSQL_HOST`, `PSQL_USER` and `PSQL_PWD` are set (they go into the test property files):

```bash
export PSQL_HOST=localhost PSQL_USER=test PSQL_PWD=test
python3 -I config/lib/install_intermine.py
```

Flags (from `--help`):

| Flag | Default | Meaning |
|---|---|---|
| `--clean` | off | run `./gradlew clean` in each build before `publishToMavenLocal` |
| `--offline` | off | use `--offline_url` for the Central, Clojars, EBI and plugin repositories, and use just the zip file name as the wrapper distribution URL (the zip is then expected next to the wrapper properties) |
| `--offline_url` | `http://localhost:8081/repository/maven-public/` | the mirror (for example a Nexus) for `--offline` |
| `--central_url` | `https://repo1.maven.org/maven2/` | Maven Central |
| `--clojars_url` | `https://clojars.org/repo` | Clojars |
| `--ebi_url` | `https://www.ebi.ac.uk/Tools/maven/repos/content/groups/ebi-repo/` | EMBL-EBI repository |
| `--plugins_url` | `https://plugins.gradle.org/m2/` | Gradle plugin portal |
| `--gradle_distribution_url` | `https://services.gradle.org/distributions/gradle-8.14.6-bin.zip` | the distribution all seven wrappers download |
| `--gradle_distribution_sha256` | looked up by zip name | written as `distributionSha256Sum`; if the zip name is not known to the script (it knows 4.9, 6.9.4, 7.6.6 and 8.14.6) and no value is given, the line is left out and the wrapper does not verify the download |

### Gradle version

The version is not in the `.in` templates themselves (they contain `@@im_gradle_distribution_url@@`); it is the default of `--gradle_distribution_url`, `DEFAULT_GRADLE_VERSION` in `config/lib/install_intermine.py`. **Today that is Gradle 8.14.6** (the 5.1.1 release candidate, branch `release/5.1.1-candidate`, is the last 6.9.4 state). Check that constant rather than this page. To try another version without editing anything, pass `--gradle_distribution_url` (and `--gradle_distribution_sha256` for a version the script does not know).

The committed `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` of the seven builds are the ones Gradle 8.14.6's own `gradle wrapper` writes (the jar's SHA-256 is the published `gradle-8.14.6-wrapper.jar.sha256`). The jar only downloads and starts the distribution named in the generated `gradle-wrapper.properties`; regenerate the three files when the version changes.

## Publishing configuration: `config/gradle/publishing.gradle`

Every published module applies this script (`ext.imPublicationDescription = '...'` first, then `apply from: "${rootDir}/../config/gradle/publishing.gradle"`, with `../../` for `bio/sources` and `bio/postprocess`). It:

- applies `maven-publish` and `signing`, and adds the sources and javadoc jars (`java { withSourcesJar(); withJavadocJar() }`);
- creates one publication, `mavenJava` (`org.intermine:<project name>:<version>`, with the POM metadata), from `components.java`, or the war for modules that apply the `war` plugin;
- turns Gradle module metadata off, so only a POM is published, as in 5.1.0;
- maps scopes into the POM: `api` becomes `compile`, `implementation` and `runtimeOnly` become `runtime`, `providedCompile` becomes `provided`;
- registers `install` as a deprecated alias of `publishToMavenLocal`, so older callers keep working;
- signs only when the root task `centralBundle` is in the task graph, so local installs never need a key.

`config/gradle/webapp-classes-dependency.gradle` is applied by the two modules that depend on `intermine-webapp` (`intermine/webtasks` and `intermine/jbrowse-endpoint`). It writes that dependency into their POMs as `intermine-webapp:<version>:classes` instead of the 12 MB war, so a mine's war does not carry `intermine-webapp-<version>.war` in `WEB-INF/lib`.

`publishToMavenLocal` honours `-Dmaven.repo.local`, for an install that does not touch `~/.m2`:

```bash
cd intermine && ./gradlew -Dmaven.repo.local=/tmp/im-m2 publishToMavenLocal
```

`config/gradle/central-bundle.gradle` adds the `centralBundle` task; see [RELEASING.md](RELEASING.md).

## Files that the plugin's tasks write: `build/im/<task>`

In a project that applies the plugin (`bio/core`, `bio/model`, the `bio-source-*`, `bio-postprocess-*` and `bio/postprocess-test` modules, and a mine's `dbmodel` and `webapp`), every plugin task that writes a file has a directory of its own, `build/im/<task name>`, declared as its output, so that the task is up to date when its inputs have not changed. `build/resources/main` holds only what `processResources` puts there, so the jar and javadoc have the same inputs whatever ran before them.

| Task | Writes | Read by | In the jar |
|---|---|---|---|
| `mergeModels` | `<model>_model.xml`, the merged model (bio-core and bio-postprocess: bio-model's as it is) | `generateModel`, `buildDB`, `buildUnitTestDB`, the unit tests | only in `bio-model` and a mine's `dbmodel` (there its directory is a main output) |
| `copyDefaultInterMineProperties` | `default.intermine.properties` (`defaultInterminePropertiesFile`) | the Ant tasks, the unit tests | no; a mine's `webapp` takes it through `processResources` (`WEB-INF/classes`) |
| `copyGenomicKeys` | `<model>_keyDefs.properties` of the `bioModel` jar | `buildUnitTestDB`, the unit tests | no |
| `generateKeys` | `<model>_keyDefs.properties` merged from the sources of `project.xml` | `buildDB`, the Ant tasks | no |
| `copyUserProfileModel` | `userprofile_model.xml` | `createUserDB` | no |
| `createTestPrioritiesFile` | an empty `genomic_priorities.properties` | the unit tests of the bio sources | no |
| `copyMineProperties` (the build file's own, in each mine and in `bio/core`, `bio/model`, `bio/sources`, `bio/postprocess`, `bio/postprocess-test`, `testmine`) | `intermine.properties`, under `build/im/mine-properties` (the directory its `into` names) | `processResources` (it takes the task as a source), so every reader of `build/resources/main` depends on it | yes, through `processResources` |
| the integrate tasks | `build/im/integrate/so.obo` (source `so`) | the `so` converter | no |
| `runAcceptanceTests` | `acceptance_test.html`, the report of the run | people | no |
| `generateUpdateTriggers` | `add-update-triggers.sql`, `remove-update-triggers.sql`, `key-checker.sql` | people | no |

`copyGenomicModel` is kept as an alias of `mergeModels`. The unit tests get these directories on their runtime classpath after the test and main outputs and before the dependencies (bio-model's jar has an unmerged `genomic_model.xml`); the plugin's Ant tasks list `build/im/*` first on their classpath. After moving a checkout or a mine to this layout, run `clean` once: files that an earlier plugin wrote into `build/resources/main` would otherwise still be packed into the jar.

### Logging of the plugin's Ant tasks

The Ant tasks that the plugin runs (`buildDB`, `integrate`, the post-processing, the model and key tasks) load InterMine's classes in an Ant class loader of their own, with the log4j 1 API (`reload4j`). Nothing on that classpath configures it (`intermine-resources` had only a `log4j2.properties`, which nothing read because log4j 2 is on no class path; it is gone, im-ee7.58), so until this was fixed every logger printed `log4j:WARN No appenders could be found` and the INFO messages of the converters and the data loader were lost. The plugin now writes `build/im/antLogging/log4j.properties` (a console appender on the task's standard output, which Gradle shows) and puts that directory on the classpath of every Ant task. The level follows Gradle's: INFO at the default level and with `-i`, WARN with `-q` and `-w`, and `org.intermine` at DEBUG with `-d`. A mine that has its own `log4j.properties` or `log4j.xml` in its resources keeps it (the plugin then writes none). The plugin also sets the system property `log4j.ignoreTCL=true` in the Gradle daemon, which only reload4j reads: Gradle's own class loader carries the log4j-over-slf4j classes (`ConsoleAppender`, `PatternLayout`), and without the property reload4j would take those for the classes that the configuration names and drop the appender. The file is not a task output: the level of a run must not make `buildDB` or `integrate` out of date.

### Tests of the plugin

`plugin/src/test/groovy` holds Gradle TestKit tests (JUnit 4): each test writes a small synthetic mine (a root project with `dbmodel` and `webapp` projects, laid out like a converted HumanMine) under `plugin/build/testkit/mines/<test>`, applies the plugin of this checkout to it (`GradleRunner.withPluginClasspath()`) and runs its tasks: `createSoModel` (the SO additions file, up to date on a second run, relative and absolute SO paths), `mergeProperties` with the mine's `copyMineProperties` and `copyDefaultInterMineProperties`, `:webapp:war` (the mine's files first, the bio-webapp files with the model's additions, no repeated entry, no `ant:ant` jar, a `duplicatesStrategy` of the mine kept, no `:dbmodel` task needed; two versions of one library in `WEB-INF/lib` warn, or fail with `-PimWarDuplicateLibraries=fail`), `unwarBioWebApp` (no file of an earlier bio-webapp survives), the reload4j logging of the Ant tasks, and, in mines with a `project.xml` (`TestMine.createModelMine`), `mergeModels` of `mine-dbmodel` (the extra models and the additions of the sources in the order of `project.xml`, up to date, rerun when `project.xml` changes), `generateModel` (before `compileJava`, and only while `build/gen` does not exist), `runAcceptanceTests` and `generateUpdateTriggers` (their files are in `build/im/<task>`, not in `build/resources/main` or the jar, and are written again by each run), `generateKeys` (keys by source name, then by type; the error for a source without a keys file), the `integrate` and `postProcess` plugins (the bio-source and bio-postprocess jars chosen by `project.xml`, `-Psource` and `-Pprocess`, the pre-retrieve, loader and post-processor classes that their properties files name) and, for `bio/sources`, the `biosource-dbmodel` plugin (`TestMine.createBioSources`: `mergeModels` with the global additions, and a jar that carries none of the files written for the unit tests). The mines resolve only from a file Maven repository that the test writes, with fake `intermine-resources`, `bio-webapp`, `bio-model`, `intermine-integrate` and `bio-source-*` artifacts and the plugin's own jar; the `bio-model` and `intermine-integrate` jars carry stand-ins for `SOToModelTask`, `ModelMergerTask`, `ModelOutputTask`, `PostProcessorTask` and the pre-retrieve and loader tasks of the fake sources (`plugin/src/testKitTasks`). The tests need neither network nor PostgreSQL nor the other builds, and take about 40 seconds (each test about a second, the first one 9 s for the start of the TestKit daemon); CI runs them in the `intermine` suite, on both JDKs of the matrix (the test of relative SO paths only catches a regression to Ant's own resolution of the path on JDK 9+, so only the JDK 17 cells guard that). To run them:

```bash
cd plugin && ./gradlew test        # after install_intermine.py has written plugin/gradle.properties
```

The builds run in TestKit daemons with their own Gradle user home, `plugin/build/testkit/gradle`, on the JDK that runs the tests (8 or 17); they stop when the tests end. A failed test leaves its mine in `plugin/build/testkit/mines` and its output in the test report. `publishToMavenLocal` does not run the tests; `./gradlew build` does.

## Dependency versions: `gradle/libs.versions.toml`

Every third-party library the module build files use is declared once in the version catalog `gradle/libs.versions.toml`. Each of the seven `settings.gradle` files loads it as `libs`, so a module declares a dependency as

```groovy
api libs.commons.lang3                                  // alias commons-lang3
implementation(libs.hikaricp) { transitive = false }    // options go in a closure
```

The `[versions]` table holds one entry per library, or per family that is released together (`jackson`, `struts`, `lucene`, `jena`, `biojava`, `jami`, `jaxb`); `[libraries]` maps the alias to the Maven coordinate and a `version.ref`. The InterMine release itself (`intermine`) and the Checkstyle version (`checkstyle`) are in the same table.

To change a version, edit its line in `[versions]` and nothing else; every module that uses the library follows. To add a library, add a `[versions]` entry and a `[libraries]` entry (aliases are lower case; dashes become dots in the accessor, so `struts-core` is `libs.struts.core`), then use `libs.<alias>` in the module. One catalog entry means one version for the whole tree: if two modules need different versions of a library, add a second alias with its own `[versions]` entry and a comment saying why (as `jami-complexes` does for `bio/sources/psi-complexes`).

Version stances recorded in the catalog comments:

- `jena` is 3.17.0, the last release that runs on Java 8 (4.x needs Java 11, 5.x Java 17). Only the RDF model API is used, to write RDF, so `jena-core` is the compile dependency and `jena-arq` is a run-time one: with it on the class path `Model.write()` uses the RIOT writers, and the N-Triples and Turtle text differ without it (`RDFSerialisationTest` pins the output). RIOT's start-up also needs `libthrift` and `commons-compress` even for write-only use, so they cannot be excluded.
- `solr` is 8.11.4, the last 8.x. SolrJ follows the Solr server's major version, and a SolrJ 9 client is not supported against a Solr 8 server, so the move to 9 is part of the Solr server upgrade. `solr-solrj` is declared with its Netty, Jetty and Snappy dependencies excluded (never loaded: only `HttpSolrClient` is used); `zookeeper` must stay, because the javabin decoder calls a class in it.
- `lucene` stays at 3.0.2: it still serves the in-memory search over a user's lists and templates (`SearchRepository`, `SearchResults`). Keyword search, autocomplete and facets are Solr.

Outside the catalog:

- `buildscript { }` blocks read the catalog too (Gradle 8.14): the InterMine `plugin` in `bio/core`, `bio/model`, `bio/sources`, `bio/postprocess` and `bio/postprocess-test` is `libs.intermine.plugin`, the Cargo plugin in `testmine/webapp` is `libs.cargo.plugin`.
- `org.intermine:*` modules are in the catalog since im-ee7.33 (`libs.intermine.*`, `libs.bio.*`, all at the release version). They are still resolved from the build's own jars (the ivy repositories above), not from Maven Central, so a build file needs `libs.intermine.objectstore` and nothing else; `transitive = false`, a classifier (`variantOf(libs.intermine.webapp) { classifier "classes" }`) and the war type (`artifact { ... }`, as for the Tomcat zip below) are added where the build file used them before. The plugin's own sources (`WebAppPlugin`, `DBModelPlugin`, ...) keep reading the `imVersion` and `bioVersion` system properties: they run in a deployer's build, which has no catalog.
- The `.in` templates (`gradle.properties.in`, `gradle-wrapper.properties.in`) are not Gradle files, so they cannot read the catalog. `config/lib/install_intermine.py` reads `[versions] intermine` of the catalog itself and fills `@@im_version@@` and `@@bio_version@@` (the generated `systemProp.imVersion` / `systemProp.bioVersion`); the Gradle version is `DEFAULT_GRADLE_VERSION` in the script (see "Generated files" above).
- Vendored jars (`libs/*.jar`, the antlr caching jar in `flatDir`) have no coordinates and stay as file dependencies.
- The `@zip` Tomcat distribution that Cargo installs (`testmine/webapp/build.gradle`) takes its version from the catalog but adds the `zip` artifact type in the build file, because a catalog entry cannot carry an artifact type.

## Dependency configuration conventions

The build files use the Gradle 7 configuration names; `compile`, `testCompile`, `runtime` and `provided` are no longer used (removed in Gradle 7). What the build files do today:

| Configuration | Use | POM scope |
|---|---|---|
| `api` | a library whose types appear in the module's public or protected signatures, so that consumers need it to compile. Only available where `java-library` is applied: all `intermine/` and `bio/` modules, `bio/sources` and `testmine`. `bio/postprocess` and `bio/postprocess-test` apply `java`, and use `implementation`. | `compile` |
| `implementation` | used inside method bodies only, or needed only at run time | `runtime` |
| `runtimeOnly` | needed only at run time (for example `lucene-snowball` in `bio-webapp`, or `commons-io` in `intermine-webapp`) | `runtime` |
| `testImplementation` | test code only | not published |
| `providedCompile` | war modules (`intermine/webapp`, `intermine/webtasks`, `bio/webapp`, `testmine/webapp`): supplied by the servlet container or by another war, not packed into the war | `provided` |
| `compileOnly` | compile time only, and kept out of the war (once, in `testmine/webapp`) | none |
| `bundled` | jars that are not resolved from a repository but copied into the module's own jar | none (file dependencies have no coordinates) |

`bundled` is declared in `bio/core/build.gradle` (`obo.jar`, `bbop.jar`) as

```groovy
configurations {
    bundled
    implementation.extendsFrom bundled
}
dependencies {
    bundled fileTree(dir: 'libs', include: '*.jar')
}
jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from { configurations.bundled.collect { it.isDirectory() ? it : zipTree(it) } }
}
```

(`intermine/objectstore` declared it too until 6.0, for the vendored Torque 3.2 and Velocity 1.3 jars; `DdlGenerator` replaced them.) `bio/core/build.gradle` also adds the patched `antlr-2.7.6-caching.jar` from `intermine/libs` (`bundled files("${rootDir}/../intermine/libs/antlr-2.7.6-caching.jar")`). It replaces the old `provided` configuration, whose name was misleading: those jars are not "provided" by anyone, they are merged into the module's jar (the `libs/` directories hold vendored jars that are not in a public repository). Because `bundled` is added to `implementation`, they stay on the compile and test classpaths. `compileOnly` would have removed them from the tests. `duplicatesStrategy = EXCLUDE` keeps the first copy of entries that several bundled jars share, such as `META-INF/LICENSE.txt`.

Other configurations you will meet are build-specific and not part of the Gradle standard set: `bioModel`, `mergeModel`, `commonResources`, `imWebApp`, `testModelProps` and `testArtifacts` (the test jar that `intermine-testresources` shares with the modules that test against it, used as `testImplementation project(path: ':intermine-testresources', configuration: 'testArtifacts')`). The plugin creates `imApiJar` and `imAntlr` (renamed from `api` and `antlr`, which clash with the `java-library` and `antlr` plugins), `bioModel`, `bioModelForSOOnly`, `commonResources` and `mergeSource`, as resolvable-only configurations.

The `plugin/` build itself uses `implementation gradleApi()` and `implementation localGroovy()`, and for its tests `gradleTestKit()` and `libs.junit` (see [Tests of the plugin](#tests-of-the-plugin)).

## Common commands

All run inside one build directory, after `config/lib/install_intermine.py` has generated the properties and the earlier builds exist:

```bash
./gradlew publishToMavenLocal      # build the jars (into build/libs) and install them
./gradlew build                    # compile, checkstyle and unit tests (tests need PostgreSQL)
./gradlew checkstyleMain           # checkstyle only
```

Checkstyle 9.3 (`checkstyle` in `gradle/libs.versions.toml`, the last release that runs on JDK 8) is run by the plain Gradle `checkstyle` plugin on `src/main` only, with the rules in `config/checkstyle/checkstyle.xml`, the header in `config/checkstyle/copyright_header.txt` and the exceptions in `config/checkstyle/suppressions.xml`. It needs nothing from the build classpath. The Javadoc comments must be ones that its Javadoc parser can read: a comment with a stray `<` or `>` (a generic type, an XML element, an e-mail address in `@author`), an unclosed element or an empty `@return` gives a `MissingDeprecated` violation `Javadoc comment ... has parse error`; put such text in `{@code ...}` or escape it (`&lt;`). (A suppression of these messages hid 26 of them until im-ee7.45.)

**`@throws` tags are not checked.** Checkstyle before 8.28 loaded the exception classes of a method's `throws` clause and asked for a `@throws` tag for each checked one; 9.3's `JavadocMethod` has no classpath, so a missing tag is no longer reported. Two replacements were measured on the tree (every `src/main/java` file of intermine, bio, bio/sources and bio/postprocess for the first, the `compileJava` of intermine and bio for the second). `JavadocMethod` with `validateThrows=true` needs no classpath, but wants a tag for every exception in a `throws` clause and every `throw new`, checked or not: 569 new violations, of which about 500 are `RuntimeException` (205), `IllegalArgumentException` (172), `NullPointerException` (77), `IllegalStateException` (19) and the like, so it is not enabled. javac's `-Xdoclint:missing` knows which exceptions are checked and reports `warning: no @throws for <type>` for those only: 16 in intermine and 1 in bio (`bio/sources` and `bio/postprocess` not measured), but the same option prints 5,600 `no comment`, 116 `no @param` and 41 `no @return` warnings for intermine alone, and javac cannot select one message, so it is not on in the builds either. To see the missing tags, add `options.compilerArgs += ['-Xdoclint:missing', '-Xmaxwarns', '100000']` to the `JavaCompile` tasks (an init script is enough) and search the output for `no @throws`; a CI step that does so and fails on a match is the way to enforce them again, after the 17 are fixed. The reports are `build/reports/checkstyle/main.xml` and `main.html` in each module.

These are the commands in `CONTRIBUTING.md`, `config/ci/run.sh` and `config/lib/install_intermine.py`. CI adds `-Dorg.gradle.jvmargs=-Xmx4g --max-workers=4 --no-daemon --warning-mode all --stacktrace`.

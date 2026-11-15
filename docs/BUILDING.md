# Building InterMine

This page describes how the back-end code in this repository is built: the Gradle builds, how they find each other, the generated build files and the dependency conventions. For tests see [TESTING.md](TESTING.md), for releases [RELEASING.md](RELEASING.md), for mines (humanmine, flymine, ...) that use the InterMine Gradle plugin see [UPGRADING-build.md](UPGRADING-build.md).

## Requirements

- JDK 17. JDK 8 still works (CI runs both) until 6.0.0 is released; JDK 21 passes the full gate too (its javac warns that `--release 8` is obsolete: some later JDK will no longer build Java 8 bytecode). The JDK that runs Gradle, i.e. `JAVA_HOME` (`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 PATH=$JAVA_HOME/bin:$PATH`), compiles the code, runs the tests and runs the plugin's Ant tasks (inside the Gradle daemon). No Java toolchain is declared, so `org.gradle.java.installations.*` settings are not needed: a toolchain would compile and test on one JDK while the daemon runs InterMine code on another, and a machine with only JDK 8 could no longer build.
- Whatever the build JDK, the jars are Java 8 bytecode (class file version 52), so they run on a JDK 8 Tomcat. On JDK 9+ the code is compiled with `--release 8`, i.e. against the Java 8 API, and the tests run with `--add-opens=java.base/java.lang=ALL-UNNAMED`, which cglib 3.1 needs on JDK 16+ (`org.intermine.util.DynamicBean`). Both are set in one place, `config/gradle/java-target.gradle`, which every build applies. Gradle 8.14.6 runs on JDK 8 to 24; on JDK 8 it prints one deprecation in every build ("Executing Gradle on JVM versions 16 and lower has been deprecated"), because Gradle 9 needs JDK 17 to run.
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

1. **Local file (ivy) repositories of `build/libs`.** Each consuming build adds one `ivy { url "file://<dir>" }` repository per module directory, with the layout `[artifact]-[revision](-[classifier]).[ext]` and `metadataSources { artifact() }` (the jar alone, no descriptor). The directories come from `gradle.properties`: `intermine.development.intermineRootDir` plus `intermine.development.intermineModules` (for example `api,integrate,model,...`), and for the later builds `intermine.development.bioRootDir` plus `intermine.development.bioModules`. So `intermine-objectstore:5.1.1` is resolved from `<checkout>/intermine/objectstore/build/libs/intermine-objectstore-5.1.1.jar`. This is what `bio/build.gradle`, `bio/sources/build.gradle`, `bio/postprocess/build.gradle`, `bio/postprocess-test/build.gradle` and `testmine/build.gradle` do. `intermine/` does not need it: it only depends on its own subprojects.
2. **The plugin from `plugin/build/libs`.** `bio/core`, `bio/model`, `bio/sources`, `bio/postprocess` and `bio/postprocess-test` load the plugin in a `buildscript` block, from an ivy repository at `intermine.plugin.repo` (layout `[artifact]-[revision].[ext]`, so `plugin-5.1.1.jar`), with Maven Central as fallback, at version `System.getProperty("imVersion")`. `testmine` does not use the plugin.
3. **Maven Central** (`intermine.maven.central`), plus the EBI repository (`intermine.maven.ebi`) and Clojars (`intermine.maven.clojars`, testmine only), for everything that is not built here, and for released InterMine artifacts when no local jar exists.

No build file uses `mavenLocal()`. `publishToMavenLocal` still matters for two reasons: it depends on the jar, war, sources and javadoc tasks, so running it is what fills the `build/libs` directories that mechanisms 1 and 2 read; and it installs the artifacts into `~/.m2` (or into the directory given with `-Dmaven.repo.local=<dir>`) for builds outside this repository, such as a mine that adds `mavenLocal()`. That is why `config/lib/install_intermine.py`, `testmine/setup.sh` and `config/ci/run.sh` all call it.

Versions are passed as system properties set in `gradle.properties` (`systemProp.imVersion`, `systemProp.bioVersion`, `systemProp.flymineVersion` in `bio/` and `bio/sources/`), and read with `System.getProperty("imVersion")`. `testmine/build.gradle` has its own `imVersion`.

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

`publishToMavenLocal` honours `-Dmaven.repo.local`, for an install that does not touch `~/.m2`:

```bash
cd intermine && ./gradlew -Dmaven.repo.local=/tmp/im-m2 publishToMavenLocal
```

`config/gradle/central-bundle.gradle` adds the `centralBundle` task; see [RELEASING.md](RELEASING.md).

## Dependency versions: `gradle/libs.versions.toml`

Every third-party library the module build files use is declared once in the version catalog `gradle/libs.versions.toml`. Each of the seven `settings.gradle` files loads it as `libs`, so a module declares a dependency as

```groovy
api libs.commons.lang                                   // alias commons-lang
implementation(libs.hikaricp) { transitive = false }    // options go in a closure
```

The `[versions]` table holds one entry per library, or per family that is released together (`jackson`, `struts`, `lucene`, `jena`, `biojava`, `jami`, `jaxb`); `[libraries]` maps the alias to the Maven coordinate and a `version.ref`. The InterMine release itself (`intermine`) and the Checkstyle version (`checkstyle`) are in the same table.

To change a version, edit its line in `[versions]` and nothing else; every module that uses the library follows. To add a library, add a `[versions]` entry and a `[libraries]` entry (aliases are lower case; dashes become dots in the accessor, so `struts-core` is `libs.struts.core`), then use `libs.<alias>` in the module. One catalog entry means one version for the whole tree: if two modules need different versions of a library, add a second alias with its own `[versions]` entry and a comment saying why (as `jami-v3` does for `bio/sources/psi-complexes`).

Outside the catalog:

- `buildscript { }` blocks keep literal coordinates (they could not see `libs` on Gradle 7.6; on 8.14 they can, so this is a possible clean-up), for example the Cargo plugin in `testmine/webapp/build.gradle` and the InterMine `plugin` classpath in `bio/core` and `bio/model`. Change those by hand.
- `org.intermine:*` modules are resolved from the build's own jars and use `System.getProperty("imVersion")` (or `imVersion`), which the `gradle.properties.in` templates set. They are not in the catalog.
- The `.in` templates (`gradle.properties.in`, `gradle-wrapper.properties.in`) are not Gradle files, so they cannot read the catalog. The Gradle version and the generated `systemProp.imVersion` are changed there (see "Generated files" above).
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

`bundled` is declared in `intermine/objectstore/build.gradle` and `bio/core/build.gradle` as

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

It replaces the old `provided` configuration, whose name was misleading: those jars are not "provided" by anyone, they are merged into the module's jar (the `libs/` directories hold vendored jars that are not in a public repository). Because `bundled` is added to `implementation`, they stay on the compile and test classpaths. `compileOnly` would have removed them from the tests. `duplicatesStrategy = EXCLUDE` keeps the first copy of entries that several bundled jars share, such as `META-INF/LICENSE.txt`.

Other configurations you will meet are build-specific and not part of the Gradle standard set: `bioModel`, `mergeModel`, `commonResources`, `imWebApp`, `testModelProps` and `testArtifacts` (the test jar that `intermine-testresources` shares with the modules that test against it, used as `testImplementation project(path: ':intermine-testresources', configuration: 'testArtifacts')`). The plugin creates `imApiJar` and `imAntlr` (renamed from `api` and `antlr`, which clash with the `java-library` and `antlr` plugins), `bioModel`, `bioModelForSOOnly`, `commonResources` and `mergeSource`, as resolvable-only configurations.

The `plugin/` build itself uses `implementation gradleApi()` and `implementation localGroovy()`.

## Common commands

All run inside one build directory, after `config/lib/install_intermine.py` has generated the properties and the earlier builds exist:

```bash
./gradlew publishToMavenLocal      # build the jars (into build/libs) and install them
./gradlew build                    # compile, checkstyle and unit tests (tests need PostgreSQL)
./gradlew checkstyleMain           # checkstyle only
```

Checkstyle 9.3 (`checkstyle` in `gradle/libs.versions.toml`, the last release that runs on JDK 8) is run by the plain Gradle `checkstyle` plugin on `src/main` only, with the rules in `config/checkstyle/checkstyle.xml`, the header in `config/checkstyle/copyright_header.txt` and the exceptions in `config/checkstyle/suppressions.xml`. It needs nothing from the build classpath. The reports are `build/reports/checkstyle/main.xml` and `main.html` in each module.

These are the commands in `CONTRIBUTING.md`, `config/ci/run.sh` and `config/lib/install_intermine.py`. CI adds `-Dorg.gradle.jvmargs=-Xmx4g --max-workers=4 --no-daemon --warning-mode all --stacktrace`.

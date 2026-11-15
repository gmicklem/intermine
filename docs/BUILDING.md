# Building InterMine

This page describes how the back-end code in this repository is built: the Gradle builds, how they find each other, the generated build files and the dependency conventions. For tests see [TESTING.md](TESTING.md), for releases [RELEASING.md](RELEASING.md), for mines (humanmine, flymine, ...) that use the InterMine Gradle plugin see [UPGRADING-build.md](UPGRADING-build.md).

## Requirements

- JDK 8. Every build compiles with `sourceCompatibility = 1.8`, and CI runs on JDK 8 only. The Gradle version in the templates (below) cannot run on JDK 17 or 21. Moving the builds to JDK 17 is planned together with the Gradle 8 upgrade; it is not done on this branch.
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
| `--gradle_distribution_url` | `https://services.gradle.org/distributions/gradle-6.9.4-bin.zip` | the distribution all seven wrappers download |
| `--gradle_distribution_sha256` | looked up by zip name | written as `distributionSha256Sum`; if the zip name is not known to the script (it knows 4.9 and 6.9.4) and no value is given, the line is left out and the wrapper does not verify the download |

### Gradle version

The version is not in the `.in` templates themselves (they contain `@@im_gradle_distribution_url@@`); it is the default of `--gradle_distribution_url`, `DEFAULT_GRADLE_VERSION` in `config/lib/install_intermine.py`. **Today that is Gradle 6.9.4.** A Gradle 7.6 upgrade is in progress on another branch, and a later upgrade to Gradle 8.x is planned, so check that constant rather than this page. To try another version without editing anything, pass `--gradle_distribution_url` (and `--gradle_distribution_sha256` for a version the script does not know).

The committed `gradle/wrapper/gradle-wrapper.jar` files are old (the manifest of the one in `intermine/` says Gradle 4.1). That does not matter: the jar only downloads and starts the distribution named in the generated `gradle-wrapper.properties`.

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

## Dependency configuration conventions

The build files use the Gradle 7 configuration names; `compile`, `testCompile`, `runtime` and `provided` are no longer used (they are still accepted by Gradle 6.9.4, but removed in Gradle 7). What the build files do today:

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

These are the commands in `CONTRIBUTING.md`, `config/ci/run.sh` and `config/lib/install_intermine.py`. CI adds `-Dorg.gradle.jvmargs=-Xmx4g --max-workers=4 --no-daemon --warning-mode all --stacktrace`.

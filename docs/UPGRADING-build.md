# Upgrading a mine's build (deployer guide)

This guide is for people who run a mine (humanmine, flymine and similar) whose `build.gradle` uses the InterMine Gradle plugin (`classpath group: 'org.intermine', name: 'plugin', version: ...`). It covers two releases, in two separate parts:

- **5.1.1, non-breaking** (Gradle 6.9.4, JDK 8; branch `release/5.1.1-candidate` of https://github.com/gmicklem/intermine): a mine can stay on Gradle 4.9, or move to 6.9.4. See [5.0.x or 5.1.0 to 5.1.1](#50x-or-510-to-511).
- **6.0.0, breaking** (Gradle 8.14.6, JDK 17; branch `maint-2026`): the mine's build files must change. See [5.1.1 to 6.0.0](#511-to-600-gradle-814) and the notes around it.

Neither has been released yet. The tree of `maint-2026` still carries the version number `5.1.1` for what will be 6.0.0 (the number is changed when the release is cut), so artifacts built from it are called 5.1.1 although they are not the 5.1.1 release. Wherever this guide says `5.1.1` for the 6.0 line, a deployer uses `6.0.+` once 6.0.0 is on Maven Central.

The sources are the maintainers' planning notes (`decisions/gradle-upgrade-path.md`, `baselines/intermine-gradle-6.9.4-deprecations.md` and `plans/10-intermine-backend.md` in https://github.com/gmicklem/intermine-planning) a smoke build of a copy of flymine on Gradle 6.9.4 with the 5.1.1 plugin, and the conversion of HumanMine (`afdd8e9`) and FlyMine (`a507ed84`) to Gradle 8.14.6 with the plugin of `maint-2026` (now the `maint-2026` branches of https://github.com/gmicklem/humanmine, commits `f426ac8` and `58fbf8f`, and https://github.com/gmicklem/flymine, commits `3904f70b` and `e7ed6215`). Line numbers in the 5.1.1 section are those of the flymine copy (humanmine is the same, a few lines apart).

## Which versions go together

| InterMine plugin | Gradle in the mine | JDK | Status |
|---|---|---|---|
| 5.0.x, 5.1.0 | 4.9 | 8 | the current mines |
| 5.1.1 | 4.9 (checked) or 6.9.4 (checked); **6.9.4 is recommended** | 8 (Gradle 6.9.4 does not run on JDK 17) | branch `release/5.1.1-candidate`; not yet released |
| 6.0.0 | 8.14.6 | build: 17 (8 still works, 21 checked); run: 8, 17 or 21, see [JDK](#jdk-for-600) | branch `maint-2026`, version number `5.1.1` until the release is cut; not yet released |

Plugin 6.0.0 needs Gradle 8.14 in the mine: the plugin is compiled against Gradle's own Groovy, and a plugin built for a newer Groovy fails on an older Gradle. Plugin 5.1.1 is built so that it still runs on Gradle 4.9. A mine that pins `imVersion=5.1.+` (flymine does) picks up any 5.1.x plugin automatically, and cannot pick up 6.0.0 by accident; a mine that pins `5.0.+` stays on 5.0.x.

## 5.0.x or 5.1.0 to 5.1.1

This is the non-breaking release. Branch `release/5.1.1-candidate` (the last Gradle 6.9.4 state; its plugin is compiled with Gradle 6.9.4) was checked in a mine on **Gradle 4.9**: the plugin loads, the build configures, and the tasks `tasks`, `createSoModel` and (in `webapp`) `mergeProperties` start. It was also checked on **Gradle 6.9.4**, with the changes below. Loading data, `buildDB` of a full mine and deploying to Tomcat were not run for this line, and this branch needs JDK 8.

So nothing is required except `imVersion` and `bioVersion`, with one exception that does not depend on InterMine: a `webapp/build.gradle` that still has the `gretty` line (step 4) does not configure on any Gradle today, 4.9 included, because jcenter is gone. To move the mine to Gradle 6.9.4, which shows every deprecation that 8.14 will turn into an error:

1. Set `imVersion` and `bioVersion` in the mine's `gradle.properties` to `5.1.1`.
2. Update the wrapper: `./gradlew wrapper --gradle-version 6.9.4 --distribution-type bin`.
3. Delete the line `inputs.sourceFiles.stopExecutionIfEmpty()` (flymine `build.gradle:50`, humanmine `build.gradle:53`). **Gradle 5.0 removed that method, so without this edit a mine does not configure on any Gradle from 5.0 on.** It is the only edit that is needed to configure on 6.9.4: an empty `Copy` task is skipped as `NO-SOURCE` by Gradle itself.
4. Deal with the `gretty` line (flymine `webapp/build.gradle:2`): an `apply from: https://raw.githubusercontent.com/akhikhl/gretty/...` script. Its buildscript resolves `org.akhikhl.gretty:gretty:2.0.0` from jcenter, which is dead (it returned 403 in the smoke build), so the build cannot configure. This is not a Gradle-version problem, and it hits a mine on Gradle 4.9 as well. Two ways out:
   - **Remove gretty** (what InterMine recommends): delete the `apply from` line and the `gretty { }` block of the same file. The `cargo` plugin does the deployment. Gretty was already dropped from testmine in InterMine's own `RELEASE_NOTES` because it does not work with Gradle 4.9, and the script is an unpinned remote file.
   - **Keep gretty**: gretty 2.0.0 itself is on Maven Central, only the script looks for it in jcenter. Save the script in the mine's repository (from a fixed commit of akhikhl/gretty, not `master`), change the `jcenter()` in its `buildscript { repositories { } }` to `mavenCentral()`, and point the `apply from:` at that copy. This was not tried here. Gretty 2.0.0 dates from the Gradle 4 era and is not known to work on Gradle 6.9.4 or 8.14, so it is a stop-gap for a mine that stays on Gradle 4.9; the 6.0.0 steps below remove it.
5. Run `./gradlew --warning-mode all :dbmodel:tasks` to see the remaining deprecations. In the flymine smoke build on 6.9.4 they were:

| Warning | Where (flymine copy) | Fix |
|---|---|---|
| `maven` plugin | `build.gradle:22` | delete `apply plugin: 'maven'` |
| `jcenter()` repository (removed in Gradle 8) | `build.gradle:30` | delete it |
| `compile` / `testCompile` | `build.gradle:38,40`, and `dbmodel/` and `webapp/` build files | `implementation` / `testImplementation` (see below) |

## JDK for 6.0.0

InterMine 6.0.0 is built and tested on JDK 17, and its jars are still Java 8 bytecode (class file version 52), so a mine can keep a JDK 8 Tomcat. To move a mine to JDK 17 (or 21) (the converted HumanMine builds its war on JDK 17 and on JDK 8):

- **Build JDK.** The JDK that runs Gradle (`JAVA_HOME`) is the one that compiles the mine, and the plugin's tasks (`integrate`, `buildDB`, `postprocess`, ...) run inside the Gradle daemon on it. Set `JAVA_HOME` before `./gradlew`; no toolchain setting is needed. Gradle 8.14 runs on JDK 8 to 24.
- **If Tomcat stays on JDK 8 but the mine is built on 17**, add `tasks.withType(JavaCompile).configureEach { options.release = 8 }` to the mine's Java projects, so that its own code is compiled against the Java 8 API (`sourceCompatibility = 1.8` alone does not do that).
- **JVM option on JDK 16+.** InterMine uses cglib 3.1 to build objects that belong to more than one model class, and on JDK 16+ cglib needs `--add-opens=java.base/java.lang=ALL-UNNAMED`. Where it is already set: the Gradle daemon (Gradle adds it), and Tomcat 9 started with `bin/catalina.sh` or `startup.sh` (`catalina.sh` adds it to `JDK_JAVA_OPTIONS`). Where the mine must add it: a Tomcat started another way (Cargo's local container: `cargo { local { jvmArgs = '... --add-opens=java.base/java.lang=ALL-UNNAMED' } }`; jsvc, systemd units that call `java` directly, embedded Tomcat), the `test` tasks of a mine that has its own tests (`test { jvmArgs '--add-opens=java.base/java.lang=ALL-UNNAMED' }`), and any script that runs InterMine code with a plain `java`. JDK 8 rejects the option, so add it only for JDK 9+ (`if (JavaVersion.current().isJava9Compatible())`).
- **psi-complexes: small molecules get no name.** The converter used to look the name up at the ChEBI SOAP web service with a vendored JAX-WS client, which JDK 11 cannot run and which the service stopped answering (HTTP 500, Nov 2026). The client was removed in 6.0.0; the small molecules keep their ChEBI identifier but no `name`.

## 5.1.1 to 6.0.0 (Gradle 8.14)

This is the list a deployer follows, in order. It was run on HumanMine (`imVersion` 5.0.+, Gradle 4.9) and FlyMine (5.1.+, Gradle 4.9) at their upstream master, with the plugin and the InterMine artifacts built from `maint-2026` (version number 5.1.1; the number 6.0.0 is set when the release is cut, and until then "the 6.0 line" means `maint-2026`), and run again with the final plugin (webapp plugin composing the war, relative SO term paths, commons-lang3, Checkstyle 9.3, JDK 17 support). Both mines then pass `:dbmodel:buildDB` and `:webapp:war` on Gradle 8.14.6 with JDK 17 (HumanMine also with JDK 8), with no deprecation from their own build files, and the war has no repeated library. Loading data (`integrate`, `postProcess`) and deploying to Tomcat were not run.

You can do steps 1 to 7 directly on a mine that is still on Gradle 4.9; the edits do not depend on the 5.1.1 section above.

### Before you start

- **JDK 17** for Gradle (`export JAVA_HOME=...`). JDK 8 also works, with Gradle's deprecation notice; see [JDK](#jdk-for-600).
- **The mine's property file**, `~/.intermine/<mine>.properties` (`humanmine.properties`, `flymine.properties`; the name is `mineName` in the root `build.gradle`, plus `.<release>` when you pass `-Prelease=...`). `System.env.HOME` is read, so run the build with the `HOME` that has the file. It is not in the repository. The keys the build reads are `db.production.datasource.{serverName,databaseName,user,password}`, the same four for `db.userprofile-production` (and `db.common-tgt-items` for `buildTgtItems`), `webapp.path`, `webapp.deploy.url`, `webapp.manager` and `webapp.password` (cargo), `superuser.account`, `superuser.initialPassword`, `project.title` and the other `project.*` keys of your mine. `config/testmodel.properties` in this repository is the shortest example (it uses the `unittest` aliases of testmine; a production mine uses `db.production` and `db.userprofile-production`).
- **The mine's own data source jars on Maven.** The webapp compile needs the full data-source set, not only the sources that InterMine publishes: see the next section. Do this first, or the build stops at step 7.
- **Unreleased 6.0 artifacts** (until 6.0.0 is on Maven Central): in this checkout run `./gradlew publishToMavenLocal` in `plugin`, `intermine`, `bio`, `bio/sources` and `bio/postprocess`, in that order, after `config/lib/install_intermine.py` (see [BUILDING.md](BUILDING.md)). The mine's `buildscript` and `repositories` already list `mavenLocal()` first. If you publish to another directory, pass `-Dmaven.repo.local=<dir>` to the mine's build too.

### The data sources that are not on Maven Central

The plugin turns every `<source>` of the mine's `project.xml` into a dependency `org.intermine:bio-source-<type>:<version>` (the `name` for `intermine-items-xml-file` sources). Generating the model merges the `*_additions.xml` of all those jars into `genomic_model.xml`, and the generated classes (`org.intermine.model.bio.*`) are what the mine's `webapp` Java code compiles against. So:

- A source whose jar is missing stops the build in `:dbmodel:generateKeys` with `Could not find org.intermine:bio-source-redfly:9.9.9` (the message lists every repository it tried).
- A `project.xml` that is cut to the sources that you have gives a model without the classes of the others, and `:webapp:compileJava` fails with `error: cannot find symbol  import org.intermine.model.bio.MicroArrayAssay;` (FlyMine, cut to the 47 sources of InterMine; the same for `FlyAtlasResult`, `MicroArrayResult`). It is data, not Gradle: restore the sources. Note that `mergeModels` only runs when `dbmodel/build` has no model yet, so after editing `project.xml` delete `dbmodel/build` (or run `./gradlew :dbmodel:clean`).

This repository's `bio/sources` build publishes 44 `bio-source-*` modules. The others that the two mines list are in the mines' own repositories and are not on Maven Central: HumanMine's 16 (`ncbi-summaries`, `mgi-alleles`, `arrayexpress-atlas`, `protein-atlas`, `protein-atlas-rna`, `atlas-express`, `clinvar`, `huge-gwas`, `hpo`, `hpo-annotation`, `orphanet`, `disgenet-disease-associations`, `depmap-sample-info`, `depmap-expression`, `gtex`, `humanmine-static`, version 4.3.0) are in https://github.com/intermine/humanmine-bio-sources, FlyMine's 19 (`drosophila-homology`, `rnai`, `flyatlas`, `flybase-alleles`, `redfly`, `fly-fish`, `miranda`, `affy-probes`, `flymine-static`, ..., version 4.2.0) in https://github.com/intermine/flymine-bio-sources. Those two repositories are Gradle 4.9 builds too (`compile`, `maven`, `signing`, `uploadArchives`; the `*-static` sources also apply the bintray and artifactory plugins, which no longer resolve) and publish nothing to Central. To build a mine you therefore convert and publish them first, to your own Maven repository or to `mavenLocal()`: `java-library` and `maven-publish` instead of `java`, `maven`, `signing` and `uploadArchives`; `api` instead of `compile`; a `jar { exclude 'intermine.properties' }`; drop the bintray, artifactory and checkstyle parts; `publishToMavenLocal` at the version the mine's `project.xml` asks for (`version="4.3.0"` / `"4.2.0"`; a source without `version` takes `bioVersion`). The test run for this guide did that in a scratch copy of each repository (nothing was committed): a new root `build.gradle` of about 70 lines (the `buildscript` with the InterMine `plugin` from `mavenLocal()`; `java-library`, `maven-publish`, `dbmodel` and `biosource-dbmodel` on every subproject; the `java { }` block and `options.release = 8`; the `bioModel` and `mergeModel` configurations; `api` for `bio-core`, `intermine-resources` and `commons-collections:3.2`; `mergeModel` on `intermine-objectstore` and `bioModel` on `bio-model` with `transitive: false`; `dbModelConfig`, plus `bioSourceDBModelConfig { globalAdditionsFile = ... }` for FlyMine; the same `copyMineProperties` task and `processResources { from copyMineProperties }` as the mines; `jar { exclude 'intermine.properties' }`; a `publishing { }` block with `from components.java`), the two `*-static` build files cut down to their `sourceSets`, one `api bio-model` and the `processResources` line (their bintray, artifactory and signing parts removed), a `gradle.properties` with `systemProp.imVersion` and `systemProp.bioVersion` (`5.1.1`) and the Gradle 8.14.6 wrapper files of this repository. The checkstyle, sources-jar and javadoc-jar parts of the old root files were dropped. The converters need no `commons-lang` or `log4j` declaration of their own: they import `org.apache.commons.lang` and `org.apache.log4j`, and both still arrive through `bio-core`. Nothing else in those repositories needed to change, and all the source jars of both mines compiled on JDK 17 with `--release 8` (`./gradlew -Dmaven.repo.local=<dir> publishToMavenLocal -x test`, about 30 seconds each). Converting those two repositories properly is a separate piece of work.

### Steps

1. **Wrapper.** Gradle 8.14.6, committed with its jar as before:

   ```bash
   ./gradlew wrapper --gradle-version 8.14.6 --distribution-type bin \
       --gradle-distribution-sha256-sum 7988ed071b2a07900e2ec715fca15c6ab72bce6db433af301fa7fa7e9407bd24
   ```

   On the old Gradle this works as long as the build still configures (it does for the 5.1.1 section); otherwise run `gradle wrapper ...` from an empty directory with any Gradle 8.14 and copy `gradlew`, `gradlew.bat` and `gradle/wrapper/*`. Behind a proxy that rejects the HEAD request, the task stops with `Test of distribution url ... failed`; add `--no-validate-url` and delete the `validateDistributionUrl=false` line that this writes into `gradle-wrapper.properties`.
2. **`gradle.properties`.** `systemProp.imVersion` and `systemProp.bioVersion` to `6.0.+` (while testing against the unreleased branch: the exact version that you published, `5.1.1` today). `5.0.+` / `5.1.+` cannot pick up a 6.0 plugin by accident, and `6.0.+` stays on 6.0.x. Leave `systemProp.javax.xml.stream.XMLOutputFactory` as it is: it still works on JDK 8 and 17.
3. **Root `build.gradle`**, in `buildscript` and the `subprojects` block:
   - add `gradlePluginPortal()` to the `buildscript` repositories and raise `com.bmuschko:gradle-cargo-plugin` to `2.9.0` (2.9.0 is only on the Gradle plugin portal, not on Maven Central: without the repository the build stops with `Could not find com.bmuschko:gradle-cargo-plugin:2.9.0`; 2.8.0 is on Central and configures and runs the war tasks on 8.14.6, but neither version was run against a Tomcat here);
   - delete `apply plugin: 'maven'`, `jcenter()` and `inputs.sourceFiles.stopExecutionIfEmpty()`;
   - `sourceCompatibility`/`targetCompatibility` into a `java { }` block, and add `options.release = 8` for `JavaCompile` when `JavaVersion.current().isJava9Compatible()` (a JDK 8 `javac` has no `--release`);
   - `compile` to `implementation`, `testCompile` to `testImplementation`; `url '...'` to `url = '...'`, `description "..."` to `description = "..."`;
   - delete `compile('ant:ant:1.6.5')`: it is a 2005 Ant that ends up in `WEB-INF/lib` next to `ant-1.10.x`, and nothing in the mine needs it;
   - `copyMineProperties` writes into a directory of its own and `processResources` picks it up (explained below the example).
4. **`dbmodel/build.gradle`.** `compile` to `implementation` (`bioModel` stays as it is); keep `srcDirs = ['build/gen']`. Leave the two paths in `dbModelConfig` (`soTermListFilePath = "dbmodel/resources/so_terms"`, `soAdditionFilePath = "dbmodel/build/so_additions.xml"`) as they are: the plugin resolves a relative path against the mine's root directory, absolute paths still work, and nothing needs `$rootDir` any more. (Plugins before this fix (the 5.1.1 builds of this branch included) handed the string to Ant, which resolves it against the working directory of the JVM, and a Gradle 8 daemon on JDK 9 or later has its own `daemon/8.14.6` directory as working directory: `Could not find file containing SO terms to add to the model ... Property was: <gradle user home>/daemon/8.14.6/dbmodel/resources/so_terms`. A mine that was converted with the `$rootDir/...` form keeps working.)
5. **`webapp/build.gradle`.**
   - Delete the `apply from: "https://raw.githubusercontent.com/akhikhl/gretty/..."` line and the `gretty { }` block. The script resolves gretty 2.0.0 from jcenter, which now answers 403. The cargo plugin deploys. Gretty also passed `-Dorg.apache.el.parser.SKIP_IDENTIFIER_CHECK=true` to its Tomcat; your own Tomcat needs it too (`CATALINA_OPTS`, or `jvmArgs` in a cargo `local { }` block).
   - `compile` to `implementation`. Keep `implementation (project(":dbmodel")) {transitive = false}`; there is no need for `java-library` or `api`, because the webapp lists what it needs itself.
   - `JavaExec`: `main = ...` to `mainClass = ...` (`blueGenesStart`; Gradle 8.14 deprecates `main`, Gradle 9 removes it).
   - War composition: the plugin builds the war now, so delete everything in `webapp/build.gradle` that composed it: the `war { dependsOn ...; finalizedBy 'warWebApp' }` block, the `warWebApp` task, the `copyWebappContent` task, the `explodedWebAppDir` variable, the `tasks.named('mergeProperties') { inputs.files(...) }` block of the first conversions and any `tasks.withType(War) { classpath = classpath.filter { ... } }` block. `mergePropsDir` and `propsDir` in `webappConfig` can go as well: the plugin writes `web.properties` and `objectstoresummary.properties` to `build/props` unless `propsDir` says otherwise. What the plugin does instead: it applies the `war` plugin, and the `war` task takes, first the mine's own `src/main/webapp` (the war plugin adds it by itself), then `build/explodedWebApp`, which the `unwarBioWebApp` task fills (it is a Sync now: it unpacks the `bio-webapp` war and makes the struts, tiles, `web.xml` and message additions of the mine's model in the same task), then `WEB-INF/web.properties` (merged with `intermine.properties` by `mergeProperties`) and `objectstoresummary.properties` from `build/props`. Every part comes from the output of the task that makes it, so the war needs no `dependsOn`. `duplicatesStrategy` is `EXCLUDE` on the `war` task, so the first entry of a name wins: the mine's own files replace those of bio-webapp, and a jar that the mine's classpath and bio-webapp both carry is listed once; the unmerged `WEB-INF/web.properties` of the mine and of bio-webapp are excluded. Your cargo tasks keep `dependsOn war`; the `addStrutsConfig` task still exists, empty, for build files that mention it. A mine that does not update still gets the same war: a `warWebApp` that it keeps is rebuilt over the plugin's war from the same files, and a `war { dependsOn ... }` is harmless. A `copyWebappContent` has to go: it is a Copy into the directory that `unwarBioWebApp` now owns.
   - Why this was a plugin job: `warWebApp` zipped `build/explodedWebApp`, which `unwarBioWebApp` and `addStrutsConfig` filled (the unpacked `bio-webapp` war; an Ant unzip that never removed anything, so a file of an earlier bio-webapp version stayed in the directory), and `copyWebappContent` then copied `src/main/webapp` into the same directory. On Gradle 8, the first run of a Copy task into a directory that other tasks wrote removes their files, with no message: the war was built (46 MB instead of 68 MB for FlyMine) without the bio-webapp content (no `web.xml`, `struts-config.xml` or `WEB-INF/lib` of bio-webapp; on the next run the directory had them again, so a build that is repeated hides it). (The POM of `intermine-webtasks` 5.1.0 on Maven Central, and every plugin build before the fix of im-ee7.46 (the 5.1.1 release candidate included), named the 12 MB `intermine-webapp` war as a dependency (`intermine-jbrowse-endpoint` the same in the latter), so the war of every mine carried `intermine-webapp-<version>.war` in `WEB-INF/lib` and the first conversions filtered it out of the war's classpath. The POMs of this branch name `intermine-webapp:classes` instead.)
6. **commons-lang in the mine's own Java** (`webapp/src`, `dbmodel/src` if there is any). Nothing to do for InterMine's own classes: since 6.0 they import `org.apache.commons.lang3`, which every InterMine POM brings in (`commons-lang3` 3.21.0, in the war), and `commons-lang:commons-lang:2.6` only arrives for the vendored torque classes of `intermine-objectstore` (it is in `WEB-INF/lib`, and is a runtime dependency there). Check your own code: `grep -rn 'org.apache.commons.lang\b' webapp/src dbmodel/src` (HumanMine has no Java of its own that imports it, and FlyMine's six classes in `webapp/src/main/java/flymine/web` do not either; JSPs and tag files can import it too, so grep `webapp/src/main/webapp` as well). If something matches, either:
   - move it to `org.apache.commons.lang3` (nothing to declare: lang3 is on the compile classpath through the InterMine POMs). Renames and behaviour changes that matter: `StringEscapeUtils.escapeXml` to `escapeXml10` (lang3 still has `escapeXml`, but the whole `StringEscapeUtils` class is deprecated in favour of commons-text; InterMine's own code uses `org.intermine.util.XmlUtil.escapeXml`), `escapeHtml` to `escapeHtml4` (or `escapeHtml3`), `unescapeHtml` to `unescapeHtml4`; `ObjectUtils.equals(a, b)` to `java.util.Objects.equals(a, b)` (the lang3 method is deprecated); `StringUtils.isNumeric("")` is `false` in lang3 and was `true` in 2.6; `org.apache.commons.lang.enum.Enum` and `exception.Nestable*` have no lang3 equivalent (use a Java `enum` and ordinary exceptions); `org.apache.commons.lang3.StringEscapeUtils` itself is deprecated in favour of commons-text; or
   - keep lang 2.6 and declare it, `implementation 'commons-lang:commons-lang:2.6'`. Without the line your code still compiles today, because `intermine-api` depends on `solr-solrj`, whose POM lists `commons-lang:2.6` as a compile dependency (checked: a class with `import org.apache.commons.lang.StringUtils` compiles in HumanMine's `webapp`), but InterMine does not promise that: it keeps lang 2.6 for torque and for solr, and a later InterMine or solr that drops it would turn your import into `package org.apache.commons.lang does not exist`.
   Mixing both libraries in one mine is fine (`commons-lang-2.6.jar` and `commons-lang3-3.21.0.jar` have different packages and both stay in `WEB-INF/lib`).
7. **Check**, from a clean tree (`rm -rf build dbmodel/build webapp/build .gradle`):

   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 HOME=<home with .intermine/<mine>.properties>
   ./gradlew --warning-mode all :dbmodel:tasks :webapp:tasks --dry-run     # configures, no deprecation
   ./gradlew --warning-mode all :dbmodel:buildDB                           # creates the production database tables
   ./gradlew --warning-mode all :webapp:war
   ```

   `buildDB` needs the (empty) database named in `db.production.datasource.databaseName` to exist and be owned by the database user. `:dbmodel:createUserDB` does the same for the userprofile database.
8. **Look inside the war** and compare it with the one of your old build (`unzip -Z1 webapp/build/libs/webapp.war | sort`):

   ```bash
   W=webapp/build/libs/webapp.war
   unzip -Z1 $W | grep '^WEB-INF/lib/' | sed -E 's#WEB-INF/lib/##; s/-[0-9][0-9A-Za-z._-]*\.jar$//' | sort | uniq -d   # nothing
   unzip -Z1 $W | grep -E '\.war$'                                                                                    # nothing
   unzip -p $W WEB-INF/lib/dbmodel.jar > /tmp/dbmodel.jar; unzip -Z1 /tmp/dbmodel.jar | grep -x intermine.properties # nothing
   unzip -Z1 $W | grep -E '^WEB-INF/(web.xml|struts-config.xml|tiles-defs.xml|aspects.xml|webconfig-model.xml)$'       # all five
   ```

   ```bash
   unzip -Z1 $W | grep -E 'commons-lang'            # commons-lang-2.6.jar and commons-lang3-3.21.0.jar, one each
   unzip -p $W dataCategories.jsp | cmp - webapp/src/main/webapp/dataCategories.jsp   # no output: the mine's own file won over bio-webapp's
   ```

   Both converted mines gave 132 jars in `WEB-INF/lib` (HumanMine's war is 68.5 MB with JDK 17 and JDK 8, FlyMine's 68.4 MB; the 134 of the first conversions included `stringtree-json` and `libreadline-java`, which InterMine no longer ships), no output from the first three commands, and the same file list whether the war was built straight from a clean tree, after `buildDB`, or again after `rm -rf webapp/build`. `dbmodel.jar` has no `intermine.properties` because the plugin excludes it from the `jar` task of every project that applies one of its dbmodel plugins (you need no exclude of your own); `WEB-INF/classes/intermine.properties` and `WEB-INF/web.properties` in the war do contain the database credentials, as before.

### Worked example: HumanMine

The commit `build: convert HumanMine build files to the InterMine 6.0 line` of the HumanMine repository (now on the `maint-2026` branch of https://github.com/gmicklem/humanmine, commit `f426ac8`; later commits there moved the war composition and the SO paths to the plugin), abbreviated. The wrapper is its own commit. FlyMine's is the same with different line numbers; its differences are below.

```diff
--- gradle.properties
-systemProp.imVersion=5.0.+
-systemProp.bioVersion=5.0.+
+systemProp.imVersion=6.0.+        # 5.1.1 in the test branch
+systemProp.bioVersion=6.0.+

--- build.gradle
 buildscript {
     repositories {
         mavenLocal()
         mavenCentral()
+        gradlePluginPortal()  // gradle-cargo-plugin 2.9.0 is not on Maven Central
     }
     dependencies {
-        classpath 'com.bmuschko:gradle-cargo-plugin:2.8.0'
+        classpath 'com.bmuschko:gradle-cargo-plugin:2.9.0'
 subprojects {
     apply plugin: 'java'
-    apply plugin: 'maven'
-    sourceCompatibility = 1.8
-    targetCompatibility = 1.8
+    java {
+        sourceCompatibility = JavaVersion.VERSION_1_8
+        targetCompatibility = JavaVersion.VERSION_1_8
+    }
+    if (JavaVersion.current().isJava9Compatible()) {
+        tasks.withType(JavaCompile).configureEach { options.release = 8 }
+    }
     repositories {
         mavenLocal()
-	mavenCentral()
-        jcenter()
-        maven { url 'https://clojars.org/repo' }
+        mavenCentral()
+        maven { url = 'https://clojars.org/repo' }     // same for the ebi-repo URL
     dependencies {
-        compile group: "org.intermine", name: "intermine-resources", version: System.getProperty("imVersion")
-        compile('ant:ant:1.6.5')
-        testCompile group: 'junit', name: 'junit', version: '4.8.2'
+        implementation group: "org.intermine", name: "intermine-resources", version: System.getProperty("imVersion")
+        testImplementation group: 'junit', name: 'junit', version: '4.8.2'
     }
     task copyMineProperties(type: Copy) {
-    description "Copies mine specific ..."
-    dependsOn 'processResources'
-    from (minePropertyFile)
-    into sourceSets.main.output.resourcesDir
-    rename { fileName -> fileName.replace("$minePropertyFileName", "intermine.properties") }
-    inputs.sourceFiles.stopExecutionIfEmpty()
-  }
+        description = "Copies mine specific ..."
+        from (minePropertyFile)
+        into layout.buildDirectory.dir('im/mine-properties')
+        rename { fileName -> fileName.replace("$minePropertyFileName", "intermine.properties") }
+    }
+    processResources {
+        from copyMineProperties
+    }

--- dbmodel/build.gradle
-    compile group: "org.intermine", name: "intermine-integrate", ...      # also intermine-resources, bio-core, fileTree(libs)
+    implementation group: "org.intermine", name: "intermine-integrate", ...

--- webapp/build.gradle
 apply plugin: "war"
-apply from: "https://raw.githubusercontent.com/akhikhl/gretty/master/pluginScripts/gretty.plugin"
 apply plugin: "webapp"
-    compile (project(":dbmodel")) {transitive = false}          # and the four org.intermine lines
+    implementation (project(":dbmodel")) {transitive = false}
-war {
-    dependsOn 'mergeProperties', 'summariseObjectStore', 'unwarBioWebApp', 'addStrutsConfig'
-    finalizedBy 'warWebApp'
-}
-task warWebApp(type: War) {       // the plugin's war takes the exploded bio-webapp, src/main/webapp and the properties itself
-    ...
-}
-task copyWebappContent(type: Copy) {
-    from "src/main/webapp/"
-    into "${explodedWebAppDir}"
-}
-gretty {
-    jvmArgs = ['-Dorg.apache.el.parser.SKIP_IDENTIFIER_CHECK=true']
-    contextPath = props.getProperty("webapp.path")
-}
 ...
-    main = "bluegenes.core"
+    mainClass = "bluegenes.core"
```

Why `copyMineProperties` changed: with the old form (`into sourceSets.main.output.resourcesDir`, `dependsOn 'processResources'`) a build that runs `:webapp:copyMineProperties` and `:webapp:jar` fails on Gradle 8 with `Task ':webapp:jar' uses this output of task ':webapp:copyMineProperties' without declaring an explicit or implicit dependency`, because the file lands in a directory that `jar` reads. Keep the task and its name (the plugin's `mergeProperties` and the Ant tasks find the file through it), but let `processResources` take the file from the task's own directory, so that every reader of `build/resources/main` depends on it through its inputs. The plugin's `mergeProperties` task takes the resources directory as the output of `processResources` and of `copyMineProperties`, so nothing else is needed. (Plugin builds before this change, the 5.1.1 release candidate included, listed only the copy task as the producer of `intermine.properties`: `:webapp:war` then failed with `Task ':webapp:mergeProperties' uses this output of task ':webapp:processResources' without declaring an explicit or implicit dependency` unless the mine added `tasks.named('mergeProperties') { inputs.files(tasks.named('processResources')) }`.)

**FlyMine differences.** `imVersion=5.1.+` to the same value as above; the build files are indented by two spaces and have a `props.getProperty("webapp.port")`/`webapp.protocol` block in `cargo { }` (nothing to change); `dbmodel/build.gradle` has the same four `compile` lines in another order and an `extraModelsEnd` with two more additions files; the `blueGenesStart` task of FlyMine also sets `bluegenes-tool-path`. The 54 sources of FlyMine's `project.xml` need 19 jars from `flymine-bio-sources` (see above). With them the result is the same: `:dbmodel:buildDB` (tables in the production database), `:webapp:war` from a clean tree, 134 jars in `WEB-INF/lib`, none repeated.

### What breaks, and how it looks

The messages below were produced by running HumanMine at its upstream master on Gradle 8.14.6 and fixing one error after the other (the `gretty` and `cargo` lines separately), roughly in the order that a mine meets them:

| Message | Cause and fix |
|---|---|
| `Plugin with id 'maven' not found.` | delete `apply plugin: 'maven'` (step 3) |
| `Could not find method compile() for arguments [...] on object of type DefaultDependencyHandler` | `compile`, `testCompile` and `runtime` were removed in Gradle 7: `implementation`, `testImplementation`, `runtimeOnly` (also `compile(...)` with parentheses) |
| `No signature of method: ...TaskInputUnionFileCollection.stopExecutionIfEmpty() is applicable for argument types: () values: []` | delete the line (Gradle 5.0 removed the method); an empty `Copy` is skipped as `NO-SOURCE` by itself |
| `Could not resolve org.akhikhl.gretty:gretty:2.0.0 ... Could not GET 'https://jcenter.bintray.com/...' Received status code 403` (line 2 of `webapp/build.gradle`) | the gretty `apply from`; delete it and the `gretty { }` block (step 5) |
| `Could not find com.bmuschko:gradle-cargo-plugin:2.9.0 ... Searched in ... repo.maven.apache.org` | add `gradlePluginPortal()` to the `buildscript` repositories, or stay on 2.8.0 |
| `Could not find file containing SO terms to add to the model, check the dbmodel/build.gradle file. Property was: <gradle home>/daemon/8.14.6/dbmodel/resources/so_terms` | relative `soTermListFilePath` / `soAdditionFilePath` with a plugin from before the fix of im-ee7.47: use the current plugin, or write the paths as `"$rootDir/dbmodel/..."` (step 4) |
| `A problem was found with the configuration of task ':webapp:jar' ... uses this output of task ':webapp:copyMineProperties' without declaring an explicit or implicit dependency` (also `:webapp:mergeProperties` ... `:webapp:processResources`) | the old `copyMineProperties`; see the paragraph after the example |
| `Entry model/images/favicon.ico is a duplicate but no duplicate handling strategy has been set` (task `:webapp:warWebApp`) | a `warWebApp` of your own: delete it, the plugin's `war` has `duplicatesStrategy = DuplicatesStrategy.EXCLUDE` (step 5) |
| a war of 46 MB without `WEB-INF/web.xml`, `struts-config.xml` or the bio-webapp libraries, and no error; `build/explodedWebApp` holding only the mine's own files | `copyWebappContent` emptied the directory on its first run: delete the task (step 5); the plugin's `war` takes `src/main/webapp` itself |
| `Could not find org.intermine:bio-source-redfly:9.9.9` in `:dbmodel:generateKeys` / `:dbmodel:mergeModels` | the source's jar is not in any repository: publish the mine's source repository (see above), or the `version` in `project.xml` is wrong |
| `error: cannot find symbol  import org.intermine.model.bio.MicroArrayAssay;` in `:webapp:compileJava` | the model was generated from fewer sources than `project.xml` lists; restore the sources, delete `dbmodel/build` |
| `Properties should be assigned using the 'propName = value' syntax ...` (Gradle 10); `The JavaExecSpec.main property has been deprecated` (Gradle 9); `The org.gradle.api.plugins.JavaPluginConvention type has been deprecated` | `url = ...`, `description = ...`, `mainClass = ...`, the `java { }` block (steps 3 and 5); they are warnings in 8.14 |
| `Executing Gradle on JVM versions 16 and lower has been deprecated` | the build runs on JDK 8; use JDK 17 for Gradle (the mine's jars and war still run on JDK 8) |
| `ant-1.6.5.jar` and `ant-1.10.x.jar`, or `intermine-webapp-<version>.war` (12 MB), in `WEB-INF/lib` | `compile('ant:ant:1.6.5')` (step 3); the war is in the POM of `intermine-webtasks` 5.1.0 (Maven Central) and no longer in those of `maint-2026` (a 6.0 build against a published 5.1.0 POM needs `classpath = classpath.filter { !it.name.endsWith('.war') }` on the `War` tasks) |
| `error: package org.apache.commons.lang does not exist` or `cannot find symbol  method escapeHtml(String)` / `escapeXml` / `equals` in your own Java | step 6: declare `commons-lang:commons-lang:2.6`, or move to lang3 (`escapeHtml4`, `escapeXml10`, `Objects.equals`) |
| `Test of distribution url https://services.gradle.org/... failed` from `./gradlew wrapper` | a proxy that rejects the check; `--no-validate-url` (step 1) |

A mine on Tomcat 8 can stay on it: `containerId = 'tomcat8x'` in the `cargo { }` block does not depend on the JDK or Gradle version of the build.

## Publishing and dependencies of the InterMine artifacts

- The old OSSRH and `uploadArchives` publishing is gone from InterMine itself (see [RELEASING.md](RELEASING.md)); a mine only consumes the released artifacts from Maven Central.
- With Gradle 5 and later, Gradle uses the POM's compile and runtime scopes separately, so a mine that compiles against a class that comes through an InterMine artifact depends on that artifact declaring it as `api`/compile. The planning notes expect a `package ... does not exist` error in a mine as the symptom if an artifact declared something as `implementation` where `api` was needed.
- The plugin's internal configurations were renamed (`api` to `imApiJar`, `antlr` to `imAntlr`, `plugin` to `imPlugin`). Mines do not use them (checked in humanmine and flymine).
- The Ant tasks that the plugin runs (integrate, buildDB, postprocess) now take their classpath from `runtimeClasspath` instead of `compile`; this is a superset (it also contains `runtimeOnly` dependencies), and the order of the jars can differ.

## Removed from the webapp

- **OpenID 2.0 login (6.0.0).** The `/openid` servlet (`OpenIDAuthenticator`), its `openid-providers.properties` list, the "using OpenID" block of the login page and the `JOpenId` library are gone. OpenID 2.0 providers shut the protocol down years ago (Google in 2015), and the shipped provider list was empty, so the login page never offered it. OAuth2 / OpenID Connect login (`oauth2.*` properties) is unchanged, including the migration of old Google OpenID accounts. A mine that has its own `openid-providers.properties` or an `openid-auth` servlet entry in its `web.xml` can delete them; the `openid.allowed` web property is no longer read.
- **Bouncy Castle (6.0.0).** `bcprov-jdk15on` is no longer a dependency; the certificate that `KeyStoreBuilder` stores for each `security.publickey.*` entry is written with the JDK classes. The signing algorithms are the SHA-1/224/256/384/512 variants of RSA and ECDSA.

See [BUILDING.md](BUILDING.md) for how the InterMine builds themselves are set up.

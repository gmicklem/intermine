# Upgrading a mine's build (deployer guide)

This guide is for people who run a mine (humanmine, flymine and similar) whose `build.gradle` uses the InterMine Gradle plugin (`classpath group: 'org.intermine', name: 'plugin', version: ...`). The 6.0.0 release has not been cut yet. Until it is, "the 6.0 line" is this branch, whose artifacts are published as `5.1.1`; every place below that says `5.1.1` for the 6.0 line becomes `6.0.+` for a deployer once 6.0.0 is on Maven Central.

The sources are the maintainers' planning notes (`decisions/gradle-upgrade-path.md`, `baselines/intermine-gradle-6.9.4-deprecations.md` and `plans/10-intermine-backend.md` in https://github.com/gmicklem/intermine-planning) a smoke build of a copy of flymine on Gradle 6.9.4 with the 5.1.1 plugin, and the conversion of HumanMine (`afdd8e9`) and FlyMine (`a507ed84`) to Gradle 8.14.6 with the plugin of this branch (branches `maint/im-ee7.5-deployer-6` of those repositories). Line numbers in the 5.1.1 section are those of the flymine copy (humanmine is the same, a few lines apart).

## Which versions go together

| InterMine plugin | Gradle in the mine | JDK | Status |
|---|---|---|---|
| 5.0.x, 5.1.0 | 4.9 | 8 | the current mines |
| 5.1.1 | 4.9 still works (the plugin is built to stay compatible); **6.9.4 is recommended** | 8 | on this branch; not yet released |
| 6.0.0 | 8.14.6 | build: 17 (8 still works, 21 checked); run: 8, 17 or 21, see [JDK](#jdk-for-600) | this branch; published as 5.1.1 until the release |

Plugin 6.0.0 needs Gradle 8.14 in the mine: the plugin is compiled against Gradle's own Groovy, and a plugin built for a newer Groovy fails on an older Gradle. Plugin 5.1.1 is built so that it still runs on Gradle 4.9. A mine that pins `imVersion=5.1.+` (flymine does) picks up any 5.1.x plugin automatically, and cannot pick up 6.0.0 by accident; a mine that pins `5.0.+` stays on 5.0.x.

## 5.0.x or 5.1.0 on Gradle 4.9 to 5.1.1

Nothing is required: 5.1.1 runs on Gradle 4.9. To also move the mine to Gradle 6.9.4, which shows every deprecation that 8.14 will turn into an error:

1. Set `imVersion` and `bioVersion` in the mine's `gradle.properties` to `5.1.1`.
2. Update the wrapper: `./gradlew wrapper --gradle-version 6.9.4 --distribution-type bin`.
3. Delete the line `inputs.sourceFiles.stopExecutionIfEmpty()` (flymine `build.gradle:50`, humanmine `build.gradle:53`). **Gradle 5.0 removed that method, so without this edit a mine does not configure on any Gradle from 5.0 on.** It is the only edit that is needed to configure on 6.9.4: an empty `Copy` task is skipped as `NO-SOURCE` by Gradle itself.
4. Remove the `gretty` line (flymine `webapp/build.gradle:2`): an `apply from: https://raw.githubusercontent.com/akhikhl/gretty/...` script. Its buildscript resolves `org.akhikhl.gretty:gretty:2.0.0` from jcenter, which returned 403 in the smoke build, so the build could not configure (this is not a Gradle-version problem). It is also an unpinned remote script, and gretty was already dropped from testmine in InterMine's own `RELEASE_NOTES` because it does not work with Gradle 4.9. The `cargo` plugin does the deployment; also delete the `gretty { }` block of the same file.
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
- **psi-complexes on JDK 11+:** small molecules get no name. The ChEBI client it uses is a JAX-WS client (JDK 11 removed JAX-WS), and the ChEBI SOAP service it calls answers HTTP 500 anyway (Nov 2026), so on JDK 8 the names are missing too.

## 5.1.1 to 6.0.0 (Gradle 8.14)

This is the list a deployer follows, in order. It was run on HumanMine (`imVersion` 5.0.+, Gradle 4.9) and FlyMine (5.1.+, Gradle 4.9) at their upstream master, with the plugin and the InterMine artifacts built from this branch (version 5.1.1; the number 6.0.0 is set when the release is cut, and until then "the 6.0 line" means this branch). Both mines then pass `:dbmodel:buildDB` and `:webapp:war` on Gradle 8.14.6 with JDK 17 (HumanMine also with JDK 8), with no deprecation from their own build files, and the war has no repeated library. Loading data (`integrate`, `postProcess`) and deploying to Tomcat were not run.

You can do steps 1 to 6 directly on a mine that is still on Gradle 4.9; the edits do not depend on the 5.1.1 section above.

### Before you start

- **JDK 17** for Gradle (`export JAVA_HOME=...`). JDK 8 also works, with Gradle's deprecation notice; see [JDK](#jdk-for-600).
- **The mine's property file**, `~/.intermine/<mine>.properties` (`humanmine.properties`, `flymine.properties`; the name is `mineName` in the root `build.gradle`, plus `.<release>` when you pass `-Prelease=...`). `System.env.HOME` is read, so run the build with the `HOME` that has the file. It is not in the repository. The keys the build reads are `db.production.datasource.{serverName,databaseName,user,password}`, the same four for `db.userprofile-production` (and `db.common-tgt-items` for `buildTgtItems`), `webapp.path`, `webapp.deploy.url`, `webapp.manager` and `webapp.password` (cargo), `superuser.account`, `superuser.initialPassword`, `project.title` and the other `project.*` keys of your mine. `config/testmodel.properties` in this repository is the shortest example (it uses the `unittest` aliases of testmine; a production mine uses `db.production` and `db.userprofile-production`).
- **The mine's own data source jars on Maven.** The webapp compile needs the full data-source set, not only the sources that InterMine publishes: see the next section. Do this first, or the build stops at step 6.
- **Unreleased 6.0 artifacts** (until 6.0.0 is on Maven Central): in this checkout run `./gradlew publishToMavenLocal` in `plugin`, `intermine`, `bio`, `bio/sources` and `bio/postprocess`, in that order, after `config/lib/install_intermine.py` (see [BUILDING.md](BUILDING.md)). The mine's `buildscript` and `repositories` already list `mavenLocal()` first. If you publish to another directory, pass `-Dmaven.repo.local=<dir>` to the mine's build too.

### The data sources that are not on Maven Central

The plugin turns every `<source>` of the mine's `project.xml` into a dependency `org.intermine:bio-source-<type>:<version>` (the `name` for `intermine-items-xml-file` sources). Generating the model merges the `*_additions.xml` of all those jars into `genomic_model.xml`, and the generated classes (`org.intermine.model.bio.*`) are what the mine's `webapp` Java code compiles against. So:

- A source whose jar is missing stops the build in `:dbmodel:generateKeys` with `Could not find org.intermine:bio-source-redfly:9.9.9` (the message lists every repository it tried).
- A `project.xml` that is cut to the sources that you have gives a model without the classes of the others, and `:webapp:compileJava` fails with `error: cannot find symbol  import org.intermine.model.bio.MicroArrayAssay;` (FlyMine, cut to the 47 sources of InterMine; the same for `FlyAtlasResult`, `MicroArrayResult`). It is data, not Gradle: restore the sources. Note that `mergeModels` only runs when `dbmodel/build` has no model yet, so after editing `project.xml` delete `dbmodel/build` (or run `./gradlew :dbmodel:clean`).

This repository's `bio/sources` build publishes 44 `bio-source-*` modules. The others that the two mines list are in the mines' own repositories and are not on Maven Central: HumanMine's 16 (`ncbi-summaries`, `mgi-alleles`, `arrayexpress-atlas`, `protein-atlas`, `protein-atlas-rna`, `atlas-express`, `clinvar`, `huge-gwas`, `hpo`, `hpo-annotation`, `orphanet`, `disgenet-disease-associations`, `depmap-sample-info`, `depmap-expression`, `gtex`, `humanmine-static`, version 4.3.0) are in https://github.com/intermine/humanmine-bio-sources, FlyMine's 19 (`drosophila-homology`, `rnai`, `flyatlas`, `flybase-alleles`, `redfly`, `fly-fish`, `miranda`, `affy-probes`, `flymine-static`, ..., version 4.2.0) in https://github.com/intermine/flymine-bio-sources. Those two repositories are Gradle 4.9 builds too (`compile`, `maven`, `signing`, `uploadArchives`; the `*-static` sources also apply the bintray and artifactory plugins, which no longer resolve) and publish nothing to Central. To build a mine you therefore convert and publish them first, to your own Maven repository or to `mavenLocal()`: `java-library` and `maven-publish` instead of `java`, `maven`, `signing` and `uploadArchives`; `api` instead of `compile`; a `jar { exclude 'intermine.properties' }`; drop the bintray, artifactory and checkstyle parts; `publishToMavenLocal` at the version the mine's `project.xml` asks for (`version="4.3.0"` / `"4.2.0"`; a source without `version` takes `bioVersion`). The test run for this guide did that in a scratch copy of each repository: a new root `build.gradle` of about 50 lines, the `compile` of the two `*-static` modules changed to `api` and their bintray/artifactory/signing parts removed. Nothing else in those repositories needed to change, and all 35 source jars of both mines compiled on JDK 17 with `--release 8`. Converting those two repositories properly is a separate piece of work.

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
4. **`dbmodel/build.gradle`.** `compile` to `implementation` (`bioModel` stays as it is); keep `srcDirs = ['build/gen']`. Make the two paths in `dbModelConfig` absolute: `soTermListFilePath = "$rootDir/dbmodel/resources/so_terms"` and `soAdditionFilePath = "$rootDir/dbmodel/build/so_additions.xml"` (the plugin hands these strings to Ant, which resolves them against the working directory of the JVM, and a Gradle 8 daemon's working directory is its own `daemon/8.14.6` directory: the symptom is `Could not find file containing SO terms to add to the model ... Property was: <gradle user home>/daemon/8.14.6/dbmodel/resources/so_terms`).
5. **`webapp/build.gradle`.**
   - Delete the `apply from: "https://raw.githubusercontent.com/akhikhl/gretty/..."` line and the `gretty { }` block. The script resolves gretty 2.0.0 from jcenter, which now answers 403. The cargo plugin deploys. Gretty also passed `-Dorg.apache.el.parser.SKIP_IDENTIFIER_CHECK=true` to its Tomcat; your own Tomcat needs it too (`CATALINA_OPTS`, or `jvmArgs` in a cargo `local { }` block).
   - `compile` to `implementation`. Keep `implementation (project(":dbmodel")) {transitive = false}`; there is no need for `java-library` or `api`, because the webapp lists what it needs itself.
   - `JavaExec`: `main = ...` to `mainClass = ...` (`blueGenesStart`; Gradle 8.14 deprecates `main`, Gradle 9 removes it).
   - War composition, three changes in the same place: delete the `copyWebappContent` task and the `dependsOn 'copyWebappContent'` of `warWebApp`; add `duplicatesStrategy = DuplicatesStrategy.EXCLUDE` to `warWebApp`; and add the two small blocks of the example (`mergeProperties` input, the `.war` filter). Why: `warWebApp` zips `build/explodedWebApp`, which `unwarBioWebApp` and `addStrutsConfig` fill (the unpacked `bio-webapp` war), and `copyWebappContent` then copied `src/main/webapp` into the same directory. On Gradle 8, the first run of a Copy task into a directory that other tasks wrote removes their files, with no message: the war was built (46 MB instead of 68 MB for FlyMine) without the bio-webapp content (no `web.xml`, `struts-config.xml` or `WEB-INF/lib` of bio-webapp; on the next run the directory had them again, so a build that is repeated hides it). Every `War` task in Gradle 8 also takes `src/main/webapp` by itself, listed before `from explodedWebApp`; with `EXCLUDE` the first entry wins, so the mine's own files still replace the bio-webapp ones, as the copy did.
6. **Check**, from a clean tree (`rm -rf build dbmodel/build webapp/build .gradle`):

   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 HOME=<home with .intermine/<mine>.properties>
   ./gradlew --warning-mode all :dbmodel:tasks :webapp:tasks --dry-run     # configures, no deprecation
   ./gradlew --warning-mode all :dbmodel:buildDB                           # creates the production database tables
   ./gradlew --warning-mode all :webapp:war
   ```

   `buildDB` needs the (empty) database named in `db.production.datasource.databaseName` to exist and be owned by the database user. `:dbmodel:createUserDB` does the same for the userprofile database.
7. **Look inside the war** and compare it with the one of your old build (`unzip -Z1 webapp/build/libs/webapp.war | sort`):

   ```bash
   W=webapp/build/libs/webapp.war
   unzip -Z1 $W | grep '^WEB-INF/lib/' | sed -E 's#WEB-INF/lib/##; s/-[0-9][0-9A-Za-z._-]*\.jar$//' | sort | uniq -d   # nothing
   unzip -Z1 $W | grep -E '\.war$'                                                                                    # nothing
   unzip -p $W WEB-INF/lib/dbmodel.jar > /tmp/dbmodel.jar; unzip -Z1 /tmp/dbmodel.jar | grep -x intermine.properties # nothing
   unzip -Z1 $W | grep -E '^WEB-INF/(web.xml|struts-config.xml|tiles-defs.xml|aspects.xml|webconfig-model.xml)$'       # all five
   ```

   Both converted mines gave 134 jars in `WEB-INF/lib` and no output from the first three commands. `dbmodel.jar` has no `intermine.properties` because the plugin excludes it from the `jar` task of every project that applies one of its dbmodel plugins (you need no exclude of your own); `WEB-INF/classes/intermine.properties` and `WEB-INF/web.properties` in the war do contain the database credentials, as before.

### Worked example: HumanMine

The commit `build: convert HumanMine build files to the InterMine 6.0 line` of the HumanMine repository (branch `maint/im-ee7.5-deployer-6`), abbreviated. The wrapper is its own commit. FlyMine's is the same with different line numbers; its differences are below.

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
-    soTermListFilePath = "dbmodel/resources/so_terms"
-    soAdditionFilePath = "dbmodel/build/so_additions.xml"
+    soTermListFilePath = "$rootDir/dbmodel/resources/so_terms"
+    soAdditionFilePath = "$rootDir/dbmodel/build/so_additions.xml"

--- webapp/build.gradle
 apply plugin: "war"
-apply from: "https://raw.githubusercontent.com/akhikhl/gretty/master/pluginScripts/gretty.plugin"
 apply plugin: "webapp"
-    compile (project(":dbmodel")) {transitive = false}          # and the four org.intermine lines
+    implementation (project(":dbmodel")) {transitive = false}
+// intermine-webtasks depends on the intermine-webapp artifact itself, which Gradle resolves to its war (12 MB)
+tasks.withType(War).configureEach {
+    classpath = classpath.filter { !it.name.endsWith('.war') }
+}
 task warWebApp(type: War) {
-    dependsOn 'copyWebappContent'
-    description "Create the war from the build/explodedWebAppDir directory"
+    description = "Create the war from the build/explodedWebAppDir directory"
     from "${explodedWebAppDir}"
     exclude "WEB-INF/web.properties"
+    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
     webInf { from "${mergePropsDir}" }
 }
-task copyWebappContent(type: Copy) {
-    from "src/main/webapp/"
-    into "${explodedWebAppDir}"
-}
-gretty {
-    jvmArgs = ['-Dorg.apache.el.parser.SKIP_IDENTIFIER_CHECK=true']
-    contextPath = props.getProperty("webapp.path")
+// the plugin's mergeProperties reads build/resources/main/intermine.properties, which processResources writes
+tasks.named('mergeProperties') {
+    inputs.files(tasks.named('processResources'))
 }
-    main = "bluegenes.core"
+    mainClass = "bluegenes.core"
```

Why `copyMineProperties` changed: with the old form (`into sourceSets.main.output.resourcesDir`, `dependsOn 'processResources'`) a build that runs `:webapp:copyMineProperties` and `:webapp:jar` fails on Gradle 8 with `Task ':webapp:jar' uses this output of task ':webapp:copyMineProperties' without declaring an explicit or implicit dependency`, because the file lands in a directory that `jar` reads. Keep the task and its name (the plugin's `mergeProperties` and the Ant tasks find the file through it), but let `processResources` take the file from the task's own directory, so that every reader of `build/resources/main` depends on it through its inputs. The plugin's `mergeProperties` task only lists the copy task as the producer of `intermine.properties`, so the mine adds `processResources` as an input of it (last hunk but one; without that line `:webapp:war` fails with `Task ':webapp:mergeProperties' uses this output of task ':webapp:processResources' without declaring an explicit or implicit dependency`).

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
| `Could not find file containing SO terms to add to the model, check the dbmodel/build.gradle file. Property was: <gradle home>/daemon/8.14.6/dbmodel/resources/so_terms` | relative `soTermListFilePath` / `soAdditionFilePath`; make them `$rootDir/...` (step 4) |
| `A problem was found with the configuration of task ':webapp:jar' ... uses this output of task ':webapp:copyMineProperties' without declaring an explicit or implicit dependency` (also `:webapp:mergeProperties` ... `:webapp:processResources`) | the old `copyMineProperties`; see the paragraph after the example |
| `Entry model/images/favicon.ico is a duplicate but no duplicate handling strategy has been set` (task `:webapp:warWebApp`) | `duplicatesStrategy = DuplicatesStrategy.EXCLUDE` on `warWebApp` (step 5) |
| a war of 46 MB without `WEB-INF/web.xml`, `struts-config.xml` or the bio-webapp libraries, and no error; `build/explodedWebApp` holding only the mine's own files | `copyWebappContent` emptied the directory on its first run: delete the task (step 5) |
| `Could not find org.intermine:bio-source-redfly:9.9.9` in `:dbmodel:generateKeys` / `:dbmodel:mergeModels` | the source's jar is not in any repository: publish the mine's source repository (see above), or the `version` in `project.xml` is wrong |
| `error: cannot find symbol  import org.intermine.model.bio.MicroArrayAssay;` in `:webapp:compileJava` | the model was generated from fewer sources than `project.xml` lists; restore the sources, delete `dbmodel/build` |
| `Properties should be assigned using the 'propName = value' syntax ...` (Gradle 10); `The JavaExecSpec.main property has been deprecated` (Gradle 9); `The org.gradle.api.plugins.JavaPluginConvention type has been deprecated` | `url = ...`, `description = ...`, `mainClass = ...`, the `java { }` block (steps 3 and 5); they are warnings in 8.14 |
| `Executing Gradle on JVM versions 16 and lower has been deprecated` | the build runs on JDK 8; use JDK 17 for Gradle (the mine's jars and war still run on JDK 8) |
| `ant-1.6.5.jar` and `ant-1.10.x.jar`, or `intermine-webapp-<version>.war` (12 MB), in `WEB-INF/lib` | `compile('ant:ant:1.6.5')`, and the war filter (steps 3 and 5) |
| `Test of distribution url https://services.gradle.org/... failed` from `./gradlew wrapper` | a proxy that rejects the check; `--no-validate-url` (step 1) |

A mine on Tomcat 8 can stay on it: `containerId = 'tomcat8x'` in the `cargo { }` block does not depend on the JDK or Gradle version of the build.

## Publishing and dependencies of the InterMine artifacts

- The old OSSRH and `uploadArchives` publishing is gone from InterMine itself (see [RELEASING.md](RELEASING.md)); a mine only consumes the released artifacts from Maven Central.
- With Gradle 5 and later, Gradle uses the POM's compile and runtime scopes separately, so a mine that compiles against a class that comes through an InterMine artifact depends on that artifact declaring it as `api`/compile. The planning notes expect a `package ... does not exist` error in a mine as the symptom if an artifact declared something as `implementation` where `api` was needed.
- The plugin's internal configurations were renamed (`api` to `imApiJar`, `antlr` to `imAntlr`, `plugin` to `imPlugin`). Mines do not use them (checked in humanmine and flymine).
- The Ant tasks that the plugin runs (integrate, buildDB, postprocess) now take their classpath from `runtimeClasspath` instead of `compile`; this is a superset (it also contains `runtimeOnly` dependencies), and the order of the jars can differ.

## Removed from the webapp

- **OpenID 2.0 login (5.1.1).** The `/openid` servlet (`OpenIDAuthenticator`), its `openid-providers.properties` list, the "using OpenID" block of the login page and the `JOpenId` library are gone. OpenID 2.0 providers shut the protocol down years ago (Google in 2015), and the shipped provider list was empty, so the login page never offered it. OAuth2 / OpenID Connect login (`oauth2.*` properties) is unchanged, including the migration of old Google OpenID accounts. A mine that has its own `openid-providers.properties` or an `openid-auth` servlet entry in its `web.xml` can delete them; the `openid.allowed` web property is no longer read.
- **Bouncy Castle (5.1.1).** `bcprov-jdk15on` is no longer a dependency; the certificate that `KeyStoreBuilder` stores for each `security.publickey.*` entry is written with the JDK classes. The signing algorithms are the SHA-1/224/256/384/512 variants of RSA and ECDSA.

See [BUILDING.md](BUILDING.md) for how the InterMine builds themselves are set up.

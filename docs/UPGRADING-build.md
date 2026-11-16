# Upgrading a mine's build (deployer guide, 5.1.1 line)

This guide is for people who run a mine (humanmine, flymine and similar) whose `build.gradle` uses the InterMine Gradle plugin (`classpath group: 'org.intermine', name: 'plugin', version: ...`). It is written for the **5.1.1 release line**, branch `release/5.1.1-candidate` of https://github.com/gmicklem/intermine: the non-breaking release, built with Gradle 6.9.4 on JDK 8. A mine can stay on Gradle 4.9 or move to 6.9.4 with it; see [5.0.x or 5.1.0 to 5.1.1](#50x-or-510-to-511).

The breaking release, **6.0.0** (Gradle 8.14.6, JDK 17), is on the `maint-2026` branch, and its guide is there; see [5.1.1 to 6.0.0](#511-to-600).

Neither has been released yet. The tree of `maint-2026` still carries the version number `5.1.1` for what will be 6.0.0 (the number is changed when the release is cut), so artifacts built from it are also called 5.1.1 although they are not the 5.1.1 release. If you test against `mavenLocal()`, make sure that the 5.1.1 artifacts in `~/.m2` come from this branch (or use `-Dmaven.repo.local=<dir>` for each line).

The sources are the maintainers' planning notes (`decisions/gradle-upgrade-path.md` and `baselines/intermine-gradle-6.9.4-deprecations.md` in https://github.com/gmicklem/intermine-planning), a smoke build of a copy of flymine on Gradle 6.9.4 with the 5.1.1 plugin, and the conversion of HumanMine and FlyMine to the 6.0 line. Line numbers below are those of the flymine copy (humanmine is the same, a few lines apart).

## Which versions go together

| InterMine plugin | Gradle in the mine | JDK | Status |
|---|---|---|---|
| 5.0.x, 5.1.0 | 4.9 | 8 | the current mines |
| 5.1.1 | 4.9 (checked) or 6.9.4 (checked); **6.9.4 is recommended** | 8 (Gradle 6.9.4 does not run on JDK 17) | this branch, `release/5.1.1-candidate`; not yet released |
| 6.0.0 | 8.14.6 | build: 17 (8 still works, 21 checked); run: 8, 17 or 21 | branch `maint-2026`, version number `5.1.1` until the release is cut; not yet released; see [5.1.1 to 6.0.0](#511-to-600) |

Plugin 6.0.0 needs Gradle 8.14 in the mine: the plugin is compiled against Gradle's own Groovy, and a plugin built for a newer Groovy fails on an older Gradle. Plugin 5.1.1 is built so that it still runs on Gradle 4.9. A mine that pins `imVersion=5.1.+` (flymine does) picks up any 5.1.x plugin automatically, and cannot pick up 6.0.0 by accident; a mine that pins `5.0.+` stays on 5.0.x.

## Three edits that almost every mine needs

HumanMine and FlyMine needed these three changes in their own build files, on either line. Each is explained in the step list below.

1. **Delete `inputs.sourceFiles.stopExecutionIfEmpty()`** from the `copyMineProperties` task in the root `build.gradle` (flymine line 50, humanmine line 53). Gradle 5.0 removed the method, so a mine does not configure on Gradle 5, 6.9.4, 7 or 8 until the line is gone; only Gradle 4.9 still has it. An empty `Copy` is skipped by Gradle itself, so nothing replaces it.
2. **Gretty.** `webapp/build.gradle` applies gretty from a script on raw.githubusercontent.com, and that script fetches gretty 2.0.0 from jcenter, which is dead. The mine does not configure on any Gradle. Remove the `apply from` line and the `gretty { }` block; the `cargo` plugin deploys. On Gradle 4.9 only, you can instead copy the script and point it at `mavenCentral()` (step 4 below).
3. **The Sequence Ontology paths** `soTermListFilePath` and `soAdditionFilePath` in `dbModelConfig { }` of `dbmodel/build.gradle`. The 5.1.1 plugin (this branch) hands these strings to Ant unchanged (`createSoModel` in `plugin/src/main/groovy/org/intermine/plugin/dbmodel/DBModelPlugin.groovy`), and Ant resolves a relative path against the working directory of the Gradle daemon's JVM. That is the mine's root only by luck of how the daemon was started: on a Gradle 8 daemon on JDK 9 or later it is the daemon's own directory (`Could not find file containing SO terms ...`). Write them as absolute paths: `"$rootDir/dbmodel/resources/so_terms"` and `"$rootDir/dbmodel/build/so_additions.xml"` (or `rootProject.file('dbmodel/resources/so_terms').absolutePath`; `file(...)` and `project.file(...)` inside `dbmodel/build.gradle` are relative to `dbmodel/`, not to the root). The 6.0.0 plugin resolves relative paths against the mine's root itself, so both forms work there.

## 5.0.x or 5.1.0 to 5.1.1

This is the non-breaking release. This branch (the last Gradle 6.9.4 state; its plugin is compiled with Gradle 6.9.4) was checked in a mine on **Gradle 4.9**: the plugin loads, the build configures, and the tasks `tasks`, `createSoModel` and (in `webapp`) `mergeProperties` start. It was also checked on **Gradle 6.9.4**, with the changes below. Those checks do not cover the packaging and security fixes listed under "What else changes for a mine", which were added to the branch later; the only plugin change among them is that `DBModelPlugin` excludes `intermine.properties` from the jar of the mine's `dbmodel` project (`project.tasks.named('jar')`, available since Gradle 4.9), and it has not been run in a mine. Loading data, `buildDB` of a full mine and deploying to Tomcat were not run for this line, and this branch needs JDK 8.

So nothing is required except `imVersion` and `bioVersion`, with one exception that does not depend on InterMine: a `webapp/build.gradle` that still has the `gretty` line (step 4) does not configure on any Gradle today, 4.9 included, because jcenter is gone. A mine that builds its war on this line also needs step 6 (`webXml`) and, on a fresh checkout, step 7 (run `copyWebappContent` once first). To move the mine to Gradle 6.9.4, which shows every deprecation that 8.14 will turn into an error:

1. Set `imVersion` and `bioVersion` in the mine's `gradle.properties` to `5.1.1`.
2. Update the wrapper: `./gradlew wrapper --gradle-version 6.9.4 --distribution-type bin`.
3. Delete the line `inputs.sourceFiles.stopExecutionIfEmpty()` (flymine `build.gradle:50`, humanmine `build.gradle:53`). **Gradle 5.0 removed that method, so without this edit a mine does not configure on any Gradle from 5.0 on.** It is the only edit that is needed to configure on 6.9.4: an empty `Copy` task is skipped as `NO-SOURCE` by Gradle itself.
4. Deal with the `gretty` line (flymine `webapp/build.gradle:2`): an `apply from: https://raw.githubusercontent.com/akhikhl/gretty/...` script. Its buildscript resolves `org.akhikhl.gretty:gretty:2.0.0` from jcenter, which is dead (it returned 403 in the smoke build), so the build cannot configure. This is not a Gradle-version problem, and it hits a mine on Gradle 4.9 as well. Two ways out:
   - **Remove gretty** (what InterMine recommends): delete the `apply from` line and the `gretty { }` block of the same file. The `cargo` plugin does the deployment. Gretty was already dropped from testmine in InterMine's own `RELEASE_NOTES` because it does not work with Gradle 4.9, and the script is an unpinned remote file.
   - **Keep gretty**: gretty 2.0.0 itself is on Maven Central, only the script looks for it in jcenter. Save the script in the mine's repository (from a fixed commit of akhikhl/gretty, not `master`), change the `jcenter()` in its `buildscript { repositories { } }` to `mavenCentral()`, and point the `apply from:` at that copy. This was not tried here. Gretty 2.0.0 dates from the Gradle 4 era and is not known to work on Gradle 6.9.4 or 8.14, so it is a stop-gap for a mine that stays on Gradle 4.9; the 6.0.0 steps remove it.
5. In `dbmodel/build.gradle`, make `soTermListFilePath` and `soAdditionFilePath` absolute (`"$rootDir/..."`, see "Three edits" above). The 5.1.1 plugin does not resolve them. It is not needed while the daemon happens to start in the mine's root, but it is cheap and it carries over to 6.0.0.
6. **Set `webXml` on the mine's war task** (a mine of this line composes its war in a `warWebApp` task of `webapp/build.gradle`; flymine `webapp/build.gradle:38-44`). Gradle's `War` task takes `WEB-INF/web.xml` through its own `webXml` property, not through the generic `from(...)` copy, and the mine's `warWebApp` never set it. In the testbed's build of the 5.1.1 line (Gradle 6.9.4) the war that came out had no `web.xml` at all: it deployed, Tomcat logged success, and every request returned 404 because no servlet mapping existed. The real file, with the struts and tiles additions of the mine's model merged in by `addStrutsConfig`, is `build/explodedWebApp/WEB-INF/web.xml`. Point `webXml` at it, **and** exclude it from the `from`, so that the war is not offered the entry twice (two overlapping `web.xml` entries, which `unzip` refuses as a possible zip bomb; the plugin of this branch sets `duplicatesStrategy = EXCLUDE`, which should also drop the second one, but that combination was not run, and the `exclude` is what was tested):

   ```groovy
   task warWebApp(type: War) {
       dependsOn 'copyWebappContent'
       description "Create the war from the build/explodedWebAppDir directory"
       from "${explodedWebAppDir}"
       exclude "WEB-INF/web.properties", "WEB-INF/web.xml"                 // was: exclude "WEB-INF/web.properties"
       webXml = file("${explodedWebAppDir}/WEB-INF/web.xml")               // new
       webInf { from "${mergePropsDir}" }
   }
   ```

   This is on the 5.1.1 line only: the 6.0.0 plugin composes the war itself and sets `web.xml` (the maint-2026 guide, see [5.1.1 to 6.0.0](#511-to-600), has the steps), so the line goes away with the rest of `warWebApp`. It was not checked on Gradle 4.9.
7. **The first `:webapp:war` of a fresh checkout fails** with `File .../build/explodedWebApp/WEB-INF/web.xml specified for property 'webXml' does not exist` (the `webXml` of step 6 is what makes it visible; without it the first war is built, with most of the bio-webapp content missing). The cause is the known first-run wipe of Gradle: `unwarBioWebApp` unpacks the `bio-webapp` war into `build/explodedWebApp`, and `copyWebappContent`, a `Copy` into the same directory, has no execution history yet. Gradle treats a task without history as the owner of nothing that exists in its output directory and removes it, so the copy deletes what the unwar had just written (2739 files down to 5, `web.xml` among them). Run the copy once on its own, first, so that it has a history, and then the war:

   ```bash
   ./gradlew :webapp:copyWebappContent
   ./gradlew :webapp:war
   ```

   Every later run has the history and passes, which is why a build that was run by hand before never shows it; a script that builds the mine from a fresh checkout (CI) must run the first command once. With the history in place the war's own `unwarBioWebApp` and `copyWebappContent` sequence leaves both sets of files, the unpacked war overlaid with `src/main/webapp`. The 6.0 plugin composes the war from the outputs of the tasks that make each part (`unwarBioWebApp` is a Sync there, and `copyWebappContent` is gone), so a first run is no different from a later one and this note does not apply.
8. Run `./gradlew --warning-mode all :dbmodel:tasks` to see the remaining deprecations. In the flymine smoke build on 6.9.4 they were:

| Warning | Where (flymine copy) | Fix |
|---|---|---|
| `maven` plugin | `build.gradle:22` | delete `apply plugin: 'maven'` |
| `jcenter()` repository (removed in Gradle 8) | `build.gradle:30` | delete it |
| `compile` / `testCompile` | `build.gradle:38,40`, and `dbmodel/` and `webapp/` build files | `implementation` / `testImplementation` (see below) |

### JDK

Build the mine with JDK 8: Gradle 6.9.4 does not run on JDK 17, and the CI of this branch runs on JDK 8 only. The jars are Java 8 bytecode and run on a JDK 8 Tomcat.

### What else changes for a mine

These fixes are in the build files of this branch (the full list, for the release notes, is "5.1.1 changes" in [RELEASING.md](RELEASING.md)). They were made and gated in InterMine's own builds; none was run in a mine. A mine gets them by raising `imVersion` and `bioVersion`:

- `intermine.properties` (the developer's test database user and password) is no longer in `bio-model-5.1.1.jar` and `intermine-testresources-5.1.1.jar`, and the plugin keeps it out of the jar of the mine's own `dbmodel` project.
- The POMs of `intermine-webtasks` and `intermine-jbrowse-endpoint` name `intermine-webapp:classes` (`config/gradle/webapp-classes-dependency.gradle`), not the 12 MB war. The war of a mine on 5.1.1 therefore no longer has `intermine-webapp-<version>.war` in `WEB-INF/lib`. A mine that builds against 5.1.0 (whose POM on Maven Central still names the war) needs `classpath = classpath.filter { !it.name.endsWith('.war') }` on its `War` task; with 5.1.1 that filter is not needed, and it is harmless.
- The `bio-webapp` war has no log4j 2.5 (Log4Shell), no junit, hamcrest-core or spring-test, and no second Jackson, commons-lang3, jcl-over-slf4j or logback; the `intermine-webapp` war has one antlr and one `javax.inject`. commons-fileupload is 1.6.0 and commons-collections 3.2.2.
- One oltu version: the POM of `intermine-webapp` named `org.apache.oltu.oauth2.client` 1.0.0 (compile) and `.common` 1.0.0 (provided; a `providedCompile` pair in its build file that overrode the `api` 1.0.1 line), while the `bio-webapp` war carried 1.0.1, so the war of a mine had `org.apache.oltu.oauth2.client` and `.common` in two versions (1.0.0 and 1.0.1). The POM now names the client 1.0.2 only, and the client's own POM brings `common` 1.0.2, so the war has one copy of each.
- The plugin (`webapp`) changes every `War` task of the mine (`war` and the `warWebApp` that mines of this line compose their war with), whatever the build file says, once the mine's build files have run:
  - The jars of the legacy `ant:ant` coordinate are left out of the task's classpath, and the build prints `The war leaves out ant-1.6.5.jar (legacy groupId 'ant', ...)` when it does so. A mine of the 5.x line declares `compile('ant:ant:1.6.5')`, the 2005 Ant under the groupId Ant had before it moved to `org.apache.ant`; Gradle takes the two for different libraries, so no version conflict resolution joins them and `ant-1.6.5.jar` went to `WEB-INF/lib` next to `ant-1.7.0.jar`. Nothing at run time needs it. It stays on the mine's other classpaths (the Ant tasks of the build), so delete the `compile('ant:ant:1.6.5')` line from the root `build.gradle` when you can.
  - `duplicatesStrategy` is `EXCLUDE` (unless the mine's build file chose another one): a zip entry that the war would get twice is written once, the first one wins, and `unzip -Z1 webapp.war | sort | uniq -d` prints nothing. The first entry is `WEB-INF/lib` from the war's classpath, then the mine's own `src/main/webapp`, then `build/explodedWebApp` (bio-webapp unpacked by `unwarBioWebApp`, with the mine's `src/main/webapp` copied over it by `copyWebappContent`), so a file of the mine wins over the file of the same name in bio-webapp. Gradle 6 prints a deprecation warning for each duplicate path (`Copying or archiving duplicate paths with the default duplicates strategy has been deprecated`) and Gradle 7 fails on it, which is why the plugin sets the strategy. Two versions of one library are two file names and are not duplicates of the same entry; InterMine's own artifacts carry one version of each library, as the oltu note above says.
  - Checked with HumanMine `master` (upstream) with the edits above (steps 6 and 7 for the war's `web.xml`: `webXml` on `warWebApp` and `copyWebappContent` run once first), on Gradle 6.9.4 and on Gradle 4.9: 202 jars with `ant-1.6.5.jar`, `ant-1.7.0.jar` and the oltu 1.0.0 and 1.0.1 pairs and 63 duplicate entries before; 151 jars with one `ant-1.7.0.jar`, the oltu 1.0.2 pair and no duplicate entries after.
- The InterMine POMs are written by `maven-publish`, not by the `maven` plugin that 5.1.0 used. The scopes in the `bio-source-*` POMs changed with it, which matters to a mine's own bio-source project; see the next section.

## Dependency scopes in the bio-source POMs

This applies to 5.1.1 as well as to 6.0.0.

How the POMs of this branch are made: `config/gradle/publishing.gradle` applies `maven-publish` and publishes `components.java` of the `java-library` plugin, which maps `api` to scope `compile` and `implementation` and `runtimeOnly` to scope `runtime`. (The `maven` plugin is gone from every build file here.) `bio/sources/build.gradle` declares its dependencies with `api` and `implementation`, and no module uses `compile`. In the 5.1.0 release the `bio/sources` build used the `maven` plugin and `compile` for everything, so every dependency of a `bio-source-*` POM had scope `compile`. In 5.1.1 most are `runtime`.

For a `bio-source-*` POM of this branch, from `bio/sources/build.gradle` and the individual `bio/sources/*/build.gradle` files (and consistent with the `bio-source-fasta-5.1.1.pom` of a local publish):

| Scope | Dependencies |
|---|---|
| `compile` | `bio-core`, `intermine-objectstore`, `intermine-model`, `ant`, `commons-collections`, `reload4j`, `slf4j-reload4j`, `intermine-integrate` (declared `api` by all 44 sources), and what a source adds for its own API (for example `biojava-core` in `bio-source-fasta`) |
| `runtime` | `HikariCP`, `intermine-resources`, `commons-io`, `commons-lang` 2.6, `commons-logging`, `postgresql`, `jakarta.xml.bind-api`, `jaxb-runtime`, `javax.annotation-api`, and `bio-model` in the 39 sources that list it |

(5.1.0 had `log4j-1.2-api` 2.17.1 instead of `reload4j` and `slf4j-reload4j`; both provide `org.apache.log4j`. There is no `commons-lang3` in these POMs: it is a 6.0.0 change.)

Nothing changes for the plugin: it puts the bio-source jars on a runtime classpath, where both scopes arrive. It matters for a mine's own bio-source project (your `*-bio-sources` repository) that **compiles against classes of an `org.intermine` bio-source jar**, for example a processor that extends a class of `bio-source-chado-db`, and that is built with Gradle 5 or later (Gradle 5 started to use the POM's compile and runtime scopes separately; this was not tried here with a project on Gradle 4.9). Gradle gives such a project only the `compile` entries of that POM. A library that your code imports, and that used to arrive through the bio-source jar, may now be missing at compile time, and the build stops with `error: package org.apache.commons.logging does not exist` (or `org.postgresql`, `com.zaxxer.hikari`, `javax.xml.bind`).

What to do:

- Declare every `org.intermine` bio-source jar that your classes use, with the same version as before. It is `implementation`, or `api` if its types appear in your own public signatures. (A bio-source project that is still on Gradle 4.9 says `compile`.) This always was necessary, but it used to be hidden when the jar came in through another dependency.
- Declare every library whose classes you import yourself, instead of relying on what a POM brings in. Use the versions that `bio/sources/build.gradle` of this branch uses, so that your war carries one copy:

  ```groovy
  dependencies {
      implementation 'org.intermine:bio-source-chado-db:5.1.+'
      implementation 'commons-logging:commons-logging:1.3.5'
      implementation 'org.postgresql:postgresql:42.7.14'
  }
  ```

- `commons-lang` 2.6 and `commons-io` 1.2 stay on your compile classpath when you declare `bio-core`: `bio/core/build.gradle` declares both as `api`, so they are `compile` entries of the `bio-core` POM. The `bio-source-*` POMs themselves list them as `runtime`. `commons-logging` and `postgresql` are `runtime` in `bio-core` too.
- To see what a POM gives you: `./gradlew dependencies --configuration compileClasspath` in your project, or the `<scope>` entries of the POM on Maven Central.

## 5.1.1 to 6.0.0

The 6.0.0 release is a breaking change of the mine's build files (Gradle 8.14.6, JDK 17, the plugin composes the war, the bio-source scopes above, `commons-lang3`, and more), and its guide lives in the `maint-2026` branch, not here: https://github.com/gmicklem/intermine/blob/maint-2026/docs/UPGRADING-build.md. It has the ordered steps, a worked HumanMine example, a table of error messages and the JDK 17 notes. It can be followed from 5.0.x, 5.1.0 or a mine already on 5.1.1; the steps in this guide are not a prerequisite, but steps 3 and 4 of the list above (the `stopExecutionIfEmpty` line and gretty) are repeated there.

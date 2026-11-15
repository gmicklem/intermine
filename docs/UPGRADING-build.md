# Upgrading a mine's build (deployer guide)

This guide is for people who run a mine (humanmine, flymine and similar) whose `build.gradle` uses the InterMine Gradle plugin (`classpath group: 'org.intermine', name: 'plugin', version: ...`). It is a stub: it records what is known today. Parts that depend on the 6.0.0 plugin, which has not been cut yet, are marked **To be confirmed when 6.0.0 is cut**.

The sources are the maintainers' planning notes (`decisions/gradle-upgrade-path.md`, `baselines/intermine-gradle-6.9.4-deprecations.md` and `plans/10-intermine-backend.md` in https://github.com/gmicklem/intermine-planning) and a smoke build of a copy of flymine on Gradle 6.9.4 with the 5.1.1 plugin. Line numbers below are those of that flymine copy (humanmine is the same, a few lines apart).

## Which versions go together

| InterMine plugin | Gradle in the mine | JDK | Status |
|---|---|---|---|
| 5.0.x, 5.1.0 | 4.9 | 8 | the current mines |
| 5.1.1 | 4.9 still works (the plugin is built to stay compatible); **6.9.4 is recommended** | 8 | on this branch; not yet released |
| 6.0.0 | 8.14.x (planned: 8.14.6) | build: 17 (8 still works, 21 checked); run: 8, 17 or 21, see [JDK](#jdk-for-600) | planned |

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

InterMine 6.0.0 is built and tested on JDK 17, and its jars are still Java 8 bytecode (class file version 52), so a mine can keep a JDK 8 Tomcat. To move a mine to JDK 17 (or 21):

- **Build JDK.** The JDK that runs Gradle (`JAVA_HOME`) is the one that compiles the mine, and the plugin's tasks (`integrate`, `buildDB`, `postprocess`, ...) run inside the Gradle daemon on it. Set `JAVA_HOME` before `./gradlew`; no toolchain setting is needed. Gradle 8.14 runs on JDK 8 to 24.
- **If Tomcat stays on JDK 8 but the mine is built on 17**, add `tasks.withType(JavaCompile).configureEach { options.release = 8 }` to the mine's Java projects, so that its own code is compiled against the Java 8 API (`sourceCompatibility = 1.8` alone does not do that).
- **JVM option on JDK 16+.** InterMine uses cglib 3.1 to build objects that belong to more than one model class, and on JDK 16+ cglib needs `--add-opens=java.base/java.lang=ALL-UNNAMED`. Where it is already set: the Gradle daemon (Gradle adds it), and Tomcat 9 started with `bin/catalina.sh` or `startup.sh` (`catalina.sh` adds it to `JDK_JAVA_OPTIONS`). Where the mine must add it: a Tomcat started another way (Cargo's local container: `cargo { local { jvmArgs = '... --add-opens=java.base/java.lang=ALL-UNNAMED' } }`; jsvc, systemd units that call `java` directly, embedded Tomcat), the `test` tasks of a mine that has its own tests (`test { jvmArgs '--add-opens=java.base/java.lang=ALL-UNNAMED' }`), and any script that runs InterMine code with a plain `java`. JDK 8 rejects the option, so add it only for JDK 9+ (`if (JavaVersion.current().isJava9Compatible())`).
- **psi-complexes: small molecules get no name.** The converter used to look the name up at the ChEBI SOAP web service with a vendored JAX-WS client, which JDK 11 cannot run and which the service stopped answering (HTTP 500, Nov 2026). The client was removed in 6.0.0; the small molecules keep their ChEBI identifier but no `name`.

## 5.1.1 to 6.0.0 (Gradle 8.14)

**To be confirmed when 6.0.0 is cut.** What is planned, for a mine that is already on 5.1.1 and Gradle 6.9.4. The items marked *(checked)* were confirmed by a smoke build of a flymine copy on Gradle 8.14.6 with the plugin built from this branch (see the end of this section).

1. While still on the old Gradle (the build still configures), update the wrapper: `./gradlew wrapper --gradle-version 8.14.6 --distribution-type bin --gradle-distribution-sha256-sum 7988ed071b2a07900e2ec715fca15c6ab72bce6db433af301fa7fa7e9407bd24`. Gradle 8.14 still runs on JDK 8, but prints "Executing Gradle on JVM versions 16 and lower has been deprecated" in every build; that warning goes away only with JDK 17.
2. In `gradle.properties` change the plugin version to `6.0.+` (`5.0.+` or `5.1.+` today), and the Gradle-internal property `systemProp.javax.xml.stream.XMLOutputFactory = com.sun.xml.internal...` can be removed once the InterMine build drops it.
3. In `build.gradle`:
   - drop `apply plugin: 'maven'` and the `jcenter()` repository (if still there);
   - replace `compile` by `implementation` and `testCompile` by `testImplementation`. Gradle 7 removed `compile`, `testCompile` and `runtime`. Use `api` instead of `implementation` where a project exposes a dependency in its own public API to a project that compiles against it (and apply `java-library` there); for example, a `dbmodel` project that the `webapp` project compiles against uses `api` for what `webapp` needs;
   - the `cargo` plugin `com.bmuschko:gradle-cargo-plugin`: `2.8.0` to `2.9.0`. 2.9.0 deploys InterMine's testmine to Tomcat 9 on Gradle 8.14.6 without a deprecation warning; 2.8.0 configures on 8.14.6 (flymine copy) but was not run. Neither works on Gradle 9;
   - `sourceCompatibility` / `targetCompatibility` at project level become `java { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }` *(checked: the project-level form prints "The org.gradle.api.plugins.JavaPluginConvention type has been deprecated", removed in Gradle 9)*;
   - assign properties with `=`: `url 'https://...'` in `repositories { maven { } }` becomes `url = 'https://...'`, and `description "..."` in task blocks becomes `description = "..."` (also in `webapp/build.gradle`) *(checked: Gradle 8.14 deprecates the space form, Gradle 10 removes it)*;
   - the mine's own `copyMineProperties` task must stop writing into `build/resources/main` *(checked: on Gradle 8 any build that runs it together with `jar` fails with "Task ':dbmodel:jar' uses this output of task ':dbmodel:copyMineProperties' without declaring an explicit or implicit dependency")*. Keep the task and its name (the plugin's tasks find the file through it), but copy into a directory of its own and let `processResources` take it:

     ```groovy
     task copyMineProperties(type: Copy) {
       description = "Copies mine specific intermine.properties file (from .intermine directory) into resources output to be included in the war"
       from (minePropertyFile)
       into layout.buildDirectory.dir('im/mine-properties')
       rename { fileName -> fileName.replace("$minePropertyFileName", "intermine.properties") }
     }

     processResources {
       from copyMineProperties
     }
     ```

     Delete its `dependsOn 'processResources'` and the `inputs.sourceFiles.stopExecutionIfEmpty()` line. The file also reaches the `dbmodel` resources output, but the plugin excludes `intermine.properties` from the `jar` task of every project that applies a dbmodel plugin, so the database credentials stay out of that jar and you need not add an exclude. (The `webapp` war still gets its own copy through its own `processResources`.)
4. In `dbmodel/build.gradle`: `compile` to `implementation`; keep `srcDirs = ['build/gen']`.
5. In `webapp/build.gradle`: `compile` to `implementation`; `JavaExec` `main = ...` to `mainClass = ...` (removed in Gradle 8; *checked*); the war composition (the `war { }` block, `warWebApp`, `copyWebappContent`) is planned to move into the plugin, so these can then be deleted; if you keep them add `duplicatesStrategy = DuplicatesStrategy.EXCLUDE`.
6. Check with `./gradlew :dbmodel:tasks :webapp:war --dry-run`, then build the war and compare its listing (`unzip -Z1 webapp/build/libs/*.war | sort`) with the war from the old build.

Smoke build on Gradle 8.14.6 (a copy of flymine at `a507ed84`, JDK 8, the plugin and InterMine artifacts built from this branch as 5.1.1): with the edits above plus the ones of the 5.1.1 section (gretty, `maven`, `jcenter()`, `compile`, `stopExecutionIfEmpty`), `./gradlew --warning-mode all :dbmodel:tasks` and `:webapp:war :dbmodel:buildDB --dry-run` configure, and `:dbmodel:jar` runs `createSoModel`, `mergeModels`, `generateModel` and `compileJava`, with no deprecation warning other than the JDK 8 one. The plugin itself printed none. The flymine-specific sources (`bio-source-drosophila-homology` and 19 more) are in no reachable repository, so the copy's `project.xml` was cut to the 47 sources that InterMine publishes; with that model `:webapp:compileJava` stops at classes from the missing sources (`MicroArrayAssay`), which is data, not Gradle.

## Publishing and dependencies of the InterMine artifacts

- The old OSSRH and `uploadArchives` publishing is gone from InterMine itself (see [RELEASING.md](RELEASING.md)); a mine only consumes the released artifacts from Maven Central.
- With Gradle 5 and later, Gradle uses the POM's compile and runtime scopes separately, so a mine that compiles against a class that comes through an InterMine artifact depends on that artifact declaring it as `api`/compile. The planning notes expect a `package ... does not exist` error in a mine as the symptom if an artifact declared something as `implementation` where `api` was needed.
- The plugin's internal configurations were renamed (`api` to `imApiJar`, `antlr` to `imAntlr`, `plugin` to `imPlugin`). Mines do not use them (checked in humanmine and flymine).
- The Ant tasks that the plugin runs (integrate, buildDB, postprocess) now take their classpath from `runtimeClasspath` instead of `compile`; this is a superset (it also contains `runtimeOnly` dependencies), and the order of the jars can differ.

## Removed from the webapp

- **OpenID 2.0 login (5.1.1).** The `/openid` servlet (`OpenIDAuthenticator`), its `openid-providers.properties` list, the "using OpenID" block of the login page and the `JOpenId` library are gone. OpenID 2.0 providers shut the protocol down years ago (Google in 2015), and the shipped provider list was empty, so the login page never offered it. OAuth2 / OpenID Connect login (`oauth2.*` properties) is unchanged, including the migration of old Google OpenID accounts. A mine that has its own `openid-providers.properties` or an `openid-auth` servlet entry in its `web.xml` can delete them; the `openid.allowed` web property is no longer read.
- **Bouncy Castle (5.1.1).** `bcprov-jdk15on` is no longer a dependency; the certificate that `KeyStoreBuilder` stores for each `security.publickey.*` entry is written with the JDK classes. The signing algorithms are the SHA-1/224/256/384/512 variants of RSA and ECDSA.

See [BUILDING.md](BUILDING.md) for how the InterMine builds themselves are set up.

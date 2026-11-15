# Upgrading a mine's build (deployer guide)

This guide is for people who run a mine (humanmine, flymine and similar) whose `build.gradle` uses the InterMine Gradle plugin (`classpath group: 'org.intermine', name: 'plugin', version: ...`). It is a stub: it records what is known today. Parts that depend on the 6.0.0 plugin, which has not been cut yet, are marked **To be confirmed when 6.0.0 is cut**.

The sources are the maintainers' planning notes (`decisions/gradle-upgrade-path.md`, `baselines/intermine-gradle-6.9.4-deprecations.md` and `plans/10-intermine-backend.md` in https://github.com/gmicklem/intermine-planning) and a smoke build of a copy of flymine on Gradle 6.9.4 with the 5.1.1 plugin. Line numbers below are those of that flymine copy (humanmine is the same, a few lines apart).

## Which versions go together

| InterMine plugin | Gradle in the mine | JDK | Status |
|---|---|---|---|
| 5.0.x, 5.1.0 | 4.9 | 8 | the current mines |
| 5.1.1 | 4.9 still works (the plugin is built to stay compatible); **6.9.4 is recommended** | 8 | on this branch; not yet released |
| 6.0.0 | 8.14.x (planned: 8.14.6) | **To be confirmed when 6.0.0 is cut** (the InterMine builds move to JDK 17 in the same effort) | planned |

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

## 5.1.1 to 6.0.0 (Gradle 8.14)

**To be confirmed when 6.0.0 is cut.** What is planned, from the design notes, for a mine that is already on 5.1.1 and Gradle 6.9.4:

1. While still on the old Gradle (the build still configures), update the wrapper: `./gradlew wrapper --gradle-version 8.14.6 --distribution-type bin`.
2. In `gradle.properties` change the plugin version to `6.0.+` (`5.0.+` or `5.1.+` today), and the Gradle-internal property `systemProp.javax.xml.stream.XMLOutputFactory = com.sun.xml.internal...` can be removed once the InterMine build drops it.
3. In `build.gradle`:
   - drop `apply plugin: 'maven'` and the `jcenter()` repository (if still there);
   - replace `compile` by `implementation` and `testCompile` by `testImplementation`. Gradle 7 removed `compile`, `testCompile` and `runtime`. Use `api` instead of `implementation` where a project exposes a dependency in its own public API to a project that compiles against it (and apply `java-library` there); for example, a `dbmodel` project that the `webapp` project compiles against uses `api` for what `webapp` needs;
   - the `cargo` plugin `com.bmuschko:gradle-cargo-plugin`: `2.8.0` to `2.9.0` (to be verified on 8.14);
   - `sourceCompatibility` / `targetCompatibility` at project level become `java { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }`;
   - the mine's own `copyMineProperties` task is replaced by `processResources { from(minePropertyFile) { rename { 'intermine.properties' } } }`; a task of that name defined by the mine no longer runs with plugin 6.0.0 (breaking).
4. In `dbmodel/build.gradle`: `compile` to `implementation`; keep `srcDirs = ['build/gen']`.
5. In `webapp/build.gradle`: `compile` to `implementation`; `JavaExec` `main = ...` to `mainClass = ...` (removed in Gradle 8); the war composition (the `war { }` block, `warWebApp`, `copyWebappContent`) is planned to move into the plugin, so these can then be deleted; if you keep them add `duplicatesStrategy = DuplicatesStrategy.EXCLUDE`.
6. Check with `./gradlew :dbmodel:tasks :webapp:war --dry-run`, then build the war and compare its listing (`unzip -Z1 webapp/build/libs/*.war | sort`) with the war from the old build.

## Publishing and dependencies of the InterMine artifacts

- The old OSSRH and `uploadArchives` publishing is gone from InterMine itself (see [RELEASING.md](RELEASING.md)); a mine only consumes the released artifacts from Maven Central.
- With Gradle 5 and later, Gradle uses the POM's compile and runtime scopes separately, so a mine that compiles against a class that comes through an InterMine artifact depends on that artifact declaring it as `api`/compile. The planning notes expect a `package ... does not exist` error in a mine as the symptom if an artifact declared something as `implementation` where `api` was needed.
- The plugin's internal configurations were renamed (`api` to `imApiJar`, `antlr` to `imAntlr`, `plugin` to `imPlugin`). Mines do not use them (checked in humanmine and flymine).
- The Ant tasks that the plugin runs (integrate, buildDB, postprocess) now take their classpath from `runtimeClasspath` instead of `compile`; this is a superset (it also contains `runtimeOnly` dependencies), and the order of the jars can differ.

See [BUILDING.md](BUILDING.md) for how the InterMine builds themselves are set up.

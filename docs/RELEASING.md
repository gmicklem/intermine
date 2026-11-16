# Releasing InterMine to Maven Central

Two releases are planned from two branches of https://github.com/gmicklem/intermine:

- **5.1.1**: the non-breaking release, on Gradle 6.9.4 and JDK 8, from the branch `release/5.1.1-candidate` (the last 6.9.4 state; section 3). Mines on Gradle 4.9 keep working with its plugin.
- **6.0.0**: the breaking release, on Gradle 8.14.6 and JDK 17, from `maint-2026` (section 4). Until it is cut, every version number in `maint-2026` still reads `5.1.1`, so artifacts built from that branch are *not* the 5.1.1 release: change the number to `6.0.0` first (see "Version bump").

InterMine publishes these builds to Maven Central under the group `org.intermine`:

| Build (directory) | What it publishes | Bundle file |
|---|---|---|
| `plugin` | the Gradle plugin `plugin` | `plugin/build/central-bundle/plugin-bundle.zip` |
| `intermine` | `intermine-*` (api, model, objectstore, webapp as a war, ...) | `intermine/build/central-bundle/intermine-bundle.zip` |
| `bio` | `bio-core`, `bio-model`, `bio-tools`, `bio-webapp` (war), `bio-testresources` | `bio/build/central-bundle/bio-bundle.zip` |
| `bio/sources` | `bio-source-*` | `bio/sources/build/central-bundle/bio-sources-bundle.zip` |
| `bio/postprocess` | `bio-postprocess-*` | `bio/postprocess/build/central-bundle/bio-postprocess-bundle.zip` |

`bio/postprocess-test` and `testmine` are never published.

The old OSSRH service (`s01.oss.sonatype.org`, `uploadArchives`, `ossrhUsername`) was shut down in 2025 and is no longer used anywhere.
Publishing now uses `maven-publish` and the Central Portal (https://central.sonatype.com).

## What is in the build

- `config/gradle/publishing.gradle` is applied to every published module. It adds `maven-publish` and `signing`, the sources and javadoc jars, one publication `mavenJava` (groupId `org.intermine`, artifactId = project name, version = `version` of the build file) and the POM metadata (name, description, url, LGPL-2.1 licence, scm, developers).
- Module metadata (`.module` files) is switched off, so a release contains exactly what the 5.1.0 release contained: jar (or war), `-sources`, `-javadoc`, the `-classes`/`-testClasses` jars where they existed, and a POM.
- `publishToMavenLocal` installs into `~/.m2` (or into `-Dmaven.repo.local=<dir>`). `install` still exists as a deprecated alias of it. Nothing is signed for a local install.
- `config/gradle/central-bundle.gradle` is applied to the root of each build above. `./gradlew centralBundle` publishes every module into `build/central-bundle/m2` with PGP signatures and checksums and zips that tree. This task is the only place where signing is required; it fails fast if the key is missing.
- Nothing in the Gradle build uploads anything. Uploading to the Portal is a `curl` call (below).
- The generated POMs carry the dependency scopes the old `maven` plugin produced: `api`/`compile` become `compile`, `implementation`/`runtimeOnly` become `runtime`, and `providedCompile` becomes `provided`. Test dependencies are no longer listed (the old plugin listed them with scope `test`; nothing resolves them).

## One-time setup (human steps)

These are done by the person releasing, not by a script or an agent.

1. A Central Portal account at https://central.sonatype.com, with the namespace `org.intermine` verified (Portal: Publishing > Namespaces). The old OSSRH account does not carry over.
2. A Portal user token (Account > Generate User Token). It has a username and a password part; treat both as secrets.
3. A GPG key whose public half is on a keyserver (`keys.openpgp.org` or `keyserver.ubuntu.com`). Central checks signatures against published keys.
4. Environment for the build, never committed:

```bash
export SIGNING_KEY="$(gpg --armor --export-secret-keys <KEYID>)"   # armoured private key, multi-line
export SIGNING_PASSWORD='...'                                        # empty if the key has no passphrase
export SIGNING_KEY_ID=<last 8 hex of the key id>                     # optional: only needed if the key has subkeys
export CENTRAL_USERNAME='...'                                        # Portal token, user part
export CENTRAL_PASSWORD='...'                                        # Portal token, password part
```

## 1. Prepare the release

### Version bump

The version appears in these places; change all of them in one commit (`release: 6.0.0`). On `release/5.1.1-candidate` there is no version catalog: the version is `version '5.1.1'` in each of the `build.gradle` files below (`subprojects { }` block, or the top level in `plugin`), and `imVersion` in `testmine/build.gradle`, and it is already 5.1.1. On `maint-2026`:

- `gradle/libs.versions.toml`: `intermine = "5.1.1"` in `[versions]`. `intermine`, `bio`, `bio/sources`, `bio/postprocess`, `bio/postprocess-test` and `testmine` read it as `libs.versions.intermine`. `plugin/build.gradle` is the exception: it has its own `version = '5.1.1'` at the top level.
- `plugin/gradle.properties.in`, `intermine/gradle.properties.in`, `bio/gradle.properties.in`, `bio/sources/gradle.properties.in`, `bio/postprocess/gradle.properties.in`, `bio/postprocess-test/gradle.properties.in`: `systemProp.imVersion` and `systemProp.bioVersion`. The generated `gradle.properties` files are git-ignored; regenerate them with `python3 -I config/lib/install_intermine.py` (or the gate) after editing the `.in` files.
- `README.md`: the version badge (`badge/version-5.1.0-blue`).
- `intermine/webapp/src/main/java/org/intermine/web/logic/Constants.java`: `INTERMINE_VERSION`.

Check that nothing was missed: `grep -rn "5\.1\.1" --include=*.gradle --include=*.toml --include=*.in --include=*.java .` should show only the new version.

### Changelog and tag

1. Add the section for the new version at the top of `RELEASE_NOTES` (same format as the `InterMine 5.1.0 (August 2022)` entry: one line per issue).
2. Run the EL sweep on the commit that will be released, with the `el-api.jar` and `jasper-el.jar` of the newest Tomcat 9 that the release should support. It must print `failures=0`; a Java keyword as an EL property name (`widget.class`, `dec.boolean`) breaks pages on current Tomcat although the build and the unit tests pass. The tool and its README are in `reference/jsp-el/` of https://github.com/gmicklem/intermine-planning; the commands are in [UPGRADING-build.md](UPGRADING-build.md#tomcat-rejects-java-keywords-as-el-property-names):

```bash
CP=/path/to/tomcat/lib/el-api.jar:/path/to/tomcat/lib/jasper-el.jar
javac -cp $CP -d /tmp/elsweep/out ElSweep.java
java -cp /tmp/elsweep/out:$CP ElSweep intermine/webapp/src bio/webapp/src
```

3. Check that no asset is loaded from `cdn.intermine.org` (gone): `grep -rn cdn.intermine.org intermine/webapp/src/main intermine/api/src/main bio/webapp/src/main` must print nothing once im-jqz.43 is in the release.
4. Run the full gate (below) on the commit that will be released.
5. Tag the release commit `intermine-X.Y.Z` (for example `intermine-5.1.1`) and push the tag. Older releases use the same naming (`intermine-5.1.0`).

## 2. Build and verify locally

Use the wrapper of each build, with JDK 17 for 6.0.0 (JDK 8 for 5.1.1: its Gradle 6.9.4 does not run on JDK 17). The jars are Java 8 bytecode with either JDK; to check, `unzip -p <jar> <some>.class | head -c 8 | od -An -j6 -N2 -tu1` prints `0 52`. Build in this order, because later builds resolve the jars of earlier builds from `build/libs`: plugin, intermine, bio, bio/sources, bio/postprocess.

```bash
export LANG=C.UTF-8 LC_ALL=C.UTF-8        # javadoc fails on non-ASCII sources under an ASCII locale
export PSQL_HOST=localhost PSQL_USER=test PSQL_PWD=test   # the script exits at once without them (they go into ~/.intermine/*.properties)
python3 -I config/lib/install_intermine.py --clean   # writes gradle.properties, then runs publishToMavenLocal in the five builds
```

For an isolated check that does not touch `~/.m2`:

```bash
for b in plugin intermine bio bio/sources bio/postprocess; do
  (cd $b && ./gradlew -Dmaven.repo.local=/tmp/im-m2 publishToMavenLocal)
done
find /tmp/im-m2/org/intermine -name '*.pom' | head      # one POM per module
```

Then run the unit tests and checkstyle as CI does (`./gradlew build checkstyleMain` in `intermine`, `bio`, `bio/sources`, `bio/postprocess`, `bio/postprocess-test`).

Smoke test with a deployer: in a checkout of a mine (for example `humanmine` or `flymine`), set `imVersion` and `bioVersion` in its `gradle.properties` to the new version, add `mavenLocal()` (or the `/tmp/im-m2` directory) to the repositories of its `buildscript` and `repositories` blocks, and run `./gradlew :dbmodel:compileJava :webapp:war`. The plugin and the `intermine-webapp:classes` and `bio-webapp` artifacts must resolve from the local repository.

## 3. Release 5.1.1 (Gradle 6.9.4, manual bundle upload)

5.1.1 is built from the branch `release/5.1.1-candidate`, on Gradle 6.9.4, so that the plugin stays usable by mines that are still on Gradle 4.9 (checked: see [UPGRADING-build.md](UPGRADING-build.md)).
Releasing needs only JDK 8, the build wrappers and `curl`. Check out that branch first.

1. Make sure the working tree is the tagged release commit and the environment of "One-time setup" is exported.
2. Create the five bundles, in build order:

```bash
for b in plugin intermine bio bio/sources bio/postprocess; do
  (cd $b && ./gradlew --no-daemon centralBundle) || break
done
```

   Each build prints `BUILD SUCCESSFUL` and leaves `build/central-bundle/<name>-bundle.zip`. Without `SIGNING_KEY` the task stops with a message that says so.
3. Inspect the bundles before uploading. Every artifact must have `.asc`, `.md5` and `.sha1` files next to it, and there must be no `maven-metadata*` file:

```bash
unzip -Z1 intermine/build/central-bundle/intermine-bundle.zip | grep -v '/$' | head -30
unzip -p intermine/build/central-bundle/intermine-bundle.zip org/intermine/intermine-webapp/5.1.1/intermine-webapp-5.1.1.pom | head -30   # packaging war, provided scopes
```

4. Upload each bundle to the Portal, as a user-managed deployment so that it is validated but not yet released:

```bash
TOKEN=$(printf '%s:%s' "$CENTRAL_USERNAME" "$CENTRAL_PASSWORD" | base64 -w0)
for z in plugin/build/central-bundle/plugin-bundle.zip intermine/build/central-bundle/intermine-bundle.zip \
         bio/build/central-bundle/bio-bundle.zip bio/sources/build/central-bundle/bio-sources-bundle.zip \
         bio/postprocess/build/central-bundle/bio-postprocess-bundle.zip; do
  n=$(basename "$z" .zip)
  curl --fail-with-body -sS -X POST -H "Authorization: Bearer $TOKEN" \
       -F "bundle=@$z" "https://central.sonatype.com/api/v1/publisher/upload?name=$n-5.1.1&publishingType=USER_MANAGED"
  echo "  <- deployment id for $n"
done
```

   The response body is the deployment id. Check its state with `curl -sS -X POST -H "Authorization: Bearer $TOKEN" "https://central.sonatype.com/api/v1/publisher/status?id=<id>"` (`PENDING`, `VALIDATING`, `VALIDATED`, `PUBLISHING`, `PUBLISHED` or `FAILED` with the reasons), or look at https://central.sonatype.com/publishing/deployments.
5. When all five show `VALIDATED`, publish them in the Portal UI (Publish), or with `curl -sS -X POST -H "Authorization: Bearer $TOKEN" https://central.sonatype.com/api/v1/publisher/deployment/<id>`. Publishing cannot be undone: a version on Central can never be changed or deleted. Use `publishingType=AUTOMATIC` instead of `USER_MANAGED` in step 4 only once the procedure has been proven.
6. If a deployment fails validation, drop it in the Portal UI, fix the cause and upload that bundle again; nothing is released until a deployment is published.

## 4. Release 6.0.0 (Gradle 8.14, with the plugin and build upgrade)

6.0.0 ships the Gradle 8.14 build and plugin (steps 2 and 3 of `decisions/gradle-upgrade-path.md` in https://github.com/gmicklem/intermine-planning). The publishing scripts are written for Gradle 6.9.4 and also work unchanged on 7.x and 8.x (tested on 8.14.6 with JDK 17 against a sample multi-project build with a war module), so the procedure is the same as for 5.1.1:

- Run the five `centralBundle` builds with the Gradle 8.14 wrappers, then upload with the same `curl` calls (use the new version in `name=`).
- The Portal plugin `com.gradleup.nmcp` was evaluated and vetted (`decisions/package-vetting.md` in the planning repository). It is safe but is not used: it needs JDK 17 and Gradle 8.2+ to run, the builds must still work on JDK 8 until 6.0.0 is out, and it would only replace the zip and the `curl` call above. Revisit it when JDK 8 is dropped and the builds become one composite build, where a single aggregated deployment (`com.gradleup.nmcp.aggregation`, credentials from `CENTRAL_USERNAME` / `CENTRAL_PASSWORD`, `nmcpAggregation { centralPortal { username = ...; password = ...; publishingType = 'USER_MANAGED' } }`) is worth the extra dependency.
- Gradle 9 is not covered yet: re-run the publishing scripts with `--warning-mode all` when that step is done (`afterEvaluate` publication, `signing.useInMemoryPgpKeys`).
- Mines must move to Gradle 8.14 together with the 6.0.0 plugin; their upgrade steps are in [UPGRADING-build.md](UPGRADING-build.md). Say so in the release notes.

## 5. After the release

1. Wait for the deployments to reach `PUBLISHED` (a few minutes up to an hour), then check that the files are visible, for example `https://repo1.maven.org/maven2/org/intermine/plugin/5.1.1/` and `https://repo1.maven.org/maven2/org/intermine/intermine-webapp/5.1.1/` (war, `-classes`, `-sources`, `-javadoc`, POM and signatures). The search index at https://central.sonatype.com can lag by up to a day.
2. Check one POM against the previous release: packaging `war` for `intermine-webapp` and `bio-webapp`, scopes `compile`/`runtime`/`provided` as before.
3. From a clean cache, resolve the plugin and `intermine-webapp:classes` in a mine: `./gradlew --refresh-dependencies :dbmodel:tasks` with only Maven Central as repository.
4. Update the mines (`humanmine`, `flymine`): `imVersion` and `bioVersion` in `gradle.properties` to the released version, then build them.
5. Push the tag (if not already done), create the GitHub release from the `RELEASE_NOTES` entry, and move the next `-SNAPSHOT`-style work (if any) to the new version.

## Troubleshooting

- `centralBundle needs the armoured PGP private key in the SIGNING_KEY environment variable`: export the variables of "One-time setup". The key must be the armoured private key, not a file name.
- `No signatory`/`Could not read PGP secret key`: the key has a passphrase that does not match `SIGNING_PASSWORD`, or the key has subkeys and `SIGNING_KEY_ID` must name the signing subkey.
- Portal validation reports a missing checksum or signature: the bundle was not made by `centralBundle`, or a module was excluded by hand.
- Portal validation reports `Namespace not verified` or `Invalid token`: Portal side; see "One-time setup".
- Javadoc fails with `unmappable character for encoding ASCII`: run with `LANG=C.UTF-8`.

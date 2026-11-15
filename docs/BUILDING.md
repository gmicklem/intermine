# Building InterMine (5.1.1 line)

How the back-end code in this branch (`release/5.1.1-candidate`) is built. For releasing see [RELEASING.md](RELEASING.md), for mines (humanmine, flymine, ...) that use the InterMine Gradle plugin see [UPGRADING-build.md](UPGRADING-build.md). The 6.0.0 line (Gradle 8.14.6, JDK 17) is on the `maint-2026` branch and has its own page.

## Requirements

- JDK 8. This line builds with Gradle 6.9.4, which does not run on JDK 17, and the CI of this branch (`.github/workflows/im-build.yml`) runs on JDK 8 only.
- Python 3.10 or later for `config/lib/install_intermine.py`.
- PostgreSQL, but only to run tests (see `config/ci/init.sh`). Compiling and publishing do not need it.
- `export LANG=C.UTF-8 LC_ALL=C.UTF-8` before building: javadoc fails on non-ASCII sources under an ASCII locale.

## Seven separate Gradle builds

There is no root build. Each directory has its own `settings.gradle`, `build.gradle` and Gradle wrapper, and is built by running `./gradlew` inside it: `plugin`, `intermine`, `bio`, `bio/sources`, `bio/postprocess`, `bio/postprocess-test` and `testmine`. The first five are published (see [RELEASING.md](RELEASING.md)); `bio/postprocess-test` and `testmine` are not.

They are built in this order, and each needs the jars of the ones before it:

```
plugin -> intermine -> bio -> bio/sources -> bio/postprocess -> bio/postprocess-test -> testmine
```

The jars are found through `file://` ivy repositories of each module's `build/libs` directory, and the plugin from `plugin/build/libs`, both set in `gradle.properties` (`intermine.development.*`, `intermine.plugin.repo`); everything else comes from Maven Central and the EBI repository. So a build only works after the builds before it have produced their jars with `publishToMavenLocal` (or `install`, a deprecated alias of it; see `config/gradle/publishing.gradle`).

## Generated files: `config/lib/install_intermine.py`

`gradle.properties` and `gradle/wrapper/gradle-wrapper.properties` are not committed (they are in `.gitignore`). Each build has a `.in` template, and the script fills in the `@@key@@` placeholders. Edit the `.in` file, then run the script again. The script:

1. writes every `gradle.properties` and `gradle-wrapper.properties` from its `.in` file;
2. copies `config/ci.properties`, `config/testmodel.properties` and `config/ci-bio.properties` to `~/.intermine/` (an existing file is never overwritten);
3. runs `./gradlew publishToMavenLocal --stacktrace` in `plugin`, `intermine`, `bio`, `bio/sources` and `bio/postprocess`, in that order (`bio/postprocess-test` and `testmine` are not built by it).

It exits with an error unless `PSQL_HOST`, `PSQL_USER` and `PSQL_PWD` are set:

```bash
export PSQL_HOST=localhost PSQL_USER=test PSQL_PWD=test
python3 -I config/lib/install_intermine.py            # add --clean to run ./gradlew clean first
```

`python3 config/lib/install_intermine.py --help` lists the repository URL options and `--offline`.

The Gradle version is `DEFAULT_GRADLE_VERSION` in the script (**6.9.4** on this branch; it also knows 4.9). The committed `gradle-wrapper.jar` files are old (Gradle 3.0 to 4.9, depending on the build); they only download the distribution named in the generated `gradle-wrapper.properties`.

## Common commands

All run inside one build directory, after the script above has generated the properties and the earlier builds exist:

```bash
./gradlew publishToMavenLocal      # build the jars (into build/libs) and install them into ~/.m2
./gradlew build                    # compile, checkstyle and unit tests (tests need PostgreSQL)
./gradlew checkstyleMain           # checkstyle only
```

`-Dmaven.repo.local=<dir>` makes `publishToMavenLocal` install into `<dir>` instead of `~/.m2`. These are the commands in `CONTRIBUTING.md` and `config/ci/run.sh`.

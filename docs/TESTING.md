# Testing InterMine

How the unit tests and web service tests find their databases and settings, how CI runs and judges them, and how to run one build's tests on your own machine. For the builds themselves see [BUILDING.md](BUILDING.md).

## What the tests need

- **PostgreSQL**, reachable on `localhost` at the default port (the scripts and Gradle tasks call `psql -h localhost` without a port). CI runs PostgreSQL 14 and 16 (`postgres:<version>` service container); the maintainers also run the suite on PostgreSQL 18.
- **An administrator role** that can create databases and roles: `PGUSER` (default `postgres`) with `PGPASSWORD` (default `postgres`). `config/ci/init.sh`, the `createUnitTestDatabases` Gradle tasks and `testmine/setup.sh` connect directly with `psql -w -h localhost -U $PGUSER`, with no `sudo`.
- **A test role**, `test` with password `test`, which owns or has all privileges on the test databases. `config/ci/init.sh` drops and recreates it.
- **The test databases.** In the `intermine` build, `:intermine-testresources:createUnitTestDatabases` creates `notxmltest`, `truncunittest`, `flatmodetest`, `fulldatatest`, `userprofile-test` and `unittest`; the unit tests of the modules that need them run it first (via `:intermine-testresources:buildDB`). In the `bio` builds, `:bio-testresources:createUnitTestDatabases` creates `bio-test` and `bio-fulldata-test` (no test currently opens `bio-fulldata-test`; it is created for the `os.bio-fulldata-test` objectstore that the bio default properties define, and `config/ci-bio.properties` points that objectstore at the same name). The web service tests also use `intermine-demo` and `userprofile-demo`, created by `testmine/setup.sh`.
- **Property files under `~/.intermine/`**, which tell the tests how to connect to those databases:
  - `intermine-test.properties`, from `config/ci.properties`
  - `intermine-bio-test.properties`, from `config/ci-bio.properties`
  - `testmodel.properties`, from `config/testmodel.properties`

  `config/lib/install_intermine.py` writes them, replacing `PSQL_HOST`, `PSQL_USER` and `PSQL_PWD` with the environment variables of those names, and skips any file that already exists. If you change the test password, delete the files so that they are regenerated. `testmine/setup.sh` also writes `~/.intermine/testmodel.properties` (from `testmine/dbmodel/resources/testmodel.properties`) if it is missing.
- **JDK 17** (or JDK 8, until 6.0.0 is released) and Python 3 (see [BUILDING.md](BUILDING.md)). The JDK that runs Gradle (`JAVA_HOME`) also runs the tests. On JDK 9 and later the test tasks get `--add-opens=java.base/java.lang=ALL-UNNAMED` from `config/gradle/java-target.gradle`; nothing to set. The wrapper downloads Gradle itself (8.14.6).
- For the web service tests only: Solr (`config/ci/init-solr-docker.sh`, which starts the official image in Docker), `wait-for-it`, and a checkout of a client (`imjs` with Node and `grunt`, or `intermine-ws-python`).

## How CI runs the tests

Two workflows in `.github/workflows/`. Both start on `push` and `pull_request` when a path under `bio/`, `config/`, `docs/`, `intermine/`, `plugin/` or `testmine/` (or the workflow file itself) changes, and can be started by hand (`workflow_dispatch`). A newer push to the same branch or pull request cancels the older run, except on `dev` and `master`. Permissions are `contents: read`.

### `im-build.yml` ("InterMine CI")

| Job | Matrix | What it runs |
|---|---|---|
| `unit-tests` | `testSuite` in `intermine`, `bio`; `java` 8 and 17; `postgres` 14 and 16 (four cells per suite, `fail-fast: false`) | `config/ci/init.sh` then `config/ci/run.sh` with the suite name |
| `ws-tests` | `client` in `JS`, `PY`; `java` 17; PostgreSQL 14 | Solr in Docker, then `init.sh` and `run.sh` with the suite `ws`: build and start the testmine web application, then run the client's tests against it (`config/run-ws-tests.sh`) |

Both jobs have a 45 minute timeout, cache `~/.gradle/caches` and `~/.gradle/wrapper`, keyed on the build scripts and templates, check the database connection first (`psql -w -h localhost -U postgres -c 'select version()'` with `PGPASSWORD=postgres`), write a test summary into the job summary (`config/ci/junit-summary.py`), and upload the JUnit XML (always) and the HTML reports (on failure).

`config/ci/init.sh <workspace> <python> <suite> <client> <testmodel_url>` installs the Python requirements, drops the test databases and the `test` role and recreates the role (`dropuser`, `createuser`, then `alter user test with encrypted password 'test'`; for the suites `intermine`, `bio` and `ws`), and runs `config/lib/install_intermine.py` with `PSQL_USER=test`, `PSQL_PWD=test`, `PSQL_HOST=localhost`. For `ws` it also creates the unit test databases, runs `testmine/setup.sh` and clones the client.

`config/ci/run.sh` (same arguments) runs, for each build, `./gradlew -Dorg.gradle.jvmargs=-Xmx4g --max-workers=4 --no-daemon --warning-mode all --stacktrace build`: `intermine` for the suite `intermine`; `bio`, `bio/sources`, `bio/postprocess`, `bio/postprocess-test` for the suite `bio`.

### `checkstyle.yml` ("Checkstyle")

One job, JDK 17, 30 minute timeout. It runs `init.sh` and `run.sh` with the suite `checkstyle`: `./gradlew checkstyleMain` in `intermine`, `bio`, `bio/sources` and `bio/postprocess`, then `config/lib/parse_checkstyle_report.py`, which reads every `reports/checkstyle/main.xml` and fails on any remaining error, or if no report was found. Checkstyle itself already fails the build (`ignoreFailures = false`, `maxWarnings = 0`).

### How pass or fail is decided despite `ignoreFailures = true`

The `test` task of each build is configured with `ignoreFailures = true`, so `./gradlew build` succeeds even when tests fail and the build finishes with all the reports written. The verdict comes from the second step: `run.sh` calls `config/lib/parse_test_report.py <build dir>` after the Gradle runs. It looks at `build/test-results/test/*.xml` of every project directly below that directory, prints each failed or errored test with its trace, and exits with status 1 if any test failed or if **no test at all** was found ("NO TESTS WERE RUN!"). It is called for `intermine`, `bio`, `bio/sources` and `bio/postprocess-test` (not for `bio/postprocess`, which has no tests of its own). The script needs the `xunitparser` package (`config/lib/requirements.txt`). Only after those succeed does `run.sh` print `ALL TESTS PASSED`. A compile error or a failing task still fails the Gradle step itself.

## Running one build's tests locally

You need PostgreSQL with an administrator role (see above). Then, once:

```bash
export PSQL_HOST=localhost PSQL_USER=test PSQL_PWD=test
export PGUSER=postgres PGPASSWORD=postgres            # the administrator role
python3 -m pip install -r config/lib/requirements.txt   # xunitparser, for parse_test_report.py
createuser -w -h localhost -U "$PGUSER" test              # skip if the role exists
psql -w -h localhost -U "$PGUSER" -c "alter user test with encrypted password 'test';"
python3 -I config/lib/install_intermine.py                # properties, wrappers, and publishToMavenLocal of the 5 builds
```

(`config/ci/init.sh` is the full version of the database set-up: it also drops the test databases first, which gives you a clean state.) Then, for the build you changed, as `run.sh` does:

```bash
cd intermine
./gradlew -Dorg.gradle.jvmargs=-Xmx4g --max-workers=4 --no-daemon --warning-mode all --stacktrace build
cd .. && python3 config/lib/parse_test_report.py "$PWD/intermine"
```

The `bio` builds need the `intermine` build installed first (the `install_intermine.py` step does that), then `bio`, `bio/sources`, `bio/postprocess` and `bio/postprocess-test` in that order. After changing code in an earlier build, run `./gradlew publishToMavenLocal` there again so that the later builds see the new jars (see "How the builds depend on each other" in [BUILDING.md](BUILDING.md)).

The standard Gradle options apply to narrow a run to one module or class (not run for this page): `./gradlew :intermine-objectstore:test --tests 'org.intermine.util.UtilTest'` (the tests are selected by the `**/*Test.class` pattern, with class scanning off). Open `build/reports/tests/test/index.html` of a module for the report. Before dropping the test databases by hand (or with the debugging task `dropDBs` of `intermine/testresources`), run `./gradlew --stop`: a live Gradle daemon keeps a connection to `unittest` open (a comment in `intermine/testresources/build.gradle` says so).

For the web service tests use the steps of `config/ci/init.sh` for the suite `ws`, which build and start the testmine web application with `testmine/setup.sh <workspace_dir>`.

## Two PostgreSQL gotchas that are fixed in the scripts

1. **PostgreSQL 15 and later: privileges on the `public` schema.** Since PostgreSQL 15 only the owner of a database may create objects in its `public` schema. The test databases are created by `postgres`, but the tests connect as `test`, so the first `create table` failed with `permission denied for schema public` (followed by `table intermine_metadata doesn't exist`). `grantPrivileges` in `intermine/testresources/build.gradle` and `bio/testresources/build.gradle` now also runs `grant all on schema public to test;` in each database. In `testmine/setup.sh` the databases are created with `createdb -O "$PSQL_USER"`, so the mine's user owns them. If you create test databases by hand, do the same.
2. **`sudo -E` drops `PGPASSWORD`.** The scripts used to run `sudo -E -u postgres psql ...`. The newer `sudo` on the `ubuntu-latest` runner image ignores `-E`, so `PGPASSWORD` was lost and every `dropdb` waited for a password in a loop until the job timed out. `config/ci/init.sh`, `testmine/setup.sh` and the two `createUnitTestDatabases` tasks now connect directly as `PGUSER` (default `postgres`) with `PGPASSWORD` (default `postgres`) and pass `-w`, so a wrong or missing password fails at once instead of prompting. Do not reintroduce `sudo` in these scripts.

## The maintainers' gate script

The maintainers run all of the above (install, build, checkstyle, test reports, in an isolated home directory and database) with a gate script that lives outside this repository, in https://github.com/gmicklem/intermine-planning. It is not needed to contribute: the commands on this page are enough.

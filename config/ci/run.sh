#!/bin/bash

set -euxo pipefail

if [ "$#" != "5" ]; then
   echo "Usage: $0 <workspace_dir> <python executable> <test_suite> <client> <testmodel_url>"
   exit 2
fi

WORKSPACE_DIR=$1
PYTHON=$2
TEST_SUITE=$3
CLIENT=$4
TESTMODEL_URL=$5

echo "RUNNING test suite $TEST_SUITE"

export ANT_OPTS='-server'


run_gradle() {
    local dir=$1
    local command=$2

    (cd "${WORKSPACE_DIR}/$dir" && ./gradlew -Dorg.gradle.jvmargs=-Xmx4g --max-workers=4 --no-daemon  --warning-mode all --stacktrace "$command")
}

gradlew_install() {
    local dir=$1

    run_gradle "$dir" publishToMavenLocal
}

gradlew_build() {
    # Add --rerun-tasks below to force rebuild
    # Add --info --stacktrace for more useful information when debugging
    # && ./gradlew clean if things look a bit broken (also killall java and rm -r ~/.gradle/caches)

    local dir=$1

    run_gradle "$dir" build
}

# The TestKit tests of the Gradle plugin (plugin/src/test: synthetic mines that apply the plugin, see
# docs/BUILDING.md "Tests of the plugin"). publishToMavenLocal, which init.sh runs, does not run them. They need
# no database, so they run once per Java version, with the intermine suite; unlike the intermine tests they are not
# run with ignoreFailures, so a failure fails this step at once.
plugin_tests() {
    run_gradle plugin test

    # parse_test_report.py takes a directory of Gradle builds and reads <build>/build/test-results/test: with the
    # workspace root that is plugin/build/test-results/test (and fails if no test was run)
    "${WORKSPACE_DIR}"/config/lib/parse_test_report.py "${WORKSPACE_DIR}"
}

if [ "$TEST_SUITE" = "intermine" ]; then
    echo "RUNNING plugin tests"
    plugin_tests

    echo "RUNNING intermine unit tests"
    gradlew_build intermine

    echo CHECKING results
    "${WORKSPACE_DIR}"/config/lib/parse_test_report.py "${WORKSPACE_DIR}/intermine"

    echo ALL TESTS PASSED
elif [ "$TEST_SUITE" = "bio" ]; then
    echo "RUNNING bio unit tests"
    gradlew_build bio
    gradlew_build bio/sources
    gradlew_build bio/postprocess
    gradlew_build bio/postprocess-test

    echo CHECKING results
    "${WORKSPACE_DIR}"/config/lib/parse_test_report.py "${WORKSPACE_DIR}/bio"
    "${WORKSPACE_DIR}"/config/lib/parse_test_report.py "${WORKSPACE_DIR}/bio/sources"
    "${WORKSPACE_DIR}"/config/lib/parse_test_report.py "${WORKSPACE_DIR}/bio/postprocess-test"

    echo ALL TESTS PASSED
elif [ "$TEST_SUITE" = "checkstyle" ]; then
    run_gradle intermine checkstyleMain
    run_gradle bio checkstyleMain
    run_gradle bio/sources checkstyleMain
    run_gradle bio/postprocess checkstyleMain

    "${WORKSPACE_DIR}"/config/lib/parse_checkstyle_report.py "${WORKSPACE_DIR}"

    # A checked exception that a method declares needs a Javadoc @throws tag; checkstyle 9.3 cannot check it
    # (docs/BUILDING.md, "@throws tags"). Fails with the list of methods. Fast: the jars of init.sh are built already.
    run_gradle intermine checkThrowsDocs
    run_gradle bio checkThrowsDocs
    run_gradle bio/sources checkThrowsDocs
    run_gradle bio/postprocess checkThrowsDocs
elif [ "$TEST_SUITE" = "ws" ]; then
    "${WORKSPACE_DIR}"/config/run-ws-tests.sh "${WORKSPACE_DIR}" "${PYTHON}" "${CLIENT}" "${TESTMODEL_URL}"
fi

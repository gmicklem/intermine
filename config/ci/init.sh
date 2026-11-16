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

export PSQL_USER=test
export PSQL_PWD=test
export PSQL_HOST=localhost
# Database administrator for setup_postgres and the Gradle createUnitTestDatabases
# tasks: connects directly, no sudo (newer sudo ignores -E, which dropped
# PGPASSWORD and made every command prompt for a password). -w makes a wrong
# or missing password fail at once instead of prompting.
export PGUSER=${PGUSER:-postgres}
export PGPASSWORD=${PGPASSWORD:-postgres}
PG_ADMIN_USER=$PGUSER

if [ -z "$(which wget)" ]; then
    # use curl
    GET='curl'
else
    GET='wget -O -'
fi

GIT_GET="git clone --single-branch --depth 1"

export KEYSTORE=${PWD}/keystore.jks

setup_postgres() {
    dropdb -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" --if-exists flatmodetest
    dropdb -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" --if-exists fulldatatest
    dropdb -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" --if-exists notxmltest
    dropdb -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" --if-exists truncunittest
    dropdb -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" --if-exists unittest
    dropdb -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" --if-exists userprofile-test
    dropdb -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" --if-exists bio-test
    dropdb -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" --if-exists intermine-demo
    dropdb -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" --if-exists userprofile-demo

    dropuser -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" --if-exists test
    createuser -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" test
    psql -w -h "$PSQL_HOST" -U "$PG_ADMIN_USER" -c "alter user test with encrypted password 'test';"
}

setup_python() {
    echo '#---> Installing python requirements'
    # Install lib requirements
    ${PYTHON} -m pip install -r "${WORKSPACE_DIR}"/config/lib/requirements.txt
}

setup_python

if [[ "$TEST_SUITE" = "intermine" ]]; then
    setup_postgres
fi

if [[ "$TEST_SUITE" = "bio" ]]; then
    setup_postgres
fi

if [[ "$TEST_SUITE" = "ws" ]]; then
    setup_postgres
fi

${PYTHON} "${WORKSPACE_DIR}"/config/lib/install_intermine.py

if [[ "$TEST_SUITE" = "ws" ]]; then
    # set up database for testing
    (cd "${WORKSPACE_DIR}"/intermine && ./gradlew createUnitTestDatabases)

    # We will need a fully operational web-application
    echo '#---> Building and releasing web application to test against'
    (cd "${WORKSPACE_DIR}"/testmine && ./setup.sh "${WORKSPACE_DIR}")

    # Warm up the keyword search by requesting results, but ignoring the results
    $GET "$TESTMODEL_URL/service/search" > /dev/null
    # Start any list upgrades
    $GET "$TESTMODEL_URL/service/lists?token=test-user-token" > /dev/null

    cd "${WORKSPACE_DIR}"
    if [[ "$CLIENT" = "JS" ]]; then
        # We need the imjs code to exercise the webservices
        $GIT_GET https://github.com/intermine/imjs.git client-JS
    elif [[ "$CLIENT" = "PY" ]]; then
        $GIT_GET -b dev https://github.com/intermine/intermine-ws-python client-PY
    fi
fi

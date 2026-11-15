#!/bin/bash

# Start Solr in Docker with the two empty cores testmine's setup script
# populates (intermine-search, intermine-autocomplete). This replaces
# config/ci/init-solr.sh in CI: pulling the official image is much quicker
# than downloading and unpacking the Solr tarball from archive.apache.org.
#
# Usage: init-solr-docker.sh [solr_version] (default 8.6.2)
# The container is called ${SOLR_CONTAINER:-im-solr}; stop it with
# `docker rm -f im-solr`.

set -euo pipefail

SOLR_VERSION=${1:-8.6.2}
CONTAINER=${SOLR_CONTAINER:-im-solr}
SOLR_URL=http://localhost:8983/solr

docker rm -f "$CONTAINER" > /dev/null 2>&1 || true
# solr-precreate creates the first core from the default configset and then
# starts Solr in the foreground.
docker run -d --name "$CONTAINER" -p 8983:8983 "solr:${SOLR_VERSION}" \
    solr-precreate intermine-search

core_exists() {
    curl -fsS "${SOLR_URL}/admin/cores?action=STATUS&core=$1&wt=json" \
        | grep -q "\"name\":\"$1\""
}

echo "Waiting for Solr ${SOLR_VERSION} to start"
for _ in $(seq 1 60); do
    if core_exists intermine-search 2> /dev/null; then
        break
    fi
    sleep 2
done
if ! core_exists intermine-search; then
    echo "Solr did not come up; container log follows" >&2
    docker logs "$CONTAINER" >&2 || true
    exit 1
fi

docker exec "$CONTAINER" solr create -c intermine-autocomplete
core_exists intermine-autocomplete
echo "Solr cores intermine-search and intermine-autocomplete are ready"

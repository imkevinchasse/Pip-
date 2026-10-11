#!/usr/bin/env bash
# usage: publish.sh <name-on-branch> <local-file>   (adds/updates one file on the lab-results branch)
set -euo pipefail
NAME="$1"; FILE="$2"
URL="https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
rm -rf /tmp/pub && mkdir -p /tmp/pub && cd /tmp/pub
git init -q
git config user.name "pip-lab"; git config user.email "pip-lab@users.noreply.github.com"
git remote add origin "$URL"
if git fetch -q --depth 1 origin lab-results 2>/dev/null; then git checkout -q -b lab-results FETCH_HEAD; else git checkout -q -b lab-results; fi
cp "$FILE" "/tmp/pub/$NAME"
git add "$NAME"
git commit -q -m "lab: $NAME ${GITHUB_RUN_ID:-}" || true
git push -q origin lab-results

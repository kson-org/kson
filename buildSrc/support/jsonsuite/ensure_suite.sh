#!/usr/bin/env bash

CURRENT_TEST_SHA="d64aefb55228d9584d3e5b2433f720ea8fd00c82"

echo "[ensure_suite.sh] INFO: Preparing JSONTestSuite (https://github.com/nst/JSONTestSuite)"
echo "[ensure_suite.sh]       source for Kson compatibility validation and generation of JsonSuiteTest"

cd "$(dirname "$0")" || exit 1

DIRECTORY="JSONTestSuite/"
if [ ! -d "$DIRECTORY" ]; then
  echo "[ensure_suite.sh] INFO: JSONTestSuite git repo not found, cloning..."
  git clone git@github.com:nst/JSONTestSuite.git
fi

cd "$DIRECTORY" || exit 1

pwd

# git status should be clean now... bail if not
if ! (git add . && git diff-index --quiet HEAD); then
  git status
  echo "[ensure_suite.sh] ERROR: Dirty 'buildSrc/src/test/json/JSONTestSuite/' directory.  Delete the directory and"
  echo "[ensure_suite.sh]        re-run this script"
  exit 1
fi

echo "[ensure_suite.sh] INFO: Ensuring correct testing SHA is checked out (to change testing SHA,"
echo "[ensure_suite.sh]       update CURRENT_TEST_SHA in this script)"
git fetch origin
git -c advice.detachedHead=false checkout "$CURRENT_TEST_SHA"

echo "[ensure_suite.sh] INFO: JSONTestSuite source preparation complete"

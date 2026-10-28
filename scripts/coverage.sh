#!/usr/bin/env sh
# Runs both coverage suites and prints one summary. Coverage is report-only: this script never
# turns a low percentage into a failure.
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

npm --prefix "$repo_root/client/guigu-oa-client" run coverage

(
  cd "$repo_root/server/guigu-oa-parent"
  mvn -B test
)

node "$repo_root/scripts/coverage-summary.mjs"

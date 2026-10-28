#!/usr/bin/env sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

npm --prefix "$repo_root/client/guigu-oa-client" run verify

(
  cd "$repo_root/server/guigu-oa-parent"
  mvn -pl service-oa -am test
)

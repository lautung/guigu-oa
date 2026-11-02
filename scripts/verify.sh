#!/usr/bin/env sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

npm --prefix "$repo_root/client/guigu-oa-client" run verify

(
  cd "$repo_root/server/lautung-oa-parent"
  mvn -pl lautung-oa-server -am test
)

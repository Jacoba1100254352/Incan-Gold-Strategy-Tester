#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
scratch_root="${TMPDIR:-/tmp}/incan-gold-strategy-tester"

mkdir -p "$scratch_root/build" "$scratch_root/project-cache"

INCAN_GOLD_BUILD_DIR="$scratch_root/build" \
    "$project_dir/gradlew" \
    --project-dir "$project_dir" \
    --project-cache-dir "$scratch_root/project-cache" \
    --no-daemon \
    --build-cache \
    --configuration-cache \
    clean check

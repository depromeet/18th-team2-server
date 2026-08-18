#!/usr/bin/env bash
set -euo pipefail

SCRIPT=$1   # 예: sse-loadtest.js
BIN=$2      # 예: ./loadtest/k6-sse 또는 k6

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$ROOT_DIR/results"

NAME=$(basename "$SCRIPT" .js)

for VUS in 50 100 200 500 1000; do
  echo "=== VUS=$VUS ($SCRIPT) ==="
  VUS=$VUS "$BIN" run \
    --summary-export="$ROOT_DIR/results/${NAME}-${VUS}.json" \
    "$ROOT_DIR/$SCRIPT"
done

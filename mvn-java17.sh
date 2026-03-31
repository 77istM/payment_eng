#!/usr/bin/env bash
set -euo pipefail

# Convenience wrapper so both commands work from repo root:
#   bash mvn-java17.sh test
#   bash scripts/mvn-java17.sh test

exec bash scripts/mvn-java17.sh "$@"

#!/usr/bin/env bash
set -euo pipefail

# Run Maven with Java 17+ even when the default terminal Java is older.
# Usage:
#   bash scripts/mvn-java17.sh test
#   bash scripts/mvn-java17.sh verify
#   bash scripts/mvn-java17.sh -q test

required_major=17

java_major() {
  local version_output
  version_output="$1"
  # Handles formats like:
  #   openjdk version "17.0.12" ...
  #   openjdk version "11.0.14" ...
  local version_token
  version_token="$(echo "$version_output" | awk -F '"' '/version/ {print $2; exit}')"
  if [[ -z "$version_token" ]]; then
    echo 0
    return
  fi
  echo "$version_token" | awk -F. '{ if ($1 == 1) print $2; else print $1 }'
}

ensure_java17() {
  # If current Java is already 17+, keep it.
  if command -v java >/dev/null 2>&1; then
    local current_major
    current_major=$(java_major "$(java -version 2>&1 || true)")
    if [[ "$current_major" -ge "$required_major" ]]; then
      return
    fi
  fi

  # Try common JDK install locations.
  local candidates=(
    /usr/lib/jvm/msopenjdk-21-amd64
    /usr/lib/jvm/msopenjdk-17-amd64
    /usr/lib/jvm/java-21-openjdk-amd64
    /usr/lib/jvm/java-17-openjdk-amd64
    /opt/java/openjdk
    /opt/java/openjdk-17
  )

  # Include sdkman candidates when available.
  if [[ -d "$HOME/.sdkman/candidates/java" ]]; then
    while IFS= read -r sdk_java; do
      candidates+=("$sdk_java")
    done < <(find "$HOME/.sdkman/candidates/java" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -r)
  fi

  local java_home_candidate
  for java_home_candidate in "${candidates[@]}"; do
    if [[ -x "$java_home_candidate/bin/java" ]]; then
      local found_major
      found_major=$(java_major "$("$java_home_candidate/bin/java" -version 2>&1 || true)")
      if [[ "$found_major" -ge "$required_major" ]]; then
        export JAVA_HOME="$java_home_candidate"
        export PATH="$JAVA_HOME/bin:$PATH"
        return
      fi
    fi
  done

  cat <<'EOF'
ERROR: Java 17+ was not found.

Install Java 17 and then re-run:
  bash scripts/mvn-java17.sh test

Helpful commands to inspect Java candidates:
  update-alternatives --list java
  ls -d /usr/lib/jvm/*
EOF
  exit 1
}

ensure_java17

if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: mvn was not found on PATH." >&2
  exit 1
fi

echo "Using JAVA_HOME=$JAVA_HOME"
java -version

echo "Running: mvn $*"
mvn "$@"

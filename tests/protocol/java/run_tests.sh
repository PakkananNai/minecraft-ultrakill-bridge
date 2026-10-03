#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# Discover Java 21 JDK
JAVA_HOME="${JAVA_HOME:-/home/pakkanannai/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/java/java-runtime-delta}"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"

if [ ! -x "$JAVAC" ]; then
    # Fallback to system javac if available
    JAVAC="$(which javac || true)"
    JAVA="$(which java || true)"
fi

if [ -z "$JAVAC" ] || [ ! -x "$JAVAC" ]; then
    echo "Error: javac not found at $JAVA_HOME or system PATH" >&2
    exit 1
fi

echo "Using Java Compiler: $JAVAC"
"$JAVAC" -version

OUT_DIR="$SCRIPT_DIR/bin"
mkdir -p "$OUT_DIR"

SOURCES=$(find "$REPO_ROOT/shared/protocol/java" "$SCRIPT_DIR" -name "*.java")

echo "=== Compiling Java Protocol Unit Tests ==="
"$JAVAC" -d "$OUT_DIR" -sourcepath "$REPO_ROOT/shared/protocol/java:$SCRIPT_DIR" $SOURCES

echo "=== Executing Java Protocol Unit Tests ==="
"$JAVA" -cp "$OUT_DIR" tests.protocol.java.ProtocolTests

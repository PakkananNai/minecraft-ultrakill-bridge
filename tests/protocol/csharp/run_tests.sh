#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

GAME_DIR="${GAME_DIR:-/home/pakkanannai/Downloads/ULTRAKILL.v2026.04.25/ULTRAKILL.v2026.04.25}"
MANAGED_DIR="$GAME_DIR/ULTRAKILL_Data/Managed"
OUT_DIR="$SCRIPT_DIR/bin"
OUT_EXE="$OUT_DIR/ProtocolTests.exe"

mkdir -p "$OUT_DIR"

# Ensure Wine drive mappings
mkdir -p "$HOME/.wine/dosdevices"
ln -sfn "$REPO_ROOT" "$HOME/.wine/dosdevices/w:"

SOURCES=$(find "$REPO_ROOT/shared/protocol/csharp" "$SCRIPT_DIR" -name "*.cs")

W_SOURCES=()
for src in $SOURCES; do
    rel="${src#$REPO_ROOT/}"
    rel_win=$(echo "$rel" | tr '/' '\\')
    W_SOURCES+=("W:\\$rel_win")
done

MANAGED_W="Z:\\ULTRAKILL.v2026.04.25\\ULTRAKILL.v2026.04.25\\ULTRAKILL_Data\\Managed"
W_OUTPUT="W:\\tests\\protocol\\csharp\\bin\\ProtocolTests.exe"
WINE_MCS="$HOME/.wine/drive_c/windows/mono/mono-2.0/lib/mono/4.5/mcs.exe"

echo "=== Compiling C# Protocol Unit Tests ==="
wine "$WINE_MCS" \
    -target:exe \
    -nostdlib \
    -noconfig \
    -out:"$W_OUTPUT" \
    -r:"$MANAGED_W\\mscorlib.dll" \
    -r:"$MANAGED_W\\netstandard.dll" \
    -r:"$MANAGED_W\\System.dll" \
    -r:"$MANAGED_W\\System.Core.dll" \
    "${W_SOURCES[@]}"

echo "=== Executing C# Protocol Unit Tests via Wine ==="
wine "$W_OUTPUT"

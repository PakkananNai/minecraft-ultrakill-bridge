#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

GAME_DIR="${GAME_DIR:-/home/pakkanannai/Downloads/ULTRAKILL.v2026.04.25/ULTRAKILL.v2026.04.25}"
BEPINEX_CORE="$GAME_DIR/BepInEx/core"
MANAGED_DIR="$GAME_DIR/ULTRAKILL_Data/Managed"
PLUGINS_DIR="$GAME_DIR/BepInEx/plugins"
OUTPUT_DIR="$REPO_ROOT/bin"
OUTPUT_DLL="$OUTPUT_DIR/MinecraftBridge.dll"

echo "=== Building MinecraftBridge Host Plugin ==="
echo "Repo Root:   $REPO_ROOT"
echo "Game Dir:    $GAME_DIR"
echo "Output:      $OUTPUT_DLL"

mkdir -p "$OUTPUT_DIR"

# Verify required assemblies exist
for dll in "$BEPINEX_CORE/BepInEx.Core.dll" \
           "$BEPINEX_CORE/BepInEx.Unity.Mono.dll" \
           "$MANAGED_DIR/netstandard.dll" \
           "$MANAGED_DIR/mscorlib.dll" \
           "$MANAGED_DIR/UnityEngine.CoreModule.dll" \
           "$MANAGED_DIR/UnityEngine.UIModule.dll" \
           "$MANAGED_DIR/UnityEngine.ScreenCaptureModule.dll" \
           "$MANAGED_DIR/Unity.InputSystem.dll" \
           "$MANAGED_DIR/UnityEngine.UI.dll" \
           "$MANAGED_DIR/UnityEngine.dll"; do
    if [ ! -f "$dll" ]; then
        echo "Error: Missing required assembly: $dll" >&2
        exit 1
    fi
done

# Ensure Wine drive mappings for workspace (W:)
mkdir -p "$HOME/.wine/dosdevices"
ln -sfn "$REPO_ROOT" "$HOME/.wine/dosdevices/w:"

# Find all C# source files
SOURCES=$(find "$REPO_ROOT/host/MinecraftBridge" -name "*.cs" -print; find "$REPO_ROOT/shared/protocol/csharp" -maxdepth 1 -name "*.cs" -print)
if [ -z "$SOURCES" ]; then
    echo "Error: No C# source files found in host/MinecraftBridge" >&2
    exit 1
fi

# Convert paths to Wine-compatible paths
W_OUTPUT="W:\\bin\\MinecraftBridge.dll"
MANAGED_W="Z:\\ULTRAKILL.v2026.04.25\\ULTRAKILL.v2026.04.25\\ULTRAKILL_Data\\Managed"
BEPINEX_W="Z:\\ULTRAKILL.v2026.04.25\\ULTRAKILL.v2026.04.25\\BepInEx\\core"

# Build source file arguments for Wine
W_SOURCES=()
for src in $SOURCES; do
    rel="${src#$REPO_ROOT/}"
    rel_win=$(echo "$rel" | tr '/' '\\')
    W_SOURCES+=("W:\\$rel_win")
done

echo "Compiling with Wine Mono C# Compiler (mcs)..."
WINE_MCS="$HOME/.wine/drive_c/windows/mono/mono-2.0/lib/mono/4.5/mcs.exe"

wine "$WINE_MCS" \
    -target:library \
    -nostdlib \
    -noconfig \
    -unsafe \
    -out:"$W_OUTPUT" \
    -r:"$MANAGED_W\\mscorlib.dll" \
    -r:"$MANAGED_W\\netstandard.dll" \
    -r:"$MANAGED_W\\System.dll" \
    -r:"$MANAGED_W\\System.Core.dll" \
    -r:"$BEPINEX_W\\BepInEx.Core.dll" \
    -r:"$BEPINEX_W\\BepInEx.Unity.Mono.dll" \
    -r:"$MANAGED_W\\UnityEngine.CoreModule.dll" \
    -r:"$MANAGED_W\\UnityEngine.UIModule.dll" \
    -r:"$MANAGED_W\\UnityEngine.ScreenCaptureModule.dll" \
    -r:"$MANAGED_W\\Unity.InputSystem.dll" \
    -r:"$MANAGED_W\\UnityEngine.UI.dll" \
    -r:"$MANAGED_W\\UnityEngine.dll" \
    "${W_SOURCES[@]}"

if [ -f "$OUTPUT_DLL" ]; then
    echo "Build SUCCESSFUL: $OUTPUT_DLL"
    file "$OUTPUT_DLL"
else
    echo "Build FAILED: $OUTPUT_DLL was not created." >&2
    exit 1
fi

# Deploy if requested
if [[ "${1:-}" == "--deploy" ]]; then
    echo "Deploying to $PLUGINS_DIR..."
    mkdir -p "$PLUGINS_DIR"
    cp -v "$OUTPUT_DLL" "$PLUGINS_DIR/MinecraftBridge.dll"
    echo "Deployment complete: $PLUGINS_DIR/MinecraftBridge.dll"
fi

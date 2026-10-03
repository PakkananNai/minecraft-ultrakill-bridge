#!/usr/bin/env bash
set -euo pipefail

CSharpDir="tools/cross_lang_test"
CSharpExe="$CSharpDir/CSharpSerialize.exe"
CSharpOutDir="$CSharpDir/csharp_packets"
JavaOutDir="$CSharpDir/java_packets"
JavaSrc="$CSharpDir/JavaCrossTest.java"
JavaClassesDir="$CSharpDir/classes"

mkdir -p "$CSharpOutDir" "$JavaOutDir" "$JavaClassesDir"

echo "=== Generating C# packets ==="
wine "$HOME/.wine/drive_c/windows/mono/mono-2.0/lib/mono/4.5/mono.exe" "$CSharpExe"

echo "=== Compiling Java cross-language test ==="
javac -d "$JavaClassesDir" $(find shared/protocol/java/com/bridge/minecraft/protocol -name "*.java") "$JavaSrc"

echo "=== Running Java cross-language test ==="
java -cp "$JavaClassesDir" com.bridge.minecraft.protocol.JavaCrossTest

echo "=== Verifying packet compatibility ==="
PASS=1
for csharpFile in "$CSharpOutDir"/*.bin; do
    base=$(basename "$csharpFile")
    javaFile="$JavaOutDir/$base"
    if ! cmp -s "$csharpFile" "$javaFile"; then
        echo "[FAIL] Mismatch in $base"
        PASS=0
    else
        echo "[OK] $base matches"
    fi
done

if [ $PASS -eq 1 ]; then
    echo "Milestone 2: PASS"
else
    echo "Milestone 2: FAIL"
fi

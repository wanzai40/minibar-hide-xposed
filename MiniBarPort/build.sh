#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

JAVA_HOME="${JAVA_HOME:-/home/user/jdk-17.0.21+9}"
ANDROID_JAR="${ANDROID_JAR:-/home/user/android-sdk/platform33/android-13/android.jar}"
BUILD_TOOLS="${BUILD_TOOLS:-/home/user/android-sdk/bt34/android-14}"
AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
KEYTOOL="$JAVA_HOME/bin/keytool"

OUT="build_release"
CLASSES_DIR="$OUT/classes"
GEN_DIR="$OUT/gen"
DEX_DIR="$OUT/dex"
UNSIGNED_APK="$OUT/unsigned.apk"
ALIGNED_APK="$OUT/aligned.apk"
KEYSTORE="$OUT/debug.keystore"
FINAL_APK="$OUT/小窗提示线移植-2.4.apk"

rm -rf "$OUT"
mkdir -p "$CLASSES_DIR" "$GEN_DIR" "$DEX_DIR"

# 1. Compile and link Android resources.
"$AAPT2" compile --dir app/src/main/res -o "$OUT/res.zip"
"$AAPT2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest app/src/main/AndroidManifest.xml \
  -R "$OUT/res.zip" \
  --java "$GEN_DIR" \
  --auto-add-overlay

# 2. Compile Java sources.
find "$GEN_DIR" app/src/main/java -name '*.java' > "$OUT/sources.txt"
"$JAVA_HOME/bin/javac" \
  -source 8 -target 8 \
  -encoding UTF-8 \
  -cp "$ANDROID_JAR:app/libs/libxposed-api-101.0.1.jar" \
  -d "$CLASSES_DIR" \
  @"$OUT/sources.txt"

# 3. Convert bytecode to DEX. Xposed API is provided by the framework at runtime.
"$D8" \
  --release \
  --min-api 26 \
  --output "$DEX_DIR" \
  --lib "$ANDROID_JAR" \
  $(find "$CLASSES_DIR" -name '*.class')

# 4. Add classes.dex and Xposed metadata to the resource-only APK.
cp "$OUT/base.apk" "$UNSIGNED_APK"
python3 - "$UNSIGNED_APK" "$DEX_DIR/classes.dex" app/src/main/resources <<'PY'
import sys
import zipfile

apk, dex, resources_dir = sys.argv[1:4]
with zipfile.ZipFile(apk, 'a', compression=zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
    for name in (
        'META-INF/xposed/java_init.list',
        'META-INF/xposed/module.prop',
        'META-INF/xposed/scope.list',
    ):
        z.write(f'{resources_dir}/{name}', name)
PY

# 5. Align and sign.
"$ZIPALIGN" -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"
if [ ! -f "$KEYSTORE" ]; then
  "$KEYTOOL" -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname 'CN=Android Debug,O=Android,C=US'
fi
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$FINAL_APK" \
  "$ALIGNED_APK"

echo "Build completed: $FINAL_APK"

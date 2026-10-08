#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
TASK_JAVA="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
TOOLS="$TASK_SDK/build-tools/35.0.0"
PLATFORM="$TASK_SDK/platforms/android-35/android.jar"
export JAVA_HOME="$TASK_JAVA"
export PATH="$TASK_JAVA/bin:$PATH"
for executable in "$TOOLS/aapt2" "$TOOLS/d8" "$TOOLS/zipalign" "$TOOLS/apksigner" "$TASK_JAVA/bin/javac"; do
    if [ ! -x "$executable" ]; then echo "Missing tool: $executable" >&2; exit 1; fi
done
mkdir -p build/generated build/classes build/dex build/tests signing dist
"$TASK_JAVA/bin/javac" -encoding UTF-8 -d build/tests app/src/main/java/pl/spokojniej/app/Streaks.java tests/StreaksTest.java
"$TASK_JAVA/bin/java" -cp build/tests StreaksTest
"$TOOLS/aapt2" compile --dir app/src/main/res -o build/resources.zip
# AGP supplies namespace; standalone aapt2 needs package on the generated manifest.
sed 's/<manifest /<manifest package="pl.spokojniej.app" /' app/src/main/AndroidManifest.xml > build/AndroidManifest.xml
"$TOOLS/aapt2" link -o build/resources.apk -I "$PLATFORM" --manifest build/AndroidManifest.xml --java build/generated build/resources.zip
find app/src/main/java build/generated -name '*.java' > build/sources.txt
"$TASK_JAVA/bin/javac" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLATFORM:$TOOLS/core-lambda-stubs.jar" -d build/classes @build/sources.txt
find build/classes -name '*.class' > build/classes.txt
"$TOOLS/d8" --lib "$PLATFORM" --min-api 26 --output build/dex @build/classes.txt
cp build/resources.apk build/unsigned.apk
(cd build/dex && zip -q -j ../unsigned.apk classes*.dex)
"$TOOLS/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [ ! -f signing/local.keystore ]; then
    umask 077
    "$TASK_JAVA/bin/keytool" -genkeypair -keystore signing/local.keystore -storepass local-spokojniej -keypass local-spokojniej -alias spokojniej -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Spokojniej Local, O=Spokojniej, C=PL"
fi
"$TOOLS/apksigner" sign --ks signing/local.keystore --ks-key-alias spokojniej --ks-pass pass:local-spokojniej --key-pass pass:local-spokojniej --out dist/Spokojniej-1.2.0.apk build/aligned.apk
"$TOOLS/apksigner" verify --verbose dist/Spokojniej-1.2.0.apk
"$TOOLS/aapt" dump badging dist/Spokojniej-1.2.0.apk > dist/apk-info-1.2.0.txt
shasum -a 256 dist/Spokojniej-1.2.0.apk > dist/SHA256SUMS-1.2.0
echo 'APK: dist/Spokojniej-1.2.0.apk'

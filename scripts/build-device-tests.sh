#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
TASK_JAVA="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
TOOLS="$TASK_SDK/build-tools/35.0.0"
PLATFORM="$TASK_SDK/platforms/android-35/android.jar"
export JAVA_HOME="$TASK_JAVA"
export PATH="$TASK_JAVA/bin:$PATH"
mkdir -p build/device-tests/classes build/device-tests/dex
"$TOOLS/aapt2" link -o build/device-tests/resources.apk -I "$PLATFORM" --manifest tests/android/AndroidManifest.xml
"$TASK_JAVA/bin/javac" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLATFORM:$TOOLS/core-lambda-stubs.jar" -cp build/classes -d build/device-tests/classes tests/android/*.java
find build/device-tests/classes -name '*.class' > build/device-tests/classes.txt
"$TOOLS/d8" --lib "$PLATFORM" --classpath build/classes --min-api 26 --output build/device-tests/dex @build/device-tests/classes.txt
cp build/device-tests/resources.apk build/device-tests/unsigned.apk
(cd build/device-tests/dex && zip -q -j ../unsigned.apk classes*.dex)
"$TOOLS/zipalign" -f -p 4 build/device-tests/unsigned.apk build/device-tests/aligned.apk
"$TOOLS/apksigner" sign --ks signing/local.keystore --ks-key-alias spokojniej --ks-pass pass:local-spokojniej --key-pass pass:local-spokojniej --out build/device-tests/checks.apk build/device-tests/aligned.apk

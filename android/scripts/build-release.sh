#!/bin/sh
set -eu
# The external file contains the private upload key path/passwords; never copy it into the project.
OFFZONE_SIGNING_PROPERTIES=${OFFZONE_SIGNING_PROPERTIES:-"$HOME/.config/offzone/credentials/android-signing.properties"}
export OFFZONE_SIGNING_PROPERTIES
[ -f "$OFFZONE_SIGNING_PROPERTIES" ] || { echo "Missing external Offzone signing properties." >&2; exit 1; }
cd "$(dirname "$0")/.."
rtk proxy ./gradlew --no-configuration-cache :app:assembleRelease :app:bundleRelease
version=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
mkdir -p ../output/android-release
cp app/build/outputs/apk/release/app-release.apk "../output/android-release/offzone-$version-release.apk"
cp app/build/outputs/bundle/release/app-release.aab "../output/android-release/offzone-$version-release.aab"

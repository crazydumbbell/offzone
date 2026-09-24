# Android release signing

Run from the project root:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' android/scripts/build-release.sh
```

The script creates signed APK/AAB files in `output/android-release/`. It does not upload or publish them. Keep its artifact filenames aligned with the app version when increasing `versionName`.

Gradle reads `~/.config/offzone/credentials/android-signing.properties` (or the path in `OFFZONE_SIGNING_PROPERTIES`). The properties contain `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`; the dedicated PKCS12 upload key is `~/.config/offzone/credentials/android-upload.p12`. Keep these private files outside the repository and backed up securely. Both files have mode `600`, their directory `700`. Do not regenerate or replace the key for future updates.

`output/android-release/upload-certificate.pem` and its fingerprint report are public. Google Play App Signing uses a separate app-signing certificate: the existing current Play SHA1/SHA256 and previous Play SHA256 have also been registered with Firebase (see `firebase-certificates.json`). Verify any future key rotation separately. APKs built locally use the upload certificate.

Release verification evidence is in `output/android-release/`: APK `apksigner`, trusted-keystore strict AAB `jarsigner`, official `bundletool validate`, dumped manifest, and artifact SHA-256 values. AGP places the AAB JAR manifest after other entries, which produces a JarInputStream ordering warning in JDK jarsigner; strict JarFile signature verification and bundle validation pass.

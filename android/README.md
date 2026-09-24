# Offzone Android

Kotlin + Jetpack Compose implementation beside the iOS app. Android8.0+ (API26), target36. Open this folder in Android Studio.

Debug package: `com.exchip.offzone.debug`. Release package: `com.exchip.roomdns`, matching the existing Play draft. Public Firebase and RevenueCat client configuration is in gradle.properties; no service-account key belongs in the app. Release signing is configured outside the repository; see `scripts/RELEASE.md`. Publication remains pending.

Implemented: goal/window onboarding, saved/applied place rules, app selection, daily and overnight schedules, precise arrival confirmation and explicit focus, optional user-started place monitoring, AccessibilityService shield and free recovery, quick timed focus, Firebase email/Google/Apple account flows, verified goal sync, RevenueCat identity/expiry/purchase/restore flows, private plans/reflections/weekly summaries, JSON import/export/share, English/Korean SUIT UI and **static adult Rue v6** (8 expressions + 4 full-body resources; currently rendered on onboarding/home). Historical Kiwi resources are archived outside the app; the Android debug APK no longer contains Kiwi. Three additional outfit PNGs are concept studies, **not** dressable/rewarded/purchasable features. See `.growth-design/mascots/2026-09-24-rue-wardrobe/WARDROBE_PLAN.md` from the repo root. Color-fringe/rights/device QA remains before release.

`PRO_OFFER_READY` and `UNLOCK_PASS_READY` remain false, matching iOS. Existing Pro access is checked by verified account UID and expiry. No test entitlement or fake purchase unlock is included in release code. Real Google/Apple OAuth and Play purchases still require live-device/store validation.

## Build

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

Pinned Gradle8.14 wrapper, AGP8.13.2 and Kotlin2.3.21. Firebase34.19.0 requires Kotlin2.3 metadata support. DebugAPK: `app/build/outputs/apk/debug/app-debug.apk`.

## Testing

Instrumentation changes only the isolated Android emulator and restores preferences/settings. Install both APKs, grant precise location to the debug app for place tests, then run AndroidJUnitRunner. See `MIGRATION.md` and `../output/android-migration/` for actual results.

Auth test additionally requires:

```sh
cd ..
firebase emulators:start --config firebase.android-test.json --project demo-offzone-android --only auth,firestore
```

Pass instrumentation argument `offzoneAuthEmulator=true`; test uses a named demo Firebase app with billing disabled and disposable emulator accounts. Debug alone allows HTTP for the localhost emulator; release does not.

## Platform behavior

- Accessibility sees package-change events only; no screen content, typed text or browsing history is read. Separate disclosure precedes system permission.
- Essential system apps, default calls/SMS, keyboard, launcher and Offzone remain available. Some preinstalled entertainment apps are selectable.
- Arrival does not automatically block apps. Start requires a fresh precise fix and an active daily window. Exit, stale/lost location, permission loss, schedule end and free restore release restrictions.
- Place focus and monitoring show an explicit ongoing-location notice before permissions or service start; Not now cancels and stale permission/consent requests cannot start monitoring.
- Optional monitoring is started visibly by the user, displays a foreground notification, and can be stopped freely. No background location permission, exact alarm, auto-start after reboot or paid recovery.
- Android can revoke permissions, stop the app, or uninstall it. Split-screen/PiP may limit shielding; Android has no consumer Screen Time uninstall restriction or Apple category/web-domain selection tokens.
- Rules/selection/journal stay local and are excluded from backup/device transfer. Account goal alone is stored in Firestore. Use journal JSON export to move existing iOS records; Android app packages must be selected again.
- Native Geocoder handles place search. Optional map uses bundled Leaflet1.9.4 and OpenStreetMap tiles with attribution, identified User-Agent and normal HTTP caching; no offline/prefetch. Map privacy notice precedes loading.
- Animation uses the approved source motion with alpha. API26–27 and disabled motion use static artwork.

Physical Pixel/Samsung, TalkBack, battery/lock/unlock/multiwindow, production OAuth and Play billing remain release checks. The public privacy/support site now includes Android (v5); Play declarations still need to match Android accessibility, foreground location and the optional OpenStreetMap provider before release. No store submission or real purchase is implied by a passing local test.

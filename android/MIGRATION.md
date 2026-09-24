# Android migration verification

## 2026-09-24 D-43 Rue v6 visual migration (after the original Kiwi tests)

The native `KiwiView`/WebP/vectors were replaced with `RueView` static PNGs; `OnboardingScreen` displays Rue Welcome and `MainActivity` Ready/Focused. All 12 resources decode with alpha; `OnboardingRueTest` 2/2 passed on the Offzone_Dev emulator, debug/test APKs build offline, current debug APK contains 12 Rue PNG and **no Kiwi resource**. Initial emulator screen briefly had a **System UI isn't responding** prompt, dismissed with Wait; app first screen rendered afterward. No Android physical-device, transparent-edge cleanup/rights or signed release/Play deployment verified for this change. Three other outfits are concept art only, no user inventory/payments. The old table, test command and 10-test count below record the **previous Kiwi migration**, not this Rue change; run `OnboardingRueTest` in new focused suites instead.

Native Kotlin/Compose companion to the current SwiftUI app. Local verification completed 2026-09-24: debug APK and signed release APK/AAB built, unit3 + functional instrumentation10 passed with no skips, lint0 errors/49 warnings. Implementation is not a claim of store release or physical-device parity.

| iOS behavior | Android implementation | Verification |
|---|---|---|
| Goal/window onboarding | OnboardingScreen, persisted goal/window | Onboarding persistence and full UI flow passed |
| Saved rules vs applied snapshot | RuleStore, explicit apply/pause/edit/delete | RuleRuntimeTest passed |
| Daily/overnight schedules | RulePolicy, wall-clock end plus monotonic session cap | RulePolicyTest passed |
| Search/place pin/current location | Native Geocoder, optional bundled Leaflet map, precise LocationManager fix | Seoul center, nonzero viewport, 6/6 map tiles and attribution verified |
| Arrival plus manual Start focus | Fresh precise fix, 150 m entry/200 m exit hysteresis, FGS while focused | PlaceFocusFlowTest passed |
| App shielding/free restore | Package-only AccessibilityService, native overlay, essential-app exclusions | FocusFlowTest passed |
| Email/Google/Apple account | Firebase SDK, explicit link, verify/reset, reauth/delete | Auth emulator lifecycle passed; real OAuth not yet tested |
| Verified goal sync | Same Firestore users/<uid> contract | Auth + Firestore emulator passed |
| Pro entitlement and purchase/restore | RevenueCat Android SDK, same UID/expiry gates, original store management | SDK debug/release compiled; Play purchases not yet validated |
| Plans/reflections/weekly summaries | JournalStore/JournalScreen, live Pro write checks | JournalStoreTest passed |
| Free read/delete/export | Local JSON, SAF export/share, strict merge-only iOS JSON import | JournalStoreTest passed |
| Kiwi motion/8 expressions | Original alpha animation converted to animated WebP, native vectors | Animated WebP decode passed |
| English/Korean | Complete native strings/SUIT fonts | 237 English/Korean keys matched; Korean 1.5x UI passed |

## OS differences

Android has no consumer equivalent of Apple Family Controls application/category/web-domain tokens or denyAppRemoval. Users select Android packages again; iOS application tokens cannot be migrated. No invasive website/content scraping, device-owner enrollment, uninstall obstruction, or paid emergency exit is introduced. Android can revoke accessibility/location, stop a service, or uninstall this app. Blocking ends on process death/reboot. Multiwindow/PiP and unlock behavior need physical-device validation.

Location checks occur on explicit arrival/start actions, during active focus, and optional user-started foreground monitoring with a visible notification. Monitoring can notify arrival/exit without automatically blocking. No automatic focus on arrival/reentry or reboot. API26–27 shows static Kiwi because AnimatedImageDrawable requires28.

## Console registrations (2026-09-24)

- Firebase project roomdns-exchip: debug com.exchip.offzone.debug / app b484e4679cd99a9ef212c9; debug SHA1 + SHA256 registered; public web OAuth client obtained from generated SDK config.
- Existing Play draft uses com.exchip.roomdns. Release build follows that identifier; Firebase Android Play app90a67f18aa795dabf212c9 registered. Provisional com.exchip.offzone Firebase registration870790002857b834f212c9 remains unused, not deleted.
- RevenueCat Offzone project b8a02e7b, new Android app app8437b9dc01. Public Android SDK key configured. Dedicated app-scoped Play service account saved; catalog/base-plan access verified and RevenueCat RTDN connected. The first bundle is now uploaded and monthly/annual products mapped as drafts; subscription-purchase API validation and real purchases remain release gates.
- Upload-key and current Play app-signing SHA1/SHA256 plus previous Play SHA256 are registered with Firebase, verified by API readback. Production Google/Apple OAuth is not yet tested.
- Public privacy/support v5 deployed and HTTP200 verified, including Android data handling and `/support#account-deletion`.
- User confirmed no physical Android device is currently available (2026-09-24); physical QA remains pending.
- PRO_OFFER_READY=false and UNLOCK_PASS_READY=false, matching iOS. No purchase, production account mutation, signing-key replacement, publication, or review submission performed.

## Local checks

Use Android Studio JBR and Gradle wrapper. `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug`.
Auth integration uses only demo-offzone-android via firebase.android-test.json; pass instrumentation arg offzoneAuthEmulator=true. Place tests require precise location permission on the isolated emulator; skipped tests must not be reported as passed.

### Reproduce emulator checks

Use a dedicated emulator with no real focus session. After installing debug and test APKs:

```sh
adb -s emulator-5554 shell pm grant com.exchip.offzone.debug android.permission.ACCESS_COARSE_LOCATION
adb -s emulator-5554 shell pm grant com.exchip.offzone.debug android.permission.ACCESS_FINE_LOCATION
adb -s emulator-5554 shell am instrument -w -e class com.exchip.offzone.FocusFlowTest,com.exchip.offzone.PlaceFocusFlowTest,com.exchip.offzone.RuleRuntimeTest com.exchip.offzone.debug.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class com.exchip.offzone.MigrationUiTest,com.exchip.offzone.JournalStoreTest,com.exchip.offzone.OnboardingRueTest com.exchip.offzone.debug.test/androidx.test.runner.AndroidJUnitRunner
```

With the isolated Firebase emulators running, authenticate only the named demo project:

```sh
adb -s emulator-5554 shell am instrument -w -e offzoneAuthEmulator true -e class com.exchip.offzone.AccountStoreAuthIntegrationTest com.exchip.offzone.debug.test/androidx.test.runner.AndroidJUnitRunner
```

No test entitlement is injected into production code. These tests do not validate production OAuth or a real Play purchase. Debug APK is signed with the local debug certificate; the release APK/AAB use a dedicated external upload key. Strict JAR/APK signatures and official bundletool validation passed; see `scripts/RELEASE.md` and `../output/android-release/`.

Final proof: `../output/android-migration/` contains APK/AAB with SHA256SUMS, build/lint reports, auth-test.log, engine-instrumentation.txt (3 passed), migration-ui-final.log (5 passed,65.491s), and actual ui-captures. Earlier test-harness failures and map-render diagnostics are preserved under diagnostics/. The final map and Compose rule-state regressions are covered by runnable UI assertions.

## Final release-preparation checks

- LocationDisclosureTest passed (18.065s, no skips): no monitoring before choice, cancellation, stale-generation rejection and explicit Agree and continue. Both languages disclose precise ongoing checks while the app is not visible; one-shot Check arrival remains separate.
- StoreScreenshotTest passed (86.319s). `../output/android-store/` has six genuine 1080×1920 RGB emulator captures, dimensions/hashes, and five recommended listing images; no fake purchase/Pro entitlement.
- RevenueCat RTDN test sent from Play and received, verified in `revenuecat-connection.json`. Monthly/annual base plans are now DRAFT (USD4.99/KRW7700 monthly; USD29.99/KRW49000 annual), imported and mapped to RevenueCat offzone_pro, preserving iOS. The subscription-purchase API still reports No application after the first AAB; cause is not confirmed. Products are inactive and sales flags remain false.
- Internal release1 AAB/title/English notes saved as draft after the user enabled Chrome file access. Play accepted version1(0.1.0),API26+,target36 and reports16KB support. No blocking errors observed; warnings: no testers, no R8 mapping (R8 disabled), no native symbols. No rollout. See `play-draft.json`.
- Remaining review inputs: `play-declarations.md`, actual permission/FGS demo videos, final Play Data safety/provider classifications, product/purchase validation, production OAuth and physical-device QA. User currently cannot connect a physical phone.

- Play en-US listing draft now contains five ordered actual Android screenshots and corrected manual-focus/Accessibility description. Account deletion URL points to `/support#account-deletion`; both email/password and OAuth account methods saved. No review submission. Icon512×512 and feature graphic1024×500 still absent. `store-listing.json` records readback evidence; remaining provider/location Data safety classifications are not certified.

# Offzone

Offzone is a digital self-management app with native iOS (SwiftUI) and Android (Kotlin/Compose) clients and Firebase backend functions.

- iOS: open `RoomDNS.xcodeproj` in Xcode.
- Android: run `cd android && ./gradlew :app:assembleDebug`.
- Backend: run `cd functions && npm ci && npm test`.

The repository includes public client configuration and the current Rue artwork sources. Signing credentials, local build output, research exports, and internal handoffs stay outside Git. Store submission and device validation are separate release steps.

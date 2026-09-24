import CoreLocation
import DeviceActivity

final class RoomDNSMonitor: DeviceActivityMonitor {
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        guard activity == .roomDNS else { return }
        reconcileCurrentState()
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        guard activity == .roomDNS else { return }
        reconcileCurrentState()
    }

    private func reconcileCurrentState() {
        let manager = CLLocationManager()
        SharedState.reconcileRestrictions(locationAllowed:
            manager.authorizationStatus == .authorizedAlways && manager.accuracyAuthorization == .fullAccuracy)
    }
}

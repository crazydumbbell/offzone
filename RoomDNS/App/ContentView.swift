import CoreLocation
import FamilyControls
import MapKit
import SwiftUI
import UIKit
import UserNotifications

private enum RoomScreen: Int {
    case welcome
    case goal
    case rhythm
    case permission
    case targets
    case place
    case schedule
    case review
    case ready
    case home

    var stepNumber: Int? {
        switch self {
        case .permission: 1
        case .targets: 2
        case .place: 3
        case .schedule: 4
        case .review: 5
        default: nil
        }
    }
}

enum RoomSpiritState: Equatable {
    case welcome
    case idle
    case attentive
    case guiding
    case working
    case confirmed
    case focused
    case needsAction
    case failed
    case celebrating
    case recovered
    case returning
    case finished

    static func home(focused: Bool, needsAction: Bool, recovered: Bool = false, reaction: Self? = nil) -> Self {
        if needsAction { return .needsAction }
        if focused { return .focused }
        if recovered { return .recovered }
        return reaction ?? .idle
    }

    var allowsAmbientMotion: Bool {
        self == .idle || self == .attentive || self == .working
    }
}

struct ContentView: View {
    @ObservedObject var model: AppModel
    @EnvironmentObject private var account: AccountStore
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var screen: RoomScreen
    @State private var permissionDestination: RoomScreen = .targets
    @State private var isRequestingPermission = false
    @State private var placeMode: PlaceMode = .gps
    @State private var placeQuery = ""
    @State private var placeResults: [MKMapItem] = []
    @State private var placeSearch: MKLocalSearch?
    @State private var placeSearchID = UUID()
    @State private var placeSearchMessage: String?
    @State private var mapCenter: CLLocationCoordinate2D?
    @State private var safetyReleasePresented = false
    @State private var unlockPassPresented = false
    @State private var notificationsPresented = false
    @State private var quickFocusPresented = false
    @State private var showsSaveError = false
    @State private var ruleToDelete: FocusRule?
    @State private var roomieReaction: RoomSpiritState?
    @State private var accountPresented = false
    @State private var paywallPresented = false
    @State private var customStartTime = FocusWindow.morning.date(minutes: 12 * 60)
    @State private var readyActivationError: String?
#if DEBUG
    @State private var didPresentPreviewPaywall = false
#endif
    @AppStorage(OnboardingProfile.completedKey) private var onboardingCompleted = false
    @AppStorage(OnboardingProfile.goalKey) private var goalRaw = FocusGoal.work.rawValue
    @AppStorage(OnboardingProfile.windowKey) private var windowRaw = FocusWindow.morning.rawValue
    @AppStorage("onboarding.firstFocus") private var firstFocus = false
    @AppStorage("offer.dismissed") private var offerDismissed = false
    @AppStorage("roomie.lastVisit") private var lastVisit: Double = 0
    @State private var mapPosition: MapCameraPosition = .userLocation(
        followsHeading: false,
        fallback: .automatic
    )

    init(model: AppModel) {
        self.model = model
        _screen = State(initialValue: OnboardingProfile.shouldShowWelcome(
            hasRules: !model.rules.isEmpty, storageError: SharedState.rulesStorageError,
            completed: UserDefaults.standard.bool(forKey: OnboardingProfile.completedKey)) ? .welcome : .home)
    }

    var body: some View {
        ZStack {
            Color.roomCanvas.ignoresSafeArea()

            Group {
                switch screen {
                case .welcome:
                    welcomeScreen
                case .goal:
                    goalScreen
                case .rhythm:
                    rhythmScreen
                case .permission:
                    permissionScreen
                case .targets:
                    targetsScreen
                case .place:
                    placeScreen
                case .schedule:
                    scheduleScreen
                case .review:
                    reviewScreen
                case .ready:
                    readyScreen
                case .home:
                    homeScreen
                }
            }
            .id(screen)
            .transition(reduceMotion ? .identity : .opacity)
        }
        .tint(Color.roomAccent)
        .preferredColorScheme(.light)
        .safeAreaInset(edge: .top, spacing: 0) {
            HStack(spacing: 12) {
                if screen == .ready {
                    Button {
                        screen = .review
                    } label: {
                        Label("Back", systemImage: "chevron.left")
                    }
                    .font(.suit(.subheadline, weight: .semibold))
                    .foregroundStyle(Color.roomAccent)
                    .frame(minWidth: 44, minHeight: 44)
                }
                if !dynamicTypeSize.isAccessibilitySize {
                    Text("OFFZONE")
                        .font(.suit(.caption, weight: .bold))
                        .tracking(1.4)
                        .foregroundStyle(Color.roomInk)
                }
                Spacer(minLength: 0)
                Button {
                    if model.isFocused && account.unlockPassEnabled { unlockPassPresented = true }
                    else { safetyReleasePresented = true }
                } label: {
                    Label(model.isFocused && account.unlockPassEnabled ? roomString("End session") : roomString("Restore access"), systemImage: "arrow.counterclockwise")
                }
                .font(.suit(.subheadline, weight: .medium))
                .foregroundStyle(Color.roomInkSecondary)
                .frame(minHeight: 44)
            }
            .padding(.horizontal, 24)
            .frame(minHeight: 54)
            .background(Color.roomCanvas)
        }

        .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: screen)
        .familyActivityPicker(
            headerText: roomString("Choose apps, categories, and websites to put aside."),
            footerText: roomString("Your selection stays on this iPhone."),
            isPresented: $model.isPickerPresented,
            selection: $model.selection
        )
        .sheet(isPresented: $safetyReleasePresented) {
            SafetyReleaseView(detail: model.safetyReleaseDescription) {
                model.safetyRelease()
                roomieReaction = model.errorMessage == nil ? .recovered : nil
                screen = .home
            }
            .presentationDetents([.large])
            .presentationCornerRadius(24)
        }
        .sheet(isPresented: $unlockPassPresented) {
            UnlockPassView(model: model) {
                model.safetyRelease()
                roomieReaction = model.errorMessage == nil ? .recovered : nil
                screen = .home
            }
            .presentationCornerRadius(24)
        }
        .sheet(isPresented: $notificationsPresented) {
            ZoneNotificationSettingsView(model: model)
                .presentationCornerRadius(24)
        }
        .sheet(isPresented: $quickFocusPresented) {
            QuickFocusView(model: model)
        }
        .sheet(isPresented: $accountPresented) {
            RoomAccountView(goal: goal)
                .presentationCornerRadius(24)
        }
        .sheet(isPresented: $paywallPresented, onDismiss: { offerDismissed = true }) {
            RoomPaywallView(goal: goal)
                .presentationCornerRadius(24)
        }
        .confirmationDialog(
            "Delete rule?",
            isPresented: Binding(
                get: { ruleToDelete != nil },
                set: { if !$0 { ruleToDelete = nil } }
            ),
            titleVisibility: .visible,
            presenting: ruleToDelete
        ) { rule in
            Button("Delete rule", role: .destructive) {
                if model.isFocused && model.activeRuleID == rule.id { presentSessionEnd() }
                else { model.deleteRule(rule.id) }
                ruleToDelete = nil
            }
            Button("Cancel", role: .cancel) { ruleToDelete = nil }
        } message: { rule in
            Text(roomString("Delete “%@”? Its active restrictions will end.", rule.name))
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.refresh(); recordVisit() }
            else { roomieReaction = nil }
        }
        .task(id: scenePhase == .active && !paywallPresented) {
            // The paywall owns its offer session; foregrounding after an OS prompt must not clear its plans.
            guard scenePhase == .active && !paywallPresented else { return }
            await account.refresh()
            await account.loadOfferings()
        }
        .onAppear {
            recordVisit()
            if !model.rules.isEmpty { onboardingCompleted = true }
            if model.isFocused { firstFocus = true }
#if DEBUG
            if !didPresentPreviewPaywall && ProcessInfo.processInfo.arguments.contains("-offzonePreviewPaywall") {
                didPresentPreviewPaywall = true
                paywallPresented = true
            }
            let arguments = ProcessInfo.processInfo.arguments
            if arguments.contains("-offzonePreviewQuick") { quickFocusPresented = true }
            if arguments.contains("-offzonePreviewPlace") {
                model.beginNewRuleDraft()
                screen = .place
            }
            if arguments.contains("-offzonePreviewSchedule") {
                model.beginNewRuleDraft()
                model.days = RestrictionPolicy.weekdays
                screen = .schedule
            }
#endif
        }
        .onChange(of: screen) { _, value in
            if value != .home { roomieReaction = nil }
            if value != .ready { readyActivationError = nil }
        }
        .onChange(of: model.isFocused) { wasFocused, focused in
            if focused { firstFocus = true }
            if wasFocused && !focused && SharedState.ruleEnabled && !SharedState.scheduleActive
                && !SharedState.safetyReleased && !homeNeedsAction {
                roomieReaction = .finished
            }
        }
        .task(id: roomieReaction) {
            guard roomieReaction != nil else { return }
            do { try await Task.sleep(for: .milliseconds(850)) }
            catch { return }
            roomieReaction = nil
        }
    }

    private var welcomeScreen: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Make room for what matters.")
                        .font(.suit(dynamicTypeSize.isAccessibilitySize ? .title : .largeTitle, weight: .bold))
                        .foregroundStyle(Color.roomInk)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                    Text("Choose when and where to block distractions.")
                        .font(.suit(.body))
                        .foregroundStyle(Color.roomInkSecondary)
                }
                RoomSpirit(state: .welcome, interactive: true)
                    .frame(height: dynamicTypeSize.isAccessibilitySize ? 150 : 186)
                    .frame(maxWidth: .infinity)
                    .background(Color.roomButter)
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                Label("You choose the boundaries. You can always restore access.", systemImage: "checkmark.shield")
                    .font(.suit(.subheadline))
                    .foregroundStyle(Color.roomInkSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: 560, alignment: .leading)
            .padding(.horizontal, 24).padding(.vertical, 32)
            .frame(maxWidth: .infinity)
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            primaryButton(roomString(onboardingCompleted || !model.rules.isEmpty ? "Go to home" : "Get started")) {
                screen = onboardingCompleted || !model.rules.isEmpty ? .home : .goal
            }.bottomBarStyle()
        }
    }

    private var goal: FocusGoal { FocusGoal(rawValue: goalRaw) ?? .work }
    private var window: FocusWindow { FocusWindow(rawValue: windowRaw) ?? goal.suggestedWindow }

    private func rhythmTimeRange(_ item: FocusWindow) -> String {
        "\(item.date(minutes: item.startMinutes).formatted(date: .omitted, time: .shortened))–\(item.date(minutes: item.endMinutes).formatted(date: .omitted, time: .shortened))"
    }

    private var customTimeRange: String {
        let endMinutes = (minutes(from: customStartTime) + 60) % (24 * 60)
        let end = FocusWindow.morning.date(minutes: endMinutes)
        return "\(customStartTime.formatted(date: .omitted, time: .shortened))–\(end.formatted(date: .omitted, time: .shortened))"
    }

    private var goalScreen: some View {
        setupScreen(title: roomString("What matters most?"),
                    detail: roomString("Start with one thing that matters to you."),
                    actionTitle: roomString("Continue"), action: { screen = .rhythm }) {
            VStack(spacing: 12) {
                ForEach(FocusGoal.allCases) { item in
                    choiceRow(title: roomString(item.title), detail: roomString(item.detail),
                              symbol: item.symbol, selected: goal == item) {
                        goalRaw = item.rawValue
                        windowRaw = item.suggestedWindow.rawValue
                    }
                }
            }
        }
    }

    private var rhythmScreen: some View {
        let optionLayout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(spacing: 10))
            : AnyLayout(HStackLayout(spacing: 10))
        return setupScreen(title: roomString("When does your phone get in the way?"),
                    detail: roomString("Choose a time or use a suggestion. You can change it next."),
                    actionTitle: roomString("Build my first rule"), action: {
                        model.beginNewRuleDraft()
                        model.ruleName = roomString(goal.ruleName)
                        let draft = OnboardingProfile.draftMinutes(
                            windowRaw: windowRaw, goal: goal,
                            customStartMinutes: minutes(from: customStartTime))
                        model.startTime = window.date(minutes: draft.start)
                        model.endTime = window.date(minutes: draft.end)
                        placeMode = .gps
                        permissionDestination = .targets
                        screen = model.screenTimeAuthorized ? .targets : .permission
                    }) {
            VStack(spacing: 12) {
                optionLayout {
                    rhythmQuickChoice(title: roomString("Another time"), symbol: "clock.arrow.circlepath",
                                      selected: windowRaw == "custom") { windowRaw = "custom" }
                    rhythmQuickChoice(title: roomString("Not sure yet"), symbol: "sparkles",
                                      selected: windowRaw == "unsure") { windowRaw = "unsure" }
                }
                if windowRaw == "custom" {
                    VStack(alignment: .leading, spacing: 6) {
                        DatePicker(roomString("Start time"), selection: $customStartTime, displayedComponents: .hourAndMinute)
                            .datePickerStyle(.compact)
                        Text(customTimeRange)
                            .font(.suit(.subheadline))
                            .foregroundStyle(Color.roomInkSecondary)
                    }
                    .font(.suit(.body, weight: .semibold))
                    .padding(16)
                    .editorialPanel(cornerRadius: 16)
                } else if windowRaw == "unsure" {
                    Text(roomString("Suggested: %@. You can change it next.", rhythmTimeRange(goal.suggestedWindow)))
                        .font(.suit(.subheadline))
                        .foregroundStyle(Color.roomInkSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .editorialPanel(cornerRadius: 16)
                }
                ForEach(FocusWindow.allCases) { item in
                    choiceRow(title: roomString(item.title),
                              detail: rhythmTimeRange(item),
                              symbol: "clock", selected: windowRaw == item.rawValue) { windowRaw = item.rawValue }
                }
            }
        }
    }

    private func rhythmQuickChoice(title: String, symbol: String, selected: Bool,
                                   action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: symbol).accessibilityHidden(true)
                Text(title).fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                if selected { RoomRadio(selected: true, font: .suit(.body)).accessibilityHidden(true) }
            }
            .font(.suit(.subheadline, weight: .semibold))
            .foregroundStyle(Color.roomInk)
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(selected ? Color.roomMint : Color.roomCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(selected ? Color.roomAccent : Color.roomOutline, lineWidth: selected ? 1.5 : 1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func choiceRow(title: String, detail: String, symbol: String, selected: Bool,
                           action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: symbol).font(.suit(.title3)).frame(width: 28)
                VStack(alignment: .leading, spacing: 5) {
                    Text(title).font(.suit(.headline))
                    Text(detail).font(.suit(.subheadline))
                        .foregroundStyle(Color.roomInkSecondary)
                }
                Spacer(minLength: 0)
                RoomRadio(selected: selected)
            }
            .multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
            .foregroundStyle(Color.roomInk).padding(16).frame(maxWidth: .infinity, minHeight: 76)
            .background(selected ? Color.roomMint : Color.roomCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(selected ? Color.roomAccent : Color.roomOutline, lineWidth: selected ? 1.5 : 1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var readyScreen: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                RoomSpirit(state: .confirmed).frame(height: 160).frame(maxWidth: .infinity)
                    .background(Color.roomMint).clipShape(RoundedRectangle(cornerRadius: 24))
                Text("A little room, ready for you.")
                    .font(.suit(.largeTitle, weight: .bold))
                    .accessibilityAddTraits(.isHeader)
                if let rule = model.rules.first(where: { $0.id == model.editingRuleID }) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(rule.name).font(.suit(.title2, weight: .bold))
                        Text(ruleScheduleSummary(rule)).font(.suit(.body))
                        Text("Saved on this iPhone. Restrictions haven’t started.")
                            .font(.suit(.body)).foregroundStyle(Color.roomInkSecondary)
                    }
                    .padding(20).frame(maxWidth: .infinity, alignment: .leading)
                    .editorialPanel()
                }
                Label("You can always restore access for free.", systemImage: "arrow.counterclockwise")
                    .font(.suit(.subheadline))
                actionFeedback
                if account.isConfigured && !account.isSignedIn {
                    Button("Save my goal to an account") { accountPresented = true }
                        .font(.suit(.body, weight: .semibold)).foregroundStyle(Color.roomAccent).frame(minHeight: 44)
                }
            }
            .foregroundStyle(Color.roomInk).padding(24).frame(maxWidth: 560).frame(maxWidth: .infinity)
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 4) {
                primaryButton(roomString("Activate my rule")) {
                    guard let rule = model.rules.first(where: { $0.id == model.editingRuleID }) else {
                        readyActivationError = roomString("Saved rule unavailable. Go to home and choose a rule.")
                        return
                    }
                    readyActivationError = nil
                    guard activate(rule) else { return }
                    screen = .home
                    paywallPresented = account.offersEnabled && !account.isPro
                }
                Button("Go to home") { screen = .home }
                    .font(.suit(.body, weight: .medium)).frame(minHeight: 44)
            }.bottomBarStyle()
        }
    }

    private var permissionScreen: some View {
        setupScreen(
            title: model.screenTimeAuthorized ? roomString("You’re connected.") : roomString("Connect Screen Time"),
            detail: roomString("Allow Screen Time to block your selected apps."),
            actionTitle: permissionActionTitle,
            actionEnabled: !isRequestingPermission,
            action: handlePermissionAction
        ) {
            HStack(spacing: 14) {
                Image(systemName: model.screenTimeAuthorized ? "checkmark.circle" : "shield.lefthalf.filled")
                    .font(.suit(.title2))
                    .foregroundStyle(Color.roomAction)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 6) {
                    Text(model.screenTimeAuthorized ? roomString("Connected") : model.screenTimeStatusText)
                        .font(.suit(.headline)).foregroundStyle(Color.roomInk)
                    Text("Required to apply restrictions")
                        .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(22)
            .editorialPanel(model.screenTimeAuthorized ? .roomMint : .roomButter, outlined: false)
            Text("You’ll choose the apps in Apple’s picker.")
                .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
            actionFeedback
        }
    }

    private var targetsScreen: some View {
        setupScreen(
            title: roomString("Choose apps to put aside"),
            detail: roomString("Keep essentials like calls, maps, banking, and authentication available. Review category selections too."),
            actionTitle: model.selectionCount == 0
                ? roomString("Choose apps first")
                : roomString("Choose a place"),
            actionEnabled: model.selectionCount > 0,
            action: { screen = .place }
        ) {
            Button {
                model.requestTargetSelection()
            } label: {
                HStack(spacing: 16) {
                    Image(systemName: "square.stack.3d.up.fill")
                        .font(.suit(.title2))
                        .foregroundStyle(Color.roomInk)
                        .frame(width: 46, height: 46)
                        .background(Color.roomMint)
                        .clipShape(RoundedRectangle(cornerRadius: 16))

                    VStack(alignment: .leading, spacing: 4) {
                        Text(model.selectionCount == 0
                            ? roomString("Choose apps")
                            : roomString("Choose again"))
                            .font(.suit(.headline))
                            .foregroundStyle(Color.roomInk)
                        Text(model.selectionCount == 0
                            ? roomString("Nothing selected yet")
                            : model.selectionCount == 1
                                ? roomString("1 selected item")
                                : roomString("%d selected items", model.selectionCount))
                            .font(.suit(.subheadline))
                            .foregroundStyle(Color.roomInkSecondary)
                    }

                    Spacer(minLength: 8)
                    Image(systemName: "chevron.right")
                        .font(.suit(.subheadline, weight: .bold))
                        .foregroundStyle(Color.roomInkTertiary)
                }
                .padding(18)
                .editorialPanel(cornerRadius: 16)
            }
            .buttonStyle(.plain)
        }
    }

    private var placeScreen: some View {
        setupScreen(
            title: roomString("Choose where to start"),
            actionTitle: placeActionTitle,
            actionEnabled: model.placeConfigured,
            action: handlePlaceAction
        ) {
            VStack(alignment: .leading, spacing: 10) {
                Button {
                    placeMode = .anywhere
                    screen = .schedule
                } label: {
                    Text("Use time only, no place")
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .font(.suit(.body, weight: .medium)).buttonStyle(RoomSecondaryActionStyle(horizontalPadding: 8))
                Text("A time-only rule never uses Location. Tap Start focus during its schedule.")
                    .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            placeMapPicker
            Text("Choose a place, then start focus when you arrive during your schedule. The 150 m area is approximate, not room-level accuracy.")
                .font(.suit(.subheadline))
                .foregroundStyle(Color.roomInkSecondary)
                .fixedSize(horizontal: false, vertical: true)
            Text("Requires Always and Precise Location. Detection timing varies.")
                .font(.suit(.subheadline))
                .foregroundStyle(Color.roomInkSecondary)

            actionFeedback
        }
    }

    private var placeMapPicker: some View {
        let controlsLayout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 12))
            : AnyLayout(HStackLayout(spacing: 12))
        return VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    TextField("Search places or addresses", text: $placeQuery)
                        .foregroundStyle(Color.roomInk)
                        .submitLabel(.search)
                        .onSubmit { searchPlaces() }
                    Button { searchPlaces() } label: {
                        Image(systemName: "magnifyingglass").frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel("Search places")
                    .disabled(placeQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .font(.suit(.body))
                .padding(.leading, 14)
                .roomField()
                .onChange(of: placeQuery) {
                    placeSearch?.cancel()
                    placeSearchID = UUID()
                    placeResults = []
                    placeSearchMessage = nil
                }
                if let placeSearchMessage {
                    Text(placeSearchMessage).font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                }
                ForEach(placeResults.indices, id: \.self) { index in
                    let item = placeResults[index]
                    Button {
                        model.selectPlace(item.placemark.coordinate)
                        centerMap(on: item.placemark.coordinate)
                        placeResults = []
                        placeSearchMessage = nil
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.name ?? roomString("Focus place")).font(.suit(.headline))
                            if let address = item.placemark.title {
                                Text(address).font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                            }
                        }.frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    }.buttonStyle(.plain)
                }
            }.padding(16)
            MapReader { proxy in
                Map(position: $mapPosition) {
                    UserAnnotation()
                    if let coordinate = model.draftPlaceCoordinate {
                        MapCircle(center: coordinate, radius: 150)
                            .foregroundStyle(Color.roomAccent.opacity(0.18))
                            .stroke(Color.roomAccent, lineWidth: 2)
                        Marker(roomString("Focus place"), coordinate: coordinate)
                            .tint(Color.roomAccent)
                    }
                }
                .mapStyle(.standard(elevation: .flat))
                .onMapCameraChange(frequency: .onEnd) { context in mapCenter = context.region.center }
                .overlay {
                    Image(systemName: "plus").foregroundStyle(Color.roomInk)
                        .padding(6).background(.regularMaterial, in: Circle())
                        .allowsHitTesting(false).accessibilityHidden(true)
                }
                .mapControls {
                    MapCompass()
                    MapScaleView()
                }
                .simultaneousGesture(
                    SpatialTapGesture().onEnded { value in
                        guard let coordinate = proxy.convert(value.location, from: .local) else { return }
                        model.selectPlace(coordinate)
                        centerMap(on: coordinate)
                    }
                )
                .accessibilityLabel(roomString("Focus place map"))
                .accessibilityHint(roomString("Tap the map or use your current location."))
            }
            .frame(height: 260)

            VStack(alignment: .leading, spacing: 14) {
                Label(model.placeConfigured ? roomString("150 m place selected") : roomString("Tap the map to choose"),
                      systemImage: model.placeConfigured ? "checkmark.circle" : "hand.tap")
                    .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                controlsLayout {
                    Button {
                        guard let coordinate = mapCenter else { return }
                        model.selectPlace(coordinate)
                    } label: {
                        Label("Use map center", systemImage: "scope")
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }.disabled(mapCenter == nil)
                    Button {
                        mapPosition = .userLocation(followsHeading: false, fallback: mapPosition)
                        model.requestCurrentPlace()
                    } label: {
                        Label(roomString("Current location"), systemImage: "location")
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                }.font(.suit(.body, weight: .medium)).buttonStyle(RoomSecondaryActionStyle(horizontalPadding: 8))
            }.padding(16).background(Color.roomCard)
        }
        .editorialPanel()
        .onDisappear { placeSearch?.cancel(); placeSearchID = UUID() }
        .onAppear {
            if let coordinate = model.draftPlaceCoordinate {
                centerMap(on: coordinate)
            }
        }
        .onChange(of: draftPlaceCoordinateSignature) {
            if let coordinate = model.draftPlaceCoordinate {
                centerMap(on: coordinate)
            }
        }
    }

    private func searchPlaces() {
        let query = placeQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return }
        placeSearch?.cancel()
        let requestID = UUID()
        placeSearchID = requestID
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        if let center = mapCenter {
            request.region = MKCoordinateRegion(center: center, latitudinalMeters: 10_000, longitudinalMeters: 10_000)
        }
        let search = MKLocalSearch(request: request)
        placeSearch = search
        placeResults = []
        placeSearchMessage = roomString("Searching…")
        Task { @MainActor in
            do {
                let response = try await search.start()
                guard placeSearchID == requestID else { return }
                placeResults = Array(response.mapItems.prefix(6))
                placeSearchMessage = placeResults.isEmpty ? roomString("No places found. Try another name or address.") : nil
            } catch {
                guard placeSearchID == requestID else { return }
                placeSearchMessage = roomString("Search unavailable. Try again or choose on the map.")
            }
        }
    }

    private var draftPlaceCoordinateSignature: String? {
        model.draftPlaceCoordinate.map { "\($0.latitude),\($0.longitude)" }
    }

    private func centerMap(on coordinate: CLLocationCoordinate2D) {
        mapPosition = .region(MKCoordinateRegion(
            center: coordinate,
            latitudinalMeters: 700,
            longitudinalMeters: 700
        ))
    }

    private var scheduleScreen: some View {
        setupScreen(
            title: roomString("Choose your schedule"),
            detail: roomString("Pick the days and times it repeats."),
            actionTitle: roomString("Review rule"),
            actionEnabled: scheduleIsValid,
            action: {
                showsSaveError = false
                screen = .review
            }
        ) {
            dayPicker
            VStack(spacing: 0) {
                DatePicker("Start", selection: $model.startTime, displayedComponents: .hourAndMinute)
                    .padding(18)
                Divider().overlay(Color.roomLine)
                DatePicker("End", selection: $model.endTime, displayedComponents: .hourAndMinute)
                    .padding(18)
            }
            .font(.suit(.body, weight: .semibold))
            .foregroundStyle(Color.roomInk)
            .editorialPanel()

            if model.days == 0 {
                Label("Choose at least one day.", systemImage: "exclamationmark.circle.fill")
                    .font(.suit(.subheadline))
                    .foregroundStyle(Color.roomWarning)
            } else if !scheduleIsValid {
                Label("Choose a focus window of at least 15 minutes.", systemImage: "exclamationmark.circle.fill")
                    .font(.suit(.subheadline))
                    .foregroundStyle(Color.roomWarning)
            }
            if minutes(from: model.endTime) < minutes(from: model.startTime) {
                Text("Ends the next day.")
                    .font(.suit(.subheadline))
                    .foregroundStyle(Color.roomInkSecondary)
            }
        }
    }

    private var dayPicker: some View {
        let symbols = Calendar.current.shortWeekdaySymbols
        let columns = [GridItem(.adaptive(minimum: dynamicTypeSize.isAccessibilitySize ? 96 : 40), spacing: 8)]
        return VStack(alignment: .leading, spacing: 10) {
            Text("Days").font(.suit(.subheadline, weight: .semibold)).foregroundStyle(Color.roomInkSecondary)
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(Self.mondayFirstWeekdays, id: \.self) { weekday in
                    let bit = RestrictionPolicy.dayBit(weekday: weekday)
                    let on = model.days & bit != 0
                    Button { model.days ^= bit } label: {
                        Text(symbols[weekday - 1])
                            .font(.suit(.subheadline, weight: .semibold))
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .background(on ? Color.roomAccent : Color.roomCard, in: Capsule())
                            .overlay { if !on { Capsule().strokeBorder(Color.roomOutline, lineWidth: 1) } }
                            .foregroundStyle(on ? Color.roomCanvas : Color.roomInk)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Calendar.current.weekdaySymbols[weekday - 1])
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
    }

    private static let mondayFirstWeekdays = [2, 3, 4, 5, 6, 7, 1]

    private func daysSummary(_ days: Int) -> String {
        switch days {
        case RestrictionPolicy.everyDay: return roomString("Every day")
        case RestrictionPolicy.weekdays: return roomString("Weekdays")
        case RestrictionPolicy.everyDay ^ RestrictionPolicy.weekdays: return roomString("Weekends")
        default:
            let symbols = Calendar.current.shortWeekdaySymbols
            return Self.mondayFirstWeekdays.filter { days & RestrictionPolicy.dayBit(weekday: $0) != 0 }
                .map { symbols[$0 - 1] }.joined(separator: ", ")
        }
    }

    private var reviewScreen: some View {
        setupScreen(
            title: roomString("Review your rule"),
            detail: roomString("Saving keeps your rule here. You decide when to activate it."),
            actionTitle: roomString("Save rule"),
            action: saveAndFinish
        ) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Rule name")
                    .font(.suit(.subheadline, weight: .semibold))
                    .foregroundStyle(Color.roomInkSecondary)
                TextField("e.g. Work time or Wind-down", text: $model.ruleName)
                    .textInputAutocapitalization(.words)
                    .font(.suit(.body))
                    .foregroundStyle(Color.roomInk)
                    .padding(16)
                    .roomField()
            }

            VStack(spacing: 0) {
                summaryRow(icon: "square.stack.3d.up", title: roomString("Apps & sites"), value: model.selectionCount == 1 ? roomString("1 selected item") : roomString("%d selected items", model.selectionCount))
                Divider().overlay(Color.roomLine).padding(.leading, 54)
                summaryRow(
                    icon: placeMode == .anywhere ? "clock" : "location",
                    title: roomString("Place"),
                    value: placeMode == .anywhere
                        ? roomString("No place, time only")
                        : roomString("Within 150 m of this place")
                )
                Divider().overlay(Color.roomLine).padding(.leading, 54)
                summaryRow(icon: "clock", title: roomString("Time"), value: scheduleSummary)
            }
            .background(Color.roomCanvas)

            VStack(alignment: .leading, spacing: 10) {
                Toggle("Prevent app deletion during blocking", isOn: $model.preventAppRemoval)
                    .font(.suit(.body, weight: .semibold))
                Text("Restricts deleting any app while this rule is blocking. This does not lock your iPhone. You can revoke Screen Time access in Settings. Emergency recovery is free.")
                    .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(18).editorialPanel(Color.roomButter, outlined: false)

            Label("Saving won’t start restrictions.", systemImage: "checkmark")
                .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)

            Text("Early session endings use an unlock pass. Emergency recovery is free. Pass purchases are not available yet.")
                .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                .fixedSize(horizontal: false, vertical: true)

            if showsSaveError {
                Text(model.message)
                    .font(.suit(.subheadline))
                    .foregroundStyle(Color.roomWarning)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var homeScreen: some View {
        let toolbarLayout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout())
        return VStack(spacing: 0) {
            toolbarLayout {
                Text("Your space")
                    .font(.suit(.headline, weight: .bold))
                    .foregroundStyle(Color.roomInk)
                if !dynamicTypeSize.isAccessibilitySize { Spacer() }
                HStack {
                    Button { notificationsPresented = true } label: { Image(systemName: "bell") }
                        .font(.suit(.title3)).foregroundStyle(Color.roomInk)
                        .frame(minWidth: 44, minHeight: 44).accessibilityLabel("Zone notifications")
                    Button { accountPresented = true } label: { Image(systemName: "person.crop.circle") }
                        .font(.suit(.title3)).foregroundStyle(Color.roomInk)
                        .frame(minWidth: 44, minHeight: 44).accessibilityLabel("Account & plan")
                    Button { addRule() } label: { Label("New rule", systemImage: "plus").fixedSize(horizontal: !dynamicTypeSize.isAccessibilitySize, vertical: true) }
                        .font(.suit(.subheadline, weight: .medium))
                        .foregroundStyle(Color.roomAction).frame(minHeight: 44)
                }
            }
            .padding(.horizontal, 24).padding(.bottom, 10)
            ScrollView {
                VStack(spacing: 24) {
                    homeStatusCard
                    actionFeedback
                    quickFocusSection
                    if SharedState.ruleEnabled && model.activeRule?.placeMode == .gps && !model.isFocused {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(model.placeArrivalText)
                                .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                            Button { model.checkPlaceArrival() } label: {
                                Label(model.isCheckingPlace ? roomString("Checking location…") : roomString("Check location"), systemImage: "location")
                                    .frame(minHeight: 44)
                            }.disabled(model.isCheckingPlace)
                        }
                    }
                    if SharedState.ruleEnabled && model.activeRule?.placeMode == .anywhere && !model.isFocused {
                        Text("Time-only rule: tap Start focus during its schedule. No Location needed.")
                            .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    if model.activeRule?.placeMode == .gps && (model.locationStatus == .denied || model.locationStatus == .restricted || model.placeMonitoringNeedsAttention) {
                        Link("Open Location Settings", destination: URL(string: UIApplication.openSettingsURLString)!)
                            .font(.suit(.subheadline, weight: .semibold)).foregroundStyle(Color.roomWarning).frame(minHeight: 44)
                    }
                    if firstFocus && account.offersEnabled && !account.isPro && !offerDismissed {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(roomString(goal.headline))
                                .font(.suit(.title2, weight: .bold))
                            HStack {
                                Button("Explore Pro") { paywallPresented = true }
                                    .font(.suit(.body, weight: .semibold))
                                Spacer()
                                Button("Not now") { offerDismissed = true }
                                    .font(.suit(.subheadline))
                            }.frame(minHeight: 44)
                        }
                        .padding(20).editorialPanel(Color.roomMint, outlined: false)
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Your rules")
                                .font(.suit(.title2, weight: .bold))
                                .foregroundStyle(Color.roomInk).accessibilityAddTraits(.isHeader)
                            Spacer()
                            Text(roomString("%d saved", model.rules.count))
                                .font(.suit(.caption)).foregroundStyle(Color.roomInkSecondary)
                        }
                        if model.rules.isEmpty {
                            Text("No rules yet").font(.suit(.body))
                                .foregroundStyle(Color.roomInkSecondary).padding(.vertical, 12)
                        } else {
                            VStack(spacing: 0) {
                                ForEach(Array(model.rules.enumerated()), id: \.element.id) { index, rule in
                                    if index > 0 { Divider().overlay(Color.roomLine) }
                                    ruleRow(rule)
                                }
                            }
                        }
                    }
                }
                .frame(maxWidth: 560).padding(.horizontal, 24).padding(.bottom, 32)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
    }

    private var homeStatusCard: some View {
        let dark = model.isFocused && !homeNeedsAction && !SharedState.safetyReleased
        let foreground: Color = dark ? .roomCanvas : .roomInk
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 12))
            : AnyLayout(HStackLayout(alignment: .center, spacing: 16))
        return VStack(alignment: .leading, spacing: 20) {
            layout {
                VStack(alignment: .leading, spacing: 12) {
                    if let rule = model.activeRule, SharedState.ruleEnabled {
                        Text(rule.name).font(.suit(.subheadline, weight: .medium))
                    }
                    Text(model.currentStatusTitle)
                        .font(.suit(.title, weight: .bold))
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                    if let detail = model.currentStatusDetail {
                        Text(detail).font(.suit(.body, weight: .medium)).monospacedDigit()
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
                RoomSpirit(state: homeSpiritState, interactive: true).frame(width: 84, height: 96)
            }
            if !model.screenTimeAuthorized {
                primaryButton(roomString("Check Screen Time Settings"), inverted: dark) {
                    permissionDestination = .home; screen = .permission
                }
            } else if model.rules.isEmpty {
                primaryButton(roomString("Create a rule"), inverted: dark) { addRule() }
            } else if let rule = suggestedRule, !SharedState.ruleEnabled || SharedState.safetyReleased {
                primaryButton(rule.placeMode != .nfc ? roomString("Activate %@", rule.name) : roomString("Choose a place"), inverted: dark) { activate(rule) }
            } else if (model.activeRule?.placeMode ?? .nfc) != .nfc && !model.isFocused && model.canStartPlaceFocus {
                primaryButton(roomString("Start focus"), inverted: dark) {
                    model.startPlaceFocus()
                }
            }
        }
        .foregroundStyle(foreground).padding(24).frame(maxWidth: .infinity, alignment: .leading)
        .editorialPanel(homeStatusTint, outlined: homeStatusTint == .roomCard)
    }

    @ViewBuilder private var quickFocusSection: some View {
        if let end = model.quickEndsAt {
            VStack(alignment: .leading, spacing: 16) {
                Text("Quick focus").font(.suit(.subheadline, weight: .medium))
                Text(roomString("Until %@", end.formatted(date: .omitted, time: .shortened)))
                    .font(.suit(.title, weight: .semibold)).monospacedDigit()
                    .fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
                primaryButton(roomString("Restore access"), inverted: true) {
                    model.endQuickFocus()
                    roomieReaction = model.errorMessage == nil ? .recovered : nil
                }
            }
            .foregroundStyle(Color.roomCanvas).padding(22).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.roomAccent).clipShape(RoundedRectangle(cornerRadius: 22))
        } else if model.screenTimeAuthorized && !model.isFocused {
            Button { quickFocusPresented = true } label: {
                Label("Quick focus", systemImage: "timer").frame(maxWidth: .infinity, minHeight: 50)
            }
            .font(.suit(.body, weight: .semibold)).foregroundStyle(Color.roomInk)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.roomInk.opacity(0.35)))
        }
    }

    private var suggestedRule: FocusRule? {
        model.rules.first(where: { $0.id == model.activeRuleID }) ?? model.rules.first
    }

    private var homeSpiritState: RoomSpiritState {
        if model.errorMessage != nil { return .failed }
        if model.isCheckingPlace { return .working }
        return .home(focused: model.isFocused, needsAction: homeNeedsAction,
              recovered: SharedState.safetyReleased, reaction: roomieReaction)
    }

    private var homeStatusTint: Color {
        if homeNeedsAction { return .roomButter }
        if SharedState.runtime.recoveryPaused { return .roomCard }
        if SharedState.safetyReleased { return .roomMint }
        return model.isFocused ? .roomAccent : .roomCard
    }

    @discardableResult
    private func activate(_ rule: FocusRule) -> Bool {
        guard rule.placeMode != .nfc else { editRule(rule); return false }
        guard !model.isFocused else { presentSessionEnd(); return false }
        let activated = model.activateRule(rule.id)
        if activated {
            placeMode = rule.placeMode
            roomieReaction = .confirmed
        }
        return activated
    }

    private func presentSessionEnd() {
        if account.unlockPassEnabled { unlockPassPresented = true }
        else { safetyReleasePresented = true }
    }

    private func recordVisit() {
        let now = Date().timeIntervalSince1970
        if lastVisit > 0 && now - lastVisit >= 48 * 60 * 60 && !model.rules.isEmpty {
            roomieReaction = .returning
        }
        lastVisit = now
    }

    @ViewBuilder
    private var actionFeedback: some View {
        if let error = readyActivationError ?? model.errorMessage {
            Text(error)
                .font(.suit(.subheadline, weight: .semibold))
                .foregroundStyle(Color.roomInk)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .editorialPanel(Color.roomButter, outlined: false)
        }
    }

    private func ruleRow(_ rule: FocusRule) -> some View {
        let isActive = SharedState.ruleEnabled && model.activeRuleID == rule.id
        let hasChanges = model.hasUnappliedChanges(rule.id)
        let canResume = isActive && SharedState.safetyReleased
        // Color carries meaning: soft butter needs attention, mint is active, ivory is saved.
        let tint: Color = hasChanges || rule.placeMode == .nfc ? .roomButter : isActive ? .roomMint : .roomCard
        return VStack(alignment: .leading, spacing: 8) {
            Button { editRule(rule) } label: {
                HStack(alignment: .top, spacing: 14) {
                    Image(systemName: rule.placeMode == .anywhere ? "clock.fill" : "location.fill")
                        .font(.suit(.body, weight: .semibold))
                        .foregroundStyle(Color.roomInk)
                        .frame(width: 40, height: 40)
                        .background(tint)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay {
                            RoundedRectangle(cornerRadius: 16)
                                .strokeBorder(Color.roomOutline, lineWidth: 1)
                        }
                        .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 5) {
                        Text(rule.name)
                            .font(.suit(.headline, weight: .bold))
                            .foregroundStyle(Color.roomInk)
                            .fixedSize(horizontal: false, vertical: true)
                        if rule.placeMode == .nfc {
                            Text("Choose a place to use this rule")
                                .font(.suit(.subheadline)).foregroundStyle(Color.roomWarning)
                        }
                        if hasChanges || isActive {
                            Text(hasChanges ? roomString("Unapplied changes") : canResume ? roomString("Paused") : roomString("Active"))
                                .font(.suit(.subheadline, weight: .bold))
                                .foregroundStyle(hasChanges ? Color.roomWarning : Color.roomAccent)
                        }
                        Text(ruleScheduleSummary(rule))
                            .font(.suit(.subheadline, weight: .medium))
                            .foregroundStyle(Color.roomInkSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .multilineTextAlignment(.leading)

                    Spacer(minLength: 4)
                    Image(systemName: "chevron.right")
                        .font(.suit(.caption, weight: .bold))
                        .foregroundStyle(Color.roomInkTertiary)
                        .accessibilityHidden(true)
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint("Edit this saved rule. Changes are applied separately.")

            HStack(spacing: 16) {
                if !isActive || hasChanges || canResume {
                    Button {
                        activate(rule)
                    } label: {
                        Text(rule.placeMode == .nfc ? roomString("Choose a place") : hasChanges ? roomString("Apply changes") : canResume ? roomString("Resume rule") : roomString("Activate"))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .font(.suit(.subheadline, weight: .bold))
                    .foregroundStyle(Color.roomAccent)
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityHint("This replaces the currently active rule.")
                }
                Spacer(minLength: 0)
                Button(role: .destructive) {
                    if isActive && model.isFocused { presentSessionEnd() }
                    else { ruleToDelete = rule }
                } label: {
                    Image(systemName: "trash")
                }
                .font(.suit(.subheadline))
                .foregroundStyle(Color.roomInkSecondary)
                .frame(minWidth: 44, minHeight: 44)
                .accessibilityLabel(roomString("Delete %@", rule.name))
            }
        }
        .padding(.vertical, 16)
    }

    private func setupScreen<Content: View>(
        title: String,
        detail: String? = nil,
        actionTitle: String,
        actionEnabled: Bool = true,
        action: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(spacing: 0) {
            setupHeader

            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    VStack(alignment: .leading, spacing: 18) {
                        if screen == .goal || screen == .permission {
                            RoomSpirit(state: setupSpiritState)
                                .frame(width: 64, height: 58)
                                .accessibilityHidden(true)
                        }
                        Text(title)
                            .font(.suit(dynamicTypeSize.isAccessibilitySize ? .title2 : .title, weight: .bold))
                            .foregroundStyle(Color.roomInk)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                        if let detail {
                            Text(detail).font(.suit(.body))
                                .foregroundStyle(Color.roomInkSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }


                    content()
                }
                .frame(maxWidth: 560, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 32)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            primaryButton(actionTitle, enabled: actionEnabled, action: action)
                .bottomBarStyle()
        }
    }

    private var setupHeader: some View {
        VStack(spacing: 12) {
            HStack {
                Button {
                    if (onboardingCompleted || !model.rules.isEmpty) && (screen == .targets || screen == .permission) {
                        cancelEditing()
                    } else if screen == .targets && model.screenTimeAuthorized {
                        screen = .rhythm
                    } else {
                        screen = RoomScreen(rawValue: screen.rawValue - 1) ?? .welcome
                    }
                } label: {
                    Label("Back", systemImage: "chevron.left")
                        .font(.suit(.subheadline, weight: .semibold))
                        .frame(minWidth: 44, minHeight: 44)
                }
                .foregroundStyle(Color.roomAccent)
                .accessibilityLabel("Back")

                Spacer()

                if let progress = setupProgress {
                    Text("\(progress.current) / \(progress.total)")
                        .font(.suit(.caption, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(Color.roomInkSecondary)
                }

                Spacer()

                if onboardingCompleted || !model.rules.isEmpty {
                    Button("Cancel") { cancelEditing() }
                        .font(.suit(.subheadline, weight: .semibold))
                        .foregroundStyle(Color.roomAccent)
                        .frame(minWidth: 44, minHeight: 44)
                } else {
                    Color.clear.frame(width: 44, height: 44)
                }
            }

            if let progress = setupProgress {
                ProgressView(value: Double(progress.current), total: Double(progress.total))
                    .progressViewStyle(RoomProgressStyle())
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 4)
    }

    private var setupProgress: (current: Int, total: Int)? {
        if screen == .goal { return (1, 7) }
        if screen == .rhythm { return (2, 7) }
        guard let step = screen.stepNumber else { return nil }
        if (onboardingCompleted || !model.rules.isEmpty) && step >= 2 {
            return (step - 1, 4)
        }
        return (step + 2, 7)
    }

    private func summaryRow(icon: String, title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: icon)
                .font(.suit(.subheadline, weight: .semibold))
                .foregroundStyle(Color.roomInkSecondary)
            Text(value)
                .font(.suit(.body, weight: .medium))
                .foregroundStyle(Color.roomInk)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.leading, 26)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 17)
        .accessibilityElement(children: .combine)
    }

    private func primaryButton(_ title: String, enabled: Bool = true, inverted: Bool = false, action: @escaping () -> Void) -> some View {
        RoomPrimaryButton(title: title, enabled: enabled, inverted: inverted, action: action)
    }

    private var permissionActionTitle: String {
        if isRequestingPermission { return roomString("Connecting…") }
        if model.screenTimeAuthorized {
            return permissionDestination == .home ? roomString("Go home") : roomString("Choose apps")
        }
#if targetEnvironment(simulator)
        return roomString("Choose apps")
#else
        if model.screenTimeStatus == .denied { return roomString("Open Settings") }
        return roomString("Continue")
#endif
    }

    private func handlePermissionAction() {
        guard !isRequestingPermission else { return }
        if model.screenTimeAuthorized {
            screen = permissionDestination
            return
        }
#if !targetEnvironment(simulator)
        if model.screenTimeStatus == .denied {
            UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!)
            return
        }
#endif
        isRequestingPermission = true
        let destination = permissionDestination
        Task {
            let approved = await model.requestScreenTimeAuthorization()
            isRequestingPermission = false
            if approved && screen == .permission && permissionDestination == destination {
                screen = destination
            }
        }
    }

    private var placeReady: Bool {
        model.gpsPlaceReady
    }

    private var placeActionTitle: String {
        if placeReady { return roomString("Choose a time") }
        if !model.placeConfigured { return roomString("Choose a place") }
        if model.gpsNeedsSettings { return roomString("Open Location Settings") }
        return roomString("Continue")
    }

    private func handlePlaceAction() {
        placeMode = .gps
        if placeReady {
            screen = .schedule
        } else {
            guard model.placeConfigured else { return }
            if model.gpsNeedsSettings {
                UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!)
            } else {
                model.requestPlaceAuthorization()
            }
        }
    }

    private var scheduleIsValid: Bool {
        RestrictionPolicy.intervalMinutes(
            start: minutes(from: model.startTime),
            end: minutes(from: model.endTime)
        ) >= 15 && model.days != 0
    }

    private var scheduleSummary: String {
        "\(model.startTime.formatted(date: .omitted, time: .shortened))–\(model.endTime.formatted(date: .omitted, time: .shortened)) · \(daysSummary(model.days))"
    }

    private var homeNeedsAction: Bool {
        if model.errorMessage != nil || SharedState.rulesStorageError { return true }
        guard model.activeRule?.placeMode == .gps else { return !model.screenTimeAuthorized }
        return !model.screenTimeAuthorized
            || model.locationStatus == .denied
            || model.locationStatus == .restricted
            || model.placeMonitoringNeedsAttention
    }

    private var setupSpiritState: RoomSpiritState {
        if model.errorMessage != nil { return .needsAction }
        if isRequestingPermission { return .working }
        switch screen {
        case .permission:
            return model.screenTimeAuthorized ? .confirmed : .attentive
        case .targets:
            return model.selectionCount == 0 ? .guiding : .confirmed
        case .place:
            return placeReady ? .confirmed : .working
        case .schedule, .review:
            return .attentive
        default:
            return .idle
        }
    }

    private func saveAndFinish() {
        let firstSetup = !onboardingCompleted && model.rules.isEmpty
        showsSaveError = !model.saveRule(placeMode: placeMode)
        if !showsSaveError {
            onboardingCompleted = true
            roomieReaction = .celebrating
            screen = firstSetup ? .ready : .home
        }
    }

    private func ruleScheduleSummary(_ rule: FocusRule) -> String {
        let start = Calendar.current.date(
            byAdding: .minute,
            value: rule.startMinutes,
            to: Calendar.current.startOfDay(for: .now)
        ) ?? .now
        let end = Calendar.current.date(
            byAdding: .minute,
            value: rule.endMinutes,
            to: Calendar.current.startOfDay(for: .now)
        ) ?? .now
        let timeRange = "\(start.formatted(date: .omitted, time: .shortened))–\(end.formatted(date: .omitted, time: .shortened))"
        let range = rule.endMinutes < rule.startMinutes ? roomString("%@ (+1 day)", timeRange) : timeRange
        return "\(range) · \(daysSummary(rule.activeDays))"
    }

    private func addRule() {
        model.beginNewRuleDraft()
        placeMode = .gps
        showsSaveError = false
        permissionDestination = .targets
        screen = model.screenTimeAuthorized ? .targets : .permission
    }

    private func editRule(_ rule: FocusRule) {
        model.loadRuleForEditing(rule.id)
        placeMode = rule.placeMode == .anywhere ? .anywhere : .gps
        showsSaveError = false
        permissionDestination = .targets
        screen = model.screenTimeAuthorized ? .targets : .permission
    }

    private func cancelEditing() {
        model.resetRuleDraft()
        placeMode = .gps
        screen = .home
    }

    private func minutes(from date: Date) -> Int {
        let components = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (components.hour ?? 0) * 60 + (components.minute ?? 0)
    }
}

private struct SafetyReleaseView: View {
    let detail: String
    let release: () -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                Button { dismiss() } label: { Label("Back", systemImage: "chevron.left") }
                    .font(.suit(.subheadline, weight: .semibold)).foregroundStyle(Color.roomAccent).frame(minHeight: 44)
                Text("You’re in control")
                    .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                Text("Need your apps back?")
                    .font(.suit(.largeTitle, weight: .bold))
                    .foregroundStyle(Color.roomInk).accessibilityAddTraits(.isHeader)
                Text("Restore access now. Your saved rules will stay here.")
                    .font(.suit(.body))
                    .foregroundStyle(Color.roomInkSecondary).fixedSize(horizontal: false, vertical: true)
                RoomSpirit(state: .attentive)
                    .frame(height: dynamicTypeSize.isAccessibilitySize ? 120 : 180)
                Label(detail, systemImage: "arrow.counterclockwise")
                    .font(.suit(.body))
                    .foregroundStyle(Color.roomInkSecondary).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: 560, alignment: .leading)
            .padding(24).frame(maxWidth: .infinity)
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 4) {
                RoomPrimaryButton(title: roomString("Restore access now")) { release(); dismiss() }
                Button("Cancel") { dismiss() }
                    .foregroundStyle(Color.roomAccent).frame(maxWidth: .infinity, minHeight: 44)
            }.bottomBarStyle()
        }
        .background(Color.roomCanvas)
    }
}

// Checkout stays unavailable until a real consumable product and verified spend ledger exist.
// A subscription entitlement is never treated as a reusable unlock pass.
private struct UnlockPassView: View {
    @ObservedObject var model: AppModel
    let recover: () -> Void
    @EnvironmentObject private var account: AccountStore
    @Environment(\.dismiss) private var dismiss
    @State private var recoveryPresented = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Text("End this session early?")
                        .font(.suit(.largeTitle, weight: .bold))
                    if let rule = model.activeRule {
                        Label(rule.name, systemImage: "location.circle")
                            .font(.suit(.headline))
                    }
                    RoomSpirit(state: .attentive)
                        .frame(height: 150).frame(maxWidth: .infinity)
                        .editorialPanel()
                    VStack(alignment: .leading, spacing: 12) {
                        Label("1 unlock pass", systemImage: "ticket")
                            .font(.suit(.title2, weight: .bold))
                        Text("One-time purchase. End one session early, without a subscription.")
                        Text(model.safetyReleaseDescription)
                            .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                    }
                    .padding(20).frame(maxWidth: .infinity, alignment: .leading)
                    .editorialPanel(Color.roomButter, outlined: false)
                    passControls
                }
                .font(.suit(.body)).foregroundStyle(Color.roomInk)
                .padding(24).frame(maxWidth: 560).frame(maxWidth: .infinity)
            }
            .background(Color.roomCanvas)
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 8) {
                    Button("Keep my session") { dismiss() }.roomPrimaryAction()
                    Button("Location wrong or need urgent access? Recover for free") {
                        recoveryPresented = true
                    }
                    .font(.suit(.body)).frame(minHeight: 44)
                    .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 24).padding(.vertical, 12).background(Color.roomCanvas)
            }
            .navigationTitle("Unlock pass").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } } }
        }
        .tint(Color.roomAction)
        .task { await account.loadUnlockPass() }
        .onChange(of: account.isSignedIn) { _, signedIn in
            if signedIn { Task { await account.loadUnlockPass() } }
        }
        .sheet(isPresented: $recoveryPresented) {
            SafetyReleaseView(detail: model.safetyReleaseDescription) {
                recover()
                dismiss()
            }
            .presentationCornerRadius(24)
        }
        .onChange(of: model.isFocused) { _, focused in
            if !focused && !recoveryPresented { dismiss() }
        }
    }

    @ViewBuilder private var passControls: some View {
        if !account.unlockPassEnabled {
            Text("Unlock passes aren’t available yet. You haven’t been charged.")
                .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
            Button("Purchases unavailable") {}.roomPrimaryAction().disabled(true)
        } else if !account.isSignedIn {
            Text("Sign in to see and use your unlock passes.")
                .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
        } else if let pending = account.pendingUnlockSessionID, pending == model.unlockSessionID {
            Text("We couldn’t confirm the pass. Your session is still active.")
                .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
            Button("Try again") { usePass(pending) }
                .roomPrimaryAction().disabled(account.isUnlockBusy)
        } else if account.pendingUnlockSessionID != nil {
            Text("A previous pass use still needs confirmation. This session is still active.")
                .font(.suit(.subheadline)).foregroundStyle(Color.roomWarning)
            Button("Check previous pass") { Task { await account.reconcilePreviousUnlockPass() } }
                .roomPrimaryAction().disabled(account.isUnlockBusy)
            Link("Contact support", destination: URL(string: "https://offzone-privacy-support.wogus5357.chatgpt.site/support")!)
        } else if account.unlockPassBalance == nil {
            ProgressView("Checking your unlock passes…").frame(maxWidth: .infinity)
        } else if let balance = account.unlockPassBalance, balance > 0 {
            Text(roomString("%d unlock passes available", balance))
                .font(.suit(.headline))
            Button("Use 1 pass to end this session") {
                if let sessionID = model.unlockSessionID { usePass(sessionID) }
            }.roomPrimaryAction().disabled(account.isUnlockBusy || model.unlockSessionID == nil)
        } else {
            Text("No unlock passes available.")
                .font(.suit(.headline))
            if let product = account.unlockProduct {
                Button(roomString("Buy 1 unlock pass — %@", product.localizedPriceString)) {
                    Task { await account.purchaseUnlockPass() }
                }.roomPrimaryAction().disabled(account.isUnlockBusy)
            } else {
                Button("Check again") { Task { await account.loadUnlockPass() } }
                    .roomPrimaryAction().disabled(account.isUnlockBusy)
            }
        }
        if let notice = account.unlockNotice {
            Text(notice).font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
        }
        if let error = account.errorMessage {
            Text(error).font(.suit(.subheadline)).foregroundStyle(Color.roomWarning)
        }
    }

    private func usePass(_ sessionID: String) {
        Task {
            guard let spent = await account.spendUnlockPass(sessionID: sessionID) else { return }
            if model.release(usingUnlockPass: spent) {
                account.completeUnlockPass(sessionID: spent)
                dismiss()
            }
        }
    }
}

private struct QuickFocusView: View {
    @ObservedObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var minutes = 25

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Duration", selection: $minutes) {
                        ForEach(QuickFocus.durations, id: \.self) { Text(roomString("%d min", $0)).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    Button { model.requestQuickSelection() } label: {
                        LabeledContent("Choose apps", value: roomString("%d selected", model.quickSelectionCount))
                            .frame(minHeight: 44)
                    }
                } footer: {
                    Text("Choose a duration and apps. Quick focus works anywhere, without a saved place.")
                }
                Section {
                    Button {
                        if model.startQuickFocus(minutes: minutes) { dismiss() }
                    } label: {
                        Text("Start focus").font(.suit(.headline, weight: .semibold)).frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .disabled(model.quickSelectionCount == 0)
                    if let error = model.errorMessage {
                        Text(error).font(.suit(.subheadline)).foregroundStyle(Color.roomWarning)
                    }
                } footer: {
                    Text("You can end it anytime with Restore access. It ends on its own when time is up.")
                }
            }
            .font(.suit(.body))
            .scrollContentBackground(.hidden).background(Color.roomCanvas)
            .navigationTitle("Quick focus").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
            .familyActivityPicker(
                headerText: roomString("Choose apps, categories, and websites to put aside."),
                footerText: roomString("Your selection stays on this iPhone."),
                isPresented: $model.isQuickPickerPresented,
                selection: $model.quickSelection
            )
        }
        .tint(Color.roomAction)
    }
}

private struct ZoneNotificationSettingsView: View {
    @ObservedObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var updating = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("Zone notifications", isOn: Binding(
                        get: { model.zoneNotificationsEnabled },
                        set: { enabled in
                            updating = true
                            Task {
                                await model.setZoneNotificationsEnabled(enabled)
                                updating = false
                            }
                        }
                    )).disabled(updating)
                    Text(model.notificationStatusText).font(.suit(.subheadline))
                    if model.notificationStatus == .denied {
                        Link("Open Notification Settings", destination: URL(string: UIApplication.openSettingsURLString)!)
                            .foregroundStyle(Color.roomAccent).frame(minHeight: 44)
                    }
                } footer: {
                    Text("Get updates when your active GPS zone is entered or left, and when blocking starts or ends. Only one rule is active at a time.")
                }
                .listRowBackground(Color.roomCard)
                Section {
                    Label("Zone entered or left", systemImage: "location")
                    Label("Blocking started or ended", systemImage: "shield")
                } header: {
                    Text("What you’ll receive")
                } footer: {
                    Text("Alerts follow detected changes and may be delayed by iOS or Focus settings. Rule names may appear on your Lock Screen.")
                }
                .listRowBackground(Color.roomCard)
            }
            .font(.suit(.body)).foregroundStyle(Color.roomInk)
            .scrollContentBackground(.hidden).background(Color.roomCanvas)
            .navigationTitle("Zone notifications").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .tint(Color.roomAction)
        .task { await model.refreshNotificationSettings() }
    }
}

enum OffzoneNookExpression: String, CaseIterable {
    case welcome, ready, focused, reflection, finished, recovered, needsAction = "needs-action", failed
    var assetName: String { "OffzoneNook-portraits-" + rawValue }

    static func expression(for state: RoomSpiritState) -> Self {
        switch state {
        case .welcome, .guiding, .returning: return .welcome
        case .idle, .attentive, .confirmed, .celebrating: return .ready
        case .working: return .reflection
        case .focused: return .focused
        case .needsAction: return .needsAction
        case .failed: return .failed
        case .finished: return .finished
        case .recovered: return .recovered
        }
    }
}

// Wide banners show Nook's vest; compact slots keep the face readable.
enum OffzoneNookFullBody: String, CaseIterable {
    case welcome, ready, focused, recovered
    var assetName: String { "OffzoneNook-fullbody-" + rawValue }

    static func expression(for state: RoomSpiritState) -> Self? {
        switch state {
        case .welcome, .guiding, .returning: return .welcome
        case .idle, .attentive, .confirmed, .celebrating: return .ready
        case .focused: return .focused
        case .recovered: return .recovered
        case .working, .needsAction, .failed, .finished: return nil
        }
    }
}

struct RoomSpiritMotion: Equatable {
    let breath: CGFloat
    let eyeScale: CGFloat

    static let still = RoomSpiritMotion(breath: 0, eyeScale: 1)

    static func sample(at elapsed: TimeInterval) -> Self {
        .init(breath: CGFloat(sin(elapsed * 2 * .pi / 2.8)), eyeScale: 1)
    }

}

struct RoomSpiritReaction: Equatable {
    var lift: CGFloat = 0
    var rotation: Double = 0
    var scaleY: CGFloat = 1

    static func sample(state: RoomSpiritState, elapsed: TimeInterval) -> Self {
        let duration: Double
        switch state {
        case .welcome, .returning: duration = 0.9
        case .needsAction, .failed: duration = 0.42
        default: duration = 0.72
        }
        guard elapsed >= 0, elapsed < duration else { return .init() }
        let progress = elapsed / duration
        let pulse = sin(progress * .pi)
        switch state {
        case .welcome: return .init(lift: -4 * pulse, scaleY: 1 + 0.025 * pulse)
        case .guiding, .needsAction, .failed: return .init(rotation: sin(progress * 4 * .pi) * 3)
        case .confirmed: return .init(scaleY: 1 - 0.06 * pulse)
        case .celebrating: return .init(lift: -6 * pulse, scaleY: 1 + 0.025 * pulse)
        case .returning: return .init(rotation: sin(progress * 2 * .pi) * 4)
        case .finished: return .init(scaleY: 0.82 + 0.18 * progress)
        case .recovered: return .init(rotation: -2.5 * (1 - progress))
        default: return .init()
        }
    }
}

struct RoomSpirit: View {
    let state: RoomSpiritState
    var interactive = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var isVisible = false
    @State private var animationStart = Date()
    @State private var reactionActive = false
    @State private var helloStart: Date?
    @State private var lastHello = Date.distantPast

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: animationPaused)) { timeline in
                    let elapsed = timeline.date.timeIntervalSince(animationStart)
                    drawing(in: proxy.size,
                            motion: animationPaused || !state.allowsAmbientMotion ? .still : .sample(at: elapsed),
                            reaction: reduceMotion || scenePhase != .active || !isVisible ? .init() : helloStart.map {
                                RoomSpiritReaction.sample(state: .returning, elapsed: timeline.date.timeIntervalSince($0))
                            } ?? RoomSpiritReaction.sample(state: state, elapsed: elapsed))
                }
                .accessibilityHidden(true)
                if interactive {
                    Button(action: sayHello) { Color.clear.contentShape(Rectangle()) }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Say hello to Nook Cat")
                        .accessibilityHint("A small greeting. Your rule stays the same.")
                }
            }
        }
        .onAppear { isVisible = true }
        .onScrollVisibilityChange(threshold: 0.1) { isVisible = $0 }
        .onDisappear { isVisible = false; helloStart = nil }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { helloStart = nil; reactionActive = false }
        }
        .onChange(of: state) { _, _ in helloStart = nil }
        .task(id: state) {
            animationStart = .now
            guard !reduceMotion else { reactionActive = false; return }
            reactionActive = true
            do { try await Task.sleep(for: .milliseconds(920)) }
            catch { return }
            reactionActive = false
        }
        .task(id: helloStart) {
            guard helloStart != nil else { return }
            do { try await Task.sleep(for: .milliseconds(920)) }
            catch { return }
            helloStart = nil
        }
    }

    private var animationPaused: Bool {
        reduceMotion || scenePhase != .active || !isVisible
            || (!state.allowsAmbientMotion && !reactionActive && helloStart == nil)
    }

    private func sayHello() {
        let now = Date()
        guard helloStart == nil, now.timeIntervalSince(lastHello) >= 0.92 else { return }
        lastHello = now
        let quiet = state == .focused || state == .working || state == .needsAction || state == .failed || state == .recovered
        UIAccessibility.post(notification: .announcement, argument: roomString(quiet ? "Nook Cat gives you a quiet hello." : "Nook Cat says hello."))
        if !quiet && !reduceMotion { helloStart = .now }
    }

    private func drawing(in size: CGSize, motion: RoomSpiritMotion, reaction: RoomSpiritReaction) -> some View {
        Image(assetName(for: size))
            .resizable()
            .scaledToFit()
            .frame(width: min(size.width * 0.94, size.height * 1.05), height: size.height * 0.9)
            .scaleEffect(x: 1, y: reaction.scaleY, anchor: .bottom)
            .rotationEffect(.degrees(reaction.rotation), anchor: .bottom)
            .offset(y: motion.breath * 3 + reaction.lift)
            .position(x: size.width / 2, y: size.height / 2)
    }

    private func assetName(for size: CGSize) -> String {
        if size.width >= 160, size.height >= 132, let body = OffzoneNookFullBody.expression(for: state) {
            return body.assetName
        }
        return OffzoneNookExpression.expression(for: state).assetName
    }
}

// Selection mark shared by choice rows and plan rows: filled Pine disc with a Butter check, or a Pine 28% ring.
struct RoomRadio: View {
    let selected: Bool
    var font: Font = .suit(.title2)

    var body: some View {
        Image(systemName: selected ? "checkmark.circle.fill" : "circle")
            .symbolRenderingMode(.palette)
            .foregroundStyle(selected ? Color.roomCanvas : Color.roomOutline, Color.roomAccent)
            .font(font)
    }
}

// Pill button for the one main action on a screen. Inverse (Butter fill, Ink text) sits on Pine surfaces.
private struct RoomPrimaryButton: View {
    let title: String
    var enabled = true
    var inverted = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Text(title).fixedSize(horizontal: false, vertical: true)
                Image(systemName: "chevron.right").fontWeight(.bold).accessibilityHidden(true)
            }
            .font(.suit(.headline, weight: .semibold))
            .multilineTextAlignment(.center)
            .foregroundStyle(!enabled ? Color.roomDisabledText : inverted ? Color.roomInk : Color.roomCanvas)
            .padding(.horizontal, 24).padding(.vertical, 14)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(!enabled ? Color.roomDisabledFill : inverted ? Color.roomCanvas : Color.roomAccent, in: Capsule())
        }
        .buttonStyle(.plain).disabled(!enabled)
    }
}

// Pine fill on a Pine 14% track, 4pt tall.
private struct RoomProgressStyle: ProgressViewStyle {
    func makeBody(configuration: Configuration) -> some View {
        let fraction = configuration.fractionCompleted ?? 0
        Capsule().fill(Color.roomLine)
            .frame(height: 4)
            .overlay(alignment: .leading) {
                GeometryReader { proxy in
                    Capsule().fill(Color.roomAccent).frame(width: proxy.size.width * fraction)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityValue(Text(fraction, format: .percent.precision(.fractionLength(0))))
    }
}

extension View {
    // Card: ivory by default, mint (positive) or soft butter (attention) through `fill`. Radius 24, or 16 for rows.
    func editorialPanel(
        _ fill: Color = .roomCard,
        cornerRadius: CGFloat = 24,
        outlined: Bool = true
    ) -> some View {
        background(fill)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .strokeBorder(outlined ? Color.roomLine : Color.clear, lineWidth: 1)
            }
    }

    // Text field: ivory fill, Pine 28% outline, radius 16.
    func roomField() -> some View {
        background(Color.roomCard, in: RoundedRectangle(cornerRadius: 16))
            .overlay { RoundedRectangle(cornerRadius: 16).strokeBorder(Color.roomOutline, lineWidth: 1) }
    }

    func bottomBarStyle() -> some View {
        padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 8)
            .background(Color.roomCanvas)
    }
}


// Palette (DESIGN.md D-47): black is text only, Pine is the one accent for actions and active states.
extension Color {
    static let roomCanvas = Color(red: 247 / 255, green: 240 / 255, blue: 199 / 255)  // Butter #F7F0C7, also text on Pine
    static let roomInk = Color(red: 31 / 255, green: 42 / 255, blue: 34 / 255)        // #1F2A22
    static let roomInkSecondary = Color(red: 94 / 255, green: 102 / 255, blue: 96 / 255)  // #5E6660
    static let roomInkTertiary = roomInkSecondary
    static let roomAccent = Color(red: 61 / 255, green: 91 / 255, blue: 63 / 255)     // Pine #3D5B3F
    static let roomAction = roomAccent
    static let roomOutline = roomAccent.opacity(0.28)
    static let roomLine = roomAccent.opacity(0.14)
    static let roomDisabledFill = roomInk.opacity(0.10)  // Disabled controls: Ink 10% fill, Ink 38% label
    static let roomDisabledText = roomInk.opacity(0.38)
    static let roomWarning = Color(red: 137 / 255, green: 104 / 255, blue: 57 / 255)
    static let roomCard = Color(red: 250 / 255, green: 247 / 255, blue: 232 / 255)    // Ivory #FAF7E8, default card
    static let roomMint = Color(red: 232 / 255, green: 238 / 255, blue: 221 / 255)    // #E8EEDD, positive / selected / restored
    static let roomButter = Color(red: 239 / 255, green: 229 / 255, blue: 171 / 255)  // Soft butter #EFE5AB, attention / setup
}

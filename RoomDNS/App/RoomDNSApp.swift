import SwiftUI
import GoogleSignIn

@main
struct RoomDNSApp: App {
    @StateObject private var model = AppModel()
    @StateObject private var account = AccountStore()

    var body: some Scene {
        WindowGroup {
            ContentView(model: model)
                .environmentObject(model)
                .environmentObject(account)
                .font(.suit(.body))
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
        }
    }
}


// Single-weight bundled faces (Jua by default; Fredoka-SemiBold for accented European locales).
// Every weight resolves to the same static face to avoid faux bold; relativeTo retains Dynamic Type.
extension Font {
    private static let fredokaLanguages: Set<String> = ["de", "es", "fr", "it", "pt"]

    static func suit(_ style: Font.TextStyle, weight: Font.Weight? = nil) -> Font {
        custom(suitFace(weight ?? (style == .headline ? .semibold : .regular)), size: suitSize(style), relativeTo: style)
    }

    static func suitFace(_ weight: Font.Weight) -> String {
        suitFace(forLanguage: Bundle.main.preferredLocalizations.first)
    }

    static func suitFace(forLanguage language: String?) -> String {
        let primary = (language ?? "en")
            .lowercased()
            .split(whereSeparator: { $0 == "-" || $0 == "_" })
            .first
            .map(String.init) ?? "en"
        return fredokaLanguages.contains(primary) ? "Fredoka-SemiBold" : "Jua-Regular"
    }

    static func suitSize(_ style: Font.TextStyle) -> CGFloat {
        switch style {
        case .largeTitle: return 32
        case .title: return 28
        case .title2: return 24
        case .title3: return 20
        case .headline: return 17
        case .body, .callout: return 16
        case .subheadline: return 15
        default: return 14
        }
    }
}

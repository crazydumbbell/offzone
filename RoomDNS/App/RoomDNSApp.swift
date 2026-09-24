import SwiftUI
import GoogleSignIn

@main
struct RoomDNSApp: App {
    @StateObject private var model = AppModel()
    @StateObject private var account = AccountStore()

    var body: some Scene {
        WindowGroup {
            ContentView(model: model)
                .environmentObject(account)
                .font(.suit(.body))
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
        }
    }
}


// Named, bundled faces preserve real weights; relativeTo retains Dynamic Type.
extension Font {
    static func suit(_ style: Font.TextStyle, weight: Font.Weight? = nil) -> Font {
        custom(suitFace(weight ?? (style == .headline ? .semibold : .regular)), size: suitSize(style), relativeTo: style)
    }

    static func suitFace(_ weight: Font.Weight) -> String {
        switch weight {
        case .bold, .heavy, .black: return "SUIT-Bold"
        case .semibold: return "SUIT-SemiBold"
        case .medium: return "SUIT-Medium"
        default: return "SUIT-Regular"
        }
    }

    static func suitSize(_ style: Font.TextStyle) -> CGFloat {
        switch style {
        case .largeTitle: return 32
        case .title: return 28
        case .title2: return 24
        case .title3: return 20
        case .headline: return 17
        case .body, .callout: return 16
        case .subheadline: return 16
        default: return 14
        }
    }
}

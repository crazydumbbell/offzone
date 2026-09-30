import ManagedSettings
import ManagedSettingsUI
import UIKit

final class RoomDNSShieldConfiguration: ShieldConfigurationDataSource {
    private func localized(_ key: String) -> String {
        Bundle.main.localizedString(forKey: key, value: key, table: nil)
    }

    private var configuration: ShieldConfiguration {
        // Same palette as the app (DESIGN.md D-47): Butter canvas, Ink text, Pine action.
        let background = UIColor(red: 247 / 255, green: 240 / 255, blue: 199 / 255, alpha: 1)
        let ink = UIColor(red: 31 / 255, green: 42 / 255, blue: 34 / 255, alpha: 1)
        let secondary = UIColor(red: 94 / 255, green: 102 / 255, blue: 96 / 255, alpha: 1)
        let pine = UIColor(red: 61 / 255, green: 91 / 255, blue: 63 / 255, alpha: 1)
        let buttonTitle = if #available(iOS 26.5, *) {
            localized("View rule")
        } else {
            localized("Done")
        }

        return ShieldConfiguration(
            backgroundBlurStyle: .systemUltraThinMaterialLight,
            backgroundColor: background,
            icon: UIImage(systemName: "line.3.horizontal.decrease"),
            title: .init(text: localized("Your rule is active"), color: ink),
            subtitle: .init(
                text: localized("You chose to put this app or website aside. Open Offzone to review your rule or restore access."),
                color: secondary
            ),
            primaryButtonLabel: .init(text: buttonTitle, color: background),
            primaryButtonBackgroundColor: pine
        )
    }

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        configuration
    }

    override func configuration(
        shielding application: Application,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        configuration
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        configuration
    }

    override func configuration(
        shielding webDomain: WebDomain,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        configuration
    }
}

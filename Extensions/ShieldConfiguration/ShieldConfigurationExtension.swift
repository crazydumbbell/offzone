import ManagedSettings
import ManagedSettingsUI
import UIKit

final class RoomDNSShieldConfiguration: ShieldConfigurationDataSource {
    private func localized(_ key: String) -> String {
        Bundle.main.localizedString(forKey: key, value: key, table: nil)
    }

    private var configuration: ShieldConfiguration {
        let background = UIColor(red: 229 / 255, green: 241 / 255, blue: 246 / 255, alpha: 1)
        let ink = UIColor(red: 17 / 255, green: 17 / 255, blue: 17 / 255, alpha: 1)
        let secondary = UIColor(red: 76 / 255, green: 76 / 255, blue: 76 / 255, alpha: 1)
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
            primaryButtonLabel: .init(text: buttonTitle, color: .white),
            primaryButtonBackgroundColor: ink
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

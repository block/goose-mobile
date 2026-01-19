import SwiftUI

/// Global notice types that can be displayed to the user
enum AppNotice: Equatable {
    case tunnelDisabled
    case tunnelUnreachable
    case appNeedsUpdate

    var title: String {
        switch self {
        case .tunnelDisabled:
            return "Unable to reach goose"
        case .tunnelUnreachable:
            return "Unable to reach goose"
        case .appNeedsUpdate:
            return "Update Required"
        }
    }

    var message: String {
        switch self {
        case .tunnelDisabled:
            return
                "Temporarily unable to reach your agent, please try again later"
        case .tunnelUnreachable:
            return
                "Temporarily unable to reach your agent, please try again later"
        case .appNeedsUpdate:
            return
                "The desktop app may need to be updated to work with this version of the mobile app."
        }
    }

    var icon: String {
        switch self {
        case .tunnelDisabled, .tunnelUnreachable:
            return "network.slash"
        case .appNeedsUpdate:
            return "arrow.down.circle"
        }
    }
}

/// Shared global state for displaying app-wide notices
class AppNoticeCenter: ObservableObject {
    static let shared = AppNoticeCenter()

    @Published var activeNotice: AppNotice?

    private init() {}

    /// Set a notice to be displayed
    func setNotice(_ notice: AppNotice) {
        DispatchQueue.main.async {
            self.activeNotice = notice
        }
    }

    /// Clear the current notice
    func clearNotice() {
        DispatchQueue.main.async {
            self.activeNotice = nil
        }
    }
}

//
//  ThemeManager.swift
//  Goose
//
//  Stub implementation for theme management
//  Full PR #1 implementation deferred per migration.md
//

import SwiftUI

class ThemeManager: ObservableObject {
    static let shared = ThemeManager()
    
    @Published var isDarkMode: Bool {
        didSet {
            UserDefaults.standard.set(isDarkMode, forKey: "isDarkMode")
        }
    }
    
    init() {
        self.isDarkMode = UserDefaults.standard.bool(forKey: "isDarkMode")
    }
    
    // Color properties matching TaskDetailView requirements
    var primaryTextColor: Color {
        isDarkMode ? .white : .black
    }
    
    var secondaryTextColor: Color {
        isDarkMode ? Color(.systemGray) : Color(.systemGray)
    }
    
    var backgroundColor: Color {
        isDarkMode ? Color(.systemBackground) : Color(.systemBackground)
    }
    
    var chatInputBackgroundColor: Color {
        Color(.systemGray6)
    }
}

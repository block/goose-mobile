//
//  FavoriteSessionsStorage.swift
//  Goose
//
//  Manages favorite sessions using UserDefaults
//

import Foundation

class FavoriteSessionsStorage: ObservableObject {
    static let shared = FavoriteSessionsStorage()
    
    @Published private(set) var favoriteSessionIds: Set<String> = []
    
    private let userDefaultsKey = "favorite_sessions"
    
    private init() {
        loadFavorites()
    }
    
    private func loadFavorites() {
        if let data = UserDefaults.standard.array(forKey: userDefaultsKey) as? [String] {
            favoriteSessionIds = Set(data)
            print("✨ Loaded \(favoriteSessionIds.count) favorite sessions")
        }
    }
    
    private func saveFavorites() {
        let array = Array(favoriteSessionIds)
        UserDefaults.standard.set(array, forKey: userDefaultsKey)
        print("💾 Saved \(favoriteSessionIds.count) favorite sessions")
    }
    
    func isFavorite(_ sessionId: String) -> Bool {
        return favoriteSessionIds.contains(sessionId)
    }
    
    func toggleFavorite(_ sessionId: String) {
        if favoriteSessionIds.contains(sessionId) {
            favoriteSessionIds.remove(sessionId)
            print("⭐ Removed favorite: \(sessionId)")
        } else {
            favoriteSessionIds.insert(sessionId)
            print("⭐ Added favorite: \(sessionId)")
        }
        saveFavorites()
    }
    
    func addFavorite(_ sessionId: String) {
        guard !favoriteSessionIds.contains(sessionId) else { return }
        favoriteSessionIds.insert(sessionId)
        saveFavorites()
    }
    
    func removeFavorite(_ sessionId: String) {
        guard favoriteSessionIds.contains(sessionId) else { return }
        favoriteSessionIds.remove(sessionId)
        saveFavorites()
    }
}

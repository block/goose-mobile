import Foundation

/// Handles all trial mode logic - manages the single trial session
class TrialMode {
    static let shared = TrialMode()

    private let trialSessionKey = "trial_session_id"

    private init() {}

    // MARK: - Session Management

    /// Get the existing trial session ID, or create a new one if needed
    func getOrCreateTrialSession() async throws -> (sessionId: String, messages: [Message]) {
        // Check if we have an existing trial session
        if let existingSessionId = getSavedTrialSessionId() {
            print("📱 Found existing trial session: \(existingSessionId)")

            // Try to resume it
            do {
                let (sessionId, messages) = try await GooseAPIService.shared.resumeAgent(
                    sessionId: existingSessionId)
                print("✅ Successfully resumed trial session")
                return (sessionId, messages)
            } catch {
                print("⚠️ Failed to resume trial session, creating new one: \(error)")
                // If resume fails, clear the saved ID and create new
                clearTrialSession()
            }
        }

        // Create new trial session
        print("📱 Creating new trial session")
        let (sessionId, messages) = try await GooseAPIService.shared.startAgent()
        saveTrialSessionId(sessionId)
        print("✅ Created and saved new trial session: \(sessionId)")
        return (sessionId, messages)
    }

    // MARK: - Storage

    private func getSavedTrialSessionId() -> String? {
        let sessionId = UserDefaults.standard.string(forKey: trialSessionKey)
        return sessionId?.isEmpty == false ? sessionId : nil
    }

    func saveTrialSessionId(_ sessionId: String) {
        UserDefaults.standard.set(sessionId, forKey: trialSessionKey)
    }

    private func clearTrialSession() {
        UserDefaults.standard.removeObject(forKey: trialSessionKey)
    }
}

import Foundation
import UIKit

// MARK: - Agent Configuration Model
struct AgentConfiguration: Identifiable, Codable, Equatable {
    let id: String
    var name: String?  // Optional custom name
    let url: String
    let secret: String
    var lastUsed: Date
    
    init(id: String = UUID().uuidString, name: String? = nil, url: String, secret: String, lastUsed: Date = Date()) {
        self.id = id
        self.name = name
        self.url = url
        self.secret = secret
        self.lastUsed = lastUsed
    }
    
    /// Generate a default name based on the URL pattern
    static func defaultName(for url: String) -> String? {
        let lowercaseURL = url.lowercased()
        
        // Check for demo-goosed pattern
        if lowercaseURL.contains("demo-goosed") {
            return "Trial"
        }
        
        // Check for cloudflare-tunnel-proxy pattern
        if lowercaseURL.contains("cloudflare-tunnel-proxy") {
            return "Desktop"
        }
        
        // No default name for other patterns
        return nil
    }
    
    /// Display name for the agent (custom name or formatted URL)
    var displayName: String {
        if let name = name, !name.isEmpty {
            return name
        }
        // Format URL for display (remove protocol and port)
        var formatted = url
        formatted = formatted.replacingOccurrences(of: "https://", with: "")
        formatted = formatted.replacingOccurrences(of: "http://", with: "")
        formatted = formatted.replacingOccurrences(of: ":443", with: "")
        
        // Truncate if too long
        if formatted.count > 30 {
            return String(formatted.prefix(27)) + "..."
        }
        return formatted
    }
    
    /// Subtitle showing the URL (only shown when custom name is set)
    var subtitle: String? {
        guard name != nil && !name!.isEmpty else { return nil }
        var formatted = url
        formatted = formatted.replacingOccurrences(of: "https://", with: "")
        formatted = formatted.replacingOccurrences(of: "http://", with: "")
        formatted = formatted.replacingOccurrences(of: ":443", with: "")
        return formatted
    }
}

// MARK: - Agent Storage Manager
class AgentStorage: ObservableObject {
    static let shared = AgentStorage()
    
    @Published var savedAgents: [AgentConfiguration] = []
    @Published var currentAgentId: String?
    
    private let agentsKey = "saved_servers"  // Keep old key for backwards compatibility
    private let currentAgentKey = "current_server_id"  // Keep old key for backwards compatibility
    
    private init() {
        loadAgents()
        loadCurrentAgentId()
        ensureCurrentAgentInList()
    }
    
    /// Ensure the current UserDefaults configuration is in the agent list
    func ensureCurrentAgentInList() {
        let currentURL = UserDefaults.standard.string(forKey: "goose_base_url") ?? ""
        let currentSecret = UserDefaults.standard.string(forKey: "goose_secret_key") ?? ""
        
        // Skip if no valid configuration
        guard !currentURL.isEmpty, !currentSecret.isEmpty else { return }
        
        // Check if this agent already exists
        if let existing = savedAgents.first(where: { $0.url == currentURL && $0.secret == currentSecret }) {
            // Set it as current
            currentAgentId = existing.id
            saveCurrentAgentId()
        } else {
            // Add the current configuration with a default name if URL matches a pattern
            let defaultName = AgentConfiguration.defaultName(for: currentURL)
            let agent = AgentConfiguration(name: defaultName, url: currentURL, secret: currentSecret)
            savedAgents.insert(agent, at: 0)
            currentAgentId = agent.id
            saveAgents()
            saveCurrentAgentId()
        }
    }
    
    /// Load saved agents from UserDefaults
    private func loadAgents() {
        guard let data = UserDefaults.standard.data(forKey: agentsKey),
              let agents = try? JSONDecoder().decode([AgentConfiguration].self, from: data) else {
            savedAgents = []
            return
        }
        // Sort by last used (most recent first)
        savedAgents = agents.sorted { $0.lastUsed > $1.lastUsed }
    }
    
    /// Save agents to UserDefaults
    private func saveAgents() {
        guard let data = try? JSONEncoder().encode(savedAgents) else { return }
        UserDefaults.standard.set(data, forKey: agentsKey)
        UserDefaults.standard.synchronize()
    }
    
    /// Load current agent ID
    private func loadCurrentAgentId() {
        currentAgentId = UserDefaults.standard.string(forKey: currentAgentKey)
    }
    
    /// Save current agent ID
    private func saveCurrentAgentId() {
        if let id = currentAgentId {
            UserDefaults.standard.set(id, forKey: currentAgentKey)
        } else {
            UserDefaults.standard.removeObject(forKey: currentAgentKey)
        }
        UserDefaults.standard.synchronize()
    }
    
    /// Add or update an agent configuration
    func saveAgent(_ agent: AgentConfiguration) {
        // Check if agent already exists (by URL and secret)
        if let index = savedAgents.firstIndex(where: { $0.url == agent.url && $0.secret == agent.secret }) {
            // Update existing agent
            var updated = savedAgents[index]
            updated.name = agent.name
            updated.lastUsed = Date()
            savedAgents[index] = updated
            
            // If this is the current agent, update the ID
            if currentAgentId == savedAgents[index].id {
                currentAgentId = updated.id
                saveCurrentAgentId()
            }
        } else {
            // Add new agent
            var newAgent = agent
            newAgent.lastUsed = Date()
            savedAgents.insert(newAgent, at: 0)
        }
        
        // Sort by last used
        savedAgents.sort { $0.lastUsed > $1.lastUsed }
        saveAgents()
    }
    
    /// Get current agent configuration
    var currentAgent: AgentConfiguration? {
        guard let id = currentAgentId else { return nil }
        return savedAgents.first { $0.id == id }
    }
    
    /// Switch to a saved agent
    func switchToAgent(_ agent: AgentConfiguration) {
        // Update last used
        if let index = savedAgents.firstIndex(where: { $0.id == agent.id }) {
            savedAgents[index].lastUsed = Date()
            saveAgents()
        }
        
        // Set as current
        currentAgentId = agent.id
        saveCurrentAgentId()
        
        // Apply to UserDefaults
        UserDefaults.standard.set(agent.url, forKey: "goose_base_url")
        UserDefaults.standard.set(agent.secret, forKey: "goose_secret_key")
        UserDefaults.standard.synchronize()
        
        // Notify that configuration changed
        NotificationCenter.default.post(name: Notification.Name("RefreshSessions"), object: nil)
    }
    
    /// Delete an agent
    func deleteAgent(_ agent: AgentConfiguration) {
        savedAgents.removeAll { $0.id == agent.id }
        
        // If this was the current agent, clear current
        if currentAgentId == agent.id {
            currentAgentId = nil
            saveCurrentAgentId()
        }
        
        saveAgents()
    }
    
    /// Get agent by current UserDefaults configuration
    func getAgentFromCurrentDefaults() -> AgentConfiguration? {
        let url = UserDefaults.standard.string(forKey: "goose_base_url") ?? ""
        let secret = UserDefaults.standard.string(forKey: "goose_secret_key") ?? ""
        
        guard !url.isEmpty, !secret.isEmpty else { return nil }
        
        // Check if this matches an existing agent
        return savedAgents.first { $0.url == url && $0.secret == secret }
    }
    
    /// Mark current UserDefaults as an agent
    func saveCurrentConfiguration(withName name: String? = nil) -> AgentConfiguration {
        let url = UserDefaults.standard.string(forKey: "goose_base_url") ?? ""
        let secret = UserDefaults.standard.string(forKey: "goose_secret_key") ?? ""
        
        // Check if this configuration already exists
        if let existing = savedAgents.first(where: { $0.url == url && $0.secret == secret }) {
            // Update name if provided
            var updated = existing
            if let name = name {
                updated.name = name
            }
            updated.lastUsed = Date()
            saveAgent(updated)
            currentAgentId = updated.id
            saveCurrentAgentId()
            return updated
        } else {
            // Create new agent with default name if no custom name provided
            let finalName = name ?? AgentConfiguration.defaultName(for: url)
            let agent = AgentConfiguration(name: finalName, url: url, secret: secret)
            saveAgent(agent)
            currentAgentId = agent.id
            saveCurrentAgentId()
            return agent
        }
    }
}

/// Handles configuration of the app from QR codes and URLs
class ConfigurationHandler: ObservableObject {
    static let shared = ConfigurationHandler()
    
    @Published var isConfiguring = false
    @Published var configurationError: String?
    @Published var configurationSuccess = false
    @Published var isTailscaleError = false
    @Published var showSaveAgentDialog = false
    
    private init() {}
    
    /// Configuration data structure matching the format from launch_tunnel.sh
    struct ConfigurationData: Codable {
        let url: String
        let secret: String
    }
    
    
    /// Handles incoming URL from QR code scan or deep link
    /// Expected format: goosechat://configure?data=<url-encoded-json>
    func handleURL(_ url: URL) -> Bool {
        guard url.scheme == "goosechat" else {
            print("❌ Invalid URL scheme: \(url.scheme ?? "nil")")
            return false
        }
        
        guard url.host == "configure" else {
            print("❌ Unknown URL host: \(url.host ?? "nil")")
            return false
        }
        
        // Parse query parameters
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let queryItems = components.queryItems,
              let dataParam = queryItems.first(where: { $0.name == "data" })?.value else {
            print("❌ Missing 'data' parameter in URL")
            self.configurationError = "Invalid configuration link"
            return false
        }
        
        // URL decode the parameter
        guard let decodedData = dataParam.removingPercentEncoding,
              let jsonData = decodedData.data(using: .utf8) else {
            print("❌ Failed to decode URL parameter")
            self.configurationError = "Failed to decode configuration"
            return false
        }
        
        
        do {
            // Parse the JSON configuration
            let config = try JSONDecoder().decode(ConfigurationData.self, from: jsonData)
            
            // Apply the configuration
            applyConfiguration(config)
            return true
            
        } catch {
            print("❌ Failed to parse configuration: \(error)")
            self.configurationError = "Invalid configuration format"
            return false
        }
    }
    
    /// Apply the configuration to UserDefaults
    private func applyConfiguration(_ config: ConfigurationData) {
        isConfiguring = true
        configurationError = nil
        configurationSuccess = false
        
        let baseURL: String
        if config.url.hasPrefix("http://") || config.url.hasPrefix("https://") {
            // Already has protocol, use as-is but remove :443 if present
            baseURL = config.url.replacingOccurrences(of: ":443", with: "")
        } else {
            // No protocol, add https://
            baseURL = "https://\(config.url.replacingOccurrences(of: ":443", with: ""))"
        }
        
        
        // Save to UserDefaults
        UserDefaults.standard.set(baseURL, forKey: "goose_base_url")
        UserDefaults.standard.set(config.secret, forKey: "goose_secret_key")
        UserDefaults.standard.synchronize()
        
        // Test the connection
        Task {
            let success = await GooseAPIService.shared.testConnection()
            
            await MainActor.run {
                self.isConfiguring = false
                
                if success {
                    self.configurationSuccess = true
                    self.configurationError = nil
                    
                    // Ensure the new configuration is in the agent list
                    AgentStorage.shared.ensureCurrentAgentInList()
                    
                    // Notify that configuration changed - ADDED THIS FIX
                    NotificationCenter.default.post(name: Notification.Name("RefreshSessions"), object: nil)
                    
                    // Clear success flag after 3 seconds
                    DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                        self.configurationSuccess = false
                    }
                } else {
                    // Check if this is a Tailscale URL (100.x.x.x range or .ts.net domain)
                    let originalError = GooseAPIService.shared.connectionError ?? "Connection test failed"
                    if self.isTailscaleURL(baseURL) {
                        self.isTailscaleError = true
                        self.configurationError = "Please log in to Tailscale to connect to your agent"
                    } else {
                        self.isTailscaleError = false
                        self.configurationError = originalError
                    }
                    print("❌ Configuration test failed: \(self.configurationError ?? "Unknown error")")
                }
            }
        }
    }

    /// Check if a URL is a Tailscale URL
    /// Returns true for both 100.x.x.x IP addresses and .ts.net domains
    func isTailscaleURL(_ urlString: String) -> Bool {
        return urlString.hasPrefix("http://100.") || 
               urlString.hasPrefix("https://100.") ||
               urlString.contains(".ts.net")
    }
    
    /// Clear any configuration errors
    func clearError() {
        configurationError = nil
        isTailscaleError = false
    }
    
    /// Open Tailscale app or App Store
    func openTailscale() {
        // Try to open the Tailscale app first
        if let tailscaleURL = URL(string: "tailscale://"), 
           UIApplication.shared.canOpenURL(tailscaleURL) {
            UIApplication.shared.open(tailscaleURL)
        } else {
            // If app isn't installed, open App Store
            if let appStoreURL = URL(string: "https://apps.apple.com/app/tailscale/id1470499037") {
                UIApplication.shared.open(appStoreURL)
            }
        }
    }
    
    /// Reset configuration to demo/trial mode
    func resetToTrialMode() {
        let config = ConfigurationData(
            url: "https://demo-goosed.fly.dev",
            secret: "test"
        )
        applyConfiguration(config)
    }
    
    /// Save current configuration as an agent
    func saveCurrentConfigurationAsAgent(withName name: String? = nil) {
        let agent = AgentStorage.shared.saveCurrentConfiguration(withName: name)
    }
    
    /// Switch to a saved agent configuration
    func switchToAgent(_ agent: AgentConfiguration) {
        AgentStorage.shared.switchToAgent(agent)
        
        // Test the connection
        Task {
            _ = await GooseAPIService.shared.testConnection()
        }
    }
}

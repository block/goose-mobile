import Combine
import Foundation

class GooseAPIService: ObservableObject {
    static let shared = GooseAPIService()

    @Published var isConnected = false
    @Published var connectionError: String?

    // The configured URL
    private var baseURL: String {
        UserDefaults.standard.string(forKey: "goose_base_url") ?? "http://127.0.0.1:62996"
    }

    private var secretKey: String {
        UserDefaults.standard.string(forKey: "goose_secret_key") ?? "test"
    }

    // Trial mode detection
    var isTrialMode: Bool {
        return baseURL.contains("demo-goosed.fly.dev")
    }
    
    // Private network detection (tailscale, private IPs)
    private var isPrivateNetworkURL: Bool {
        return baseURL.contains(".ts.net") || baseURL.contains("://100.")
    }

    private init() {}
    
    // MARK: - Centralized Error Handling
    
    /// Check if an error is a transient network error worth retrying
    private func isTransientError(_ error: Error) -> Bool {
        guard let urlError = error as? URLError else { return false }
        
        // Only retry these specific transient network errors
        switch urlError.code {
        case .timedOut, .networkConnectionLost, .notConnectedToInternet:
            return true
        default:
            return false
        }
    }
    
    /// Retry wrapper for network requests - handles transient errors gracefully
    private func fetchWithRetry<T>(
        maxAttempts: Int = 2,
        operation: () async throws -> T
    ) async -> Result<T, Error> {
        var lastError: Error?
        
        for attempt in 1...maxAttempts {
            do {
                let result = try await operation()
                return .success(result)
            } catch {
                lastError = error
                
                // Only retry on transient network errors
                if isTransientError(error) && attempt < maxAttempts {
                    // Quick retry once (1 second delay)
                    try? await Task.sleep(nanoseconds: 1_000_000_000)
                    print("🔄 Retrying request (attempt \(attempt + 1)/\(maxAttempts))...")
                    continue
                }
                
                // Other errors or max attempts reached = fail immediately
                break
            }
        }
        
        return .failure(lastError ?? APIError.unknown)
    }
    
    /// Handle API errors and set appropriate notices
    private func handleAPIError(_ error: Error, context: String = "") {
        if isTrialMode { return }
        
        if let apiError = error as? APIError {
            switch apiError {
            case .httpError(let code, _):
                if code == 503 {
                    AppNoticeCenter.shared.setNotice(.tunnelDisabled)
                }
            case .decodingError:
                AppNoticeCenter.shared.setNotice(.appNeedsUpdate)
            default:
                break
            }
        } else if error is DecodingError {
            AppNoticeCenter.shared.setNotice(.appNeedsUpdate)
        } else if let urlError = error as? URLError {
            // Connection failures on private networks likely mean tunnel isn't running
            if urlError.code == .cannotConnectToHost && isPrivateNetworkURL {
                AppNoticeCenter.shared.setNotice(.tunnelUnreachable)
            }
        }
        
        if !context.isEmpty {
            print("🚨 \(context): \(error)")
        }
    }
    
    /// Handle HTTP status codes and set appropriate notices
    private func handleHTTPStatus(_ statusCode: Int, body: String, context: String = "") {
        if isTrialMode { return }
        
        if statusCode == 503 {
            AppNoticeCenter.shared.setNotice(.tunnelDisabled)
        }
        
        if !context.isEmpty {
            print("🚨 \(context) - HTTP \(statusCode): \(body)")
        }
    }

    // MARK: - Proper SSE Streaming Implementation
    func startChatStreamWithSSE(
        messages: [Message],
        sessionId: String? = nil,
        onEvent: @escaping (SSEEvent) -> Void,
        onComplete: @escaping () -> Void,
        onError: @escaping (Error) -> Void
    ) async -> URLSessionDataTask? {

        guard let url = URL(string: "\(baseURL)/reply") else {
            onError(APIError.invalidURL)
            return nil
        }

        // Log the sessionId we're about to use
        let actualSessionId = sessionId ?? ""
        
        // Server expects user_message (the new message) and conversation_so_far (history)
        // The last message in the array should be the new user message
        guard let userMessage = messages.last else {
            onError(APIError.noData)
            return nil
        }
        
        // All messages except the last one are the conversation history
        let conversationHistory = messages.count > 1 ? Array(messages.dropLast()) : nil
        
        let request = ChatRequest(
            userMessage: userMessage,
            conversationSoFar: conversationHistory,
            sessionId: actualSessionId,
            recipeName: nil,
            recipeVersion: nil
        )

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.setValue(secretKey, forHTTPHeaderField: "X-Secret-Key")
        urlRequest.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        urlRequest.setValue("no-cache", forHTTPHeaderField: "Cache-Control")

        do {
            let requestData = try JSONEncoder().encode(request)
            urlRequest.httpBody = requestData
            
            // Sign the request with Ed25519 if signer is available

            // Debug logging
            if let bodyString = String(data: requestData, encoding: .utf8) {
                if let jsonData = bodyString.data(using: .utf8),
                    let prettyJson = try? JSONSerialization.jsonObject(with: jsonData),
                    let prettyData = try? JSONSerialization.data(
                        withJSONObject: prettyJson, options: .prettyPrinted),
                    let prettyString = String(data: prettyData, encoding: .utf8)
                {
                    print(prettyString)
                } else {
                    print(bodyString)
                }
            }
        } catch {
            onError(error)
            return nil
        }

        // Create a custom URLSessionDataDelegate to handle streaming
        // Errors are handled by ChatView which switches to polling mode
        let delegate = SSEDelegate(
            onEvent: onEvent,
            onComplete: onComplete,
            onError: onError
        )

        let session = URLSession(configuration: .default, delegate: delegate, delegateQueue: nil)
        let task = session.dataTask(with: urlRequest)

        // Store the delegate reference to prevent deallocation
        objc_setAssociatedObject(task, "delegate", delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        
        // Store the session reference so it can be invalidated when task completes/cancels
        objc_setAssociatedObject(task, "session", session, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)

        task.resume()
        return task
    }

    // MARK: - Connection Test
    func testConnection() async -> Bool {
        let fullURL = "\(baseURL)/status"

        guard let url = URL(string: fullURL) else {
            print("❌ Failed to create URL from: '\(fullURL)'")
            await MainActor.run {
                self.connectionError = "Invalid URL: \(fullURL)"
                self.isConnected = false
            }
            return false
        }

        var request = URLRequest(url: url)
        request.setValue(secretKey, forHTTPHeaderField: "X-Secret-Key")

        do {
            let (data, response) = try await URLSession.shared.data(for: request)

            if let httpResponse = response as? HTTPURLResponse {
                let connected = httpResponse.statusCode == 200
                await MainActor.run {
                    self.isConnected = connected
                    if connected {
                        self.connectionError = nil
                    } else {
                        // Get error details for connection test
                        let errorBody = String(data: data, encoding: .utf8) ?? "No error details"
                        handleHTTPStatus(httpResponse.statusCode, body: errorBody, context: "Connection Test Failed")
                        self.connectionError = "HTTP \(httpResponse.statusCode): \(errorBody)"
                    }
                }
                return connected
            } else {
                await MainActor.run {
                    self.isConnected = false
                    self.connectionError = "Invalid response"
                }
                return false
            }
        } catch {
            await MainActor.run {
                self.isConnected = false
                self.connectionError = error.localizedDescription
            }
            return false
        }
    }

    // MARK: - Session Creation
    func startAgent(workingDir: String? = nil) async throws -> (
        sessionId: String, messages: [Message]
    ) {
        do {
            guard let url = URL(string: "\(self.baseURL)/agent/start") else {
                throw APIError.invalidURL
            }

            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(self.secretKey, forHTTPHeaderField: "X-Secret-Key")

            // For remote goosed: use "." (current directory where goosed is running)
            // This matches CLI behavior which uses std::env::current_dir()
            let effectiveWorkingDir = workingDir ?? "."
            
            let body: [String: Any] = ["working_dir": effectiveWorkingDir]
            request.httpBody = try JSONSerialization.data(withJSONObject: body)

            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIError.invalidResponse
            }

            if httpResponse.statusCode != 200 {
                let errorBody = String(data: data, encoding: .utf8) ?? "No error details"
                handleHTTPStatus(httpResponse.statusCode, body: errorBody, context: "Start Agent")
                throw APIError.httpError(httpResponse.statusCode, errorBody)
            }

            let agentResponse = try JSONDecoder().decode(AgentResponse.self, from: data)
            return (agentResponse.id, agentResponse.conversation ?? [])
        }
        catch let error as DecodingError {
            handleAPIError(APIError.decodingError(error), context: "Start Agent")
            throw APIError.decodingError(error)
        }
    }

    // MARK: - Session Resume
    func resumeAgent(sessionId: String, loadModelAndExtensions: Bool = false) async throws -> (sessionId: String, messages: [Message]) {
        do {
            // If loadModelAndExtensions=false, fetch session directly to get messages
            // /agent/resume no longer returns messages when loadModelAndExtensions=false
            if !loadModelAndExtensions {
                guard let url = URL(string: "\(self.baseURL)/sessions/\(sessionId)") else {
                    throw APIError.invalidURL
                }
                
                var request = URLRequest(url: url)
                request.httpMethod = "GET"
                request.setValue(self.secretKey, forHTTPHeaderField: "X-Secret-Key")
                
                let (data, response) = try await URLSession.shared.data(for: request)
                
                guard let httpResponse = response as? HTTPURLResponse else {
                    throw APIError.invalidResponse
                }
                
                if httpResponse.statusCode != 200 {
                    let errorBody = String(data: data, encoding: .utf8) ?? "No error details"
                    handleHTTPStatus(httpResponse.statusCode, body: errorBody, context: "Fetch Session")
                    throw APIError.httpError(httpResponse.statusCode, errorBody)
                }
                
                let session = try JSONDecoder().decode(SessionResponse.self, from: data)
                let messages = session.conversation ?? []
                
                return (session.id, messages)
            }
            
            // When loadModelAndExtensions=true, use /agent/resume
            guard let url = URL(string: "\(self.baseURL)/agent/resume") else {
                throw APIError.invalidURL
            }

            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(self.secretKey, forHTTPHeaderField: "X-Secret-Key")

            let body: [String: Any] = [
                "session_id": sessionId,
                "load_model_and_extensions": loadModelAndExtensions
            ]
            request.httpBody = try JSONSerialization.data(withJSONObject: body)

            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIError.invalidResponse
            }

            if httpResponse.statusCode != 200 {
                let errorBody = String(data: data, encoding: .utf8) ?? "No error details"
                handleHTTPStatus(httpResponse.statusCode, body: errorBody, context: "Resume Agent")
                throw APIError.httpError(httpResponse.statusCode, errorBody)
            }

            // /agent/resume returns Session but with conversation=null when not loading messages
            // We don't need messages here anyway since we're just activating the agent
            let session = try JSONDecoder().decode(SessionResponse.self, from: data)
            
            return (session.id, [])
        }
        catch let error as DecodingError {
            handleAPIError(APIError.decodingError(error), context: "Resume Agent")
            throw APIError.decodingError(error)
        }
    }



    // MARK: - Agent Configuration
    func updateFromSession(sessionId: String) async throws {
        guard let url = URL(string: "\(self.baseURL)/agent/update_from_session") else {
            throw APIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(self.secretKey, forHTTPHeaderField: "X-Secret-Key")

        let body: [String: Any] = ["session_id": sessionId]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        if httpResponse.statusCode != 200 {
            let errorBody = String(data: data, encoding: .utf8) ?? "No error details"
            handleHTTPStatus(httpResponse.statusCode, body: errorBody, context: "Update From Session")
            throw APIError.httpError(httpResponse.statusCode, errorBody)
        }

    }

    // MARK: - Config Management
    private static let configCacheTTL: TimeInterval = 3600  // 1 hour
    
    private func cacheNamespace() -> String {
        // Namespace cache by baseURL and secret key to avoid cross-environment bleed
        let ns = "\(baseURL)|\(secretKey)"
        return String(ns.hashValue)
    }
    
    func clearConfigCache() {
        // Clear all config cache entries for current namespace
        let ns = cacheNamespace()
        let prefix = "configCache:\(ns):"
        let defaults = UserDefaults.standard
        for key in defaults.dictionaryRepresentation().keys where key.hasPrefix(prefix) {
            defaults.removeObject(forKey: key)
        }
    }
    
    func readConfigValue(key: String, isSecret: Bool = false) async -> String? {
        let startTime = Date()
        
        // Check cache for non-secret config values
        let ns = cacheNamespace()
        let storeKey = "configCache:\(ns):\(key)"
        let expKey = storeKey + ":exp"
        let now = Date().timeIntervalSince1970
        if !isSecret {
            let expiresAt = UserDefaults.standard.double(forKey: expKey)
            if expiresAt > now, let cachedValue = UserDefaults.standard.string(forKey: storeKey) {
                let elapsed = Date().timeIntervalSince(startTime)
                return cachedValue
            }
        }
        
        guard let url = URL(string: "\(baseURL)/config/read") else {
            print("⚠️ Invalid URL for config read")
            return nil
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(secretKey, forHTTPHeaderField: "X-Secret-Key")

        let body: [String: Any] = [
            "key": key,
            "is_secret": isSecret,
        ]

        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            let (data, response) = try await URLSession.shared.data(for: request)
            let elapsed = Date().timeIntervalSince(startTime)

            guard let httpResponse = response as? HTTPURLResponse else {
                print("⚠️ Invalid response reading config")
                return nil
            }

            if httpResponse.statusCode == 200 {
                // The response is just a string value, not a JSON object
                let value = String(data: data, encoding: .utf8)?.trimmingCharacters(
                    in: .whitespacesAndNewlines)
                // Remove quotes if present (JSON string encoding)
                let cleanValue = value?.trimmingCharacters(in: CharacterSet(charactersIn: "\""))
                
                // Cache non-secret values with fixed 1-hour TTL
                if !isSecret, let cleanValue = cleanValue {
                    let expiresAt = now + Self.configCacheTTL
                    UserDefaults.standard.set(cleanValue, forKey: storeKey)
                    UserDefaults.standard.set(expiresAt, forKey: expKey)
                }
                
                return cleanValue
            } else {
                print("⚠️ Failed to read config '\(key)': HTTP \(httpResponse.statusCode)")
                return nil
            }
        } catch {
            print("⚠️ Error reading config '\(key)': \(error)")

            return nil
        }
    }

    // MARK: - Provider Management
    func updateProvider(sessionId: String, provider: String, model: String?) async throws {
        do {
            guard let url = URL(string: "\(self.baseURL)/agent/update_provider") else {
                throw APIError.invalidURL
            }

            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(self.secretKey, forHTTPHeaderField: "X-Secret-Key")

            var body: [String: Any] = [
                "session_id": sessionId,
                "provider": provider,
            ]

            // Add model if provided
            if let model = model {
                body["model"] = model
            }

            request.httpBody = try JSONSerialization.data(withJSONObject: body)

            // Debug logging
            if let bodyData = request.httpBody,
                let bodyString = String(data: bodyData, encoding: .utf8)
            {
                print(bodyString)
            }

            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIError.invalidResponse
            }

            if httpResponse.statusCode != 200 {
            let errorBody = String(data: data, encoding: .utf8) ?? "No error details"
            handleHTTPStatus(httpResponse.statusCode, body: errorBody, context: "Update From Session")
            throw APIError.httpError(httpResponse.statusCode, errorBody)
        }

            
            // Clear config cache when agent changes
            clearConfigCache()
        } catch let error as DecodingError {
            handleAPIError(APIError.decodingError(error), context: "Update Provider")
            throw APIError.decodingError(error)
        }
    }

    // MARK: - Extension Management
    func loadEnabledExtensions(sessionId: String) async throws {
        do {
            // Get extensions from config, just like desktop does
            guard let url = URL(string: "\(self.baseURL)/config/extensions") else {
                print("⚠️ Invalid URL for getting extensions config")
                return
            }

            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            request.setValue(self.secretKey, forHTTPHeaderField: "X-Secret-Key")

            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                print("⚠️ Invalid response getting extensions config")
                return
            }

            if httpResponse.statusCode != 200 {
                let errorBody = String(data: data, encoding: .utf8) ?? "No error details"
                handleHTTPStatus(httpResponse.statusCode, body: errorBody, context: "Load Extensions")
                return
            }

            // Parse the extensions config
            guard
                let extensionsConfig = try? JSONSerialization.jsonObject(with: data)
                    as? [String: Any],
                let extensions = extensionsConfig["extensions"] as? [[String: Any]]
            else {
                print("⚠️ Failed to parse extensions config")
                handleAPIError(APIError.noData, context: "Load Extensions - parse error")
                return
            }

            // Load enabled extensions in parallel
            await withTaskGroup(of: Void.self) { group in
                for extensionConfig in extensions {
                    if let enabled = extensionConfig["enabled"] as? Bool, enabled {
                        group.addTask {
                            await self.loadExtension(sessionId: sessionId, extensionConfig: extensionConfig)
                        }
                    }
                }
            }
        } catch let error as DecodingError {
            handleAPIError(APIError.decodingError(error), context: "Load Extensions")
            throw error
        }
    }

    private func loadExtension(sessionId: String, extensionConfig: [String: Any]) async {
        guard let url = URL(string: "\(baseURL)/agent/add_extension") else {
            print("⚠️ Invalid URL for extension loading")
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(secretKey, forHTTPHeaderField: "X-Secret-Key")

        var body = extensionConfig
        body["session_id"] = sessionId

        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)

            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                print("⚠️ Invalid response loading extension")
                return
            }

            if httpResponse.statusCode == 200 {
                if let name = extensionConfig["name"] as? String {
                }
            } else {
                let errorBody = String(data: data, encoding: .utf8) ?? "No error details"
                if let name = extensionConfig["name"] as? String {
                    print("⚠️ Failed to load extension '\(name)': \(errorBody)")
                }
            }
        } catch {
            print("⚠️ Error loading extension: \(error)")
        }
    }

    // MARK: - Sessions Management
    func fetchInsights() async -> Result<SessionInsights, Error> {
        // In trial mode, return mock insights
        if isTrialMode {
            return .success(SessionInsights(totalSessions: 5, totalTokens: 450_000_000))
        }

        // Use retry wrapper for network resilience
        return await fetchWithRetry {
            guard let url = URL(string: "\(baseURL)/sessions/insights") else {
                throw APIError.invalidURL
            }

            var request = URLRequest(url: url)
            request.setValue(secretKey, forHTTPHeaderField: "X-Secret-Key")

            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIError.invalidResponse
            }

            if httpResponse.statusCode == 200 {
                let insights = try JSONDecoder().decode(SessionInsights.self, from: data)
                return insights
            } else {
                let errorBody = String(data: data, encoding: .utf8) ?? "No error details"
                handleHTTPStatus(httpResponse.statusCode, body: errorBody, context: "Fetch Insights")
                throw APIError.httpError(httpResponse.statusCode, errorBody)
            }
        }
    }
    
    func fetchSessions() async -> Result<[ChatSession], Error> {
        // In trial mode, return only the current trial session if it exists
        if isTrialMode {
            // Try to get the current trial session from storage
            if let sessionId = UserDefaults.standard.string(forKey: "trial_session_id"), !sessionId.isEmpty {
                let now = Date()
                let formatter = ISO8601DateFormatter()
                let session = ChatSession(
                    id: sessionId,
                    description: "Current Session",
                    messageCount: 0,  // Will be updated when loaded
                    createdAt: formatter.string(from: now),
                    updatedAt: formatter.string(from: now),
                    workingDir: nil
                )
                return .success([session])
            }
            // No trial session yet - return empty array
            return .success([])
        }

        // Use retry wrapper for network resilience
        return await fetchWithRetry {
            guard let url = URL(string: "\(baseURL)/sessions") else {
                throw APIError.invalidURL
            }

            var request = URLRequest(url: url)
            request.setValue(secretKey, forHTTPHeaderField: "X-Secret-Key")

            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw APIError.invalidResponse
            }

            if httpResponse.statusCode == 200 {
                let sessionsResponse = try JSONDecoder().decode(SessionsResponse.self, from: data)
                return sessionsResponse.sessions
            } else {
                let errorBody = String(data: data, encoding: .utf8) ?? "No error details"
                handleHTTPStatus(httpResponse.statusCode, body: errorBody, context: "Fetch Sessions")
                throw APIError.httpError(httpResponse.statusCode, errorBody)
            }
        }
    }
}

// MARK: - Response Models
struct AgentResponse: Codable {
    let id: String
    let conversation: [Message]?  // conversation is directly an array of messages
}

struct SessionResponse: Codable {
    let id: String
    let conversation: [Message]?
}

struct SessionsResponse: Codable {
    let sessions: [ChatSession]
}

struct SessionInsights: Codable {
    let totalSessions: Int
    let totalTokens: Int64
}

// MARK: - SSE Delegate
class SSEDelegate: NSObject, URLSessionDataDelegate {
    private let onEvent: (SSEEvent) -> Void
    private let onComplete: () -> Void
    private let onError: (Error) -> Void
    private var buffer = ""
    private let maxBufferSize = 10000  // Prevent buffer overflow
    private var errorStatusCode: Int? = nil  // Track error status codes

    init(
        onEvent: @escaping (SSEEvent) -> Void, onComplete: @escaping () -> Void,
        onError: @escaping (Error) -> Void
    ) {
        self.onEvent = onEvent
        self.onComplete = onComplete
        self.onError = onError
    }

    func urlSession(
        _ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
    ) {
        guard let httpResponse = response as? HTTPURLResponse else {
            DispatchQueue.main.async {
                self.onError(APIError.invalidResponse)
            }
            completionHandler(.cancel)
            return
        }


        guard httpResponse.statusCode == 200 else {
            // Store error status for later - errors are handled by earlier API calls
            errorStatusCode = httpResponse.statusCode
            // Let it continue so we can capture the error body
            completionHandler(.allow)
            return
        }

        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard let string = String(data: data, encoding: .utf8) else {
            print("🚨 SSE: Failed to decode data as UTF-8")
            return
        }

        // Add to buffer
        buffer += string

        // Process complete lines
        let lines = buffer.components(separatedBy: .newlines)

        // Keep the last incomplete line in buffer
        if !buffer.hasSuffix("\n") && !buffer.hasSuffix("\r\n") {
            buffer = lines.last ?? ""
            let completeLines = Array(lines.dropLast())
            processSSELines(completeLines)
        } else {
            buffer = ""
            processSSELines(lines)
        }
    }

    func urlSession(
        _ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?
    ) {
        // CRITICAL: Invalidate the session to prevent memory leak
        session.finishTasksAndInvalidate()
        
        if let error = error {
            DispatchQueue.main.async {
                self.onError(error)
            }
        } else if let statusCode = errorStatusCode {
            // We had an HTTP error - report it with the collected buffer as the error body
            print("🚨 HTTP Error \(statusCode) with body: \(buffer)")
            DispatchQueue.main.async {
                self.onError(APIError.httpError(statusCode, self.buffer))
            }
        } else {
            DispatchQueue.main.async {
                self.onComplete()
            }
        }
    }

    private func processSSELines(_ lines: [String]) {
        for line in lines {
            if line.hasPrefix("data: ") {
                let eventData = String(line.dropFirst(6))
                if !eventData.isEmpty {
                    do {
                        let jsonData = eventData.data(using: .utf8)!
                        let event = try JSONDecoder().decode(SSEEvent.self, from: jsonData)

                        // Minimal logging - only log important events
                        if case .finish = event {
                        } else if case .error = event {
                            print("🚨 SSE Error event received")
                        }

                        DispatchQueue.main.async {
                            self.onEvent(event)
                        }

                        // Check if this is a finish event
                        if case .finish = event {
                            DispatchQueue.main.async {
                                self.onComplete()
                            }
                            return
                        }
                    } catch {
                        // Errors are handled by earlier API calls
                        print("🚨 Failed to decode SSE event: \(error)")
                        DispatchQueue.main.async {
                            self.onError(error)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - API Errors
enum APIError: Error, LocalizedError {
    case invalidURL
    case invalidResponse
    case httpError(Int, String)
    case noData
    case decodingError(Error)
    case unknown

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid URL"
        case .invalidResponse:
            return "Invalid response"
        case .httpError(let code, let body):
            return "HTTP Error \(code): \(body)"
        case .noData:
            return "No data received"
        case .decodingError(let error):
            return "Decoding error: \(error.localizedDescription)"
        case .unknown:
            return "Unknown error"
        }
    }
}

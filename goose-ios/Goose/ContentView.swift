import SwiftUI

struct ContentView: View {
    @State private var isSettingsPresented = false
    @State private var showingSidebar = false
    @State private var showingSplash = true
    @State private var hasActiveChat = false
    @State private var initialMessage = ""
    @State private var shouldSendInitialMessage = false
    @State private var selectedSessionId: String?
    @State private var sessionName: String = "New Session"
    @State private var cachedSessions: [ChatSession] = [] // Preloaded sessions
    @State private var isLoadingMore: Bool = false
    @State private var hasMoreSessions: Bool = true
    @State private var currentDaysLoaded: Int = 5  // Track how many days we've loaded
    private let initialDaysBack: Int = 5  // Load sessions from last 5 days initially
    private let loadMoreDaysIncrement: Int = 5  // Load 5 more days when "Load More" is clicked

    // MARK: - Load More Sessions (load older sessions)
    @EnvironmentObject var noticeCenter: AppNoticeCenter
    func loadMoreSessions() async {
        guard !isLoadingMore && hasMoreSessions else { return }
        
        await MainActor.run {
            isLoadingMore = true
        }
        
        let result = await GooseAPIService.shared.fetchSessions()
        
        switch result {
        case .success(let allSessions):
            // Filter sessions on background thread
            let newDaysBack = currentDaysLoaded + loadMoreDaysIncrement
            let sessionsInRange = await filterSessionsByDate(allSessions, daysBack: newDaysBack)
            
            await MainActor.run {
                let previousCount = cachedSessions.count
                self.cachedSessions = sessionsInRange
                self.hasMoreSessions = allSessions.count > sessionsInRange.count
                
                let newCount = sessionsInRange.count - previousCount
                
                // Update the days loaded tracker
                self.currentDaysLoaded = newDaysBack
                
                
                if newCount == 0 {
                    self.hasMoreSessions = false
                }
                
                isLoadingMore = false
            }
            
        case .failure(let error):
            // Keep cached sessions, just stop loading indicator
            print("⚠️ Failed to load more sessions: \(error.localizedDescription)")
            await MainActor.run {
                isLoadingMore = false
            }
        }
    }
    
    // MARK: - Background Session Filtering
    
    /// Filter sessions by date on a background thread to avoid blocking UI
    private func filterSessionsByDate(_ sessions: [ChatSession], daysBack: Int) async -> [ChatSession] {
        return await Task.detached {
            let calendar = Calendar.current
            let now = Date()
            let cutoffDate = calendar.date(byAdding: .day, value: -daysBack, to: now) ?? now
            
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime]
            
            return sessions.filter { session in
                guard let sessionDate = formatter.date(from: session.updatedAt) else {
                    return false
                }
                return sessionDate >= cutoffDate
            }
        }.value
    }
    
    // Helper to estimate days loaded based on session dates
    private func calculateDaysLoaded(from sessions: [ChatSession]) -> Int {
        guard let oldestSession = sessions.last else { return initialDaysBack }
        
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        
        guard let oldestDate = formatter.date(from: oldestSession.updatedAt) else {
            return initialDaysBack
        }
        
        let calendar = Calendar.current  // Use local timezone
        let days = calendar.dateComponents([.day], from: oldestDate, to: Date()).day ?? initialDaysBack
        return max(days, initialDaysBack)
    }
    
    @EnvironmentObject var configurationHandler: ConfigurationHandler
    
    // Shared voice managers across WelcomeView and ChatView
    @StateObject private var sharedVoiceManager = EnhancedVoiceManager()
    @StateObject private var sharedContinuousVoiceManager = ContinuousVoiceManager()
    
    // Dynamic sidebar width based on device
    private var sidebarWidth: CGFloat {
        let screenWidth = UIScreen.main.bounds.width
        if UIDevice.current.userInterfaceIdiom == .pad {
            return screenWidth * 0.5 // 50% on iPad
        } else {
            return screenWidth // 100% on iPhone
        }
    }

    var body: some View {
        if showingSplash {
            // Splash Screen
            SplashScreenView(isActive: $showingSplash)
                .onAppear {
                    // Warm up the demo server if in trial mode (non-blocking)
                    warmUpServer()
                    // Preload sessions on app launch
                    Task {
                        await preloadSessions()
                    }
                }
        } else {
            // Main content with sidebar (following PR pattern)
            ZStack(alignment: .leading) {
                // Main content - full height (stays in place)
                GeometryReader { geometry in
                    NavigationStack {
                        if !hasActiveChat {
                            // Welcome View when no active chat
                            WelcomeView(
                                showingSidebar: $showingSidebar,
                                onStartChat: { message in
                                // Start new chat with the message
                                initialMessage = message
                                shouldSendInitialMessage = !message.isEmpty
                                selectedSessionId = nil // Clear session ID for new chat
                                withAnimation {
                                    hasActiveChat = true
                                }
                            }, onSessionSelect: { sessionId in
                                // Load existing session
                                selectedSessionId = sessionId
                                withAnimation {
                                    hasActiveChat = true
                                }
                            },
                                cachedSessions: cachedSessions,  // Pass cached sessions
                                onLoadMore: {  // Pass load more callback for NodeMatrix
                                    await loadMoreSessions()
                                },
                                onCacheUpdate: { newSessions in
                                    // Update cache from background fetches
                                    await updateCacheQuietly(newSessions)
                                },
                                voiceManager: sharedVoiceManager,
                                continuousVoiceManager: sharedContinuousVoiceManager
                            )
                            .navigationBarHidden(true)
                        } else {
                            // Chat View when there's an active chat
                            ChatViewWithInitialMessage(
                                showingSidebar: $showingSidebar,
                                voiceManager: sharedVoiceManager,
                                continuousVoiceManager: sharedContinuousVoiceManager,
                                initialMessage: initialMessage,
                                shouldSendMessage: shouldSendInitialMessage,
                                selectedSessionId: selectedSessionId,
                                onMessageSent: {
                                    // Clear the initial message after sending
                                    initialMessage = ""
                                    shouldSendInitialMessage = false
                                    selectedSessionId = nil
                                },
                                onBackToWelcome: {
                                    // Return to welcome screen and refresh sessions
                                    withAnimation {
                                        hasActiveChat = false
                                    }
                                    // Refresh sessions when returning to welcome view
                                    Task {
                                        await refreshSessionsAfterChat()
                                    }
                                }
                            )
                            .id(selectedSessionId ?? "new")  // Force view recreation when session changes
                            .navigationBarHidden(true)
                        }
                    }
                    .sheet(isPresented: $isSettingsPresented) {
                        SettingsView()
                            .environmentObject(configurationHandler)
                    }
                    .frame(width: geometry.size.width, height: geometry.size.height)
                    .background(Color(UIColor.systemBackground))
                }
                .edgesIgnoringSafeArea(.all)
                
                // Sidebar overlay - slides in independently
                if showingSidebar {
                    SidebarView(
                        isShowing: $showingSidebar,
                        isSettingsPresented: $isSettingsPresented,
                        cachedSessions: $cachedSessions,
                        onSessionSelect: { sessionId in
                            // Find session to get its description
                            if let session = cachedSessions.first(where: { $0.id == sessionId }) {
                                let name = session.description.isEmpty ? "Untitled Session" : session.description
                                navigateToSession(sessionId: sessionId, sessionName: name)
                            }
                        },
                        onNewSession: {
                            navigateToSession(sessionId: nil)
                            Task {
                                await preloadSessions()
                            }
                        },
                        onLoadMore: {
                            await loadMoreSessions()
                        },
                        isLoadingMore: isLoadingMore,
                        hasMoreSessions: hasMoreSessions
                    )
                    .transition(.move(edge: .leading))
                    .animation(.easeInOut(duration: 0.25), value: showingSidebar)
                    .zIndex(1)  // Ensure sidebar is above main content
                    // Note: Removed onAppear preload to preserve loaded sessions
                    // Sessions are loaded on app launch and via "Load More" button
                }
            }
            .overlay(alignment: .top) {
                if configurationHandler.isConfiguring {
                    ConfigurationStatusView(message: "Configuring...", isLoading: true)
                } else if configurationHandler.configurationSuccess {
                    ConfigurationStatusView(message: "✅ Configuration successful!", isSuccess: true)
                } else if let error = configurationHandler.configurationError {
                    ConfigurationStatusView(
                        message: error, 
                        isError: true,
                        isTailscaleError: configurationHandler.isTailscaleError,
                        onTailscaleAction: {
                            configurationHandler.openTailscale()
                        }
                    )
                    .onTapGesture {
                        configurationHandler.clearError()
                    }
                }
            }
            .overlay(alignment: .top) {
                AppNoticeOverlay()
            }
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("RefreshSessions"))) { _ in
                // Refresh sessions when settings are saved
                Task {
                    await preloadSessions()
                }
            }
        }
    }
    
    // MARK: - Navigation Helpers
    
    /// Navigate to a session (existing or new)
    private func navigateToSession(sessionId: String?, sessionName: String = "New Session") {
        self.selectedSessionId = sessionId
        self.sessionName = sessionName
        self.initialMessage = ""
        self.shouldSendInitialMessage = false
        withAnimation {
            self.showingSidebar = false
            self.hasActiveChat = true
        }
    }
    
    // MARK: - Session Management
    
    /// Update cache quietly from background fetches (no UI loading indicators)
    private func updateCacheQuietly(_ newSessions: [ChatSession]) async {
        // Filter to current days loaded range
        let sessionsInRange = await filterSessionsByDate(newSessions, daysBack: currentDaysLoaded)
        
        await MainActor.run {
            self.cachedSessions = sessionsInRange
            self.hasMoreSessions = newSessions.count > sessionsInRange.count
            print("✅ Cache updated from background fetch: \(sessionsInRange.count) sessions")
        }
    }
    
    /// Refresh sessions after returning from chat (to show new sessions)
    private func refreshSessionsAfterChat() async {
        let result = await GooseAPIService.shared.fetchSessions()
        
        switch result {
        case .success(let fetchedSessions):
            // Filter sessions on background thread
            let sessionsInRange = await filterSessionsByDate(fetchedSessions, daysBack: currentDaysLoaded)
            
            await MainActor.run {
                self.cachedSessions = sessionsInRange
                self.hasMoreSessions = fetchedSessions.count > sessionsInRange.count
            }
            
        case .failure(let error):
            // Keep cached sessions, just log the error
            print("⚠️ Failed to refresh sessions: \(error.localizedDescription)")
        }
    }
    
    // Preload sessions from last 5 days on app launch
    private func preloadSessions() async {
        let result = await GooseAPIService.shared.fetchSessions()
        
        switch result {
        case .success(let fetchedSessions):
            await MainActor.run {
                // In trial mode, show all sessions without filtering
                if GooseAPIService.shared.isTrialMode {
                    self.cachedSessions = fetchedSessions
                    self.currentDaysLoaded = initialDaysBack
                    self.hasMoreSessions = false  // No pagination in trial mode
                    print("✅ Loaded \(fetchedSessions.count) trial mode sessions")
                    return
                }
                
                // Use UTC calendar for date comparison to match session timestamps
                let calendar = Calendar.current  // Use local timezone
                let now = Date()
                let cutoffDate = calendar.date(byAdding: .day, value: -initialDaysBack, to: now) ?? now
                
                let formatter = ISO8601DateFormatter()
                formatter.formatOptions = [.withInternetDateTime]
                
                
                // Check for duplicate session IDs
                let sessionIds = fetchedSessions.map { $0.id }
                let uniqueIds = Set(sessionIds)
                if sessionIds.count != uniqueIds.count {
                    print("⚠️ DEBUG: DUPLICATE SESSION IDs DETECTED!")
                }
                
                // Show ALL sessions from last 7 days for debugging
                let sevenDaysAgo = calendar.date(byAdding: .day, value: -7, to: now) ?? now
                let recentForDebug = fetchedSessions.filter { session in
                    guard let sessionDate = formatter.date(from: session.updatedAt) else { return false }
                    return sessionDate >= sevenDaysAgo
                }
                
                let dayFormatter = DateFormatter()
                dayFormatter.dateFormat = "yyyy-MM-dd (EEEE)"
                dayFormatter.timeZone = TimeZone(identifier: "UTC")
                
                for session in recentForDebug.sorted(by: { $0.updatedAt > $1.updatedAt }) {
                    if let date = formatter.date(from: session.updatedAt) {
                        let dayStr = dayFormatter.string(from: date)
                        let desc = session.description.isEmpty ? "[Untitled]" : session.description
                    }
                }
                
                var includedCount = 0
                var filteredCount = 0
                var parseErrors = 0
                
                let recentSessions = fetchedSessions.filter { session in
                    guard let sessionDate = formatter.date(from: session.updatedAt) else {
                        print("⚠️ DEBUG: Failed to parse date: \(session.updatedAt) for session: \(session.description)")
                        parseErrors += 1
                        return false
                    }
                    
                    let isIncluded = sessionDate >= cutoffDate
                    if isIncluded {
                        includedCount += 1
                    } else {
                        filteredCount += 1
                    }
                    
                    return isIncluded
                }
                
                // Load all sessions within the initial time window
                self.cachedSessions = recentSessions
                self.currentDaysLoaded = initialDaysBack
                self.hasMoreSessions = fetchedSessions.count > recentSessions.count
                
                print("   - Older sessions available: \(fetchedSessions.count - recentSessions.count)")
                
                // Show what we actually loaded
                let groupedByDate = Dictionary(grouping: recentSessions) { session -> String in
                    guard let date = formatter.date(from: session.updatedAt) else { return "unknown" }
                    return dayFormatter.string(from: date)
                }
                for (date, sessions) in groupedByDate.sorted(by: { $0.key > $1.key }) {
                    print("   \(date): \(sessions.count) sessions")
                }
                
                if self.cachedSessions.isEmpty {
                    print("⚠️ No sessions preloaded - server may not be connected or no sessions exist")
                }
            }
            
        case .failure(let error):
            // Keep existing cached sessions on error (important for offline!)
            print("⚠️ Failed to preload sessions: \(error.localizedDescription)")
            // If we have no cached sessions and this is first load, we're truly offline/disconnected
            if cachedSessions.isEmpty {
                print("⚠️ No cached sessions available - app may be offline or server unavailable")
            }
        }
    }
    
    /// Warm up the server on first launch (non-blocking)
    private func warmUpServer() {
        // Check if we're using the demo server
        let baseURL = UserDefaults.standard.string(forKey: "goose_base_url") ?? "https://demo-goosed.fly.dev"
        
        // Only warm up if it's the demo server
        if baseURL.contains("demo-goosed.fly.dev") {
            Task {
                // Fire-and-forget warm-up request
                // We don't care about the response, just want to wake up the server
                if let url = URL(string: baseURL) {
                    var request = URLRequest(url: url)
                    request.httpMethod = "GET"
                    request.timeoutInterval = 10
                    
                    do {
                        // Perform the request but don't wait or care about response
                        let _ = try await URLSession.shared.data(for: request)
                        print("🔥 Demo server warmed up")
                    } catch {
                        // Silently ignore any errors - this is just a warm-up
                        print("🔥 Demo server warm-up attempted")
                    }
                }
            }
        }
    }
}

struct ConfigurationStatusView: View {
    let message: String
    var isLoading = false
    var isSuccess = false
    var isError = false
    var isTailscaleError = false
    var onTailscaleAction: (() -> Void)?
    
    var backgroundColor: Color {
        if isSuccess { return .green }
        if isError && isTailscaleError { return .blue }  // Friendly blue for Tailscale
        if isError { return .red }
        return .blue
    }
    
    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 12) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(0.8)
                }
                
                Text(message)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white)
                
                if isError && !isTailscaleError {
                    Text("Tap to dismiss")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.8))
                }
            }
            
            if isTailscaleError {
                Button(action: {
                    onTailscaleAction?()
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.right.circle.fill")
                        Text("Open Tailscale")
                    }
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.blue)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.white)
                    .cornerRadius(6)
                }
                
                Text("Tap anywhere to dismiss")
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.7))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(backgroundColor)
        .cornerRadius(8)
        .shadow(radius: 4)
        .padding(.top, 50) // Account for nav bar
        .padding(.horizontal)
        .transition(.move(edge: .top).combined(with: .opacity))
        .animation(.spring(), value: isLoading)
        .animation(.spring(), value: isSuccess)
        .animation(.spring(), value: isError)
    }
}

// Wrapper to handle initial message and session loading
struct ChatViewWithInitialMessage: View {
    @Binding var showingSidebar: Bool
    @ObservedObject var voiceManager: EnhancedVoiceManager
    @ObservedObject var continuousVoiceManager: ContinuousVoiceManager
    let initialMessage: String
    let shouldSendMessage: Bool
    let selectedSessionId: String?
    let onMessageSent: () -> Void
    let onBackToWelcome: () -> Void
    
    @State private var hasHandledAppear = false  // Track if we've already handled onAppear
    
    var body: some View {
        ChatView(showingSidebar: $showingSidebar, onBackToWelcome: onBackToWelcome, voiceManager: voiceManager, continuousVoiceManager: continuousVoiceManager)
            .onAppear {
                // CRITICAL: Only handle onAppear once to prevent duplicate notifications
                guard !hasHandledAppear else { return }
                hasHandledAppear = true
                
                // Load session if one was selected
                if let sessionId = selectedSessionId {
                    // Capture the sessionId and clear it immediately to prevent reuse
                    let capturedSessionId = sessionId
                    onMessageSent()  // Clear selectedSessionId BEFORE posting notification
                    
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        NotificationCenter.default.post(
                            name: Notification.Name("LoadSession"),
                            object: nil,
                            userInfo: ["sessionId": capturedSessionId]
                        )
                    }
                } else if shouldSendMessage && !initialMessage.isEmpty {
                    // Capture and clear before posting
                    let capturedMessage = initialMessage
                    onMessageSent()  // Clear state BEFORE posting notification
                    
                    // Send the initial message after a brief delay to ensure view is ready
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        NotificationCenter.default.post(
                            name: Notification.Name("SendInitialMessage"),
                            object: nil,
                            userInfo: ["message": capturedMessage]
                        )
                    }
                }
            }
    }

}

#Preview {
    ContentView()
        .environmentObject(ConfigurationHandler.shared)
}

import SwiftUI

struct WelcomeView: View {
    @Binding var showingSidebar: Bool
    @State private var inputText = ""
    @FocusState private var isInputFocused: Bool
    @Environment(\.colorScheme) var colorScheme
    var onStartChat: (String) -> Void
    var onSessionSelect: (String) -> Void
    var cachedSessions: [ChatSession] = []  // Pass in cached sessions from ContentView
    var onLoadMore: () async -> Void = {}  // Callback to load more sessions
    var onCacheUpdate: (([ChatSession]) async -> Void)? = nil  // Callback to update ContentView cache
    
    // Voice features - shared with ChatView
    @ObservedObject var voiceManager: EnhancedVoiceManager
    @ObservedObject var continuousVoiceManager: ContinuousVoiceManager
    
    // States for welcome view
    @State private var recentSessions: [ChatSession] = []
    @State private var isLoadingSessions = false
    @State private var isSettingsPresented = false
    @State private var showTrialInstructions = false
    
    // Animation states
    @State private var showSessionsTitle = false
    @State private var visibleSessionsCount = 0
    @State private var showTrialModeCard = false
    @State private var currentDaysOffset: Int = 0 // Track current day being viewed in NodeMatrix
    @State private var lastLoadTriggeredAtOffset: Int = 0  // Track last load to prevent duplicates
    @State private var maxDaysToLoad: Int = 90  // Only load sessions from last 10 days - next step being a chatmeta query but i dont think we get that data from goosed
    
    // Session card state
    @State private var selectedSession: ChatSession? = nil
    @State private var showSessionCard = false
    @State private var selectedSessionPosition: CGPoint = .zero // Store position for transition to focus mode
    
    // Node focus state (for focus mode)
    @State private var focusedNodeSession: ChatSession? = nil
    @State private var focusedNodePosition: CGPoint = .zero
    
    // Access API service for trial mode check
    @StateObject private var apiService = GooseAPIService.shared
    
    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .top) {
                // Main content - use VStack with Spacer to push input to bottom
                VStack(spacing: 0) {
                    // Spacer for the welcome card (animated height)
                    Color.clear
                        .frame(height: isInputFocused ? 0 : 300)
                        .animation(.spring(response: 0.4, dampingFraction: 0.8), value: isInputFocused)
                    
                    // Node Matrix Section - fills all remaining space above input
                    ZStack {
                        // Always render the space, just change the content
                        if isLoadingSessions {
                            // Loading state
                            VStack {
                                Spacer()
                                if showSessionsTitle {
                                    ProgressView()
                                        .scaleEffect(0.8)
                                        .tint(.secondary)
                                        .transition(.opacity)
                                }
                                Spacer()
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        } else if recentSessions.isEmpty && !isInputFocused {
                            // Empty state
                            VStack {
                                Spacer()
                                if showSessionsTitle {
                                    VStack(spacing: 8) {
                                        Image(systemName: "calendar.badge.clock")
                                            .font(.system(size: 32))
                                            .foregroundColor(.secondary.opacity(0.5))
                                        
                                        Text("No sessions today")
                                            .font(.system(size: 14))
                                            .foregroundColor(.secondary)
                                    }
                                    .transition(.opacity)
                                }
                                Spacer()
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        } else {
                            // Node Matrix - fills all available space
                            if showSessionsTitle {
                                NodeMatrix(
                                    sessions: recentSessions,
                                    selectedSessionId: isInputFocused ? focusedNodeSession?.id : (showSessionCard ? selectedSession?.id : nil),
                                    onNodeTap: { session, position in
                                        handleNodeTap(session, at: position)
                                    },
                                    onDayChange: { daysOffset in
                                        currentDaysOffset = daysOffset
                                        
                                        // Smart loading: Only load if we need more sessions
                                        // 1. Check if we've moved significantly (5+ days from last load)
                                        // 2. Check if we're within the 90-day boundary
                                        // 3. Check if we have more sessions to load
                                        let daysSinceLastLoad = abs(daysOffset - lastLoadTriggeredAtOffset)
                                        
                                        if daysOffset > 5 && 
                                           daysOffset <= maxDaysToLoad &&
                                           daysSinceLastLoad >= 5 &&
                                           !cachedSessions.isEmpty {
                                            // Update the trigger point to prevent immediate re-triggers
                                            lastLoadTriggeredAtOffset = daysOffset
                                            
                                            // Use detached task to avoid blocking UI thread during swipe
                                            Task.detached {
                                                await onLoadMore()
                                            }
                                        }
                                    },
                                    showDraftNode: isInputFocused && focusedNodeSession == nil
                                )
                                .transition(.opacity)
                            } else {
                                // Placeholder to maintain space before animation
                                Color.clear
                                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle()) // Make the entire area tappable
                    .onTapGesture {
                        // Dismiss keyboard and return to home when tapping outside chat input
                        if isInputFocused {
                            isInputFocused = false
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                focusedNodeSession = nil
                            }
                        } else if showSessionCard {
                            // Dismiss session card when tapping background
                            handleCloseSessionCard()
                        }
                    }
                    
                    // Chat input at bottom - always present
                    ChatInputView(
                        text: $inputText,
                        isFocused: $isInputFocused,
                        voiceManager: voiceManager,
                        continuousVoiceManager: continuousVoiceManager,
                        onSubmit: {
                            handleSubmit()
                        },
                        showTrialBanner: apiService.isTrialMode && showTrialModeCard,
                        onTrialBannerTap: {
                            showTrialInstructions = true
                        }
                    )
                    .zIndex(10) // Always on top for tappability
                }
                
                // Welcome Card overlaid on top (full bleed) - animated
                VStack {
                    WelcomeCard(
                        daysOffset: currentDaysOffset,
                        sessions: recentSessions,
                        showingSidebar: $showingSidebar,
                        onAnimationComplete: {
                            // Show trial mode card if in trial mode
                            if apiService.isTrialMode {
                                withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                                    showTrialModeCard = true
                                }
                            }
                            
                            // Show sessions title
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                                withAnimation(.easeOut(duration: 0.3)) {
                                    showSessionsTitle = true
                                }
                            }
                        }
                    )
                    
                    Spacer()
                }
                .offset(y: isInputFocused ? -350 : 0)
                .opacity(isInputFocused ? 0 : 1)
                .animation(.spring(response: 0.4, dampingFraction: 0.8), value: isInputFocused)
                .zIndex(1)
                .allowsHitTesting(!showSessionCard && !isInputFocused) // Disable interaction when session card is showing or input focused
                
                // Sessions Card - slides in from top, overlays WelcomeCard
                if showSessionCard, let session = selectedSession {
                    ZStack {
                        
                        VStack {
                            SessionsCard(
                                session: session,
                                onClose: {
                                    handleCloseSessionCard()
                                },
                                onSessionSelect: { sessionId in
                                    onSessionSelect(sessionId)
                                    handleCloseSessionCard()
                                }
                            )
                            
                            Spacer()
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    handleCloseSessionCard()
                                }
                        }
                        .allowsHitTesting(true)
                    }
                    .frame(maxHeight: .infinity, alignment: .top)
                    .zIndex(2)
                    .offset(y: showSessionCard ? 0 : -500)
                }
                
                // NodeFocus popover - overlaid on top, positioned near the node
                if let focusedSession = focusedNodeSession, isInputFocused {
                    let popoverWidth: CGFloat = 200
                    let popoverHeight: CGFloat = 100 // Approximate height
                    let offsetAboveNode: CGFloat = 70
                    
                    // Calculate optimal position with bounds checking
                    let targetX = focusedNodePosition.x
                    // Account for WelcomeCard shift when input is focused
                    let welcomeCardOffset: CGFloat = isInputFocused ? -350 : 0
                    let targetY = focusedNodePosition.y - offsetAboveNode + welcomeCardOffset
                    
                    // Clamp X to keep popover on screen
                    let clampedX = min(max(targetX, popoverWidth / 2 + 16), geometry.size.width - popoverWidth / 2 - 16)
                    
                    // Clamp Y to keep popover on screen (account for safe area)
                    let clampedY = max(targetY, popoverHeight / 2 + 60)
                    
                    NodeFocus(
                        session: focusedSession,
                        onDismiss: {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                focusedNodeSession = nil
                            }
                        }
                    )
                    .position(x: clampedX, y: clampedY)
                    .transition(.scale.combined(with: .opacity))
                    .zIndex(3)
                    .allowsHitTesting(true)
                }
                
                // Safe area extension at the very top - covers the shadow
                // Switches color based on focus state: system background when focused (draft view),
                // WelcomeCard color when not focused
                SafeAreaExtension(useSystemBackground: isInputFocused)
                    .zIndex(10) // Increased from 3 to ensure it's always on top
                    .allowsHitTesting(false) // Don't block touches
                    .animation(.spring(response: 0.4, dampingFraction: 0.8), value: isInputFocused)
            }
        }
        .sheet(isPresented: $isSettingsPresented) {
            SettingsView()
                .environmentObject(ConfigurationHandler.shared)
        }
        .sheet(isPresented: $showTrialInstructions) {
            TrialModeInstructionsView()
        }

        .onChange(of: isInputFocused) { oldValue, newValue in
            if newValue {
                // Input gained focus
                // If there's a selected session AND the card is showing, transfer it to focus mode
                if let selected = selectedSession, showSessionCard {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        focusedNodeSession = selected
                        focusedNodePosition = selectedSessionPosition
                        // Dismiss session card
                        showSessionCard = false
                    }
                }
            } else {
                // Input lost focus - clear focused node
                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                    focusedNodeSession = nil
                }
            }
        }
        .onAppear {
            // Transcribe mode (Enhanced)
            voiceManager.onSubmitMessage = { message in
                inputText = message
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    handleSubmit()
                }
            }
            voiceManager.onTranscriptionUpdate = { transcribedText in
                inputText = transcribedText
            }
            
            // Continuous mode (live partials + auto-submit)
            continuousVoiceManager.onTranscriptionUpdate = { partial in
                inputText = partial
            }
            continuousVoiceManager.onSubmitMessage = { message in
                inputText = message
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    handleSubmit()
                }
            }
            continuousVoiceManager.onCancelRequest = {
                // Nothing to cancel here (handled in ChatView), but clear input to reflect interruption
                inputText = ""
            }
            
            // Set up transcription update callback for continuous input (audio mode)
            voiceManager.onTranscriptionUpdate = { transcribedText in
                // Update the input text field without sending
                inputText = transcribedText
            }
            
            // Load recent sessions
            Task {
                await loadRecentSessions()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("RefreshSessions"))) { _ in
            // Refresh sessions when settings are saved
            Task {
                await loadRecentSessions()
            }
            
            // Update trial mode card visibility when settings change
            // This ensures the banner shows immediately when switching to trial mode
            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                showTrialModeCard = apiService.isTrialMode
            }
        }
        .onChange(of: cachedSessions) { _, newSessions in
            // Update recentSessions when cachedSessions changes (e.g., from sidebar load more)
            recentSessions = newSessions
        }
    }
    
    // Handle message submission
    private func handleSubmit() {
        let trimmedText = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedText.isEmpty else { return }

        // Stop voice input if active to prevent transcription after send
        if voiceManager.isListening {
            voiceManager.setMode(.normal)
        }
        
        if let focusedSession = focusedNodeSession {
            // Route to existing session with message
            print("📨 Routing message to session: \(focusedSession.id)")
            
            // Navigate to the session first
            onSessionSelect(focusedSession.id)
            
            // Then send the message via notification after a delay to ensure ChatView is loaded
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                NotificationCenter.default.post(
                    name: Notification.Name("SendMessageToSession"),
                    object: nil,
                    userInfo: ["message": trimmedText, "sessionId": focusedSession.id]
                )
            }
            
            // Clear state
            inputText = ""
            focusedNodeSession = nil
        } else {
            // Start new chat
            onStartChat(inputText)
            inputText = ""
        }
    }
    
    // Handle node tap
    private func handleNodeTap(_ session: ChatSession, at position: CGPoint) {
        if isInputFocused {
            // In focus mode - show NodeFocus popover
            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                focusedNodeSession = session
                focusedNodePosition = position
            }
        } else {
            // Not in focus mode - show SessionCard
            selectedSession = session
            selectedSessionPosition = position // Store position for potential transition to focus mode
            withAnimation(.easeOut(duration: 0.35)) {
                showSessionCard = true
            }
        }
    }
    
    // Handle close session card
    private func handleCloseSessionCard() {
        withAnimation(.easeOut(duration: 0.35)) {
            showSessionCard = false
        }
    }

    private func loadRecentSessions() async {
        // Use cached sessions immediately if available
        if !cachedSessions.isEmpty {
            await MainActor.run {
                recentSessions = cachedSessions
                isLoadingSessions = false
            }
            
            // Fetch fresh data in background and update ContentView's cache
            // This is non-blocking but now actually updates the cache!
            if let cacheUpdate = onCacheUpdate {
                Task.detached(priority: .background) {
                    let result = await GooseAPIService.shared.fetchSessions()
                    
                    if case .success(let sessions) = result {
                        // Update ContentView's cache with fresh data
                        await cacheUpdate(sessions)
                    }
                    // Failures are silent - offline = no update, keep existing cache
                }
            }
        } else {
            // No cache - fetch in background without blocking UI
            // Let empty state show while loading
            await MainActor.run {
                isLoadingSessions = false // Don't block UI with spinner
            }
            
            // Fetch sessions in background, update when ready
            Task.detached(priority: .userInitiated) {
                let result = await GooseAPIService.shared.fetchSessions()
                
                switch result {
                case .success(let sessions):
                    await MainActor.run {
                        self.recentSessions = sessions
                    }
                case .failure:
                    // Keep empty list, errors are logged in fetchSessions
                    break
                }
            }
            
            // Also fetch insights but don't wait for it
            Task.detached(priority: .background) {
                _ = await GooseAPIService.shared.fetchInsights()
                // Errors are logged in fetchInsights, no need to handle here
            }
        }
    }
}

// MARK: - Welcome Session Row View
struct WelcomeSessionRowView: View {
    let session: ChatSession
    @Environment(\.colorScheme) var colorScheme
    
    var formattedTimestamp: String {
        // Parse the ISO8601 date string
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        
        guard let sessionDate = formatter.date(from: session.updatedAt) else {
            return session.updatedAt
        }
        
        let now = Date()
        let interval = now.timeIntervalSince(sessionDate)
        
        let minutes = Int(interval / 60)
        let hours = Int(interval / 3600)
        let days = Int(interval / 86400)
        
        if minutes < 60 {
            return "\(minutes) Minute\(minutes == 1 ? "" : "s") ago"
        } else if hours < 24 {
            return "\(hours) Hour\(hours == 1 ? "" : "s") ago"
        } else if days == 1 {
            return "Yesterday"
        } else {
            return "\(days) Days ago"
        }
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(session.description.isEmpty ? "Session \(session.id.prefix(8))" : session.description)
                .font(.system(size: 16))
                .foregroundColor(.primary)
                .lineLimit(1)
            
            Text(formattedTimestamp)
                .font(.system(size: 12))
                .tracking(0.06)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    WelcomeView(
        showingSidebar: .constant(false),
        onStartChat: { text in
            print("Starting chat with: \(text)")
        },
        onSessionSelect: { sessionId in
            print("Selected session: \(sessionId)")
        },
        voiceManager: EnhancedVoiceManager(),
        continuousVoiceManager: ContinuousVoiceManager()
    )
}

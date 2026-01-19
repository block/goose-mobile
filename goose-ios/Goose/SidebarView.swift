//
//  SidebarView.swift
//  Goose
//
//  Extracted from ChatView for reuse between WelcomeView and ChatView
//

import SwiftUI

// MARK: - Sidebar Glass Modifier

/// Applies Liquid Glass effect on iOS 26+ for the sidebar
private struct SidebarGlassModifier: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .background(.clear)
                .glassEffect(.regular, in: .rect(cornerRadius: 0))
        } else {
            // Fallback for iOS 17-25
            content
                .background(Color(.systemBackground))
        }
    }
}

// MARK: - Sidebar View
struct SidebarView: View {
    @Binding var isShowing: Bool
    @Binding var isSettingsPresented: Bool
    @Binding var cachedSessions: [ChatSession]
    let onSessionSelect: (String) -> Void
    let onNewSession: () -> Void
    let onLoadMore: () async -> Void
    let isLoadingMore: Bool
    let hasMoreSessions: Bool
    
    // Agent management
    @StateObject private var agentStorage = AgentStorage.shared
    @State private var showingRenameDialog = false
    @State private var agentToRename: AgentConfiguration?
    @State private var newAgentName = ""
    @State private var isAgentSectionExpanded = false  // Collapsed by default
    
    // Favorites management
    @StateObject private var favoritesStorage = FavoriteSessionsStorage.shared
    
    // Cached date groupings to avoid recomputation on every render
    @State private var cachedGroupedByDate: [(String, [ChatSession])] = []
    @State private var cachedFavoriteSessions: [ChatSession] = []
    
    // Fake sessions for trial mode - always add 2 fake sessions in trial mode
    private var fakeSessions: [ChatSession] {
        guard GooseAPIService.shared.isTrialMode else {
            return []
        }
        
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        let now = Date()
        
        // Always add 2 fake sessions in trial mode
        return [
            ChatSession(
                id: "fake-sidebar-1",
                description: "Chat Session 1",
                messageCount: 15,
                createdAt: formatter.string(from: Calendar.current.date(byAdding: .hour, value: -3, to: now)!),
                updatedAt: formatter.string(from: Calendar.current.date(byAdding: .hour, value: -3, to: now)!),
                workingDir: nil
            ),
            ChatSession(
                id: "fake-sidebar-2",
                description: "Chat Session 2",
                messageCount: 8,
                createdAt: formatter.string(from: Calendar.current.date(byAdding: .hour, value: -5, to: now)!),
                updatedAt: formatter.string(from: Calendar.current.date(byAdding: .hour, value: -5, to: now)!),
                workingDir: nil
            )
        ]
    }
    
    // Static ISO8601 formatter to avoid recreating
    private static let iso8601Formatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
    
    // Dynamic sidebar width based on device
    private var sidebarWidth: CGFloat {
        let screenWidth = UIScreen.main.bounds.width
        if UIDevice.current.userInterfaceIdiom == .pad {
            return screenWidth * 0.5 // 50% on iPad
        } else {
            return screenWidth // 100% on iPhone
        }
    }
    
    // Check if we're on iPad
    private var isIPad: Bool {
        UIDevice.current.userInterfaceIdiom == .pad
    }
    
    // Group sessions by date for displaying with headers
    // Excludes favorited sessions to avoid duplication
    private func groupSessionsByDate(sessions: [ChatSession]) -> [(String, [ChatSession])] {
        let calendar = Calendar.current
        var groups: [Date: [ChatSession]] = [:]
        let favoriteIds = favoritesStorage.favoriteSessionIds
        
        for session in sessions {
            // Skip favorited sessions - they're shown in FAVORITES section
            if favoriteIds.contains(session.id) {
                continue
            }
            
            guard let sessionDate = Self.iso8601Formatter.date(from: session.updatedAt) else {
                continue
            }
            
            let startOfDay = calendar.startOfDay(for: sessionDate)
            groups[startOfDay, default: []].append(session)
        }
        
        // Sort groups by date (newest first)
        let sortedGroups = groups.sorted { $0.key > $1.key }
        
        return sortedGroups.map { date, sessions in
            let label = formatDateHeader(date)
            // Sort sessions within each group by updated time (newest first)
            let sortedSessions = sessions.sorted { s1, s2 in
                guard let date1 = Self.iso8601Formatter.date(from: s1.updatedAt),
                      let date2 = Self.iso8601Formatter.date(from: s2.updatedAt) else {
                    return false
                }
                return date1 > date2
            }
            return (label, sortedSessions)
        }
    }
    
    private func formatDateHeader(_ date: Date) -> String {
        let calendar = Calendar.current
        
        if calendar.isDateInToday(date) {
            return "TODAY"
        } else if calendar.isDateInYesterday(date) {
            return "YESTERDAY"
        } else {
            let formatter = DateFormatter()
            formatter.dateFormat = "EEEE, MMM d"
            return formatter.string(from: date).uppercased()
        }
    }
    
    // Update cached groupings when sessions change
    private func updateGroupings() {
        let sessions = cachedSessions
        let favoriteIds = favoritesStorage.favoriteSessionIds
        
        Task {
            let groupedByDate = await Task.detached {
                self.groupSessionsByDate(sessions: sessions)
            }.value
            
            let favoriteSessions = await Task.detached {
                sessions.filter { favoriteIds.contains($0.id) }
                    .sorted { s1, s2 in
                        guard let date1 = Self.iso8601Formatter.date(from: s1.updatedAt),
                              let date2 = Self.iso8601Formatter.date(from: s2.updatedAt) else {
                            return false
                        }
                        return date1 > date2
                    }
            }.value
            
            await MainActor.run {
                self.cachedGroupedByDate = groupedByDate
                self.cachedFavoriteSessions = favoriteSessions
            }
        }
    }

    var body: some View {
        ZStack {
            // Background overlay
            Color.black.opacity(0.3)
                .ignoresSafeArea()
                .onTapGesture {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        isShowing = false
                    }
                }

            // Sidebar panel
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    // Header with New Session button at top
                    HStack {
                        Button(action: {
                            onNewSession()
                            withAnimation(.easeInOut(duration: 0.3)) {
                                isShowing = false
                            }
                        }) {
                            Image(systemName: "plus")
                                .font(.system(size: 18, weight: .medium))
                                .foregroundColor(.primary)
                                .frame(width: 32, height: 32)
                                .background(Color(.systemGray5))
                                .cornerRadius(8)
                        }
                        .buttonStyle(.plain)

                        Spacer()

                        Button(action: {
                            withAnimation(.easeInOut(duration: 0.3)) {
                                isShowing = false
                            }
                        }) {
                            Image(systemName: "xmark")
                                .font(.title3)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding()
                    .background(Color(.systemBackground))

                    Divider()

                    // Sessions list
                    GeometryReader { scrollGeometry in
                        ScrollView {
                            LazyVStack(spacing: 0) {
                            // Collapsible Agent section
                            VStack(spacing: 0) {
                                // Agent section header (clickable to expand/collapse)
                                Button(action: {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        isAgentSectionExpanded.toggle()
                                    }
                                }) {
                                    HStack(spacing: 12) {
                                        Image("ServerIcon")
                                            .resizable()
                                            .aspectRatio(contentMode: .fit)
                                            .frame(width: 13, height: 13)
                                            .foregroundColor(.primary)
                                        
                                        HStack(spacing: 8) {
                                            Text("Connected Agents")
                                                .font(.system(size: 15, weight: .medium))
                                                .foregroundColor(.primary)
                                            
                                            // Agent count badge
                                            if !agentStorage.savedAgents.isEmpty {
                                                Text("\(agentStorage.savedAgents.count)")
                                                    .font(.system(size: 11, weight: .semibold))
                                                    .foregroundColor(.white)
                                                    .padding(.horizontal, 6)
                                                    .padding(.vertical, 2)
                                                    .background(Color.blue)
                                                    .cornerRadius(10)
                                            }
                                        }
                                        
                                        Spacer()
                                        
                                        // Show current agent name on the right
                                        if let currentAgent = agentStorage.savedAgents.first(where: { $0.id == agentStorage.currentAgentId }) {
                                            Text(currentAgent.displayName)
                                                .font(.system(size: 12))
                                                .foregroundColor(.secondary)
                                                .lineLimit(1)
                                                .truncationMode(.tail)
                                        }
                                        
                                        // Chevron indicator
                                        Image(systemName: isAgentSectionExpanded ? "chevron.down" : "chevron.right")
                                            .font(.system(size: 12, weight: .semibold))
                                            .foregroundColor(.secondary)
                                    }
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 12)
                                    .frame(maxWidth: .infinity)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .background(Color(.systemBackground))
                                
                                // Agent list (shown when expanded)
                                if isAgentSectionExpanded {
                                    if agentStorage.savedAgents.isEmpty {
                                        // Empty state
                                        VStack(spacing: 8) {
                                            Text("No saved agents")
                                                .font(.system(size: 14))
                                                .foregroundColor(.secondary)
                                            Text("Configure an agent in Settings to save it")
                                                .font(.system(size: 12))
                                                .foregroundColor(.secondary)
                                                .multilineTextAlignment(.center)
                                        }
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 24)
                                        .padding(.horizontal, 16)
                                        .transition(.opacity.combined(with: .move(edge: .top)))
                                    } else {
                                        ForEach(agentStorage.savedAgents) { agent in
                                            AgentRowView(
                                                agent: agent,
                                                isCurrent: agentStorage.currentAgentId == agent.id,
                                                onTap: {
                                                    // Close sidebar first for smooth UX, then switch agent
                                                    withAnimation(.easeInOut(duration: 0.25)) {
                                                        isShowing = false
                                                    }
                                                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.26) {
                                                        ConfigurationHandler.shared.switchToAgent(agent)
                                                    }
                                                },
                                                onRename: {
                                                    agentToRename = agent
                                                    newAgentName = agent.name ?? ""
                                                    showingRenameDialog = true
                                                },
                                                onDelete: {
                                                    agentStorage.deleteAgent(agent)
                                                }
                                            )
                                            Divider()
                                                .background(Color.gray.opacity(0.2))
                                                .padding(.leading)
                                        }
                                        .transition(.opacity.combined(with: .move(edge: .top)))
                                    }
                                }
                            }
                            
                            // Favorites section
                            if !cachedFavoriteSessions.isEmpty {
                                DateSectionHeader(label: "FAVORITES")
                                
                                ForEach(cachedFavoriteSessions) { session in
                                    SessionRowView(session: session)
                                        .onTapGesture {
                                            onSessionSelect(session.id)
                                            withAnimation(.easeInOut(duration: 0.3)) {
                                                isShowing = false
                                            }
                                        }
                                    Divider()
                                        .background(Color.gray.opacity(0.2))
                                        .padding(.leading)
                                }
                                
                                // Spacer after favorites
                                Color.clear
                                    .frame(height: 12)
                            }
                            
                            // Push sessions to bottom if fewer than 5
                            if cachedSessions.count < 5 {
                                Color.clear
                                    .frame(height: scrollGeometry.size.height * 0.3)
                            }

                            // Sessions grouped by date
                            ForEach(cachedGroupedByDate, id: \.0) { dateLabel, sessions in
                                // Date section header
                                DateSectionHeader(label: dateLabel)
                                
                                // Sessions for this date
                                ForEach(sessions) { session in
                                    SessionRowView(session: session)
                                        .onTapGesture {
                                            onSessionSelect(session.id)
                                            withAnimation(.easeInOut(duration: 0.3)) {
                                                isShowing = false
                                            }
                                        }
                                    Divider()
                                        .background(Color.gray.opacity(0.2))
                                        .padding(.leading)
                                }
                            }
                            
                            // Fake sessions for trial mode
                            ForEach(fakeSessions) { session in
                                SessionRowView(session: session)
                                    .opacity(0.6)  // Make them slightly faded
                                    .onTapGesture {
                                        // Fake sessions just close sidebar
                                        withAnimation(.easeInOut(duration: 0.3)) {
                                            isShowing = false
                                        }
                                    }
                                Divider()
                                    .background(Color.gray.opacity(0.2))
                                    .padding(.leading)
                            }
                            
                            // Load more indicator at the bottom
                            if hasMoreSessions {
                                Button(action: {
                                    Task {
                                        await onLoadMore()
                                    }
                                }) {
                                    HStack(spacing: 8) {
                                        if isLoadingMore {
                                            ProgressView()
                                                .progressViewStyle(CircularProgressViewStyle())
                                                .scaleEffect(0.8)
                                            Text("Loading...")
                                                .font(.system(size: 14))
                                        } else {
                                            Image(systemName: "arrow.down.circle")
                                                .font(.system(size: 14))
                                            Text("Load More Sessions")
                                                .font(.system(size: 14))
                                        }
                                    }
                                    .foregroundColor(.blue)
                                    .padding(.vertical, 12)
                                    .padding(.horizontal, 16)
                                    .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.plain)
                                .disabled(isLoadingMore)
                                .accessibilityLabel("Load more sessions")
                                .accessibilityHint("Loads the next batch of sessions")
                            }
                        }
                    }
                    }

                    Spacer()

                    Divider()

                    // Bottom row: Settings button
                    Button(action: {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            // Close sidebar with animation
                            isShowing = false
                        }
                        // Open settings after animation completes
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            isSettingsPresented = true
                        }
                    }) {
                        HStack {
                            Image(systemName: "gear")
                                .font(.system(size: 18))
                                .foregroundColor(.primary)

                            Text("Settings")
                                .font(.system(size: 16))
                                .foregroundColor(.primary)

                            Spacer()
                        }
                        .padding()
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .background(Color(.systemBackground))
                }
                .frame(width: sidebarWidth)
                .modifier(SidebarGlassModifier())
                .offset(x: isShowing ? 0 : -sidebarWidth)
                .animation(.easeInOut(duration: 0.3), value: isShowing)

                // Only add spacer on iPad to show overlay on the right
                if isIPad {
                    Spacer()
                }
            }
        }
        .alert("Rename Agent", isPresented: $showingRenameDialog) {
            TextField("Agent Name", text: $newAgentName)
            Button("Cancel", role: .cancel) {
                agentToRename = nil
                newAgentName = ""
            }
            Button("Save") {
                if let agent = agentToRename {
                    var updated = agent
                    updated.name = newAgentName.isEmpty ? nil : newAgentName
                    agentStorage.saveAgent(updated)
                }
                agentToRename = nil
                newAgentName = ""
            }
        } message: {
            Text("Enter a name for this agent")
        }
        .onAppear {
            updateGroupings()
        }
        .onChange(of: cachedSessions) { _ in
            updateGroupings()
        }
        .onChange(of: favoritesStorage.favoriteSessionIds) { _ in
            updateGroupings()
        }
    }
}

// MARK: - Date Section Header
struct DateSectionHeader: View {
    let label: String
    
    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.secondary)
                .tracking(0.5)
            
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .padding(.bottom, 8)
        .background(Color(.systemBackground))
    }
}

// MARK: - Session Row View
struct SessionRowView: View {
    let session: ChatSession
    @ObservedObject private var favoritesStorage = FavoriteSessionsStorage.shared
    
    var isFavorite: Bool {
        favoritesStorage.isFavorite(session.id)
    }

    var body: some View {
        HStack(spacing: 0) {
            // Favorite button on the left - fixed size with consistent spacing
            Button(action: {
                favoritesStorage.toggleFavorite(session.id)
            }) {
                Image(systemName: isFavorite ? "star.fill" : "star")
                    .font(.system(size: 13))
                    .foregroundColor(isFavorite ? .yellow : .secondary)
                    .frame(width: 20, height: 20, alignment: .center)
            }
            .buttonStyle(.plain)
            .fixedSize()
            .padding(.trailing, 8)  // Fixed spacing after star
            
            // Session name - truncates only, never scales
            Text(session.description)
                .font(.system(size: 15, weight: .medium))
                .lineLimit(1)
                .truncationMode(.tail)
                .foregroundColor(.primary)
                .layoutPriority(0)  // Lowest priority - truncates first
                .frame(maxWidth: .infinity, alignment: .leading)  // Left align and fill space
            
            // Time and message count container - NEVER wraps or truncates
            HStack(spacing: 8) {
                // Time ago with wider fixed width for AM/PM format
                Text(formatTime(session.updatedAt))
                    .font(.system(size: 13))
                    .foregroundColor(.secondary)
                    .frame(width: 65, alignment: .trailing)  // Fits "XX:XX AM"
                    .lineLimit(1)
                    .allowsTightening(false)
                
                // Message count with icon and wider fixed width for large numbers
                HStack(spacing: 4) {
                    Image("MessageIcon")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 12, height: 12)
                        .foregroundColor(.secondary)
                    
                    Text("\(session.messageCount)")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                        .allowsTightening(false)
                }
                .frame(width: 48, alignment: .trailing)  // Increased from 36 to 48 for numbers like "999"
            }
            .fixedSize()  // This container never changes size
            .layoutPriority(100)  // HIGHEST priority - never compresses
            .padding(.leading, 8)  // Fixed spacing before metadata
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: 44)  // Fixed height to prevent expansion
    }

    private func formatTime(_ isoDate: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        
        guard let date = formatter.date(from: isoDate) else {
            return "Unknown"
        }
        
        // Just show time for same-day sessions
        let timeFormatter = DateFormatter()
        timeFormatter.timeStyle = .short
        return timeFormatter.string(from: date)
    }
}

// MARK: - Agent Row View
struct AgentRowView: View {
    let agent: AgentConfiguration
    let isCurrent: Bool
    let onTap: () -> Void
    let onRename: () -> Void
    let onDelete: () -> Void
    
    @State private var showingOptions = false
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                // Current indicator
                Circle()
                    .fill(isCurrent ? Color.green : Color.clear)
                    .frame(width: 8, height: 8)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(agent.displayName)
                        .font(.system(size: 15, weight: isCurrent ? .semibold : .medium))
                        .lineLimit(1)
                        .foregroundColor(.primary)
                    
                    if let subtitle = agent.subtitle {
                        Text(subtitle)
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                }
                
                Spacer()
                
                // Options button
                Button(action: {
                    showingOptions = true
                }) {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .confirmationDialog("Agent Options", isPresented: $showingOptions, titleVisibility: .visible) {
            Button("Rename") {
                onRename()
            }
            Button("Delete", role: .destructive) {
                onDelete()
            }
            Button("Cancel", role: .cancel) {}
        }
    }
}

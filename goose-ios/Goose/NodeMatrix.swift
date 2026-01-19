import SwiftUI

struct NodeMatrix: View {
    let sessions: [ChatSession]
    let selectedSessionId: String?
    let onNodeTap: (ChatSession, CGPoint) -> Void
    let onDayChange: ((Int) -> Void)?
    var showDraftNode: Bool = false
    @Environment(\.colorScheme) var colorScheme
    @State private var daysOffset: Int = 0
    @State private var dragOffset: CGFloat = 0
    @State private var swipeDirection: CGFloat = 0
    @GestureState private var isDragging: Bool = false
    @State private var pulseAnimation = false
    @State private var dashPhase: CGFloat = 0
    @State private var containerOffset: CGFloat = 0
    @State private var geometrySize: CGSize = .zero
    @State private var favoritePulse = false
    
    // MARK: - Shared Date Formatter
    /// Reusable date formatter to avoid expensive recreation
    private static let dateFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
    
// MARK: - Position Cache
/// Cache structure: [cacheKey: [sessionId: position]]
/// cacheKey format: "dateLabel-widthxheight"
@State private var positionCache: [String: [String: CGPoint]] = [:]
    
    // MARK: - Session Cache
    /// Cache filtered/sorted sessions by day offset to avoid expensive filtering on every render
    /// Key: daysOffset, Value: filtered and sorted sessions for that day
    @State private var sessionCache: [Int: [ChatSession]] = [:]
    
    // MARK: - Size Constants
    private let minNodeSize: CGFloat = 8
    private let maxNodeSize: CGFloat = 16
    private let minTapTarget: CGFloat = 32
    private let messageDotSize: CGFloat = 4
    private let baseMessageDotRadius: CGFloat = 30
    
    // MARK: - Live Session Detection
    /// Check if a session is "live" (updated within last 5 minutes)
    private func isSessionLive(_ session: ChatSession) -> Bool {
        guard let sessionDate = Self.dateFormatter.date(from: session.updatedAt) else {
            return false
        }
        
        let now = Date()
        let secondsAgo = now.timeIntervalSince(sessionDate)
        
        // Consider live if updated in last 5 minutes (300 seconds)
        return secondsAgo < 300
    }
    
    // MARK: - Node Size Calculation
    private func nodeSize(for messageCount: Int) -> CGFloat {
        let lowThreshold = 5
        let highThreshold = 50
        
        if messageCount <= lowThreshold {
            return minNodeSize
        } else if messageCount >= highThreshold {
            return maxNodeSize
        } else {
            let progress = CGFloat(messageCount - lowThreshold) / CGFloat(highThreshold - lowThreshold)
            let easedProgress = 1 - pow(1 - progress, 2)
            return minNodeSize + (maxNodeSize - minNodeSize) * easedProgress
        }
    }
    
    private func tapTargetSize(for nodeSize: CGFloat) -> CGFloat {
        return max(minTapTarget, nodeSize * 3)
    }
    
    private func highlightRingSize(for nodeSize: CGFloat) -> CGFloat {
        return nodeSize * 2.5
    }
    
    private func messageDotRadius(for nodeSize: CGFloat) -> CGFloat {
        let sizeRatio = nodeSize / minNodeSize
        return baseMessageDotRadius * sizeRatio
    }
    
    // MARK: - Date Helpers
    private func targetDate(for offset: Int) -> Date {
        let calendar = Calendar.current  // Use local calendar for user's timezone
        let today = calendar.startOfDay(for: Date())
        return calendar.date(byAdding: .day, value: -offset, to: today) ?? today
    }
    
    private func daySessions(for offset: Int) -> [ChatSession] {
        // Check cache first
        if let cached = sessionCache[offset] {
            return cached
        }
        
        // If not cached, calculate it
        let calendar = Calendar.current  // Use local calendar for user's timezone
        let target = targetDate(for: offset)
        
        let filtered = sessions.filter { session in
            guard let sessionDate = Self.dateFormatter.date(from: session.updatedAt) else {
                return false
            }
            
            return calendar.isDate(sessionDate, inSameDayAs: target)
        }
        
        let sorted = filtered.sorted { session1, session2 in
            guard let date1 = Self.dateFormatter.date(from: session1.updatedAt),
                  let date2 = Self.dateFormatter.date(from: session2.updatedAt) else {
                return false
            }
            return date1 < date2
        }
        
        // Limit to most recent 30 sessions
        let limited = Array(sorted.suffix(30))
        
        // Note: We can't cache here because this is called during body rendering
        // Caching will happen in precalculateSessions()
        return limited
    }
    
    /// Pre-calculate and cache sessions for a day offset
    private func precalculateSessions(for offset: Int) {
        // Skip if already cached
        if sessionCache[offset] != nil {
            return
        }
        
        let calendar = Calendar.current  // Use local timezone
        let target = targetDate(for: offset)
        
        let filtered = sessions.filter { session in
            guard let sessionDate = Self.dateFormatter.date(from: session.updatedAt) else {
                return false
            }
            
            return calendar.isDate(sessionDate, inSameDayAs: target)
        }
        
        let sorted = filtered.sorted { session1, session2 in
            guard let date1 = Self.dateFormatter.date(from: session1.updatedAt),
                  let date2 = Self.dateFormatter.date(from: session2.updatedAt) else {
                return false
            }
            return date1 < date2
        }
        
        // Limit to most recent 30 sessions
        let limited = Array(sorted.suffix(30))
        
        sessionCache[offset] = limited
        print("💾 Cached \(limited.count) sessions for day offset \(offset)")
    }
    
    private func dateLabel(for offset: Int) -> String {
        let calendar = Calendar.current  // Use local timezone
        let target = targetDate(for: offset)
        let formatter = DateFormatter()
        
        if calendar.isDateInToday(target) {
            return "Today"
        } else if calendar.isDateInYesterday(target) {
            return "Yesterday"
        } else {
            formatter.dateStyle = .medium
            return formatter.string(from: target)
        }
    }
    
    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                // Date label header
                HStack {
                    HStack(spacing: 8) {
                        Text(dateLabel(for: daysOffset))
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.primary)
                        
                        Image(systemName: "chevron.right")
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(.secondary.opacity(0.5))
                        
                        if daysOffset > 0 {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(.secondary.opacity(0.5))
                        }
                    }
                    
                    Spacer()
                }
                .padding(.top, 24)
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
                
                // Node content area with preview effect
                GeometryReader { nodeGeometry in
                    HStack(spacing: 0) {
                        // Previous day
                        DayContentView(
                            sessions: daySessions(for: daysOffset + 1),
                            selectedSessionId: selectedSessionId,
                            showDraftNode: false,
                            dateLabel: dateLabel(for: daysOffset + 1),
                            colorScheme: colorScheme,
                            onNodeTap: onNodeTap,
                            nodeSize: nodeSize,
                            tapTargetSize: tapTargetSize,
                            highlightRingSize: highlightRingSize,
                            messageDotRadius: messageDotRadius,
                            messageDotSize: messageDotSize,
                            getCachedPosition: self.getCachedPosition,
                            isSessionLive: self.isSessionLive
                        )
                        .frame(width: nodeGeometry.size.width, height: nodeGeometry.size.height)
                        
                        // Current day
                        DayContentView(
                            sessions: daySessions(for: daysOffset),
                            selectedSessionId: selectedSessionId,
                            showDraftNode: showDraftNode && daysOffset == 0,
                            dateLabel: dateLabel(for: daysOffset),
                            colorScheme: colorScheme,
                            onNodeTap: onNodeTap,
                            nodeSize: nodeSize,
                            tapTargetSize: tapTargetSize,
                            highlightRingSize: highlightRingSize,
                            messageDotRadius: messageDotRadius,
                            messageDotSize: messageDotSize,
                            getCachedPosition: self.getCachedPosition,
                            isSessionLive: self.isSessionLive,
                            pulseAnimation: $pulseAnimation,
                            dashPhase: $dashPhase
                        )
                        .frame(width: nodeGeometry.size.width, height: nodeGeometry.size.height)
                        
                        // Next day (only if not on today)
                        if daysOffset > 0 {
                            DayContentView(
                                sessions: daySessions(for: daysOffset - 1),
                                selectedSessionId: selectedSessionId,
                                showDraftNode: showDraftNode && daysOffset == 1,
                                dateLabel: dateLabel(for: daysOffset - 1),
                                colorScheme: colorScheme,
                                onNodeTap: onNodeTap,
                                nodeSize: nodeSize,
                                tapTargetSize: tapTargetSize,
                                highlightRingSize: highlightRingSize,
                                messageDotRadius: messageDotRadius,
                                messageDotSize: messageDotSize,
                                getCachedPosition: self.getCachedPosition,
                                isSessionLive: self.isSessionLive
                            )
                            .frame(width: nodeGeometry.size.width, height: nodeGeometry.size.height)
                        }
                    }
                    .offset(x: -nodeGeometry.size.width + containerOffset + dragOffset)
                .onAppear {
                    // Pre-calculate sessions first (must happen before positions)
                    precalculateSessions(for: daysOffset + 1)
                    precalculateSessions(for: daysOffset)
                    if daysOffset > 0 {
                        precalculateSessions(for: daysOffset - 1)
                    }
                    
                    // Store geometry size and pre-calculate positions for visible days
                    let size = nodeGeometry.size
                    geometrySize = size
                    precalculatePositions(for: dateLabel(for: daysOffset + 1), sessions: daySessions(for: daysOffset + 1), size: size)
                    precalculatePositions(for: dateLabel(for: daysOffset), sessions: daySessions(for: daysOffset), size: size)
                    if daysOffset > 0 {
                        precalculatePositions(for: dateLabel(for: daysOffset - 1), sessions: daySessions(for: daysOffset - 1), size: size)
                    }
                }
                }
                .padding(.horizontal, 16)
                .clipped()
                
                // Session count indicator
                let currentDaySessions = daySessions(for: daysOffset)
                if !currentDaySessions.isEmpty || (showDraftNode && daysOffset == 0) {
                    HStack {
                        if showDraftNode && daysOffset == 0 {
                            if currentDaySessions.isEmpty {
                                Text("Draft in progress")
                                    .font(.caption2)
                                    .foregroundColor(.green.opacity(0.8))
                            } else {
                                Text("Draft + \(currentDaySessions.count) session\(currentDaySessions.count == 1 ? "" : "s")")
                                    .font(.caption2)
                                    .foregroundColor(.green.opacity(0.8))
                            }
                        } else {
                            Text("\(currentDaySessions.count) session\(currentDaySessions.count == 1 ? "" : "s")")
                                .font(.caption2)
                                .foregroundColor(.secondary.opacity(0.6))
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 16)
                } else {
                    HStack {
                        Text("Swipe left or right")
                            .font(.caption2)
                            .foregroundColor(.secondary.opacity(0.5))
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 16)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .highPriorityGesture(
                DragGesture(minimumDistance: 10)
                    .updating($isDragging) { value, state, transaction in
                        state = true
                        // Disable animations during drag for smooth performance
                        transaction.animation = nil
                    }
                    .onChanged { value in
                        let horizontalAmount = value.translation.width
                        let verticalAmount = abs(value.translation.height)
                        
                        if abs(horizontalAmount) > verticalAmount {
                            // Disable animation for immediate drag response
                            var transaction = Transaction()
                            transaction.disablesAnimations = true
                            withTransaction(transaction) {
                                dragOffset = horizontalAmount
                            }
                        }
                    }
                    .onEnded { value in
                        let threshold: CGFloat = 50
                        let horizontalAmount = value.translation.width
                        let verticalAmount = abs(value.translation.height)
                        
                        if abs(horizontalAmount) > verticalAmount {
                            if abs(horizontalAmount) > threshold {
                                swipeDirection = horizontalAmount
                                
                                let startOffset = dragOffset
                                let targetWidth = geometry.size.width - 32
                                
                                // Determine target position
                                let targetOffset: CGFloat
                                if horizontalAmount > 0 {
                                    // Swiping right - going to past
                                    targetOffset = targetWidth
                                } else {
                                    // Swiping left - going to future
                                    targetOffset = -targetWidth
                                }
                                
                                // Check if we're already close to target (within 20% of remaining distance)
                                let remainingDistance = abs(targetOffset - startOffset)
                                let needsAnimation = remainingDistance > (targetWidth * 0.2)

                                // Set containerOffset to current position first
                                containerOffset = startOffset
                                
                                dragOffset = 0  // Now reset drag
                                
                                if needsAnimation {
                                    // Animate the remaining distance
                                    withAnimation(.easeOut(duration: 0.15)) {
                                        containerOffset = targetOffset
                                    }
                                    
                                    // After animation, update daysOffset and reset containerOffset
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                                        if horizontalAmount > 0 {
                                            daysOffset += 1
                                        } else if daysOffset > 0 {
                                            daysOffset -= 1
                                        }
                                        containerOffset = 0
                                    }
                                } else {
                                    // Already there, just snap into place
                                    containerOffset = targetOffset
                                    
                                    // Immediately update daysOffset
                                    DispatchQueue.main.async {
                                        if horizontalAmount > 0 {
                                            daysOffset += 1
                                        } else if daysOffset > 0 {
                                            daysOffset -= 1
                                        }
                                        containerOffset = 0
                                    }
                                }
                            } else {
                                // Below threshold, snap back
                                dragOffset = 0
                            }
                        } else {
                            dragOffset = 0
                        }
                    }
            )
        }
        .onChange(of: showDraftNode) { oldValue, newValue in
            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
            }
        }
        .onChange(of: daysOffset) { oldValue, newValue in
            onDayChange?(newValue)
            // Pre-calculate sessions first
            precalculateSessions(for: newValue + 1)
            precalculateSessions(for: newValue)
            if newValue > 0 {
                precalculateSessions(for: newValue - 1)
            }
            // Pre-calculate positions for the new day
            if geometrySize != .zero {
                precalculatePositions(for: dateLabel(for: newValue + 1), sessions: daySessions(for: newValue + 1), size: geometrySize)
                precalculatePositions(for: dateLabel(for: newValue), sessions: daySessions(for: newValue), size: geometrySize)
                if newValue > 0 {
                    precalculatePositions(for: dateLabel(for: newValue - 1), sessions: daySessions(for: newValue - 1), size: geometrySize)
                }
            }
        }
        .onAppear {
            onDayChange?(daysOffset)
        }
        .onChange(of: sessions) { oldValue, newValue in
            // Clear session cache when sessions array changes
            sessionCache.removeAll()
            print("🔄 Session cache cleared due to sessions update")
        }
    }

    // MARK: - Cached Position Calculation
    /// Get position for a session, using cache when available
    private func getCachedPosition(
        for session: ChatSession,
        index: Int,
        in size: CGSize,
        allSessions: [ChatSession],
        dateLabel: String
    ) -> CGPoint {
        // Create cache key based on date label and geometry size
        let cacheKey = "\(dateLabel)-\(Int(size.width))x\(Int(size.height))"
        
        // Check if we have cached position for this session
        if let cachedPosition = positionCache[cacheKey]?[session.id] {
            return cachedPosition
        }
        
        // If not cached, calculate it (but don't cache during render - that causes state modification warning)
        // The position will be calculated each time until we pre-populate the cache
        return nodePosition(for: index, session: session, in: size, allSessions: allSessions)
    }
    
    /// Pre-calculate and cache positions for all sessions in a day
    private func precalculatePositions(for dateLabel: String, sessions: [ChatSession], size: CGSize) {
        let cacheKey = "\(dateLabel)-\(Int(size.width))x\(Int(size.height))"
        
        // Skip if already cached
        if positionCache[cacheKey] != nil && positionCache[cacheKey]!.count == sessions.count {
            print("✅ Positions already cached for \(dateLabel)")
            return
        }
        
        print("💾 Pre-calculating positions for \(sessions.count) sessions on \(dateLabel)")
        
        // Initialize cache for this key
        if positionCache[cacheKey] == nil {
            positionCache[cacheKey] = [:]
        }
        
        // Calculate and cache all positions
        for (index, session) in sessions.enumerated() {
            let position = nodePosition(for: index, session: session, in: size, allSessions: sessions)
            positionCache[cacheKey]?[session.id] = position
        }
        
        print("✅ Cached \(sessions.count) positions for \(dateLabel)")
    }
    
    /// Clear cache for a specific date label (call when sessions change)
    private func clearCache(for dateLabel: String) {
        positionCache = positionCache.filter { !$0.key.hasPrefix("\(dateLabel)-") }
    }
    
    /// Clear all cached positions (call when sessions list changes significantly)
    private func clearAllCache() {
        positionCache.removeAll()
    }

    // MARK: - Position Calculation
   private func nodePosition(for index: Int, session: ChatSession, in size: CGSize, allSessions: [ChatSession]) -> CGPoint {
        guard !allSessions.isEmpty else { return CGPoint(x: size.width / 2, y: size.height / 2) }
        
        let paddingX: CGFloat = size.width * 0.15
        let paddingY: CGFloat = size.height * 0.20
        let availableWidth = size.width - (paddingX * 2)
        let availableHeight = size.height - (paddingY * 2)
        
        if allSessions.count == 1 {
            return CGPoint(x: size.width / 2, y: size.height / 2)
        }
        
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        
        guard let sessionDate = formatter.date(from: session.updatedAt) else {
            return randomPosition(seed: session.id.hashValue, in: size, paddingX: paddingX, paddingY: paddingY)
        }
        
        let dates = allSessions.compactMap { formatter.date(from: $0.updatedAt) }
        guard let minDate = dates.min(), let maxDate = dates.max() else {
            return randomPosition(seed: session.id.hashValue, in: size, paddingX: paddingX, paddingY: paddingY)
        }
        
        let timeRange = maxDate.timeIntervalSince(minDate)
        let timeProgress: CGFloat
        
        if timeRange > 0 {
            timeProgress = CGFloat(sessionDate.timeIntervalSince(minDate) / timeRange)
        } else {
            timeProgress = CGFloat(index) / CGFloat(allSessions.count - 1)
        }
        
        let seed = abs(session.id.hashValue)
        let random1 = sin(Double(seed))
        let random2 = cos(Double(seed) * 1.5)
        let random3 = sin(Double(seed) * 2.3)
        
        let clusterCount: CGFloat = 4
        let clusterIndex = floor(timeProgress * clusterCount)
        
        let clusterCenterX = (clusterIndex + 0.5) / clusterCount
        let clusterCenterY: CGFloat = 0.5
        
        let clusterSpreadX: CGFloat = 0.15
        let clusterSpreadY: CGFloat = 0.25
        
        let offsetX = (random1 * 0.5 + 0.5) * clusterSpreadX - (clusterSpreadX / 2)
        let offsetY = (random2 * 0.5 + 0.5) * clusterSpreadY - (clusterSpreadY / 2)
        
        let microOffsetX = (random3 * 0.5 + 0.5) * 0.03 - 0.015
        let microOffsetY = (sin(random3 * 3) * 0.5 + 0.5) * 0.03 - 0.015
        
        let xProgress = clusterCenterX + offsetX + microOffsetX
        let yProgress = clusterCenterY + offsetY + microOffsetY
        
        let x = paddingX + (availableWidth * min(max(xProgress, 0), 1))
        let y = paddingY + (availableHeight * min(max(yProgress, 0), 1))
        
        return CGPoint(x: x, y: y)
    }
    
    private func randomPosition(seed: Int, in size: CGSize, paddingX: CGFloat, paddingY: CGFloat) -> CGPoint {
        let random1 = sin(Double(seed)) * 0.5 + 0.5
        let random2 = cos(Double(seed) * 1.5) * 0.5 + 0.5
        
        let x = paddingX + (size.width - paddingX * 2) * CGFloat(random1)
        let y = paddingY + (size.height - paddingY * 2) * CGFloat(random2)
        
        return CGPoint(x: x, y: y)
    }
}
    

// [Rest of the file remains the same - DayContentView, SimulatedMessageDotsOverlay, Preview]
// MARK: - Day Content View
struct DayContentView: View {
    let sessions: [ChatSession]
    let selectedSessionId: String?
    let showDraftNode: Bool
    let dateLabel: String
    let colorScheme: ColorScheme
    let onNodeTap: (ChatSession, CGPoint) -> Void
    let nodeSize: (Int) -> CGFloat
    let tapTargetSize: (CGFloat) -> CGFloat
    let highlightRingSize: (CGFloat) -> CGFloat
    let messageDotRadius: (CGFloat) -> CGFloat
    let messageDotSize: CGFloat
    let getCachedPosition: (ChatSession, Int, CGSize, [ChatSession], String) -> CGPoint
    let isSessionLive: (ChatSession) -> Bool
    @Binding var pulseAnimation: Bool
    @Binding var dashPhase: CGFloat
    
    // Favorite storage for checking favorite status
    @ObservedObject private var favoritesStorage = FavoriteSessionsStorage.shared
    
    // Separate pulse animations
    @State private var favoritePulse = false
    @State private var livePulse = false  // For live sessions
    
    // Computed property for sessions with fake nodes in trial mode
    private var displaySessions: [ChatSession] {
        // In trial mode, always add fake sessions to populate the matrix
        if GooseAPIService.shared.isTrialMode {
            var fakeSessions = sessions
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime]
            
            // Add 3 fake nodes with different sizes
            let now = Date()
            for i in 1...3 {
                let fakeDate = Calendar.current.date(byAdding: .hour, value: -(i * 3), to: now)!
                fakeSessions.append(ChatSession(
                    id: "fake-\(i)",
                    description: "Sample Session \(i)",
                    messageCount: [8, 14, 10][i-1],
                    createdAt: formatter.string(from: fakeDate),
                    updatedAt: formatter.string(from: fakeDate),
                    workingDir: nil
                ))
            }
            return fakeSessions
        }
        return sessions
    }
    
    init(sessions: [ChatSession],
         selectedSessionId: String?,
         showDraftNode: Bool,
         dateLabel: String,
         colorScheme: ColorScheme,
         onNodeTap: @escaping (ChatSession, CGPoint) -> Void,
         nodeSize: @escaping (Int) -> CGFloat,
         tapTargetSize: @escaping (CGFloat) -> CGFloat,
         highlightRingSize: @escaping (CGFloat) -> CGFloat,
         messageDotRadius: @escaping (CGFloat) -> CGFloat,
         messageDotSize: CGFloat,
         getCachedPosition: @escaping (ChatSession, Int, CGSize, [ChatSession], String) -> CGPoint,
         isSessionLive: @escaping (ChatSession) -> Bool = { _ in false },
         pulseAnimation: Binding<Bool> = .constant(false),
         dashPhase: Binding<CGFloat> = .constant(0)) {
        self.sessions = sessions
        self.selectedSessionId = selectedSessionId
        self.showDraftNode = showDraftNode
        self.dateLabel = dateLabel
        self.colorScheme = colorScheme
        self.onNodeTap = onNodeTap
        self.nodeSize = nodeSize
        self.tapTargetSize = tapTargetSize
        self.highlightRingSize = highlightRingSize
        self.messageDotRadius = messageDotRadius
        self.messageDotSize = messageDotSize
        self.getCachedPosition = getCachedPosition
        self.isSessionLive = isSessionLive
        self._pulseAnimation = pulseAnimation
        self._dashPhase = dashPhase
    }
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Empty state
                if displaySessions.isEmpty && !showDraftNode {
                    VStack(spacing: 8) {
                        Image(systemName: "calendar.badge.clock")
                            .font(.system(size: 32))
                            .foregroundColor(.secondary.opacity(0.5))
                        
                        Text("No sessions on \(dateLabel.lowercased())")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                
                // Draw connections
                if displaySessions.count > 1 {
                    Path { path in
                        for i in 0..<(displaySessions.count - 1) {
                            let startPoint = getCachedPosition(displaySessions[i], i, geometry.size, displaySessions, dateLabel)
                            let endPoint = getCachedPosition(displaySessions[i + 1], i + 1, geometry.size, displaySessions, dateLabel)
                            
                            path.move(to: startPoint)
                            path.addLine(to: endPoint)
                        }
                    }
                    .stroke(
                        colorScheme == .dark ?
                        Color.white.opacity(0.15) :
                            Color.black.opacity(0.1),
                        style: StrokeStyle(lineWidth: 1, lineCap: .round)
                    )
                }
                
                // Draft connection line
                if showDraftNode && !displaySessions.isEmpty {
                    Path { path in
                        let lastSession = displaySessions[displaySessions.count - 1]
                        let lastPosition = getCachedPosition(lastSession, displaySessions.count - 1, geometry.size, displaySessions, dateLabel)
                        let draftPos = draftNodePosition(in: geometry.size, sessions: displaySessions)
                        
                        path.move(to: lastPosition)
                        path.addLine(to: draftPos)
                    }
                    .stroke(
                        Color.green.opacity(0.3),
                        style: StrokeStyle(lineWidth: 1, lineCap: .round, dash: [5, 5], dashPhase: dashPhase)
                    )
                }
                
                // Session nodes
                ForEach(Array(displaySessions.enumerated()), id: \.element.id) { index, session in
                    let position = getCachedPosition(session, index, geometry.size, displaySessions, dateLabel)
                    let isSelected = selectedSessionId == session.id
                    let isFavorite = favoritesStorage.isFavorite(session.id)
                    let isLive = isSessionLive(session)
                    let isFakeNode = session.id.hasPrefix("fake-")
                    let currentNodeSize = nodeSize(session.messageCount)
                    let currentTapTarget = tapTargetSize(currentNodeSize)
                    let currentHighlightRing = highlightRingSize(currentNodeSize)
                    
                    Button(action: {
                        // Fake nodes do nothing when tapped
                        if !isFakeNode {
                            onNodeTap(session, position)
                        }
                    }) {
                        ZStack {
                            Circle()
                                .fill(Color.clear)
                                .frame(width: currentTapTarget, height: currentTapTarget)
                            
                            // Live indicator - cyan/teal pulse (takes precedence when not selected/favorited)
                            if isLive && !isSelected && !isFavorite {
                                ZStack {
                                    // Static ring
                                    Circle()
                                        .stroke(Color.cyan.opacity(0.7), lineWidth: 1.5)
                                        .frame(width: currentNodeSize + 4, height: currentNodeSize + 4)
                                    
                                    // Pulsing ring with blur (2.5s animation)
                                    Circle()
                                        .stroke(Color.cyan.opacity(0.6), lineWidth: 2)
                                        .frame(width: currentNodeSize + 4, height: currentNodeSize + 4)
                                        .blur(radius: 3)
                                        .scaleEffect(livePulse ? 1.4 : 1.0)
                                        .opacity(livePulse ? 0.0 : 0.9)
                                }
                            }
                            
                            // Favorite indicator - subtle yellow glow with slow pulse (very close to node)
                            if isFavorite && !isSelected {
                                ZStack {
                                    // Static ring - much closer to the node
                                    Circle()
                                        .stroke(Color.yellow.opacity(0.6), lineWidth: 1.5)
                                        .frame(width: currentNodeSize + 4, height: currentNodeSize + 4)
                                    
                                    // Pulsing ring with blur (slower animation)
                                    Circle()
                                        .stroke(Color.yellow.opacity(0.5), lineWidth: 2.5)
                                        .frame(width: currentNodeSize + 4, height: currentNodeSize + 4)
                                        .blur(radius: 4)
                                        .scaleEffect(favoritePulse ? 1.6 : 1.0)
                                        .opacity(favoritePulse ? 0.0 : 0.8)
                                }
                            }
                            
                            if isSelected {
                                Circle()
                                    .stroke(Color.blue, lineWidth: 2)
                                    .frame(width: currentHighlightRing, height: currentHighlightRing)
                            }
                            
                            Circle()
                                .fill(
                                    isSelected ?
                                    Color.blue :
                                        isFavorite ?
                                        Color.yellow.opacity(colorScheme == .dark ? 0.7 : 0.5) :
                                        isLive ?
                                        Color.cyan.opacity(colorScheme == .dark ? 0.6 : 0.4) :
                                        (colorScheme == .dark ?
                                         Color.white.opacity(isFakeNode ? 0.25 : 0.5) :
                                            Color.black.opacity(isFakeNode ? 0.15 : 0.3))
                                )
                                .frame(width: currentNodeSize, height: currentNodeSize)
                        }
                    }
                    .buttonStyle(.plain)
                    .position(position)
                }
                
                // Draft node
                if showDraftNode {
                    let draftPosition = draftNodePosition(in: geometry.size, sessions: sessions)
                    let draftSize: CGFloat = 8
                    let draftPulseRing = highlightRingSize(draftSize)
                    
                    ZStack {
                        Circle()
                            .stroke(Color.green.opacity(0.3), lineWidth: 2)
                            .frame(width: draftPulseRing, height: draftPulseRing)
                            .scaleEffect(pulseAnimation ? 1.8 : 1.0)
                            .opacity(pulseAnimation ? 0.0 : 0.5)
                        
                        Circle()
                            .fill(Color.green)
                            .frame(width: draftSize, height: draftSize)
                    }
                    .position(draftPosition)
                    .onAppear {
                        withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: false)) {
                            pulseAnimation = true
                        }
                        withAnimation(.linear(duration: 1.0).repeatForever(autoreverses: false)) {
                            dashPhase = 10
                        }
                    }
                }
                
                // Message dots
                if let selectedId = selectedSessionId,
                   let selectedSession = sessions.first(where: { $0.id == selectedId }),
                   let sessionIndex = sessions.firstIndex(where: { $0.id == selectedId }) {
                    
                    let sessionPosition = getCachedPosition(selectedSession, sessionIndex, geometry.size, sessions, dateLabel)
                    let currentNodeSize = nodeSize(selectedSession.messageCount)
                    let currentDotRadius = messageDotRadius(currentNodeSize)
                    
                    SimulatedMessageDotsOverlay(
                        messageCount: selectedSession.messageCount,
                        centerPosition: sessionPosition,
                        colorScheme: colorScheme,
                        radius: currentDotRadius,
                        dotSize: messageDotSize
                    )
                }
            }
            .onAppear {
                // Start pulse animations
                withAnimation(.easeInOut(duration: 3.0).repeatForever(autoreverses: false)) {
                    favoritePulse = true
                }
                withAnimation(.easeInOut(duration: 2.5).repeatForever(autoreverses: false)) {
                    livePulse = true
                }
            }
        }
    }
    
    private func draftNodePosition(in size: CGSize, sessions: [ChatSession]) -> CGPoint {
        if sessions.isEmpty {
            return CGPoint(x: size.width / 2, y: size.height / 2)
        } else {
            let lastSession = sessions[sessions.count - 1]
            let lastPosition = getCachedPosition(lastSession, sessions.count - 1, size, sessions, dateLabel)
            
            let paddingX: CGFloat = size.width * 0.15
            let maxX = size.width - paddingX
            let minX = paddingX
            
            let paddingY: CGFloat = size.height * 0.20
            let maxY = size.height - paddingY
            let minY = paddingY
            
            var targetX = lastPosition.x + 60
            var targetY = lastPosition.y + 20
            
            if targetX > maxX {
                targetX = minX + 40
                targetY = min(lastPosition.y + 60, maxY)
            }
            
            targetX = max(minX, min(targetX, maxX))
            targetY = max(minY, min(targetY, maxY))
            
            return CGPoint(x: targetX, y: targetY)
        }
    }
    
}

    
    // MARK: - Simulated Message Dots Overlay
    struct SimulatedMessageDotsOverlay: View {
        let messageCount: Int
        let centerPosition: CGPoint
        let colorScheme: ColorScheme
        let radius: CGFloat
        let dotSize: CGFloat
        
        private var displayedDotCount: Int {
            if messageCount <= 20 {
                return messageCount
            } else if messageCount <= 50 {
                return messageCount / 2
            } else {
                return min(25, messageCount / 3)
            }
        }
        
        var body: some View {
            ZStack {
                if displayedDotCount > 1 {
                    Path { path in
                        for i in 0..<(displayedDotCount - 1) {
                            let startPos = dotPosition(for: i)
                            let endPos = dotPosition(for: i + 1)
                            
                            path.move(to: startPos)
                            path.addLine(to: endPos)
                        }
                    }
                    .stroke(
                        Color.blue.opacity(0.3),
                        style: StrokeStyle(lineWidth: 1, lineCap: .round)
                    )
                }
                
                ForEach(0..<displayedDotCount, id: \.self) { index in
                    Circle()
                        .fill(dotColor(for: index))
                        .frame(width: dotSize, height: dotSize)
                        .position(dotPosition(for: index))
                }
            }
        }
        
        private func dotPosition(for index: Int) -> CGPoint {
            let angleStep = (2 * .pi) / CGFloat(displayedDotCount)
            let angle = angleStep * CGFloat(index) - (.pi / 2)
            
            let x = centerPosition.x + radius * cos(angle)
            let y = centerPosition.y + radius * sin(angle)
            
            return CGPoint(x: x, y: y)
        }
        
        private func dotColor(for index: Int) -> Color {
            if index % 7 == 6 {
                return Color.gray.opacity(0.6)
            } else if index % 2 == 0 {
                return Color.blue.opacity(0.8)
            } else {
                return colorScheme == .dark ?
                Color.white.opacity(0.6) :
                Color.black.opacity(0.4)
            }
        }
    }
    
    #Preview {
        PreviewContainer()
    }
    
    struct PreviewContainer: View {
        let formatter = ISO8601DateFormatter()
        let calendar = Calendar.current  // Use local timezone
        let now = Date()
        
        init() {
            formatter.formatOptions = [.withInternetDateTime]
        }
        
        func dateString(daysAgo: Int, hoursAgo: Int, minutesOffset: Int = 0) -> String {
            var date = calendar.date(byAdding: .day, value: -daysAgo, to: now)!
            date = calendar.date(byAdding: .hour, value: -hoursAgo, to: date)!
            date = calendar.date(byAdding: .minute, value: minutesOffset, to: date)!
            return formatter.string(from: date)
        }
        
        var body: some View {
            ZStack {
                Color(UIColor.systemBackground)
                    .ignoresSafeArea()
                
                NodeMatrix(
                    sessions: [
                        // Today
                        ChatSession(id: "1", description: "Small Session", messageCount: 3, createdAt: dateString(daysAgo: 0, hoursAgo: 8), updatedAt: dateString(daysAgo: 0, hoursAgo: 8), workingDir: nil),
                        ChatSession(id: "2", description: "Medium Session", messageCount: 15, createdAt: dateString(daysAgo: 0, hoursAgo: 7, minutesOffset: 30), updatedAt: dateString(daysAgo: 0, hoursAgo: 7, minutesOffset: 30), workingDir: nil),
                        ChatSession(id: "3", description: "Large Session", messageCount: 45, createdAt: dateString(daysAgo: 0, hoursAgo: 7), updatedAt: dateString(daysAgo: 0, hoursAgo: 7), workingDir: nil),
                        // Yesterday
                        ChatSession(id: "4", description: "Yesterday 1", messageCount: 10, createdAt: dateString(daysAgo: 1, hoursAgo: 5), updatedAt: dateString(daysAgo: 1, hoursAgo: 5), workingDir: nil),
                        ChatSession(id: "5", description: "Yesterday 2", messageCount: 25, createdAt: dateString(daysAgo: 1, hoursAgo: 3), updatedAt: dateString(daysAgo: 1, hoursAgo: 3), workingDir: nil),
                    ],
                    selectedSessionId: "2",
                    onNodeTap: { session, position in
                        print("Tapped session: \(session.description) at position: \(position)")
                    },
                    onDayChange: { daysOffset in
                        print("Day changed to offset: \(daysOffset)")
                    },
                    showDraftNode: true
                )
                .frame(height: 400)
            }
        }
    }


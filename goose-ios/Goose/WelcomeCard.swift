import SwiftUI

// MARK: - Welcome Card Glass Modifier

/// Applies Liquid Glass effect on iOS 26+ for the welcome card
private struct WelcomeCardGlassModifier: ViewModifier {
    let colorScheme: ColorScheme
    let cardBackgroundColor: Color
    
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .background(
                    // Background extension that goes upward (for safe area)
                    VStack(spacing: 0) {
                        Color.clear
                            .frame(height: 500)
                            .offset(y: -500)
                        Spacer()
                    }
                )
                .glassEffect(.regular, in: UnevenRoundedRectangle(
                    topLeadingRadius: 0,
                    bottomLeadingRadius: 32,
                    bottomTrailingRadius: 32,
                    topTrailingRadius: 0
                ))
                .shadow(
                    color: Color.black.opacity(colorScheme == .dark ? 0.3 : 0.15),
                    radius: 16,
                    x: 0,
                    y: 8
                )
        } else {
            // Fallback for iOS 17-25
            content
                .background(cardBackgroundColor)
                .background(
                    // Background extension that goes upward
                    VStack(spacing: 0) {
                        cardBackgroundColor
                            .frame(height: 500)
                            .offset(y: -500)
                        Spacer()
                    }
                )
                .clipShape(
                    UnevenRoundedRectangle(
                        topLeadingRadius: 0,
                        bottomLeadingRadius: 32,
                        bottomTrailingRadius: 32,
                        topTrailingRadius: 0
                    )
                )
                .shadow(
                    color: Color.black.opacity(colorScheme == .dark ? 0.4 : 0.12),
                    radius: 16,
                    x: 0,
                    y: 8
                )
        }
    }
}

struct WelcomeCard: View {
    @Environment(\.colorScheme) var colorScheme
    
    // Sidebar binding
    var daysOffset: Int = 0 // Days offset from today (0 = today, 1 = yesterday, etc.)
    var sessions: [ChatSession] = [] // All sessions for density calculation
    @Binding var showingSidebar: Bool
    
    // Animation states
    @State private var displayedText = ""
    @State private var typewriterGeneration = 0  // Track current animation generation
    @State private var showLogo = false
    @State private var showProgressSection = false
    @State private var progressValue: CGFloat = 0.0
    @State private var showActionsSection = false
    @State private var hasInitiallyLoaded = false // Track if initial animation has completed
    
    // Token data - managed internally
    @State private var tokenCount: Int64 = 0
    
    // Dynamic max tokens - rounds up to nearest billion
    private var maxTokens: Int64 {
        let billion: Int64 = 1_000_000_000
        if tokenCount == 0 {
            return billion
        }
        // Calculate how many billions we need (rounded up)
        let billionsNeeded = (tokenCount + billion - 1) / billion
        return billionsNeeded * billion
    }
    
    // Callbacks for animation completion
    var onAnimationComplete: (() -> Void)?
    
    // Computed property for card background color
    private var cardBackgroundColor: Color {
        colorScheme == .dark ?
        Color(red: 0.15, green: 0.15, blue: 0.18) :
        Color(red: 0.98, green: 0.98, blue: 0.99)
    }
    
    // Progress bar colors with better contrast
    private var progressBackgroundColor: Color {
        colorScheme == .dark ?
        Color.white.opacity(0.15) :
        Color.black.opacity(0.1)
    }
    
    private var progressForegroundColor: Color {
        // Brighter blue when over 1B tokens
        if tokenCount > 1_000_000_000 {
            return Color.blue.opacity(0.9) // Slightly brighter/more vibrant
        } else {
            return Color.blue
        }
    }
    
    // Dynamic label for progress bar
    private var progressLabel: String {
        let billions = maxTokens / 1_000_000_000
        return "\(formatTokenCount(tokenCount)) of \(billions)B"
    }
    
    // Session density levels
    private enum SessionDensity {
        case quiet, light, busy
    }
    
    // Calculate session density for the current day
    // Calculate session density for the current day
    private var sessionDensity: SessionDensity {
        let calendar = Calendar.current  // Use local timezone
        let targetDate = calendar.date(byAdding: .day, value: -daysOffset, to: Date()) ?? Date()
        
        let daySessions = sessions.filter { session in
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime]
            
            guard let sessionDate = formatter.date(from: session.updatedAt) else {
                return false
            }
            
            return calendar.isDate(sessionDate, inSameDayAs: targetDate)
        }
        
        let count = daySessions.count
        
        if count <= 2 {
            return .quiet
        } else if count <= 5 {
            return .light
        } else {
            return .busy
        }
    }
    
    // Computed property for day-aware greeting with density awareness
    private var greeting: String {
        let calendar = Calendar.current  // Use local timezone
        let targetDate = calendar.date(byAdding: .day, value: -daysOffset, to: Date()) ?? Date()
        
        if daysOffset == 0 {
            // Today - use time-aware greeting
            let hour = calendar.component(.hour, from: Date())
            
            switch hour {
            case 0..<12:
                return "Good morning!"
            case 12..<17:
                return "Good afternoon!"
            case 17..<21:
                return "Good evening!"
            default:
                return "Good night!"
            }
        } else {
            // Past days - use density-based messages
            let density = sessionDensity
            
            if daysOffset == 1 {
                // Yesterday with density
                switch density {
                case .quiet:
                    return "Quiet yesterday"
                case .light:
                    return "Light yesterday"
                case .busy:
                    return "Busy yesterday"
                }
            } else {
                // Other days - show day of week
                let formatter = DateFormatter()
                formatter.dateFormat = "EEEE" // Full day name (e.g., "Monday")
                return formatter.string(from: targetDate)
            }
        }
    }
    
    private var subheading: String {
        if daysOffset == 0 {
            return "What do you want to do?"
        } else {
            let calendar = Calendar.current
            let targetDate = calendar.date(byAdding: .day, value: -daysOffset, to: Date()) ?? Date()
            let formatter = DateFormatter()
            formatter.dateStyle = .medium
            return "Viewing \(formatter.string(from: targetDate))"
        }
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            // Sidebar toggle button
            HStack {
                Button(action: {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        showingSidebar.toggle()
                    }
                }) {
                    Image("SideMenuIcon")
                        .renderingMode(.template)
                        .resizable()
                        .foregroundColor(.primary)
                        .frame(width: 24, height: 22)
                }
                .buttonStyle(.plain)
                
                Spacer()
            }
            
            // Greeting text with goose logo
            HStack(alignment: .top, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    // Main greeting - 32px
                    Text(displayedText.components(separatedBy: "\n").first ?? "")
                        .font(.system(size: 32, weight: .semibold))
                        .foregroundColor(.primary)
                    
                    // Subheading - 16px with subtle color
                    if displayedText.contains("\n") {
                        Text(displayedText.components(separatedBy: "\n").dropFirst().joined(separator: "\n"))
                            .font(.system(size: 16, weight: .regular))
                            .foregroundColor(.secondary)
                    }
                }
                
                Spacer()
                
                Image("GooseLogo")
                    .resizable()
                    .renderingMode(.template)
                    .foregroundColor(.primary)
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 48, height: 48)
                    .opacity(showLogo ? 1 : 0)
                    .scaleEffect(showLogo ? 1 : 0.8)
            }
            
            // Actions and Apps Section - Placeholder (60px)
            Color.clear
                .frame(height: 60)
                .frame(maxWidth: .infinity)
                .opacity(showActionsSection ? 1 : 0)
            
            // Progress Section
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("TOKENS USED")
                        .font(.system(size: 12, weight: .semibold))
                        .tracking(1.02)
                        .foregroundColor(Color(red: 0.56, green: 0.56, blue: 0.66))
                    
                    Spacer()
                    
                    Text(progressLabel)
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
                
                // Progress bar with better visibility
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        // Background
                        RoundedRectangle(cornerRadius: 6)
                            .fill(progressBackgroundColor)
                            .frame(width: geometry.size.width, height: 4)
                        
                        // Foreground (animated) - BLUE for testing
                        RoundedRectangle(cornerRadius: 6)
                            .fill(progressForegroundColor)
                            .frame(width: geometry.size.width * progressValue, height: 4)
                    }
                }
                .frame(height: 4)
            }
            .opacity(showProgressSection ? 1 : 0)
        }
        .padding(.horizontal, 20)
        .padding(.top, 48)
        .padding(.bottom, 32)
        .frame(maxWidth: .infinity, alignment: .leading)
        .modifier(WelcomeCardGlassModifier(colorScheme: colorScheme, cardBackgroundColor: cardBackgroundColor))
        .onAppear {
            startTypewriterEffect(isInitialLoad: true)
            // Fetch token count
            Task {
                await fetchTokenCount()
            }
        }
        .onChange(of: daysOffset) { _, _ in
            // Re-trigger typewriter effect when day changes (text only)
            startTypewriterEffect()
        }
        .onReceive(NotificationCenter.default.publisher(for: Notification.Name("RefreshSessions"))) { _ in
            // Refresh token count when configuration changes
            print("🔄 WelcomeCard: RefreshSessions notification received, refetching insights")
            Task {
                await fetchTokenCount()
            }
        }
    }
    
    // Fetch token count from API - silent failure
    private func fetchTokenCount() async {
        let result = await GooseAPIService.shared.fetchInsights()
        
        await MainActor.run {
            switch result {
            case .success(let insights):
                tokenCount = insights.totalTokens
                print("✅ WelcomeCard: Fetched token count: \(tokenCount)")
                
                // Update progress bar if it's already visible
                if showProgressSection {
                    let percentage = Double(tokenCount) / Double(maxTokens)
                    print("📊 Progress update: tokenCount=\(tokenCount), max=\(maxTokens), percentage=\(percentage)")
                    withAnimation(.easeOut(duration: 0.8)) {
                        progressValue = CGFloat(min(percentage, 1.0))
                    }
                }
            case .failure:
                // Silent failure - show 0
                print("⚠️ WelcomeCard: Failed to fetch token count - showing 0")
                tokenCount = 0
                if showProgressSection {
                    withAnimation(.easeOut(duration: 0.8)) {
                        progressValue = 0
                    }
                }
            }
        }
    }
    
    // Typewriter effect for greeting text
    private func startTypewriterEffect(isInitialLoad: Bool = false) {
        // Increment generation to cancel any in-progress animations
        typewriterGeneration += 1
        let currentGeneration = typewriterGeneration
        
        displayedText = ""
        let fullText = "\(greeting)\n\(subheading)"
        
        // Fast typewriter effect - 20ms per character
        for (index, character) in fullText.enumerated() {
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(index) * 0.02) { [currentGeneration] in
                // Only update if this is still the current animation
                guard currentGeneration == self.typewriterGeneration else { return }
                
                displayedText.append(character)
                
                // When text is complete, show logo and other sections (only on initial load)
                if displayedText == fullText {
                    if isInitialLoad {
                        withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                            showLogo = true
                        }
                        
                        // Show actions section
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                                showActionsSection = true
                            }
                        }
                        
                        // Show progress section
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                                showProgressSection = true
                            }
                            
                            // Animate progress bar with current token count
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                                let percentage = Double(tokenCount) / Double(maxTokens)
                                print("📊 Initial progress: tokenCount=\(tokenCount), max=\(maxTokens), percentage=\(percentage)")
                                withAnimation(.easeOut(duration: 0.8)) {
                                    progressValue = CGFloat(min(percentage, 1.0))
                                }
                                
                                // Notify parent that animation is complete
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                                    onAnimationComplete?()
                                    hasInitiallyLoaded = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Format token count for display (e.g., "450M")
    private func formatTokenCount(_ count: Int64) -> String {
        let million: Int64 = 1_000_000
        if count >= million {
            let millions = Double(count) / Double(million)
            return String(format: "%.0fM", millions)
        } else {
            let thousands = Double(count) / 1000.0
            return String(format: "%.0fK", thousands)
        }
    }
}

#Preview {
    ZStack {
        Color(UIColor.systemBackground)
            .ignoresSafeArea()
        
        VStack(spacing: 0) {
            WelcomeCard(
                showingSidebar: .constant(false)
            )
            
            Spacer()
        }
    }
}

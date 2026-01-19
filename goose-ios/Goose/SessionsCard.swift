import SwiftUI

// MARK: - Card Glass Modifier for Top Cards

/// Applies Liquid Glass effect on iOS 26+ for cards that extend to top of screen
private struct CardGlassModifier: ViewModifier {
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

struct SessionsCard: View {
    @Environment(\.colorScheme) var colorScheme
    let session: ChatSession
    let onClose: () -> Void
    let onSessionSelect: (String) -> Void
    
    @State private var showContent = false
    
    // Computed property for card background color
    private var cardBackgroundColor: Color {
        colorScheme == .dark ?
        Color(red: 0.15, green: 0.15, blue: 0.18) :
        Color(red: 0.98, green: 0.98, blue: 0.99)
    }
    
    // Format timestamp
    private var formattedTimestamp: String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        
        guard let sessionDate = formatter.date(from: session.updatedAt) else {
            return session.updatedAt
        }
        
        let displayFormatter = DateFormatter()
        displayFormatter.dateStyle = .medium
        displayFormatter.timeStyle = .short
        
        return displayFormatter.string(from: sessionDate)
    }
    
    // Calculate time ago
    private var timeAgo: String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        
        guard let sessionDate = formatter.date(from: session.updatedAt) else {
            return ""
        }
        
        let now = Date()
        let interval = now.timeIntervalSince(sessionDate)
        
        let minutes = Int(interval / 60)
        let hours = Int(interval / 3600)
        let days = Int(interval / 86400)
        
        if minutes < 60 {
            return "\(minutes) minute\(minutes == 1 ? "" : "s") ago"
        } else if hours < 24 {
            return "\(hours) hour\(hours == 1 ? "" : "s") ago"
        } else if days == 1 {
            return "Yesterday"
        } else if days < 7 {
            return "\(days) days ago"
        } else {
            return formattedTimestamp
        }
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            // Header with close button
            HStack {
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.secondary)
                        .frame(width: 24, height: 24)
                }
                .buttonStyle(.plain)
                
                Spacer()
            }
            
            if showContent {
                // Session title and metadata
                VStack(alignment: .leading, spacing: 12) {
                    // Session description/title
                    Text(session.description.isEmpty ? "Untitled Session" : session.description)
                        .font(.system(size: 28, weight: .semibold))
                        .foregroundColor(.primary)
                        .lineLimit(2)
                    
                    // Time ago
                    Text(timeAgo)
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                    
                    // Working directory
                    if let workingDir = session.workingDir {
                        HStack(spacing: 6) {
                            Image(systemName: "folder")
                                .font(.system(size: 12))
                            Text(workingDir)
                                .font(.system(size: 13))
                                .lineLimit(1)
                                .truncationMode(.middle)
                        }
                        .foregroundColor(.secondary.opacity(0.8))
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
                
                // Session stats
                HStack(spacing: 24) {
                    // Message count
                    VStack(alignment: .leading, spacing: 4) {
                        Text("MESSAGES")
                            .font(.system(size: 11, weight: .semibold))
                            .tracking(0.92)
                            .foregroundColor(Color(red: 0.56, green: 0.56, blue: 0.66))
                        
                        Text("\(session.messageCount)")
                            .font(.system(size: 24, weight: .semibold))
                            .foregroundColor(.primary)
                    }
                    
                    Spacer()
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
                
                // Action button
                Button(action: {
                    onSessionSelect(session.id)
                }) {
                    HStack {
                        Text("Open Session")
                            .font(.system(size: 16, weight: .semibold))
                        
                        Image(systemName: "arrow.right")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color.blue)
                    .cornerRadius(12)
                }
                .buttonStyle(.plain)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 48)
        .padding(.bottom, 32)
        .frame(maxWidth: .infinity, alignment: .leading)
        .modifier(CardGlassModifier(colorScheme: colorScheme, cardBackgroundColor: cardBackgroundColor))
        .onAppear {
            // Animate content in
            withAnimation(.spring(response: 0.5, dampingFraction: 0.8).delay(0.1)) {
                showContent = true
            }
        }
    }
}

#Preview {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime]
    
    let calendar = Calendar.current
    let now = Date()
    let twoHoursAgo = calendar.date(byAdding: .hour, value: -2, to: now)!
    
    return ZStack {
        Color(UIColor.systemBackground)
            .ignoresSafeArea()
        
        VStack(spacing: 0) {
            SessionsCard(
                session: ChatSession(
                    id: "123",
                    description: "Building a SwiftUI app with node visualization",
                    messageCount: 42,
                    createdAt: formatter.string(from: twoHoursAgo),
                    updatedAt: formatter.string(from: twoHoursAgo),
                    workingDir: nil
                ),
                onClose: {
                    print("Close tapped")
                },
                onSessionSelect: { sessionId in
                    print("Open session: \(sessionId)")
                }
            )
            
            Spacer()
        }
    }
}

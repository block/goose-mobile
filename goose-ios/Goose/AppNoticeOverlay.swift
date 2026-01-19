import SwiftUI

// MARK: - Notice Card Glass Modifier

/// Applies Liquid Glass effect on iOS 26+ for notice cards
private struct NoticeCardGlassModifier: ViewModifier {
    let backgroundColor: Color
    
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .background(.clear)
                .glassEffect(.regular, in: .rect(cornerRadius: 12))
                .shadow(color: Color.black.opacity(0.25), radius: 10, x: 0, y: 4)
        } else {
            // Fallback for iOS 17-25
            content
                .background(backgroundColor)
                .cornerRadius(12)
                .shadow(color: Color.black.opacity(0.2), radius: 8, x: 0, y: 4)
        }
    }
}

/// Global overlay that displays app-wide notices
struct AppNoticeOverlay: View {
    @EnvironmentObject var noticeCenter: AppNoticeCenter
    @Environment(\.colorScheme) var colorScheme
    @State private var showingInstructionsSheet = false
    
    var body: some View {
        Group {
            if let notice = noticeCenter.activeNotice {
                VStack(spacing: 0) {
                    noticeCard(for: notice)
                        .padding(.top, 50) // Account for nav bar
                        .padding(.horizontal)
                        .transition(.move(edge: .top).combined(with: .opacity))
                        .animation(.spring(), value: noticeCenter.activeNotice)
                    
                    Spacer()
                }
            }
        }
        .sheet(isPresented: $showingInstructionsSheet) {
            TrialModeInstructionsView()
        }
    }
    
    @ViewBuilder
    private func noticeCard(for notice: AppNotice) -> some View {
        VStack(spacing: 12) {
            // Header with icon and title
            HStack(spacing: 12) {
                Image(systemName: notice.icon)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(.white)
                
                Text(notice.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                
                Spacer()
                
                // Dismiss button
                Button(action: {
                    noticeCenter.clearNotice()
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 20))
                        .foregroundColor(.white.opacity(0.8))
                }
            }
            
            // Message
            Text(notice.message)
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.95))
                .fixedSize(horizontal: false, vertical: true)
            
            // Action button
            actionButton(for: notice)
        }
        .padding(16)
        .background(backgroundColor(for: notice))
        .cornerRadius(12)
        .shadow(color: Color.black.opacity(0.2), radius: 8, x: 0, y: 4)
    }
    
    @ViewBuilder
    private func actionButton(for notice: AppNotice) -> some View {
        switch notice {
        case .tunnelDisabled, .tunnelUnreachable:
            // Small help link instead of big button - just need to enable tunneling
            Button(action: {
                showingInstructionsSheet = true
            }) {
                HStack(spacing: 4) {
                    Image(systemName: "questionmark.circle")
                        .font(.system(size: 12))
                    Text("How to enable tunneling")
                        .font(.system(size: 13))
                }
                .foregroundColor(.white.opacity(0.9))
                .underline()
            }
            
        case .appNeedsUpdate:
            Button(action: {
                showingInstructionsSheet = true
            }) {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.down.circle.fill")
                    Text("Install Fresh Desktop App")
                }
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(backgroundColor(for: notice))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(Color.white)
                .cornerRadius(8)
            }
        }
    }
    
    private func backgroundColor(for notice: AppNotice) -> Color {
        switch notice {
        case .tunnelDisabled, .tunnelUnreachable:
            return .orange
        case .appNeedsUpdate:
            return .blue
        }
    }
}

#Preview {
    VStack {
        Spacer()
    }
    .overlay(alignment: .top) {
        AppNoticeOverlay()
            .environmentObject(AppNoticeCenter.shared)
    }
    .onAppear {
        AppNoticeCenter.shared.setNotice(.tunnelDisabled)
    }
}

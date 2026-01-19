import SwiftUI

struct SafeAreaExtension: View {
    @Environment(\.colorScheme) var colorScheme
    var useSystemBackground: Bool = false // New parameter to switch between WelcomeCard and system background
    
    // Computed property for background color
    private var backgroundColor: Color {
        if useSystemBackground {
            // Use system background color (for draft view / focused state)
            return Color(UIColor.systemBackground)
        } else {
            // Use WelcomeCard colors (for default state)
            return colorScheme == .dark ?
                Color(red: 0.15, green: 0.15, blue: 0.18) :
                Color(red: 0.98, green: 0.98, blue: 0.99)
        }
    }
    
    var body: some View {
        GeometryReader { geometry in
            backgroundColor
                .frame(height: geometry.safeAreaInsets.top)
                .ignoresSafeArea(edges: .top)
        }
        .frame(height: 0) // Don't take up layout space
    }
}

#Preview {
    ZStack {
        Color(UIColor.systemBackground)
            .ignoresSafeArea()
        
        VStack(spacing: 0) {
            SafeAreaExtension(useSystemBackground: true)
            
            Text("Content below")
                .padding()
            
            Spacer()
        }
    }
}

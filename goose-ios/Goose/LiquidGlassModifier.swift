//
//  LiquidGlassModifier.swift
//  Goose
//
//  Provides Liquid Glass effect support for iOS 26+ with graceful fallback
//

import SwiftUI

// MARK: - Liquid Glass View Modifier

/// A view modifier that applies Liquid Glass effect on iOS 26+ and falls back to a
/// translucent material background on earlier versions.
struct LiquidGlassModifier: ViewModifier {
    var cornerRadius: CGFloat
    var shadowRadius: CGFloat
    
    @Environment(\.colorScheme) var colorScheme
    
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .glassEffect(.regular.interactive(), in: .rect(cornerRadius: cornerRadius))
                .shadow(color: Color.black.opacity(0.15), radius: shadowRadius, x: 0, y: 2)
        } else {
            // Fallback for iOS 17-25: use translucent material
            content
                .background(
                    RoundedRectangle(cornerRadius: cornerRadius)
                        .fill(.ultraThinMaterial)
                )
                .shadow(color: Color.black.opacity(0.1), radius: shadowRadius, x: 0, y: 2)
        }
    }
}

/// A view modifier for navigation/toolbar areas with Liquid Glass
struct LiquidGlassToolbarModifier: ViewModifier {
    @Environment(\.colorScheme) var colorScheme
    
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .toolbarBackgroundVisibility(.hidden, for: .navigationBar)
                .glassEffect(.regular, in: .rect(cornerRadius: 0))
        } else {
            content
                .toolbarBackground(.ultraThinMaterial, for: .navigationBar)
        }
    }
}

/// A view modifier specifically for card-style glass effects
struct LiquidGlassCardModifier: ViewModifier {
    var cornerRadius: CGFloat = 16
    
    @Environment(\.colorScheme) var colorScheme
    
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
        } else {
            // Fallback: subtle translucent background
            content
                .background(
                    RoundedRectangle(cornerRadius: cornerRadius)
                        .fill(.thinMaterial)
                )
        }
    }
}

// MARK: - View Extensions

extension View {
    /// Applies Liquid Glass effect on iOS 26+, with translucent material fallback on earlier versions.
    /// - Parameters:
    ///   - cornerRadius: The corner radius for the glass effect shape
    ///   - shadowRadius: The shadow radius (default: 8)
    /// - Returns: A view with the glass effect applied
    func liquidGlass(cornerRadius: CGFloat = 24, shadowRadius: CGFloat = 8) -> some View {
        modifier(LiquidGlassModifier(cornerRadius: cornerRadius, shadowRadius: shadowRadius))
    }
    
    /// Applies Liquid Glass effect to toolbar/navigation areas on iOS 26+
    func liquidGlassToolbar() -> some View {
        modifier(LiquidGlassToolbarModifier())
    }
    
    /// Applies Liquid Glass card effect on iOS 26+
    func liquidGlassCard(cornerRadius: CGFloat = 16) -> some View {
        modifier(LiquidGlassCardModifier(cornerRadius: cornerRadius))
    }
}

// MARK: - Preview

#Preview("Liquid Glass Examples") {
    ZStack {
        // Background gradient to show glass effect
        LinearGradient(
            colors: [.blue, .purple, .pink],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
        
        VStack(spacing: 20) {
            // Card example
            VStack(alignment: .leading, spacing: 8) {
                Text("Liquid Glass Card")
                    .font(.headline)
                Text("This card uses the glass effect on iOS 26+")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .liquidGlassCard(cornerRadius: 16)
            .padding(.horizontal)
            
            Spacer()
            
            // Input bar example
            HStack {
                TextField("Type a message...", text: .constant(""))
                    .padding(.horizontal)
                
                Button(action: {}) {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.title2)
                }
                .padding(.trailing)
            }
            .frame(height: 50)
            .liquidGlass(cornerRadius: 25, shadowRadius: 12)
            .padding(.horizontal)
            .padding(.bottom)
        }
    }
}

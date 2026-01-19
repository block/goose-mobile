//
//  ChatInputView.swift
//  Goose
//
//  Shared input field component used by WelcomeView and ChatView
//

import SwiftUI

// MARK: - Chat Input Glass Modifier

/// Applies Liquid Glass effect on iOS 26+, falls back to standard styling on earlier versions
private struct ChatInputGlassModifier: ViewModifier {
    let colorScheme: ColorScheme
    
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .glassEffect(.regular.interactive(), in: .rect(cornerRadius: 32))
                .shadow(color: Color.black.opacity(0.15), radius: 12, x: 0, y: 2)
        } else {
            // Fallback for iOS 17-25
            content
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(32)
                .overlay(
                    RoundedRectangle(cornerRadius: 32)
                        .inset(by: 0.5)
                        .stroke(Color(UIColor.separator), lineWidth: 0.5)
                )
                .shadow(color: Color.black.opacity(0.1), radius: 12, x: 0, y: 0)
        }
    }
}

struct ChatInputView: View {
    @Binding var text: String
    @Environment(\.colorScheme) var colorScheme
    @FocusState.Binding var isFocused: Bool
    
    // Configuration
    var placeholder: String = "I want to..."
    var showPlusButton: Bool = false
    var isLoading: Bool = false
    var voiceManager: EnhancedVoiceManager? = nil
    var continuousVoiceManager: ContinuousVoiceManager? = nil
    var onSubmit: () -> Void
    var onStop: (() -> Void)? = nil
    
    // Trial mode banner
    var showTrialBanner: Bool = false
    var onTrialBannerTap: (() -> Void)? = nil
    
    // Track input field height
    @State private var inputHeight: CGFloat = 80
    
    var body: some View {
        ZStack(alignment: .bottom) {
            // Trial mode banner (behind the input)
            if showTrialBanner {
                VStack(spacing: 0) {
                    TrialModeBanner(onTap: {
                        onTrialBannerTap?()
                    })
                    
                    // Spacer to extend behind input
                    Spacer()
                        .frame(height: 80)
                }
                .frame(height: 180) // Fixed total height
                .background(
                    RoundedRectangle(cornerRadius: 32)
                        .fill(colorScheme == .dark ?
                              Color(red: 0.15, green: 0.15, blue: 0.18) :
                              Color(red: 0.96, green: 0.96, blue: 0.98))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 32)
                        .strokeBorder(
                            colorScheme == .dark ?
                            Color(red: 0.25, green: 0.25, blue: 0.28) :
                            Color(red: 0.88, green: 0.88, blue: 0.90),
                            lineWidth: 1
                        )
                )
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
            
            // Listening indicator banner (behind the input) - GROWS WITH INPUT
            // For transcribe mode (EnhancedVoiceManager)
            if let vm = voiceManager,
               vm.voiceMode != .normal && vm.state == .listening {
                VStack(spacing: 0) {
                    HStack(spacing: 12) {
                        Image(systemName: "waveform")
                            .font(.system(size: 20))
                            .foregroundColor(.blue)
                            .symbolEffect(.variableColor.iterative.reversing)
                        
                        Text("Listening...")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.primary)
                        
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 12)
                    
                    // Spacer that matches the input field height
                    Spacer()
                        .frame(height: inputHeight)
                }
                .background(
                    RoundedRectangle(cornerRadius: 32)
                        .fill(colorScheme == .dark ?
                              Color(red: 0.15, green: 0.15, blue: 0.18) :
                              Color(red: 0.96, green: 0.96, blue: 0.98))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 32)
                        .strokeBorder(
                            colorScheme == .dark ?
                            Color(red: 0.25, green: 0.25, blue: 0.28) :
                            Color(red: 0.88, green: 0.88, blue: 0.90),
                            lineWidth: 1
                        )
                )
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
                .animation(.easeInOut(duration: 0.2), value: inputHeight)
            }
            
            // Continuous voice mode banner (ContinuousVoiceManager)
            if let cvm = continuousVoiceManager, cvm.isVoiceMode {
                VStack(spacing: 0) {
                    HStack(spacing: 12) {
                        Image(systemName: cvm.state.icon)
                            .font(.system(size: 20))
                            .foregroundColor(cvm.state.color)
                            .symbolEffect(.variableColor.iterative.reversing, isActive: cvm.state == .listening)
                        
                        Text(cvm.state.rawValue)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.primary)
                        
                        Spacer()
                        
                        // Volume indicator
                        if cvm.state == .listening && cvm.currentVolume > 0.01 {
                            HStack(spacing: 2) {
                                ForEach(0..<5) { i in
                                    RoundedRectangle(cornerRadius: 1)
                                        .fill(cvm.currentVolume > Float(i) * 0.2 ? Color.green : Color.gray.opacity(0.3))
                                        .frame(width: 3, height: CGFloat(4 + i * 2))
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 12)
                    
                    // Spacer that matches the input field height
                    Spacer()
                        .frame(height: inputHeight)
                }
                .background(
                    RoundedRectangle(cornerRadius: 32)
                        .fill(colorScheme == .dark ?
                              Color(red: 0.15, green: 0.15, blue: 0.18) :
                              Color(red: 0.96, green: 0.96, blue: 0.98))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 32)
                        .strokeBorder(
                            cvm.state.color.opacity(0.3),
                            lineWidth: 1
                        )
                )
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
                .animation(.easeInOut(duration: 0.2), value: inputHeight)
            }
            

            VStack(spacing: 0) {

                VStack(alignment: .leading, spacing: 12) {
                    // Text field on top
                    TextField(placeholder, text: $text, axis: .vertical)
                        .font(.system(size: 16))
                        .foregroundColor(.primary)
                        .lineLimit(1...4)
                        .padding(.vertical, 8)
                        .focused($isFocused)
                        .onSubmit {
                            onSubmit()
                        }
                    
                    // Buttons row at bottom
                    HStack(spacing: 10) {
                        // Plus button (optional)
                        if showPlusButton {
                            Button(action: {
                                print("File attachment tapped")
                            }) {
                                Image(systemName: "plus")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.primary)
                                    .frame(width: 32, height: 32)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 16)
                                            .inset(by: 0.5)
                                            .stroke(Color(UIColor.separator), lineWidth: 0.5)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                        
                        Spacer()
                        
                        HStack(spacing: 10) {
                            // Voice mode indicator text (if voice manager provided)
                            if let vm = voiceManager, vm.voiceMode != .normal {
                                Text("Transcribe")
                                    .font(.caption)
                                    .fontWeight(.medium)
                                    .foregroundColor(.blue)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(Color.blue.opacity(0.1))
                                    )
                            }
                            
                            // Continuous voice mode indicator
                            if let cvm = continuousVoiceManager, cvm.isVoiceMode {
                                Text("Continuous")
                                    .font(.caption)
                                    .fontWeight(.medium)
                                    .foregroundColor(.green)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(Color.green.opacity(0.1))
                                    )
                            }
                            
                            // Transcribe button (if voice manager provided)
                            if let vm = voiceManager {
                                Button(action: {
                                    // Toggle transcribe mode
                                    vm.cycleVoiceMode()
                                }) {
                                    Image(systemName: vm.voiceMode == .normal ? "waveform" : "keyboard")
                                        .font(.system(size: 14, weight: .medium))
                                        .foregroundColor(vm.voiceMode == .normal ? .primary : .blue)
                                        .frame(width: 32, height: 32)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16)
                                                .inset(by: 0.5)
                                                .stroke(vm.voiceMode == .normal ? Color(UIColor.separator) : Color.blue.opacity(0.5), lineWidth: 0.5)
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                            
                            // Continuous conversation button (if continuous voice manager provided)
                            if let cvm = continuousVoiceManager {
                                Button(action: {
                                    cvm.toggleVoiceMode()
                                }) {
                                    Image(systemName: cvm.isVoiceMode ? "mic.and.signal.meter.fill" : "mic.and.signal.meter")
                                        .font(.system(size: 14, weight: .medium))
                                        .foregroundColor(cvm.isVoiceMode ? .green : .primary)
                                        .frame(width: 32, height: 32)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16)
                                                .inset(by: 0.5)
                                                .stroke(cvm.isVoiceMode ? Color.green.opacity(0.5) : Color(UIColor.separator), lineWidth: 0.5)
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                            
                            // Send/Stop button
                            Button(action: {
                                if isLoading, let stopHandler = onStop {
                                    stopHandler()
                                } else if !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                    onSubmit()
                                }
                            }) {
                                Image(systemName: isLoading ? "stop.fill" : "arrow.up")
                                    .font(.system(size: 17, weight: .medium))
                                    .foregroundColor(
                                        isLoading ? .white : (colorScheme == .dark ? .black : .white)
                                    )
                                    .frame(width: 32, height: 32)
                                    .background(
                                        isLoading ? Color.red :
                                        (text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty 
                                            ? Color.gray.opacity(0.3)
                                            : (colorScheme == .dark ? Color.white : Color.black))
                                    )
                                    .cornerRadius(16)
                            }
                            .buttonStyle(.plain)
                            .disabled(!isLoading && text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        }
                    }
                }
                .padding(.top, 10)
                .padding(.bottom, 10)
                .padding(.leading, 14)
                .padding(.trailing, 10)
                .frame(maxWidth: .infinity)
                .background(
                    GeometryReader { geometry in
                        Color.clear
                            .onAppear {
                                inputHeight = geometry.size.height
                            }
                            .onChange(of: geometry.size.height) { newHeight in
                                inputHeight = newHeight
                            }
                    }
                )
                .modifier(ChatInputGlassModifier(colorScheme: colorScheme))
                .padding(.horizontal, 16)
                .padding(.bottom, 0)
            }
            .zIndex(1) // Input on top
        }
    }
}

#Preview {
    struct PreviewWrapper: View {
        @State private var text = ""
        @FocusState private var isFocused: Bool
        
        var body: some View {
            VStack {
                Spacer()
                
                // With trial banner
                ChatInputView(
                    text: $text,
                    isFocused: $isFocused,
                    onSubmit: {
                        print("Submit tapped")
                    },
                    showTrialBanner: true,
                    onTrialBannerTap: {
                        print("Trial banner tapped")
                    }
                )
            }
        }
    }
    
    return PreviewWrapper()
}

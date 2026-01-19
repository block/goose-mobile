import Foundation
import Speech
import AVFoundation
import SwiftUI

// MARK: - Voice Mode Types
enum VoiceMode: String, CaseIterable {
    case normal = "Normal"
    case audio = "Audio"
    case fullAudio = "Full Audio"
    
    var icon: String {
        switch self {
        case .normal: return "keyboard"
        case .audio: return "mic"
        case .fullAudio: return "mic.and.signal.meter"
        }
    }
    
    var color: Color {
        switch self {
        case .normal: return .gray
        case .audio: return .blue
        case .fullAudio: return .green
        }
    }
    
    var description: String {
        switch self {
        case .normal: return "Type messages"
        case .audio: return "Continuous transcription"
        case .fullAudio: return "Listen and speak back"
        }
    }
}

// MARK: - Enhanced Voice Manager
@MainActor
class EnhancedVoiceManager: ObservableObject {
    // MARK: - State
    enum State: String {
        case idle = "Idle"
        case listening = "Listening"
        case processing = "Processing"
        case speaking = "Speaking"
        case error = "Error"
        
        var icon: String {
            switch self {
            case .idle: return "mic.slash"
            case .listening: return "mic.fill"
            case .processing: return "ellipsis.circle"
            case .speaking: return "speaker.wave.2.fill"
            case .error: return "exclamationmark.triangle"
            }
        }
        
        var color: Color {
            switch self {
            case .idle: return .gray
            case .listening: return .red
            case .processing: return .blue
            case .speaking: return .green
            case .error: return .orange
            }
        }
    }
    
    // MARK: - Published Properties
    @Published var voiceMode: VoiceMode = .normal {
        didSet {
            handleModeChange(from: oldValue, to: voiceMode)
        }
    }
    @Published var state: State = .idle
    @Published var transcribedText = ""
    @Published var errorMessage: String?
    
    var isListening: Bool {
        return voiceMode != .normal && state == .listening
    }
    
    var shouldSpeak: Bool {
        return voiceMode == .fullAudio
    }
    
    // MARK: - Private Properties
    private var audioEngine: AVAudioEngine?
    private var speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let speechSynthesizer = AVSpeechSynthesizer()
    private var speechCoordinator: SpeechCoordinator?
    
    // Silence detection - modified for continuous input
    private var silenceTimer: Timer?
    private var lastSpeechTime = Date()
    private var hasDetectedSpeech = false
    private let silenceThreshold: TimeInterval = 2.0  // Increased for continuous mode
    private var isManuallyStopping = false  // Track manual stops to prevent auto-submit
    
    // Callback for updating text field (instead of auto-submitting)
    var onTranscriptionUpdate: ((String) -> Void)?
    
    // Callback for sending messages (only used in fullAudio mode)
    var onSubmitMessage: ((String) -> Void)?
    
    // MARK: - Initialization
    init() {
        speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
        setupSpeechSynthesizer()
    }
    
    private func setupSpeechSynthesizer() {
        speechCoordinator = SpeechCoordinator()
        speechCoordinator?.manager = self
        speechSynthesizer.delegate = speechCoordinator
    }
    
    // MARK: - Mode Management
    private func handleModeChange(from oldMode: VoiceMode, to newMode: VoiceMode) {
        print("🔄 Voice mode changed from \(oldMode) to \(newMode)")
        
        // Set flag to prevent auto-submit when manually stopping
        if oldMode == .audio && newMode == .normal && !transcribedText.isEmpty {
            print("📝 Preserving text without auto-submit: \(transcribedText)")
            // Update the input field one last time before stopping
            onTranscriptionUpdate?(transcribedText)
            transcribedText = ""  // Clear to prevent any auto-submit
        }
        
        // Mark that we're manually stopping
        isManuallyStopping = true
        
        // Stop any ongoing operations
        stopListening()
        stopSpeaking()
        
        // Reset the flag after a short delay
        Task {
            try? await Task.sleep(nanoseconds: 500_000_000)
            isManuallyStopping = false
        }
        
        // Handle the new mode
        switch newMode {
        case .normal:
            state = .idle
            playSound(.modeSwitch)
            
        case .audio:
            playSound(.modeSwitch)
            startListening()
            
        case .fullAudio:
            playSound(.modeSwitch)
            startListening()
        }
    }
    
    // MARK: - Public Methods
    func setMode(_ mode: VoiceMode) {
        voiceMode = mode
    }
    
    func cycleVoiceMode() {
        // Toggle between normal and audio (transcribe) only
        switch voiceMode {
        case .normal:
            setMode(.audio)
        case .audio, .fullAudio:
            setMode(.normal)
        }
    }
    
    func handleUserInteraction() {
        // If we're speaking, stop speaking and return to listening (in voice mode)
        if state == .speaking && voiceMode == .fullAudio {
            print("🛑 User interrupted speech")
            stopSpeaking()
            // Small delay before restarting listening
            Task {
                try? await Task.sleep(nanoseconds: 200_000_000) // 0.2 second
                if voiceMode == .fullAudio {
                    startListening()
                }
            }
        }
    }
    
    func speakResponse(_ text: String) {
        guard voiceMode == .fullAudio else {
            print("🔇 Not in full voice mode, skipping speech")
            return
        }
        
        print("🎤 Speaking response: \(text.prefix(50))...")
        state = .speaking
        playSound(.responseReady)
        
        // Clean the text for speech
        let cleanedText = cleanTextForSpeech(text)
        
        // Configure audio session
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playback, mode: .spokenAudio, options: .duckOthers)
            try audioSession.setActive(true)
        } catch {
            print("Failed to configure audio for playback: \(error)")
        }
        
        let utterance = AVSpeechUtterance(string: cleanedText)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = 0.52
        utterance.pitchMultiplier = 1.0
        utterance.volume = 1.0
        
        speechSynthesizer.speak(utterance)
    }
    
    // MARK: - Private Methods - Recording
    private func startListening() {
        Task {
            do {
                // Check authorization
                guard await requestAuthorization() else {
                    throw VoiceError.notAuthorized
                }
                
                // Reset state
                stopListening()
                transcribedText = ""
                hasDetectedSpeech = false
                lastSpeechTime = Date()
                state = .listening
                
                playSound(.startListening)
                
                // Configure audio session
                let audioSession = AVAudioSession.sharedInstance()
                try audioSession.setCategory(.record, mode: .measurement, options: .duckOthers)
                try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
                
                // Create recognition request
                recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
                guard let recognitionRequest = recognitionRequest else {
                    throw VoiceError.recognitionRequestFailed
                }
                
                recognitionRequest.shouldReportPartialResults = true
                
                // Setup audio engine
                audioEngine = AVAudioEngine()
                guard let audioEngine = audioEngine else {
                    throw VoiceError.audioEngineFailed
                }
                
                let inputNode = audioEngine.inputNode
                let recordingFormat = inputNode.outputFormat(forBus: 0)
                
                inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
                    recognitionRequest.append(buffer)
                }
                
                audioEngine.prepare()
                try audioEngine.start()
                
                recognitionTask = speechRecognizer?.recognitionTask(with: recognitionRequest) { [weak self] result, error in
                    self?.handleRecognitionResult(result: result, error: error)
                }
                
                startSilenceDetection()
                
            } catch {
                handleError(error)
            }
        }
    }
    
    private func stopListening() {
        silenceTimer?.invalidate()
        silenceTimer = nil
        
        audioEngine?.stop()
        audioEngine?.inputNode.removeTap(onBus: 0)
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        
        audioEngine = nil
        recognitionRequest = nil
        recognitionTask = nil
    }
    
    private func stopSpeaking() {
        speechSynthesizer.stopSpeaking(at: .immediate)
    }
    
    // MARK: - Recognition Handling
    private func handleRecognitionResult(result: SFSpeechRecognitionResult?, error: Error?) {
        Task { @MainActor in
            if let result = result {
                let newText = result.bestTranscription.formattedString
                
                if !newText.isEmpty {
                    transcribedText = newText
                    lastSpeechTime = Date()
                    
                    if !hasDetectedSpeech {
                        hasDetectedSpeech = true
                    }
                    
                    // Update the text field in real-time (for audio mode)
                    if voiceMode == .audio {
                        onTranscriptionUpdate?(newText)
                    }
                }
                
                // In fullAudio mode, auto-submit on final result
                if result.isFinal && voiceMode == .fullAudio {
                    submitTranscription()
                }
            }
            
            if let error = error {
                let nsError = error as NSError
                // Ignore common non-critical errors
                if nsError.code != 203 && nsError.code != 1 && nsError.code != 4 {
                    print("Speech recognition error (code: \(nsError.code)): \(error.localizedDescription)")
                    if nsError.code != 0 && nsError.domain != "kAFAssistantErrorDomain" {
                        handleError(error)
                    }
                }
            }
        }
    }
    
    // MARK: - Silence Detection
    private func startSilenceDetection() {
        silenceTimer?.invalidate()
        silenceTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.checkForSilence()
            }
        }
    }
    
    private func checkForSilence() {
        guard voiceMode != .normal, state == .listening, hasDetectedSpeech else { return }
        guard !isManuallyStopping else { return }  // Don't auto-submit during manual stop
        
        let timeSinceLastSpeech = Date().timeIntervalSince(lastSpeechTime)
        
        // In audio mode (continuous), we finalize text on silence but keep listening
        // In fullAudio mode, we submit on silence
        if timeSinceLastSpeech > silenceThreshold && !transcribedText.isEmpty {
            if voiceMode == .audio {
                finalizeCurrentTranscription()
            } else if voiceMode == .fullAudio {
                submitTranscription()
            }
        }
    }
    
    // NEW: Finalize transcription without submitting (for audio mode)
    private func finalizeCurrentTranscription() {
        guard !transcribedText.isEmpty else { return }
        
        print("📝 Finalizing transcription (continuous mode): \(transcribedText)")
        
        // Update the text field with finalized text
        onTranscriptionUpdate?(transcribedText)
        
        // Play a subtle sound to indicate text was captured
        playSound(.submit)
        
        // Reset for next phrase but keep listening
        transcribedText = ""
        hasDetectedSpeech = false
        lastSpeechTime = Date()
        
        // Stay in listening state - don't restart, just continue
        print("🎤 Continuing to listen...")
    }
    
    // MODIFIED: Only used in fullAudio mode now
    private func submitTranscription() {
        guard !transcribedText.isEmpty else { return }
        
        let message = transcribedText
        transcribedText = ""
        hasDetectedSpeech = false
        
        stopListening()
        state = .processing
        playSound(.submit)
        
        // Send the message via callback (only in fullAudio mode)
        onSubmitMessage?(message)
        
        // In full audio mode, we'll wait for the response to be spoken before listening again
    }
    
    // MARK: - Audio Feedback
    private enum SoundType {
        case startListening
        case submit
        case responseReady
        case error
        case modeSwitch
        
        var systemSoundID: SystemSoundID {
            switch self {
            case .startListening: return 1113
            case .submit: return 1114
            case .responseReady: return 1025
            case .error: return 1073
            case .modeSwitch: return 1104
            }
        }
    }
    
    private func playSound(_ type: SoundType) {
        AudioServicesPlaySystemSound(type.systemSoundID)
    }
    
    // MARK: - Helpers
    private func requestAuthorization() async -> Bool {
        await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
    }
    
    private func handleError(_ error: Error) {
        let nsError = error as NSError
        
        if nsError.domain == "kAFAssistantErrorDomain" {
            print("⚠️ Speech recognition interrupted (expected in simulator)")
            return
        }
        
        state = .error
        errorMessage = error.localizedDescription
        playSound(.error)
        
        print("❌ Voice error: \(error)")
        
        // Try to recover
        if voiceMode != .normal && error is VoiceError {
            Task {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                if voiceMode != .normal {
                    startListening()
                }
            }
        }
    }
    
    private func cleanTextForSpeech(_ text: String) -> String {
        var cleaned = text
        
        // Remove markdown
        cleaned = cleaned.replacingOccurrences(of: "```[^`]*```", with: "code block", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "`[^`]+`", with: "", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "\\[([^\\]]+)\\]\\([^\\)]+\\)", with: "$1", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "\\*\\*([^\\*]+)\\*\\*", with: "$1", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "\\*([^\\*]+)\\*", with: "$1", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "__([^_]+)__", with: "$1", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "_([^_]+)_", with: "$1", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "^#{1,6}\\s+", with: "", options: [.regularExpression, .anchored])
        cleaned = cleaned.replacingOccurrences(of: "\n\n+", with: ". ", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "\n", with: " ")
        cleaned = cleaned.replacingOccurrences(of: "[❌✅🚨📝🔧⚠️🛑→]", with: "", options: .regularExpression)
        
        return cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    // Called when speech finishes
    fileprivate func didFinishSpeaking() {
        print("🔊 Speech synthesis finished")
        if voiceMode == .fullAudio {
            Task {
                try? await Task.sleep(nanoseconds: 500_000_000)
                if voiceMode == .fullAudio {
                    print("🎤 Restarting listening after speech")
                    startListening()
                }
            }
        } else {
            state = .idle
        }
    }
}

// MARK: - Speech Synthesizer Delegate
private class SpeechCoordinator: NSObject, AVSpeechSynthesizerDelegate {
    weak var manager: EnhancedVoiceManager?
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            manager?.didFinishSpeaking()
        }
    }
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in
            manager?.didFinishSpeaking()
        }
    }
}

// MARK: - Error Types
enum VoiceError: LocalizedError {
    case notAuthorized
    case recognitionRequestFailed
    case audioEngineFailed
    
    var errorDescription: String? {
        switch self {
        case .notAuthorized:
            return "Speech recognition is not authorized. Please enable it in Settings."
        case .recognitionRequestFailed:
            return "Failed to create recognition request."
        case .audioEngineFailed:
            return "Failed to initialize audio engine."
        }
    }
}

// MARK: - Three State Slider View
struct ThreeStateVoiceSlider: View {
    @ObservedObject var manager: EnhancedVoiceManager
    @State private var dragOffset: CGFloat = 0
    @State private var isDragging = false
    
    private let trackWidth: CGFloat = 120  // Much more compact
    private let thumbSize: CGFloat = 26
    private let trackHeight: CGFloat = 28
    
    var body: some View {
        // Compact slider without labels
        ZStack(alignment: .leading) {
            // Track background with subtle gradient
            Capsule()
                .fill(Color(.systemGray6))
                .frame(width: trackWidth, height: trackHeight)
                .overlay(
                    Capsule()
                        .stroke(Color(.systemGray4), lineWidth: 0.5)
                )
            
            // Position indicators - just subtle marks, no dots
            HStack(spacing: 0) {
                ForEach(Array(VoiceMode.allCases.enumerated()), id: \.element) { index, mode in
                    if index > 0 {
                        Rectangle()
                            .fill(Color(.systemGray4))
                            .frame(width: 1, height: trackHeight * 0.4)
                    }
                    Spacer()
                }
            }
            .frame(width: trackWidth - 20)
            .offset(x: 10)
            
            // Thumb with icon only
            Circle()
                .fill(Color.white)
                .frame(width: thumbSize, height: thumbSize)
                .shadow(color: Color.black.opacity(0.15), radius: 2, x: 0, y: 1)
                .overlay(
                    Circle()
                        .stroke(manager.voiceMode.color, lineWidth: 1.5)
                )
                .overlay(
                    Image(systemName: manager.voiceMode.icon)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(manager.voiceMode.color)
                )
                .offset(x: thumbOffset + dragOffset)
                .animation(isDragging ? nil : .spring(response: 0.3, dampingFraction: 0.8), value: thumbOffset)
                .gesture(
                    DragGesture()
                        .onChanged { value in
                            isDragging = true
                            
                            // Constrain to track bounds
                            let potentialOffset = thumbOffset + value.translation.width
                            if potentialOffset >= 0 && potentialOffset <= trackWidth - thumbSize {
                                dragOffset = value.translation.width
                            } else if potentialOffset < 0 {
                                dragOffset = -thumbOffset
                            } else {
                                dragOffset = trackWidth - thumbSize - thumbOffset
                            }
                            
                            // Check if we've moved to a new mode
                            let absolutePosition = thumbOffset + dragOffset
                            let newMode = modeForPosition(absolutePosition)
                            if newMode != manager.voiceMode {
                            }
                        }
                        .onEnded { value in
                            isDragging = false
                            let finalPosition = thumbOffset + dragOffset
                            let newMode = modeForPosition(finalPosition)
                            
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                manager.setMode(newMode)
                                dragOffset = 0
                            }
                        }
                )
        }
        .frame(width: trackWidth, height: trackHeight)
        .onAppear {
        }
    }
    
    private var thumbOffset: CGFloat {
        let segmentWidth = (trackWidth - thumbSize) / 2
        switch manager.voiceMode {
        case .normal:
            return 0
        case .audio:
            return segmentWidth
        case .fullAudio:
            return trackWidth - thumbSize
        }
    }
    
    private func modeForPosition(_ position: CGFloat) -> VoiceMode {
        let normalizedPosition = position / (trackWidth - thumbSize)
        
        if normalizedPosition < 0.33 {
            return .normal
        } else if normalizedPosition < 0.67 {
            return .audio
        } else {
            return .fullAudio
        }
    }
}

// MARK: - Voice Mode Selector View (Original Segmented Style)
struct VoiceModeSelector: View {
    @ObservedObject var manager: EnhancedVoiceManager
    @State private var dragOffset: CGFloat = 0
    
    private let modes = VoiceMode.allCases
    private let segmentWidth: CGFloat = 100
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background track
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color(.systemGray5))
                
                // Selected segment highlight
                RoundedRectangle(cornerRadius: 18)
                    .fill(manager.voiceMode.color.opacity(0.3))
                    .frame(width: segmentWidth - 4)
                    .offset(x: selectedOffset)
                    .animation(.spring(response: 0.3, dampingFraction: 0.8), value: manager.voiceMode)
                
                // Mode indicators
                HStack(spacing: 0) {
                    ForEach(modes, id: \.self) { mode in
                        Button(action: {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                manager.setMode(mode)
                            }
                        }) {
                            VStack(spacing: 4) {
                                Image(systemName: mode.icon)
                                    .font(.system(size: 20))
                                    .foregroundColor(manager.voiceMode == mode ? mode.color : .gray)
                                
                                Text(mode.rawValue)
                                    .font(.caption2)
                                    .foregroundColor(manager.voiceMode == mode ? mode.color : .gray)
                            }
                            .frame(width: segmentWidth)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
        }
        .frame(height: 50)
        .frame(width: CGFloat(modes.count) * segmentWidth)
    }
    
    private var selectedOffset: CGFloat {
        let index = modes.firstIndex(of: manager.voiceMode) ?? 0
        return CGFloat(index - 1) * segmentWidth
    }
}

// MARK: - Compact Voice Mode Toggle
struct CompactVoiceModeToggle: View {
    @ObservedObject var manager: EnhancedVoiceManager
    
    var body: some View {
        Menu {
            ForEach(VoiceMode.allCases, id: \.self) { mode in
                Button(action: {
                    manager.setMode(mode)
                }) {
                    Label {
                        VStack(alignment: .leading) {
                            Text(mode.rawValue)
                            Text(mode.description)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    } icon: {
                        Image(systemName: mode.icon)
                    }
                }
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: manager.voiceMode.icon)
                    .foregroundColor(manager.voiceMode.color)
                    .symbolEffect(.variableColor.iterative, isActive: manager.state == .listening)
                
                if manager.voiceMode != .normal {
                    Text(manager.state.rawValue)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(manager.voiceMode.color)
                } else {
                    Text(manager.voiceMode.rawValue)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.primary)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(manager.voiceMode.color.opacity(0.15))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(manager.voiceMode.color.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

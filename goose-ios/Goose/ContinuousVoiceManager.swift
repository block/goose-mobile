import Foundation
import Speech
import AVFoundation
import SwiftUI

@MainActor
class ContinuousVoiceManager: ObservableObject {
    // MARK: - Published Properties
    @Published var isVoiceMode = false
    @Published var state: VoiceState = .idle
    @Published var transcribedText = ""
    @Published var errorMessage: String?
    @Published var currentVolume: Float = 0.0  // RMS volume level 0.0-1.0 for UI feedback
    
    // MARK: - State Enum
    enum VoiceState: String {
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
            case .speaking: return "speaker.wave.3.fill"
            case .error: return "exclamationmark.triangle"
            }
        }
        
        var color: Color {
            switch self {
            case .idle: return .gray
            case .listening: return .blue
            case .processing: return .orange
            case .speaking: return .green
            case .error: return .red
            }
        }
    }
    
    // MARK: - Private Properties
    private var audioEngine: AVAudioEngine?
    private var speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let speechSynthesizer = AVSpeechSynthesizer()
    private var speechCoordinator: SpeechCoordinator?  // Keep strong reference
    
    // RMS-based silence detection
    private var silenceTimer: Timer?
    private var lastSpeechTime = Date()
    private var hasDetectedSpeech = false
    private let silenceThreshold: TimeInterval = 0.8  // Reduced for faster response
    private var volumeHistory: [Float] = []
    private let volumeHistorySize = 10  // ~0.5 second of history
    private let silenceVolumeThreshold: Float = 0.03  // Volume level that counts as speech
    
    // Stop word detection
    private let stopWords = ["goose", "hey goose", "stop", "cancel"]
    private var lastStopWordCheck = Date()
    
    // Callbacks
    var onTranscriptionUpdate: ((String) -> Void)?  // Live partials for UI
    var onSubmitMessage: ((String) -> Void)?        // Finalized phrase submission
    var onCancelRequest: (() -> Void)?              // For cancelling API requests
    
    // MARK: - Initialization
    init() {
        speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
        setupSpeechSynthesizer()
    }
    
    private func setupSpeechSynthesizer() {
        // Set up delegate using a coordinator pattern
        speechCoordinator = SpeechCoordinator()
        speechCoordinator?.manager = self
        speechSynthesizer.delegate = speechCoordinator
    }
    
    // MARK: - Public Methods
    func toggleVoiceMode() {
        if isVoiceMode {
            stopVoiceMode()
        } else {
            startVoiceMode()
        }
    }
    
    func handleUserInteraction() {
        // If we're speaking, stop speaking and return to listening
        if state == .speaking {
            print("🛑 User interrupted speech")
            stopSpeaking()
            if isVoiceMode {
                // Go back to listening
                startListening()
            }
        }
    }
    
    func startVoiceMode() {
        isVoiceMode = true
        playSound(.modeSwitch)
        startListening()
    }
    
    func stopVoiceMode() {
        isVoiceMode = false
        playSound(.modeSwitch)
        stopListening()
        stopSpeaking()
        state = .idle
    }
    
    func speakResponse(_ text: String) {
        guard isVoiceMode else { 
            print("🔇 Not in voice mode, skipping speech")
            return 
        }
        
        print("🎤 Speaking response: \(text.prefix(50))...")
        
        // Keep listening running so we can detect "stop" commands during speech
        // The .playAndRecord audio session with .voiceChat mode provides echo cancellation
        state = .speaking
        playSound(.responseReady)
        
        // Clean the text for speech
        let cleanedText = cleanTextForSpeech(text)
        
        // Don't change audio session - keep it in .playAndRecord mode so we can still hear "stop" commands
        // The audio session was already configured in startListening() with echo cancellation
        
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
                stopListening() // Clean up any existing session
                transcribedText = ""
                hasDetectedSpeech = false
                lastSpeechTime = Date()
                volumeHistory.removeAll()
                state = .listening
                
                playSound(.startListening)
                
                // Configure audio session with better settings for continuous mode
                let audioSession = AVAudioSession.sharedInstance()
                // Use playAndRecord with voiceChat mode for echo cancellation
                try audioSession.setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker, .allowBluetoothA2DP])
                try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
                
                // Create recognition request with on-device recognition
                recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
                guard let recognitionRequest = recognitionRequest else {
                    throw VoiceError.recognitionRequestFailed
                }
                
                recognitionRequest.shouldReportPartialResults = true
                recognitionRequest.requiresOnDeviceRecognition = true  // Privacy + speed
                
                // Setup audio engine
                audioEngine = AVAudioEngine()
                guard let audioEngine = audioEngine else {
                    throw VoiceError.audioEngineFailed
                }
                
                let inputNode = audioEngine.inputNode
                let recordingFormat = inputNode.outputFormat(forBus: 0)
                
                // Install tap with RMS calculation
                inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { [weak self] buffer, _ in
                    recognitionRequest.append(buffer)
                    
                    // Calculate RMS volume level
                    Task { @MainActor in
                        self?.calculateAndUpdateRMS(buffer: buffer)
                    }
                }
                
                audioEngine.prepare()
                try audioEngine.start()
                
                // Start recognition
                recognitionTask = speechRecognizer?.recognitionTask(with: recognitionRequest) { [weak self] result, error in
                    self?.handleRecognitionResult(result: result, error: error)
                }
                
                // Start silence detection
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
                
                // Only update if we have new content
                if !newText.isEmpty {
                    transcribedText = newText
                    lastSpeechTime = Date()
                    
                    if !hasDetectedSpeech {
                        hasDetectedSpeech = true
                    }
                    
                    // Mirror partials to UI
                    onTranscriptionUpdate?(newText)
                    
                    // Check for stop words (interrupt commands)
                    checkForStopWords(in: newText)
                }
                
                // Check if recognition has finalized
                if result.isFinal {
                    submitTranscription()
                }
            }
            
            if let error = error {
                let nsError = error as NSError
                // Ignore common non-critical errors in continuous mode
                // 203 = Timeout, 1 = Cancellation, 4 = No speech detected
                if nsError.code != 203 && nsError.code != 1 && nsError.code != 4 {
                    print("Speech recognition error (code: \(nsError.code)): \(error.localizedDescription)")
                    // Only show actual errors to the user
                    if nsError.code != 0 { // Ignore generic errors
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
        guard isVoiceMode, state == .listening, hasDetectedSpeech else { return }
        
        let timeSinceLastSpeech = Date().timeIntervalSince(lastSpeechTime)
        
        if timeSinceLastSpeech > silenceThreshold && !transcribedText.isEmpty {
            // User has stopped talking, submit the query
            submitTranscription()
        }
    }
    
    private func submitTranscription() {
        guard !transcribedText.isEmpty else { return }
        
        let message = transcribedText
        transcribedText = ""
        hasDetectedSpeech = false
        
        // Do NOT stop listening in continuous mode; keep the mic hot for stop-words
        state = .processing
        playSound(.submit)
        
        // Send the message via callback
        onSubmitMessage?(message)
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
            case .startListening: return 1113  // Begin recording
            case .submit: return 1114          // End recording  
            case .responseReady: return 1025   // New mail
            case .error: return 1073           // Error
            case .modeSwitch: return 1104      // Keyboard tap
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
        // Don't set error state for normal operation interruptions
        let nsError = error as NSError
        
        // Check if this is just an audio session interruption or expected error
        if nsError.domain == "kAFAssistantErrorDomain" {
            print("⚠️ Speech recognition interrupted (expected in simulator): \(error)")
            // Don't play error sound for expected interruptions
            return
        }
        
        state = .error
        errorMessage = error.localizedDescription
        playSound(.error)
        
        print("❌ Voice error: \(error)")
        
        // Only try to recover for authorization errors
        if isVoiceMode && error is VoiceError {
            Task {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                if isVoiceMode {
                    print("🔄 Attempting to recover from error")
                    startListening()
                }
            }
        }
    }
    
    private func cleanTextForSpeech(_ text: String) -> String {
        var cleaned = text
        
        // Remove markdown code blocks
        cleaned = cleaned.replacingOccurrences(of: "```[^`]*```", with: "code block", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "`[^`]+`", with: "", options: .regularExpression)
        
        // Remove markdown links but keep the text
        cleaned = cleaned.replacingOccurrences(of: "\\[([^\\]]+)\\]\\([^\\)]+\\)", with: "$1", options: .regularExpression)
        
        // Remove markdown formatting
        cleaned = cleaned.replacingOccurrences(of: "\\*\\*([^\\*]+)\\*\\*", with: "$1", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "\\*([^\\*]+)\\*", with: "$1", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "__([^_]+)__", with: "$1", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "_([^_]+)_", with: "$1", options: .regularExpression)
        
        // Remove headers
        cleaned = cleaned.replacingOccurrences(of: "^#{1,6}\\s+", with: "", options: [.regularExpression, .anchored])
        
        // Clean up newlines
        cleaned = cleaned.replacingOccurrences(of: "\n\n+", with: ". ", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "\n", with: " ")
        
        // Remove special characters
        cleaned = cleaned.replacingOccurrences(of: "[❌✅🚨📝🔧⚠️🛑→]", with: "", options: .regularExpression)
        
        return cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    // MARK: - RMS Volume Calculation
    private func calculateAndUpdateRMS(buffer: AVAudioPCMBuffer) {
        guard let channelData = buffer.floatChannelData?[0] else { return }
        let frameLength = Int(buffer.frameLength)
        
        // Calculate RMS (Root Mean Square)
        var sum: Float = 0.0
        for i in 0..<frameLength {
            let sample = channelData[i]
            sum += sample * sample
        }
        let rms = sqrtf(sum / Float(frameLength))
        
        // Normalize and smooth
        let normalizedRMS = min(rms * 10.0, 1.0)  // Scale up and cap at 1.0
        currentVolume = normalizedRMS * 0.7 + currentVolume * 0.3  // Smoothing
        
        // Track volume history for silence detection
        volumeHistory.append(normalizedRMS)
        if volumeHistory.count > volumeHistorySize {
            volumeHistory.removeFirst()
        }
    }
    
    private func isVolumeSilent() -> Bool {
        guard volumeHistory.count >= volumeHistorySize / 2 else { return false }
        let recentVolumes = volumeHistory.suffix(volumeHistorySize / 2)
        let avgVolume = recentVolumes.reduce(0.0, +) / Float(recentVolumes.count)
        return avgVolume < silenceVolumeThreshold
    }
    
    // MARK: - Stop Word Detection
    private func checkForStopWords(in text: String) {
        // Throttle checks to avoid too frequent processing
        let now = Date()
        guard now.timeIntervalSince(lastStopWordCheck) > 0.3 else { return }
        lastStopWordCheck = now
        
        let lowercased = text.lowercased()
        let words = lowercased.split(separator: " ")
        let lastFewWords = words.suffix(3).joined(separator: " ")
        
        for stopWord in stopWords {
            if lastFewWords.contains(stopWord) {
                print("🛑 Stop word detected: '\(stopWord)' in '\(lastFewWords)'")
                handleStopCommand(context: state)
                return
            }
        }
    }
    
    private func handleStopCommand(context: VoiceState) {
        print("🛑 Handling stop command in state: \(context)")
        playSound(.error)  // Audio feedback
        
        switch context {
        case .listening:
            // Clear current transcription and restart
            print("🛑 Clearing transcription")
            transcribedText = ""
            hasDetectedSpeech = false
            lastSpeechTime = Date()
            volumeHistory.removeAll()
            
        case .processing:
            // Cancel the pending API request
            print("🛑 Cancelling API request")
            onCancelRequest?()
            transcribedText = ""
            hasDetectedSpeech = false
            volumeHistory.removeAll()
            // Return to listening
            if isVoiceMode {
                state = .listening
            }
            
        case .speaking:
            // Stop speaking and return to listening
            print("🛑 Stopping speech playback")
            stopSpeaking()
            transcribedText = ""
            hasDetectedSpeech = false
            volumeHistory.removeAll()
            if isVoiceMode {
                // Small delay before resuming listening
                Task {
                    try? await Task.sleep(nanoseconds: 200_000_000) // 0.2 second
                    if isVoiceMode {
                        startListening()
                    }
                }
            }
            
        case .idle, .error:
            // Nothing to do
            break
        }
    }
    
    // Called when speech finishes
    fileprivate func didFinishSpeaking() {
        print("🔊 Speech synthesis finished")
        if isVoiceMode {
            // Add a small delay before switching back to recording mode
            // This helps with audio session transitions
            Task {
                try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 second delay
                if isVoiceMode { // Check again after delay
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
    weak var manager: ContinuousVoiceManager?
    
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
// VoiceError is now defined in EnhancedVoiceManager.swift
/*
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
*/

// MARK: - Voice Mode Toggle View
struct VoiceModeToggle: View {
    @ObservedObject var manager: ContinuousVoiceManager
    
    var body: some View {
        Button(action: {
            manager.toggleVoiceMode()
        }) {
            HStack(spacing: 8) {
                Image(systemName: manager.state.icon)
                    .foregroundColor(manager.state.color)
                    .symbolEffect(.variableColor.iterative, isActive: manager.state == .listening)
                
                Text(manager.isVoiceMode ? manager.state.rawValue : "Voice Mode")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(manager.isVoiceMode ? manager.state.color : .primary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(manager.isVoiceMode ? manager.state.color.opacity(0.15) : Color(.systemGray5))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(manager.isVoiceMode ? manager.state.color.opacity(0.3) : Color.clear, lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

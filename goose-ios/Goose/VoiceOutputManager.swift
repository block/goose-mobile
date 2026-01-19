import Foundation
import AVFoundation

@MainActor
class VoiceOutputManager: ObservableObject {
    @Published var isSpeaking = false
    @Published var isEnabled = false
    
    private let synthesizer = AVSpeechSynthesizer()
    private var speechQueue: [String] = []
    private var currentUtterance: AVSpeechUtterance?
    
    init() {
        synthesizer.delegate = SpeechSynthesizerDelegate.shared
        SpeechSynthesizerDelegate.shared.manager = self
    }
    
    func speak(_ text: String) {
        // Clean up the text - remove markdown artifacts and format nicely
        let cleanedText = cleanTextForSpeech(text)
        
        guard !cleanedText.isEmpty else { return }
        
        // Configure audio session for playback
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playback, mode: .spokenAudio, options: .duckOthers)
            try audioSession.setActive(true)
        } catch {
            print("⚠️ Failed to configure audio session: \(error)")
        }
        
        let utterance = AVSpeechUtterance(string: cleanedText)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = 0.52 // Slightly faster than default (0.5)
        utterance.pitchMultiplier = 1.0
        utterance.volume = 1.0
        
        currentUtterance = utterance
        isSpeaking = true
        synthesizer.speak(utterance)
    }
    
    func stopSpeaking() {
        synthesizer.stopSpeaking(at: .immediate)
        speechQueue.removeAll()
        currentUtterance = nil
        isSpeaking = false
    }
    
    func pauseSpeaking() {
        synthesizer.pauseSpeaking(at: .word)
    }
    
    func resumeSpeaking() {
        synthesizer.continueSpeaking()
    }
    
    func toggleEnabled() {
        isEnabled.toggle()
        if !isEnabled {
            stopSpeaking()
        }
    }
    
    private func cleanTextForSpeech(_ text: String) -> String {
        var cleaned = text
        
        // Remove markdown code blocks
        cleaned = cleaned.replacingOccurrences(of: "```[^`]*```", with: "code block", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "`[^`]+`", with: "code", options: .regularExpression)
        
        // Remove markdown links but keep the text
        cleaned = cleaned.replacingOccurrences(of: "\\[([^\\]]+)\\]\\([^\\)]+\\)", with: "$1", options: .regularExpression)
        
        // Remove markdown bold/italic
        cleaned = cleaned.replacingOccurrences(of: "\\*\\*([^\\*]+)\\*\\*", with: "$1", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "\\*([^\\*]+)\\*", with: "$1", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "__([^_]+)__", with: "$1", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "_([^_]+)_", with: "$1", options: .regularExpression)
        
        // Remove markdown headers
        cleaned = cleaned.replacingOccurrences(of: "^#{1,6}\\s+", with: "", options: .regularExpression)
        
        // Replace multiple newlines with period for better speech flow
        cleaned = cleaned.replacingOccurrences(of: "\n\n+", with: ". ", options: .regularExpression)
        cleaned = cleaned.replacingOccurrences(of: "\n", with: " ", options: .regularExpression)
        
        // Remove emojis and special markers
        cleaned = cleaned.replacingOccurrences(of: "[❌✅🚨📝🔧⚠️🛑]", with: "", options: .regularExpression)
        
        // Trim whitespace
        cleaned = cleaned.trimmingCharacters(in: .whitespacesAndNewlines)
        
        return cleaned
    }
    
    fileprivate func didFinishSpeaking() {
        isSpeaking = false
        currentUtterance = nil
        
        // Speak next item in queue if any
        if !speechQueue.isEmpty {
            let next = speechQueue.removeFirst()
            speak(next)
        }
    }
}

// Delegate to handle speech synthesis events
private class SpeechSynthesizerDelegate: NSObject, AVSpeechSynthesizerDelegate {
    static let shared = SpeechSynthesizerDelegate()
    weak var manager: VoiceOutputManager?
    
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

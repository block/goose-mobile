//
//  TaskDetailView.swift
//  Goose
//
//  Created by Thomas Petersen on 10/9/25.
//

import SwiftUI

// MARK: - Array Extension for Safe Subscripting
extension Array {
    subscript(safe index: Int) -> Element? {
        return indices.contains(index) ? self[index] : nil
    }
}

// MARK: - Task Detail View (Shows completed tasks with conversation)
struct TaskDetailView: View {
    let message: Message
    let completedTasks: [CompletedToolCall]
    let sessionName: String
    @EnvironmentObject var themeManager: ThemeManager
    @Environment(\.dismiss) private var dismiss
    
    var taskName: String {
        if completedTasks.count == 1 {
            return completedTasks[0].toolCall.name
        } else {
            return "\(completedTasks.count) Tasks"
        }
    }
    
    // Extract text content from the message
    var messageText: String {
        for content in message.content {
            if case .text(let textContent) = content {
                return textContent.text
            }
        }
        return ""
    }
    
    // Format timestamp
    private func formatTimestamp(_ timestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp) / 1000)
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
    
    var body: some View {
        ZStack(alignment: .topLeading) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Timestamp at the top
                    Text(formatTimestamp(message.created))
                        .font(.system(size: 12))
                        .foregroundColor(themeManager.secondaryTextColor)
                        .padding(.bottom, 4)
                        .padding(.top, 56) // Add padding for custom nav bar (matches main chat)
                
                // Show the conversation/reasoning text
                if !messageText.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Response")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(themeManager.secondaryTextColor)
                        
                        Text(messageText)
                            .font(.system(size: 16))
                            .foregroundColor(themeManager.primaryTextColor)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.bottom, 8)
                }
                
                // Show all task outputs inline
                if !completedTasks.isEmpty {
                    ForEach(Array(completedTasks.enumerated()), id: \.offset) { index, task in
                        VStack(alignment: .leading, spacing: 12) {
                            // Task separator/header
                            if completedTasks.count > 1 {
                                Divider()
                                    .padding(.vertical, 8)
                                
                                HStack {
                                    Image(systemName: task.result.status == "success" ? "checkmark.circle" : "xmark.circle")
                                        .foregroundColor(task.result.status == "success" ? .green : .red)
                                        .font(.system(size: 16))
                                    
                                    Text(task.toolCall.name)
                                        .font(.system(size: 16, weight: .medium))
                                        .foregroundColor(themeManager.primaryTextColor)
                                    
                                    Spacer()
                                    
                                    Text(String(format: "%.2fs", task.duration))
                                        .font(.system(size: 12))
                                        .foregroundColor(themeManager.secondaryTextColor)
                                }
                            }
                            
                            // Arguments
                            if !task.toolCall.arguments.isEmpty {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Arguments")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(themeManager.primaryTextColor)
                                        .padding(.bottom, 4)
                                    
                                    ForEach(Array(task.toolCall.arguments.keys.sorted()), id: \.self) { key in
                                        if let argValue = task.toolCall.arguments[key]?.value {
                                            Text("\(key): \(String(describing: argValue))")
                                                .font(.system(size: 12, design: .monospaced))
                                                .foregroundColor(themeManager.secondaryTextColor)
                                        }
                                    }
                                }
                                .padding(.bottom, 8)
                            }
                            
                            // Output
                            if let value = task.result.value {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Output")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(themeManager.primaryTextColor)
                                        .padding(.bottom, 4)
                                    
                                    Text(String(describing: value.value))
                                        .font(.system(size: 12, design: .monospaced))
                                        .foregroundColor(themeManager.primaryTextColor)
                                        .textSelection(.enabled)
                                }
                            }
                            
                            // Error
                            if let error = task.result.error {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Error")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(.red)
                                        .padding(.bottom, 4)
                                    
                                    Text(error)
                                        .font(.system(size: 12, design: .monospaced))
                                        .foregroundColor(.red)
                                        .textSelection(.enabled)
                                }
                            }
                        }
                    }
                }
            }
            .padding()
        }
        .background(themeManager.backgroundColor)
        
            // Custom navigation bar overlay with frosted glass
            VStack(spacing: 0) {
                HStack(spacing: 0) {
                    // Back button
                    Button(action: {
                        dismiss()
                    }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(themeManager.primaryTextColor)
                            .frame(width: 44, height: 44)
                    }
                    .padding(.leading, 4)
                    
                    // Breadcrumb (left-aligned) - positioned to match main chat session name at 48px
                    HStack(spacing: 4) {
                        Text(sessionName)
                            .font(.system(size: 14))
                            .foregroundColor(themeManager.primaryTextColor)
                            .lineLimit(1)
                            .truncationMode(.middle)
                        
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12))
                            .foregroundColor(themeManager.primaryTextColor)
                        
                        Text(taskName)
                            .font(.system(size: 14))
                            .foregroundColor(themeManager.primaryTextColor)
                            .lineLimit(1)
                    }
                    
                    Spacer()
                }
                .padding(.horizontal, 0)
                .padding(.top, 56)
                .padding(.bottom, 24)
            }
            .background(
                ZStack {
                    Rectangle()
                        .fill(.ultraThinMaterial)
                    Rectangle()
                        .fill(themeManager.backgroundColor.opacity(0.95))
                }
                .ignoresSafeArea()
            )
            .frame(maxWidth: .infinity, alignment: .top)
            .shadow(color: Color.black.opacity(0.05), radius: 0, y: 1)
        }
        .navigationBarHidden(true)
        .ignoresSafeArea(edges: .top)
    }
}

// MARK: - Task Output Detail View (Shows individual task output)
struct TaskOutputDetailView: View {
    let task: CompletedToolCall
    let taskNumber: Int
    let sessionName: String
    var messageTimestamp: Int64? = nil // Optional timestamp from message
    @EnvironmentObject var themeManager: ThemeManager
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""
    @State private var searchMatches: [Int] = []
    @State private var currentMatchIndex: Int = 0
    @State private var outputLines: [String] = []
    
    // Format timestamp
    private func formatTimestamp(_ timestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp) / 1000)
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
    
    // Search functionality
    private func performSearch() {
        guard !searchText.isEmpty else {
            searchMatches = []
            currentMatchIndex = 0
            return
        }
        
        searchMatches = []
        for (index, line) in outputLines.enumerated() {
            if line.lowercased().contains(searchText.lowercased()) {
                searchMatches.append(index)
            }
        }
        
        if !searchMatches.isEmpty {
            currentMatchIndex = 0
        }
    }
    
    private func nextMatch() {
        guard !searchMatches.isEmpty else { return }
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.count
    }
    
    private func previousMatch() {
        guard !searchMatches.isEmpty else { return }
        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.count) % searchMatches.count
    }
    
    var body: some View {
        ZStack(alignment: .top) {
            VStack(spacing: 0) {
                // Task output with padding for nav bar
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        // Timestamp (if provided from message)
                        if let timestamp = messageTimestamp {
                            Text(formatTimestamp(timestamp))
                                .font(.system(size: 12))
                                .foregroundColor(themeManager.secondaryTextColor)
                            .padding(.bottom, 8)
                    }
                    
                    // Header with tool name and status
                    HStack {
                        Image(systemName: task.result.status == "success" ? "checkmark.circle" : "xmark.circle")
                            .foregroundColor(task.result.status == "success" ? .green : .red)
                            .font(.system(size: 16))
                        
                        Text("\(task.toolCall.name)")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(themeManager.primaryTextColor)
                        
                        Spacer()
                        
                        Text(String(format: "%.2fs", task.duration))
                            .font(.system(size: 12))
                            .foregroundColor(themeManager.secondaryTextColor)
                        
                        Text("• \(task.result.status)")
                            .font(.system(size: 12))
                            .foregroundColor(task.result.status == "success" ? .green : .red)
                    }
                    .padding(.bottom, 4)
                    
                    // Arguments
                    if !task.toolCall.arguments.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Arguments")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(themeManager.primaryTextColor)
                                .padding(.bottom, 4)
                            
                            ForEach(Array(task.toolCall.arguments.keys.sorted()), id: \.self) { key in
                                if let argValue = task.toolCall.arguments[key]?.value {
                                    Text("\(key): \(String(describing: argValue))")
                                        .font(.system(size: 12, design: .monospaced))
                                        .foregroundColor(themeManager.secondaryTextColor)
                                }
                            }
                        }
                        .padding(.bottom, 8)
                    }
                    
                    // Full output/result - formatted based on type
                    if let value = task.result.value {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Output")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(themeManager.primaryTextColor)
                                .padding(.bottom, 4)
                            
                            FormattedOutputView(
                                value: value.value,
                                searchText: searchText,
                                searchMatches: $searchMatches,
                                currentMatchIndex: $currentMatchIndex,
                                outputLines: $outputLines
                            )
                            .environmentObject(themeManager)
                        }
                    }
                    
                    if let error = task.result.error {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Error")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.red)
                                .padding(.bottom, 4)
                            
                            Text(error)
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundColor(.red)
                                .textSelection(.enabled)
                        }
                    }
                    }
                    .padding()
                    .padding(.top, 80) // Padding for nav bar (56pt top + 24pt bottom = 80pt total height)
                    .padding(.bottom, 100) // Padding for search bar
                }
            }
            .background(themeManager.backgroundColor)
            
            // Search field at bottom
            VStack {
                Spacer()
                
                HStack(spacing: 12) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(themeManager.secondaryTextColor)
                        .font(.system(size: 16))
                    
                    TextField("Search output...", text: $searchText)
                        .font(.system(size: 16))
                        .foregroundColor(themeManager.primaryTextColor)
                        .onChange(of: searchText) { _ in
                            performSearch()
                        }
                    
                    if !searchMatches.isEmpty {
                        Text("\(currentMatchIndex + 1)/\(searchMatches.count)")
                            .font(.system(size: 12))
                            .foregroundColor(themeManager.secondaryTextColor)
                        
                        Button(action: previousMatch) {
                            Image(systemName: "chevron.up")
                                .foregroundColor(themeManager.secondaryTextColor)
                                .font(.system(size: 14))
                        }
                        .buttonStyle(.plain)
                        
                        Button(action: nextMatch) {
                            Image(systemName: "chevron.down")
                                .foregroundColor(themeManager.secondaryTextColor)
                                .font(.system(size: 14))
                        }
                        .buttonStyle(.plain)
                    }
                    
                    if !searchText.isEmpty {
                        Button(action: {
                            searchText = ""
                            searchMatches = []
                            currentMatchIndex = 0
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(themeManager.secondaryTextColor)
                                .font(.system(size: 16))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(themeManager.chatInputBackgroundColor.opacity(0.85))
                .cornerRadius(26)
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
        
        
            // Custom navigation bar overlay with frosted glass
            VStack(spacing: 0) {
                HStack(spacing: 0) {
                    // Back button
                    Button(action: {
                        dismiss()
                    }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(themeManager.primaryTextColor)
                            .frame(width: 44, height: 44)
                    }
                    .padding(.leading, 4)
                    
                    // Breadcrumb (left-aligned) - positioned to match main chat session name at 48px
                    HStack(spacing: 4) {
                        Text(sessionName)
                            .font(.system(size: 14))
                            .foregroundColor(themeManager.primaryTextColor)
                            .lineLimit(1)
                            .truncationMode(.middle)
                        
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12))
                            .foregroundColor(themeManager.primaryTextColor)
                        
                        Text(task.toolCall.name)
                            .font(.system(size: 14))
                            .foregroundColor(themeManager.primaryTextColor)
                            .lineLimit(1)
                    }
                    
                    Spacer()
                }
                .padding(.horizontal, 0)
                .padding(.top, 56)
                .padding(.bottom, 24)
            }
            .background(
                ZStack {
                    Rectangle()
                        .fill(.ultraThinMaterial)
                    Rectangle()
                        .fill(themeManager.backgroundColor.opacity(0.95))
                }
                .ignoresSafeArea()
            )
            .frame(maxWidth: .infinity, alignment: .top)
            .shadow(color: Color.black.opacity(0.05), radius: 0, y: 1)
        }
        .navigationBarHidden(true)
        .ignoresSafeArea(edges: .top)
    }
}

// MARK: - Formatted Output View
struct FormattedOutputView: View {
    let value: Any
    let searchText: String
    @Binding var searchMatches: [Int]
    @Binding var currentMatchIndex: Int
    @Binding var outputLines: [String]
    @EnvironmentObject var themeManager: ThemeManager
    
    var body: some View {
        Group {
            if let stringValue = value as? String {
                // String: display with line numbers (good for command output, logs, etc.)
                StringOutputView(
                    text: stringValue,
                    searchMatches: searchMatches,
                    currentMatchIndex: currentMatchIndex,
                    outputLines: $outputLines
                )
            } else if let arrayValue = value as? [Any] {
                // Array: display as numbered list
                ArrayOutputView(array: arrayValue)
            } else if let dictValue = value as? [String: Any] {
                // Dictionary: display as key-value pairs
                DictionaryOutputView(dictionary: dictValue)
            } else {
                // Fallback: convert to string
                StringOutputView(
                    text: String(describing: value),
                    searchMatches: searchMatches,
                    currentMatchIndex: currentMatchIndex,
                    outputLines: $outputLines
                )
            }
        }
        .onAppear {
            // Initialize outputLines based on value type
            if let stringValue = value as? String {
                outputLines = stringValue.components(separatedBy: .newlines)
            } else {
                outputLines = formatValue(value, indent: 0).components(separatedBy: .newlines)
            }
        }
    }
    
    // Helper to format any value with proper indentation
    private func formatValue(_ value: Any, indent: Int) -> String {
        let indentation = String(repeating: "  ", count: indent)
        
        if let stringValue = value as? String {
            return stringValue
        } else if let arrayValue = value as? [Any] {
            if arrayValue.isEmpty { return "[]" }
            var result = "[\n"
            for (index, item) in arrayValue.enumerated() {
                result += "\(indentation)  \(index): \(formatValue(item, indent: indent + 1))\n"
            }
            result += "\(indentation)]"
            return result
        } else if let dictValue = value as? [String: Any] {
            if dictValue.isEmpty { return "{}" }
            var result = "{\n"
            for (key, val) in dictValue.sorted(by: { $0.key < $1.key }) {
                result += "\(indentation)  \(key): \(formatValue(val, indent: indent + 1))\n"
            }
            result += "\(indentation)}"
            return result
        } else {
            return String(describing: value)
        }
    }
}

// String output with line numbers
struct StringOutputView: View {
    let text: String
    let searchMatches: [Int]
    let currentMatchIndex: Int
    @Binding var outputLines: [String]
    @EnvironmentObject var themeManager: ThemeManager
    
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(outputLines.enumerated()), id: \.offset) { index, line in
                    HStack(alignment: .top, spacing: 12) {
                        // Line number
                        Text("\(index + 1)")
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundColor(themeManager.secondaryTextColor.opacity(0.5))
                            .frame(minWidth: 40, alignment: .trailing)
                        
                        // Line content with search highlight
                        if searchMatches.contains(index) {
                            Text(line)
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundColor(themeManager.primaryTextColor)
                                .background(
                                    searchMatches[safe: currentMatchIndex] == index ?
                                        Color.orange.opacity(0.4) : Color.yellow.opacity(0.3)
                                )
                                .id("line-\(index)")
                        } else {
                            Text(line)
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundColor(themeManager.primaryTextColor)
                        }
                        
                        Spacer()
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .textSelection(.enabled)
        }
    }
}

// Array output as numbered list
struct ArrayOutputView: View {
    let array: [Any]
    @EnvironmentObject var themeManager: ThemeManager
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(array.enumerated()), id: \.offset) { index, item in
                HStack(alignment: .top, spacing: 12) {
                    // Row number
                    Text("\(index + 1).")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(themeManager.secondaryTextColor)
                        .frame(minWidth: 30, alignment: .trailing)
                    
                    // Item value
                    VStack(alignment: .leading, spacing: 4) {
                        if let dictItem = item as? [String: Any] {
                            // Nested dictionary
                            ForEach(Array(dictItem.keys.sorted()), id: \.self) { key in
                                HStack(alignment: .top, spacing: 8) {
                                    Text("\(key):")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(themeManager.secondaryTextColor)
                                    Text(formatSimpleValue(dictItem[key]))
                                        .font(.system(size: 13))
                                        .foregroundColor(themeManager.primaryTextColor)
                                }
                            }
                        } else {
                            Text(formatSimpleValue(item))
                                .font(.system(size: 13))
                                .foregroundColor(themeManager.primaryTextColor)
                        }
                    }
                    
                    Spacer()
                }
            }
        }
        .textSelection(.enabled)
    }
    
    private func formatSimpleValue(_ value: Any?) -> String {
        guard let value = value else { return "null" }
        
        if let stringValue = value as? String {
            return stringValue
        } else if let numberValue = value as? NSNumber {
            return numberValue.stringValue
        } else if let boolValue = value as? Bool {
            return boolValue ? "true" : "false"
        } else if let arrayValue = value as? [Any] {
            return "[\(arrayValue.count) items]"
        } else if let dictValue = value as? [String: Any] {
            return "{\(dictValue.count) keys}"
        } else {
            return String(describing: value)
        }
    }
}

// Dictionary output as key-value pairs
struct DictionaryOutputView: View {
    let dictionary: [String: Any]
    @EnvironmentObject var themeManager: ThemeManager
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(dictionary.keys.sorted()), id: \.self) { key in
                VStack(alignment: .leading, spacing: 4) {
                    // Key
                    Text(key)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(themeManager.secondaryTextColor)
                    
                    // Value
                    Group {
                        if let stringValue = dictionary[key] as? String {
                            Text(stringValue)
                                .font(.system(size: 13, design: .monospaced))
                                .foregroundColor(themeManager.primaryTextColor)
                        } else if let arrayValue = dictionary[key] as? [Any] {
                            Text("[\(arrayValue.count) items]")
                                .font(.system(size: 13))
                                .foregroundColor(themeManager.primaryTextColor.opacity(0.7))
                            // Show array items indented
                            ForEach(Array(arrayValue.enumerated()), id: \.offset) { index, item in
                                Text("  \(index + 1). \(formatSimpleValue(item))")
                                    .font(.system(size: 12))
                                    .foregroundColor(themeManager.primaryTextColor)
                                    .padding(.leading, 8)
                            }
                        } else if let dictValue = dictionary[key] as? [String: Any] {
                            Text("{\(dictValue.count) keys}")
                                .font(.system(size: 13))
                                .foregroundColor(themeManager.primaryTextColor.opacity(0.7))
                            // Show nested keys
                            ForEach(Array(dictValue.keys.sorted()), id: \.self) { nestedKey in
                                Text("  \(nestedKey): \(formatSimpleValue(dictValue[nestedKey]))")
                                    .font(.system(size: 12))
                                    .foregroundColor(themeManager.primaryTextColor)
                                    .padding(.leading, 8)
                            }
                        } else {
                            Text(formatSimpleValue(dictionary[key]))
                                .font(.system(size: 13))
                                .foregroundColor(themeManager.primaryTextColor)
                        }
                    }
                }
                .padding(.vertical, 4)
                
                if key != dictionary.keys.sorted().last {
                    Divider()
                        .padding(.vertical, 4)
                }
            }
        }
        .textSelection(.enabled)
    }
    
    private func formatSimpleValue(_ value: Any?) -> String {
        guard let value = value else { return "null" }
        
        if let stringValue = value as? String {
            return stringValue
        } else if let numberValue = value as? NSNumber {
            return numberValue.stringValue
        } else if let boolValue = value as? Bool {
            return boolValue ? "true" : "false"
        } else if let arrayValue = value as? [Any] {
            return "[\(arrayValue.count) items]"
        } else if let dictValue = value as? [String: Any] {
            return "{\(dictValue.count) keys}"
        } else {
            return String(describing: value)
        }
    }
}

#Preview {
    TaskDetailView(message: Message(role: .assistant, text: "Here's a preview of the task detail view"), completedTasks: [], sessionName: "Test Session")
        .environmentObject(ThemeManager.shared)
}


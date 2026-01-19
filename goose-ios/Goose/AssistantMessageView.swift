import SwiftUI

/// Displays an assistant message with optional tool completion pills
struct AssistantMessageView: View {
    let message: Message
    let completedTasks: [CompletedToolCall]
    let sessionName: String
    @State private var showFullText = false
    
    private enum Constants {
        static let cornerRadius: CGFloat = 16
        static let fontSize: CGFloat = 16
        static let padding = EdgeInsets(top: 12, leading: 12, bottom: 12, trailing: 12)
        static let horizontalMargin: CGFloat = 12
        static let maxHeightRatio: CGFloat = 0.7
        static let spacing: CGFloat = 8
    }
    
    private var maxHeight: CGFloat {
        UIScreen.main.bounds.height * Constants.maxHeightRatio
    }
    
    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: Constants.spacing) {
                // Message content (filtered to exclude tool responses and requests)
                if !filteredContent.isEmpty {
                    ForEach(Array(filteredContent.enumerated()), id: \.offset) { _, content in
                        MessageContentView(content: content)
                    }
                }
                
                // Tool completion pills
                if !completedTasks.isEmpty {
                    toolCompletionPills
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.leading, Constants.horizontalMargin)
        .padding(.bottom, 8)
        .padding(.top, 8)
        .sheet(isPresented: $showFullText) {
            FullTextOverlay(content: filteredContent)
        }
    }
    
    /// Filter out tool responses and tool requests (shown as pills instead)
    private var filteredContent: [MessageContent] {
        message.content.filter { content in
            !isToolResponse(content) && !isToolRequest(content)
        }
    }
    
    /// Tool completion pills view
    @ViewBuilder
    private var toolCompletionPills: some View {
        if completedTasks.count == 1 {
            // Single task - navigate directly to output
            singleTaskPill(completedTasks[0])
        } else {
            // Multiple tasks - show combined view
            multipleTasksPill
        }
    }
    
    private func singleTaskPill(_ task: CompletedToolCall) -> some View {
        NavigationLink(destination: TaskOutputDetailView(
            task: task,
            taskNumber: 1,
            sessionName: sessionName,
            messageTimestamp: message.created
        ).environmentObject(ThemeManager.shared)) {
            TaskPillContent(
                icon: "checkmark.circle",
                text: task.toolCall.name,
                arguments: task.toolCall.arguments  // Pass arguments!
            )
        }
        .buttonStyle(.plain)
    }
    
    private var multipleTasksPill: some View {
        NavigationLink(destination: TaskDetailView(
            message: message,
            completedTasks: completedTasks,
            sessionName: sessionName
        ).environmentObject(ThemeManager.shared)) {
            TaskPillContent(
                icon: "checkmark.circle",
                text: "\(completedTasks.count) Tasks completed"
            )
        }
        .buttonStyle(.plain)
    }
    
    private func isToolResponse(_ content: MessageContent) -> Bool {
        if case .toolResponse = content { return true }
        return false
    }
    
    private func isToolRequest(_ content: MessageContent) -> Bool {
        if case .toolRequest = content { return true }
        return false
    }
}

/// Reusable pill content for task completion

/// Enhanced pill content for task completion with argument snippets
struct TaskPillContent: View {
    let icon: String
    let text: String
    let arguments: [String: AnyCodable]?  // Optional arguments to display
    
    // Convenience initializer for backward compatibility
    init(icon: String, text: String) {
        self.icon = icon
        self.text = text
        self.arguments = nil
    }
    
    // Full initializer with arguments
    init(icon: String, text: String, arguments: [String: AnyCodable]?) {
        self.icon = icon
        self.text = text
        self.arguments = arguments
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Header with icon and tool name
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.secondary)
                
                Text(text)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.secondary)
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            
            // Arguments snippet (if available)
            if let args = arguments, !args.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(Array(getArgumentSnippets(args)), id: \.key) { item in
                        HStack(alignment: .top, spacing: 4) {
                            Text("\(item.key):")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(.secondary.opacity(0.8))
                            
                            Text(item.value)
                                .font(.system(size: 10))
                                .monospaced()
                                .foregroundColor(.secondary.opacity(0.8))
                                .lineLimit(1)
                            
                            Spacer()
                        }
                    }
                }
                .padding(.leading, 20) // Align with text after icon
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color(.systemGray6).opacity(0.85))
        .cornerRadius(16)
    }
    
    private func getArgumentSnippets(_ args: [String: AnyCodable]) -> [(key: String, value: String)] {
        let maxArgs = 2 // Show up to 2 arguments in pill (less than progress view)
        let maxValueLength = 30 // Shorter for pill
        
        return args
            .sorted { $0.key < $1.key }
            .prefix(maxArgs)
            .map { key, value in
                let valueString = String(describing: value.value)
                let truncated = valueString.count > maxValueLength 
                    ? String(valueString.prefix(maxValueLength)) + "..." 
                    : valueString
                return (key: key, value: truncated)
            }
    }
}

/// Displays different types of message content
struct MessageContentView: View {
    let content: MessageContent
    
    var body: some View {
        switch content {
        case .text(let textContent):
            MarkdownText(text: textContent.text)
                .textSelection(.enabled)
            
        case .toolRequest(let toolContent):
            ToolRequestView(toolContent: toolContent)
            
        case .toolResponse:
            EmptyView()
            
        case .toolConfirmationRequest(let toolContent):
            ToolConfirmationView(toolContent: toolContent)
            
        case .summarizationRequested:
            EmptyView()
            
        case .conversationCompacted(let content):
            MarkdownText(text: "📝 \(content.msg)")
                .textSelection(.enabled)
            
        case .systemNotification(let content):
            // System notifications can be displayed as informational messages
            MarkdownText(text: "ℹ️ \(content.msg)")
                .textSelection(.enabled)
                .foregroundColor(.secondary)
        
        case .thinking(let content):
            // Display thinking content with distinct styling
            ThinkingContentView(text: content.thinking)
                .textSelection(.enabled)
        }
    }
}

#Preview {
    NavigationView {
        ScrollView {
            VStack(spacing: 16) {
                AssistantMessageView(
                    message: Message(role: .assistant, text: "I can help you with that!"),
                    completedTasks: [],
                    sessionName: "Test Session"
                )
                
                AssistantMessageView(
                    message: Message(role: .assistant, text: "Here's some **formatted** text with `code`"),
                    completedTasks: [],
                    sessionName: "Test Session"
                )
            }
            .padding()
        }
    }
}

/// View for displaying thinking content
struct ThinkingContentView: View {
    let text: String
    @State private var isExpanded = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button(action: {
                withAnimation {
                    isExpanded.toggle()
                }
            }) {
                HStack(spacing: 6) {
                    Image(systemName: "brain")
                        .font(.system(size: 12))
                    Text("Thinking Process")
                        .font(.system(size: 12, weight: .medium))
                    Image(systemName: isExpanded ? "chevron.down" : "chevron.right")
                        .font(.system(size: 10))
                }
                .foregroundColor(.secondary)
            }
            .buttonStyle(.plain)
            
            if isExpanded {
                MarkdownText(text: text)
                    .padding(.leading, 8)
                    .padding(.vertical, 4)
                    .background(Color(.systemGray6).opacity(0.5))
                    .cornerRadius(8)
            }
        }
    }
}

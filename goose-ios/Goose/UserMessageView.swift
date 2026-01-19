import SwiftUI

/// Displays a user message bubble with simple styling
struct UserMessageView: View {
    let message: Message
    
    private enum Constants {
        static let cornerRadius: CGFloat = 16
        static let fontSize: CGFloat = 16
        static let padding = EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12)
        static let horizontalMargin: CGFloat = 12
    }
    
    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            Spacer()
            
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(textContent.enumerated()), id: \.offset) { _, content in
                    MarkdownText(text: content.text, isUserMessage: true)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(Constants.padding)
            .background(Color(.darkGray))
            .cornerRadius(Constants.cornerRadius)
        }
        .frame(maxWidth: .infinity, alignment: .trailing)
        .padding(.trailing, Constants.horizontalMargin)
    }
    
    /// Extract text content from message
    private var textContent: [TextContent] {
        message.content.compactMap { content in
            if case .text(let textContent) = content {
                return textContent
            }
            return nil
        }
    }
}

#Preview {
    ScrollView {
        VStack(spacing: 16) {
            UserMessageView(message: Message(role: .user, text: "Hello, can you help me?"))
            UserMessageView(message: Message(role: .user, text: "Testing **bold** and `code` formatting"))
            UserMessageView(message: Message(role: .user, text: """
                Multi-line message
                with several lines
                of text content
                """))
        }
        .padding()
    }
}

import SwiftUI
import Markdown

// MARK: - Cached Regex Patterns

/// Cache for compiled regex patterns to avoid recreating them on every use
private enum RegexCache {
    // Text processing patterns
    static let colonSpacing = try! NSRegularExpression(pattern: #":(\S)"#, options: [])
    static let commaSpacing = try! NSRegularExpression(pattern: #",(\S)"#, options: [])
    static let semicolonSpacing = try! NSRegularExpression(pattern: #";(\S)"#, options: [])
    
    // Syntax highlighting patterns
    static let doubleQuoteString = try! NSRegularExpression(pattern: #""[^"\\]*(?:\\.[^"\\]*)*""#, options: [])
    static let singleQuoteString = try! NSRegularExpression(pattern: #"'[^'\\]*(?:\\.[^'\\]*)*'"#, options: [])
    static let templateLiteral = try! NSRegularExpression(pattern: #"`[^`]*`"#, options: [])
    static let singleLineComment = try! NSRegularExpression(pattern: #"//[^\n]*"#, options: [])
    static let hashComment = try! NSRegularExpression(pattern: #"#[^\n]*"#, options: [])
    static let multiLineComment = try! NSRegularExpression(pattern: #"/\*[\s\S]*?\*/"#, options: [])
    static let numbers = try! NSRegularExpression(pattern: #"\b\d+\.?\d*\b"#, options: [])
    static let functionCall = try! NSRegularExpression(pattern: #"(\w+)\s*\("#, options: [])
    
    // Language-specific patterns
    static let pythonDecorator = try! NSRegularExpression(pattern: #"@\w+"#, options: [])
    static let shellVariable = try! NSRegularExpression(pattern: #"\$\w+"#, options: [])
    static let rubySymbol = try! NSRegularExpression(pattern: #":\w+"#, options: [])
    static let rubyInstanceVar = try! NSRegularExpression(pattern: #"@\w+"#, options: [])
    static let rustMacro = try! NSRegularExpression(pattern: #"\w+!"#, options: [])
    static let rustLifetime = try! NSRegularExpression(pattern: #"'\w+"#, options: [])
    static let htmlTag = try! NSRegularExpression(pattern: #"</?[^>]+>"#, options: [])
    static let htmlAttribute = try! NSRegularExpression(pattern: #"\w+(?==["'])"#, options: [])
    static let cssSelector = try! NSRegularExpression(pattern: #"[.#]?[\w-]+(?=\s*\{)"#, options: [])
    static let cssProperty = try! NSRegularExpression(pattern: #"[\w-]+(?=\s*:)"#, options: [])
    static let cssColor = try! NSRegularExpression(pattern: #"#[0-9a-fA-F]{3,6}"#, options: [])
    static let jsonProperty = try! NSRegularExpression(pattern: #""([^"]+)"\s*:"#, options: [])
    
    // Helper to create word boundary patterns dynamically
    static func wordBoundaryPattern(_ word: String) -> NSRegularExpression {
        return try! NSRegularExpression(pattern: "\\b" + NSRegularExpression.escapedPattern(for: word) + "\\b", options: [])
    }
}

// MARK: - Markdown Text View with Table and Code Block Support

struct MarkdownText: View {
    let text: String
    var isUserMessage: Bool = false
    @State private var contentItems: [MarkdownContentItem] = []
    @State private var previousText = ""
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(contentItems) { item in
                switch item {
                case .text(let attributedText):
                    Group {
                        if isUserMessage {
                            Text(attributedText)
                                .font(.system(size: 14, weight: .regular))
                                .foregroundColor(.white)
                                .lineLimit(nil)
                                .multilineTextAlignment(.leading)
                                .fixedSize(horizontal: false, vertical: true)
                        } else {
                            Text(attributedText)
                                .font(.system(size: 14, weight: .regular))
                                .multilineTextAlignment(.leading)
                                .textSelection(.enabled)
                        }
                    }
                    
                case .codeBlock(let codeBlock):
                    CodeBlockView(codeBlock: codeBlock)
                    
                case .table(let table):
                    MarkdownTableView(tableData: table)
                        .padding(.leading, -28)
                        .padding(.trailing, -28)
                }
            }
        }
        .onAppear {
            if contentItems.isEmpty {
                parseContent()
            }
        }
        .onChange(of: text) { _, newText in
            let hasTable = newText.contains("|") && newText.contains("---")
            let hasCodeBlock = newText.contains("```")
            
            if !newText.hasPrefix(previousText) || hasTable || hasCodeBlock {
                // Text was modified (not appended) or has special content - full reparse with animation
                withAnimation(.easeOut(duration: 0.15)) {
                    parseContent()
                }
            } else if newText != previousText {
                // Text was appended - check if delta contains markdown syntax
                let delta = String(newText.dropFirst(previousText.count))
                if delta.contains("**") || delta.contains("*") || delta.contains("`") || 
                   delta.contains("[") || delta.contains("#") || delta.contains("\n\n") {
                    // Markdown syntax detected - reparse with animation
                    withAnimation(.easeOut(duration: 0.15)) {
                        parseContent()
                    }
                } else {
                    // Plain text - reparse with faster animation for smooth streaming
                    withAnimation(.easeOut(duration: 0.1)) {
                        parseContent()
                    }
                }
            }
            previousText = newText
        }
        .animation(.easeOut(duration: 0.1), value: contentItems.map { $0.id })
    }
    
    private func parseContent() {
        contentItems = MarkdownParser.parseSequentially(text)
        previousText = text
    }
}

// MARK: - Markdown Content Items (for preserving order)

enum MarkdownContentItem: Identifiable {
    case text(AttributedString)
    case codeBlock(CodeBlockData)
    case table(TableData)
    
    var id: String {
        switch self {
        case .text(let attr):
            return "text_\(attr.description.prefix(50).hashValue)"
        case .codeBlock(let block):
            return "code_\(block.id)"
        case .table(let table):
            return "table_\(table.id)"
        }
    }
}

// MARK: - Sequential Markdown Parser

extension MarkdownParser {
    static func parseSequentially(_ text: String) -> [MarkdownContentItem] {
        let trimmedText = text.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        let document = Document(parsing: trimmedText)
        
        var items: [MarkdownContentItem] = []
        var currentTextBuffer = AttributedString()
        
        for child in document.children {
            if let table = child as? Markdown.Table {
                // Flush any accumulated text before the table
                if !currentTextBuffer.characters.isEmpty {
                    items.append(.text(currentTextBuffer))
                    currentTextBuffer = AttributedString()
                }
                
                // Add the table
                if let tableData = extractTableData(from: table) {
                    items.append(.table(tableData))
                }
            } else if let codeBlock = child as? CodeBlock {
                // Flush any accumulated text before the code block
                if !currentTextBuffer.characters.isEmpty {
                    items.append(.text(currentTextBuffer))
                    currentTextBuffer = AttributedString()
                }
                
                // Add the code block
                let language = codeBlock.language
                items.append(.codeBlock(CodeBlockData(code: codeBlock.code, language: language)))
            } else {
                // Regular markdown element - add to text buffer
                currentTextBuffer.append(processElement(child))
            }
        }
        
        // Flush any remaining text
        if !currentTextBuffer.characters.isEmpty {
            // Remove trailing newlines
            while currentTextBuffer.characters.last == "\n" {
                currentTextBuffer = AttributedString(currentTextBuffer.characters.dropLast())
            }
            if !currentTextBuffer.characters.isEmpty {
                items.append(.text(currentTextBuffer))
            }
        }
        
        return items
    }
}





struct MarkdownParser {
    static func parse(_ text: String) -> AttributedString {
        let trimmedText = text.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        let document = Document(parsing: trimmedText)
        
        var attributedString = AttributedString()
        for child in document.children {
            attributedString.append(processElement(child))
        }
        
        // Trim trailing newlines for cleaner output
        while attributedString.characters.last == "\n" {
            attributedString = AttributedString(attributedString.characters.dropLast())
        }
        
        return attributedString.characters.isEmpty ? AttributedString(trimmedText) : attributedString
    }
    
    private static func processElement(_ element: Markup) -> AttributedString {
        var result = AttributedString()
        
        switch element {
        case let paragraph as Paragraph:
            for child in paragraph.children {
                if let inlineChild = child as? InlineMarkup {
                    result.append(processInline(inlineChild))
                }
            }
            result.append(AttributedString("\n"))
            
        case let heading as Heading:
            var headingText = AttributedString()
            for child in heading.children {
                if let inlineChild = child as? InlineMarkup {
                    headingText.append(processInline(inlineChild))
                }
            }
            headingText.font = .system(size: max(18 - CGFloat(heading.level) * 2, 14), weight: .bold)
            result.append(headingText)
            result.append(AttributedString("\n"))
            
        case let codeBlock as CodeBlock:
            var codeText = AttributedString(codeBlock.code)
            codeText.font = .system(size: 14, design: .monospaced)
            codeText.backgroundColor = .secondary.opacity(0.1)
            result.append(codeText)
            result.append(AttributedString("\n\n"))
            
        case let list as UnorderedList:
            for child in list.children {
                result.append(processElement(child))
            }
            result.append(AttributedString("\n\n"))  // Paragraph break after list
            
        case let list as OrderedList:
            for child in list.children {
                result.append(processElement(child))
            }
            result.append(AttributedString("\n\n"))  // Paragraph break after list
            
        case let listItem as ListItem:
            result.append(AttributedString("• "))
            for child in listItem.children {
                var childResult = processElement(child)
                // Strip trailing newlines from list item content
                while childResult.characters.last == "\n" {
                    childResult = AttributedString(childResult.characters.dropLast())
                }
                result.append(childResult)
            }
            // Add a smaller line break for tighter spacing
            var smallBreak = AttributedString("\n")
            smallBreak.font = .system(size: 7)  // Half the normal 14pt font
            result.append(smallBreak)
        default:
            if let blockElement = element as? BlockMarkup {
                for child in blockElement.children {
                    result.append(processElement(child))
                }
            }
        }
        
        return result
    }
    
    private static func processInline(_ element: InlineMarkup) -> AttributedString {
        switch element {
        case let text as Markdown.Text:
            // Add spacing after common punctuation if not already present
            var processedText = text.plainText
            
            // Add space after colons
            processedText = RegexCache.colonSpacing.stringByReplacingMatches(in: processedText, range: NSRange(processedText.startIndex..., in: processedText), withTemplate: ": $1")
            
            // Add space after commas
            processedText = RegexCache.commaSpacing.stringByReplacingMatches(in: processedText, range: NSRange(processedText.startIndex..., in: processedText), withTemplate: ", $1")
            
            // Add space after semicolons
            processedText = RegexCache.semicolonSpacing.stringByReplacingMatches(in: processedText, range: NSRange(processedText.startIndex..., in: processedText), withTemplate: "; $1")
            
            return AttributedString(processedText)
            
        case let strong as Strong:
            var strongText = AttributedString()
            for child in strong.children {
                if let inlineChild = child as? InlineMarkup {
                    strongText.append(processInline(inlineChild))
                }
            }
            strongText.font = .system(size: 14, weight: .bold)
            return strongText
            
        case let emphasis as Emphasis:
            var emphasisText = AttributedString()
            for child in emphasis.children {
                if let inlineChild = child as? InlineMarkup {
                    emphasisText.append(processInline(inlineChild))
                }
            }
            emphasisText.font = .system(size: 14).italic()
            return emphasisText
            
        case let inlineCode as InlineCode:
            var codeText = AttributedString(inlineCode.code)
            codeText.font = .system(size: 14, design: .monospaced)
            codeText.backgroundColor = .secondary.opacity(0.1)
            return codeText
            
        case let link as Markdown.Link:
            var linkText = AttributedString()
            for child in link.children {
                if let inlineChild = child as? InlineMarkup {
                    linkText.append(processInline(inlineChild))
                }
            }
            linkText.foregroundColor = .blue
            linkText.underlineStyle = .single
            if let destination = link.destination {
                linkText.link = URL(string: destination)
            }
            return linkText
            
        default:
            return AttributedString(element.plainText)
        }
    }
    
    // MARK: - Table Support
    
    /// Extract all tables from markdown text
    static func extractTables(_ text: String) -> [TableData] {
        let document = Document(parsing: text)
        var tables: [TableData] = []
        
        func findTables(in element: Markup) {
            if let table = element as? Markdown.Table {
                if let tableData = extractTableData(from: table) {
                    tables.append(tableData)
                }
            }
            
            // Recursively search children
            for child in element.children {
                findTables(in: child)
            }
        }
        
        for child in document.children {
            findTables(in: child)
        }
        
        return tables
    }
    
    /// Extract table data from a Table markup element

    /// Extract table data from a Table markup element
    private static func extractTableData(from table: Markdown.Table) -> TableData? {
        var headers: [String] = []
        var rows: [[String]] = []
        var alignments: [TextAlignment] = []
        
        // Extract headers from table head
        let head = table.head
        for cell in head.cells {
            headers.append(cell.plainText.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines))
        }
        
        // Extract column alignments
        for columnIndex in 0..<table.columnAlignments.count {
            let alignment = table.columnAlignments[columnIndex]
            switch alignment {
            case .left:
                alignments.append(.leading)
            case .center:
                alignments.append(.center)
            case .right:
                alignments.append(.trailing)
            default:
                alignments.append(.leading)
            }
        }
        
        // Extract data rows from table body
        let body = table.body
        for row in body.rows {
            var rowData: [String] = []
            for cell in row.cells {
                rowData.append(cell.plainText.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines))
            }
            // Ensure row has same number of columns as headers
            while rowData.count < headers.count {
                rowData.append("")
            }
            rows.append(rowData)
        }
        
        // Only return if we have headers and at least one row
        guard !headers.isEmpty, !rows.isEmpty else { return nil }
        
        return TableData(headers: headers, rows: rows, columnAlignments: alignments)
    }

    static func parseWithoutTables(_ text: String) -> AttributedString {
        let trimmedText = text.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        let document = Document(parsing: trimmedText)
        
        var attributedString = AttributedString()
        for child in document.children {
            // Skip tables - they'll be rendered as SwiftUI views
            if child is Markdown.Table {
                continue
            }
            attributedString.append(processElement(child))
        }
        
        // Trim trailing newlines
        while attributedString.characters.last == "\n" {
            attributedString = AttributedString(attributedString.characters.dropLast())
        }
        
        return attributedString.characters.isEmpty ? AttributedString(trimmedText) : attributedString
    }
    
    // MARK: - Code Block Support
    
    /// Extract all code blocks from markdown text
    static func extractCodeBlocks(_ text: String) -> [CodeBlockData] {
        let document = Document(parsing: text)
        var codeBlocks: [CodeBlockData] = []
        
        func findCodeBlocks(in element: Markup) {
            if let codeBlock = element as? CodeBlock {
                let language = codeBlock.language
                codeBlocks.append(CodeBlockData(code: codeBlock.code, language: language))
            }
            
            // Recursively search children
            for child in element.children {
                findCodeBlocks(in: child)
            }
        }
        
        for child in document.children {
            findCodeBlocks(in: child)
        }
        
        return codeBlocks
    }
    
    /// Parse markdown without rendering code blocks (they'll be rendered separately)
    static func parseWithoutTablesAndCodeBlocks(_ text: String) -> AttributedString {
        let trimmedText = text.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        let document = Document(parsing: trimmedText)
        
        var attributedString = AttributedString()
        for child in document.children {
            // Skip tables and code blocks - they'll be rendered as SwiftUI views
            if child is Markdown.Table || child is CodeBlock {
                continue
            }
            attributedString.append(processElement(child))
        }
        
        // Trim trailing newlines
        while attributedString.characters.last == "\n" {
            attributedString = AttributedString(attributedString.characters.dropLast())
        }
        
        return attributedString.characters.isEmpty ? AttributedString(trimmedText) : attributedString
    }
}
// MARK: - Full Text Overlay
struct FullTextOverlay: View {
    let content: [MessageContent]
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(content.enumerated()), id: \.offset) { _, messageContent in
                        MessageContentView(content: messageContent)
                    }
                }
                .padding()
            }
            .navigationTitle("Full Message")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - Tool Call Progress View

struct ToolCallProgressView: View {
    let toolCall: ToolCall
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header with tool name and spinner
            HStack(spacing: 8) {
                ProgressView()
                    .scaleEffect(0.7)
                
                Text(toolCall.name)
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.primary)
                
                Spacer()
            }
            
            // Arguments snippet
            if !toolCall.arguments.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(Array(getArgumentSnippets()), id: \.key) { item in
                        HStack(alignment: .top, spacing: 4) {
                            Text("\(item.key):")
                                .font(.caption2)
                                .fontWeight(.medium)
                                .foregroundColor(.secondary)
                            
                            Text(item.value)
                                .font(.caption2)
                                .monospaced()
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                            
                            Spacer()
                        }
                    }
                }
                .padding(.leading, 24) // Align with text after spinner
            }
            
            // Status text
            Text("executing...")
                .font(.caption2)
                .foregroundColor(.secondary)
                .padding(.leading, 24)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.systemGray6))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.blue.opacity(0.3), lineWidth: 1)
                )
        )
        .frame(maxWidth: UIScreen.main.bounds.width * 0.7)
    }
    
    private func getArgumentSnippets() -> [(key: String, value: String)] {
        let maxArgs = 3 // Show up to 3 arguments
        let maxValueLength = 40 // Truncate values longer than this
        
        return toolCall.arguments
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


// MARK: - Code Block Data Model

struct CodeBlockData: Hashable, Identifiable {
    let id = UUID()
    let code: String
    let language: String?
    
    init(code: String, language: String? = nil) {
        self.code = code
        self.language = language
    }
}

// MARK: - Code Block View Component


// Add this to SharedMessageComponents.swift

// MARK: - Syntax Highlighting
struct SyntaxHighlighter {
    static func highlight(_ code: String, language: String?) -> AttributedString {
        guard let language = language?.lowercased() else {
            return AttributedString(code)
        }
        
        var attributedString = AttributedString(code)
        
        switch language {
        case "swift":
            highlightSwift(&attributedString, code)
        case "python", "py":
            highlightPython(&attributedString, code)
        case "javascript", "js", "typescript", "ts":
            highlightJavaScript(&attributedString, code)
        case "json":
            highlightJSON(&attributedString, code)
        case "shell", "bash", "sh":
            highlightShell(&attributedString, code)
        case "ruby", "rb":
            highlightRuby(&attributedString, code)
        case "go":
            highlightGo(&attributedString, code)
        case "rust", "rs":
            highlightRust(&attributedString, code)
        case "sql":
            highlightSQL(&attributedString, code)
        case "html", "xml":
            highlightHTML(&attributedString, code)
        case "css", "scss":
            highlightCSS(&attributedString, code)
        default:
            break
        }
        
        return attributedString
    }
    
    // MARK: - Swift Highlighting
    private static func highlightSwift(_ attributedString: inout AttributedString, _ code: String) {
        let keywords = ["func", "var", "let", "if", "else", "for", "while", "return", "class", "struct", "enum", "protocol", "extension", "import", "private", "public", "internal", "static", "override", "init", "self", "super", "nil", "true", "false", "try", "catch", "throw", "async", "await", "actor", "some", "any"]
        let types = ["String", "Int", "Double", "Float", "Bool", "Array", "Dictionary", "Set", "Optional", "Any", "AnyObject", "Void", "Self"]
        
        // Highlight keywords
        for keyword in keywords {
            highlightPattern(keyword, in: &attributedString, color: .purple, bold: true)
        }
        
        // Highlight types
        for type in types {
            highlightPattern(type, in: &attributedString, color: Color(red: 0.0, green: 0.5, blue: 0.7))
        }
        
        // Highlight strings
        highlightStrings(&attributedString, code)
        
        // Highlight comments
        highlightComments(&attributedString, code)
        
        // Highlight numbers
        highlightNumbers(&attributedString, code)
        
        // Highlight function/method calls
        highlightPattern(#"(\w+)\s*\("#, in: &attributedString, color: Color(red: 0.0, green: 0.6, blue: 0.4))
    }
    
    // MARK: - Python Highlighting
    private static func highlightPython(_ attributedString: inout AttributedString, _ code: String) {
        let keywords = ["def", "class", "if", "elif", "else", "for", "while", "return", "import", "from", "as", "try", "except", "finally", "with", "lambda", "pass", "break", "continue", "global", "nonlocal", "assert", "yield", "raise", "del", "is", "not", "and", "or", "in", "True", "False", "None", "async", "await"]
        
        for keyword in keywords {
            highlightPattern(keyword, in: &attributedString, color: .purple, bold: true)
        }
        
        // Highlight built-in functions
        let builtins = ["print", "len", "range", "int", "str", "float", "list", "dict", "set", "tuple", "bool", "type", "isinstance", "open", "input", "sum", "min", "max", "abs", "round", "zip", "map", "filter"]
        for builtin in builtins {
            highlightPattern(builtin + #"(?=\()"#, in: &attributedString, color: Color(red: 0.0, green: 0.6, blue: 0.6))
        }
        
        highlightStrings(&attributedString, code)
        highlightComments(&attributedString, code, commentPrefix: "#")
        highlightNumbers(&attributedString, code)
        
        // Highlight decorators
        highlightRegex(RegexCache.pythonDecorator, in: &attributedString, color: .orange)
    }
    
    // MARK: - JavaScript Highlighting
    private static func highlightJavaScript(_ attributedString: inout AttributedString, _ code: String) {
        let keywords = ["function", "var", "let", "const", "if", "else", "for", "while", "return", "class", "extends", "new", "this", "super", "import", "export", "default", "from", "async", "await", "try", "catch", "finally", "throw", "typeof", "instanceof", "delete", "void", "null", "undefined", "true", "false", "switch", "case", "break", "continue", "do"]
        
        for keyword in keywords {
            highlightPattern(keyword, in: &attributedString, color: .purple, bold: true)
        }
        
        // Highlight built-in objects/methods
        let builtins = ["console", "document", "window", "Array", "Object", "String", "Number", "Boolean", "Promise", "Math", "Date", "JSON", "RegExp"]
        for builtin in builtins {
            highlightPattern(builtin, in: &attributedString, color: Color(red: 0.0, green: 0.5, blue: 0.7))
        }
        
        highlightStrings(&attributedString, code)
        highlightComments(&attributedString, code)
        highlightNumbers(&attributedString, code)
        
        // Highlight arrow functions
        highlightPattern("=>", in: &attributedString, color: .purple, bold: true)
    }
    
    // MARK: - Helper Functions
    private static func highlightPattern(_ pattern: String, in attributedString: inout AttributedString, color: Color, bold: Bool = false) {
        let string = String(attributedString.characters)
        
        let regex = RegexCache.wordBoundaryPattern(pattern)
        let matches = regex.matches(in: string, options: [], range: NSRange(location: 0, length: string.count))
        
        for match in matches {
            if let range = Range(match.range, in: string) {
                let startIndex = attributedString.characters.index(attributedString.startIndex, offsetBy: match.range.location)
                let endIndex = attributedString.characters.index(startIndex, offsetBy: match.range.length)
                
                attributedString[startIndex..<endIndex].foregroundColor = color
                if bold {
                    attributedString[startIndex..<endIndex].font = .system(size: 13, weight: .semibold, design: .monospaced)
                }
            }
        }
    }
    
    private static func highlightStrings(_ attributedString: inout AttributedString, _ code: String) {
        let string = String(attributedString.characters)
        
        let regexes = [
            RegexCache.doubleQuoteString,
            RegexCache.singleQuoteString,
            RegexCache.templateLiteral
        ]
        
        for regex in regexes {
            let matches = regex.matches(in: string, options: [], range: NSRange(location: 0, length: string.count))
            
            for match in matches {
                if let range = Range(match.range, in: string) {
                    let startIndex = attributedString.characters.index(attributedString.startIndex, offsetBy: match.range.location)
                    let endIndex = attributedString.characters.index(startIndex, offsetBy: match.range.length)
                    
                    attributedString[startIndex..<endIndex].foregroundColor = Color(red: 0.8, green: 0.2, blue: 0.2)
                }
            }
        }
    }
    
    private static func highlightComments(_ attributedString: inout AttributedString, _ code: String, commentPrefix: String = "//") {
        let string = String(attributedString.characters)
        
        let regexes: [NSRegularExpression] = commentPrefix == "#" 
            ? [RegexCache.hashComment, RegexCache.multiLineComment]
            : [RegexCache.singleLineComment, RegexCache.multiLineComment]
        
        for regex in regexes {
            let matches = regex.matches(in: string, options: [], range: NSRange(location: 0, length: string.count))
            
            for match in matches {
                if let range = Range(match.range, in: string) {
                    let startIndex = attributedString.characters.index(attributedString.startIndex, offsetBy: match.range.location)
                    let endIndex = attributedString.characters.index(startIndex, offsetBy: match.range.length)
                    
                    attributedString[startIndex..<endIndex].foregroundColor = Color(red: 0.4, green: 0.6, blue: 0.4)
                    attributedString[startIndex..<endIndex].font = .system(size: 13, weight: .regular, design: .monospaced).italic()
                }
            }
        }
    }
    
    private static func highlightNumbers(_ attributedString: inout AttributedString, _ code: String) {
        let string = String(attributedString.characters)
        
        let matches = RegexCache.numbers.matches(in: string, options: [], range: NSRange(location: 0, length: string.count))
        
        for match in matches {
            if let range = Range(match.range, in: string) {
                let startIndex = attributedString.characters.index(attributedString.startIndex, offsetBy: match.range.location)
                let endIndex = attributedString.characters.index(startIndex, offsetBy: match.range.length)
                
                attributedString[startIndex..<endIndex].foregroundColor = Color(red: 0.0, green: 0.5, blue: 1.0)
            }
        }
    }
    
    // MARK: - JSON Highlighting
    private static func highlightJSON(_ attributedString: inout AttributedString, _ code: String) {
        // Highlight property names
        let string = String(attributedString.characters)
        
        // Property names
        let propMatches = RegexCache.jsonProperty.matches(in: string, options: [], range: NSRange(location: 0, length: string.count))
        
        for match in propMatches {
            if match.numberOfRanges > 1 {
                let propRange = match.range(at: 1)
                if let range = Range(propRange, in: string) {
                    let startIndex = attributedString.characters.index(attributedString.startIndex, offsetBy: propRange.location - 1) // Include quote
                    let endIndex = attributedString.characters.index(startIndex, offsetBy: propRange.length + 2) // Include both quotes
                    
                    attributedString[startIndex..<endIndex].foregroundColor = Color(red: 0.0, green: 0.5, blue: 0.7)
                }
            }
        }
        
        highlightStrings(&attributedString, code)
        highlightNumbers(&attributedString, code)
        
        // Highlight booleans and null
        for keyword in ["true", "false", "null"] {
            highlightPattern(keyword, in: &attributedString, color: .purple, bold: true)
        }
    }
    
    // MARK: - SQL Highlighting
    private static func highlightSQL(_ attributedString: inout AttributedString, _ code: String) {
        let keywords = ["SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "AS", "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "AND", "OR", "NOT", "NULL", "IS", "IN", "EXISTS", "BETWEEN", "LIKE", "PRIMARY", "KEY", "FOREIGN", "REFERENCES"]
        
        // SQL keywords are case-insensitive
        for keyword in keywords {
            highlightPattern(keyword, in: &attributedString, color: .blue, bold: true)
            highlightPattern(keyword.lowercased(), in: &attributedString, color: .blue, bold: true)
        }
        
        highlightStrings(&attributedString, code)
        highlightNumbers(&attributedString, code)
        highlightComments(&attributedString, code, commentPrefix: "--")
    }
    
    // Additional language highlighters...
    private static func highlightShell(_ attributedString: inout AttributedString, _ code: String) {
        let keywords = ["if", "then", "else", "elif", "fi", "for", "do", "done", "while", "case", "esac", "function", "return", "export", "source", "alias", "echo", "cd", "ls", "grep", "sed", "awk", "curl", "wget", "chmod", "mkdir", "rm", "cp", "mv", "touch", "cat", "head", "tail", "find", "which", "sudo", "apt", "yum", "brew", "npm", "pip", "git"]
        
        for keyword in keywords {
            highlightPattern(keyword, in: &attributedString, color: .purple, bold: true)
        }
        
        highlightStrings(&attributedString, code)
        highlightComments(&attributedString, code, commentPrefix: "#")
        highlightNumbers(&attributedString, code)
        
        // Highlight variables
        highlightRegex(RegexCache.shellVariable, in: &attributedString, color: .orange)
    }
    
    private static func highlightRuby(_ attributedString: inout AttributedString, _ code: String) {
        let keywords = ["def", "class", "module", "if", "elsif", "else", "unless", "case", "when", "while", "until", "for", "do", "end", "return", "yield", "super", "self", "nil", "true", "false", "and", "or", "not", "begin", "rescue", "ensure", "retry", "break", "next", "redo", "require", "include", "extend", "attr_reader", "attr_writer", "attr_accessor"]
        
        for keyword in keywords {
            highlightPattern(keyword, in: &attributedString, color: .purple, bold: true)
        }
        
        highlightStrings(&attributedString, code)
        highlightComments(&attributedString, code, commentPrefix: "#")
        highlightNumbers(&attributedString, code)
        
        // Highlight symbols
        highlightRegex(RegexCache.rubySymbol, in: &attributedString, color: Color(red: 0.7, green: 0.4, blue: 0.0))
        
        // Highlight instance variables
        highlightRegex(RegexCache.rubyInstanceVar, in: &attributedString, color: .orange)
    }
    
    private static func highlightGo(_ attributedString: inout AttributedString, _ code: String) {
        let keywords = ["package", "import", "func", "var", "const", "type", "struct", "interface", "if", "else", "for", "range", "switch", "case", "default", "return", "break", "continue", "goto", "defer", "go", "select", "chan", "map", "nil", "true", "false"]
        
        for keyword in keywords {
            highlightPattern(keyword, in: &attributedString, color: .purple, bold: true)
        }
        
        // Highlight built-in functions
        let builtins = ["fmt", "Println", "Printf", "Sprintf", "make", "len", "cap", "append", "copy", "delete", "close", "panic", "recover", "new"]
        for builtin in builtins {
            highlightPattern(builtin, in: &attributedString, color: Color(red: 0.0, green: 0.6, blue: 0.6))
        }
        
        highlightStrings(&attributedString, code)
        highlightComments(&attributedString, code)
        highlightNumbers(&attributedString, code)
    }
    
    private static func highlightRust(_ attributedString: inout AttributedString, _ code: String) {
        let keywords = ["fn", "let", "mut", "const", "if", "else", "match", "for", "while", "loop", "return", "break", "continue", "struct", "enum", "trait", "impl", "pub", "mod", "use", "self", "super", "crate", "async", "await", "move", "ref", "as", "where", "unsafe", "static", "extern", "type", "true", "false", "Some", "None", "Ok", "Err"]
        
        for keyword in keywords {
            highlightPattern(keyword, in: &attributedString, color: .purple, bold: true)
        }
        
        // Highlight types
        let types = ["i8", "i16", "i32", "i64", "i128", "u8", "u16", "u32", "u64", "u128", "f32", "f64", "bool", "char", "str", "String", "Vec", "Option", "Result", "Box", "Rc", "Arc"]
        for type in types {
            highlightPattern(type, in: &attributedString, color: Color(red: 0.0, green: 0.5, blue: 0.7))
        }
        
        highlightStrings(&attributedString, code)
        highlightComments(&attributedString, code)
        highlightNumbers(&attributedString, code)
        
        // Highlight macros
        highlightRegex(RegexCache.rustMacro, in: &attributedString, color: .orange)
        
        // Highlight lifetimes
        highlightRegex(RegexCache.rustLifetime, in: &attributedString, color: Color(red: 0.7, green: 0.4, blue: 0.0))
    }
    
    private static func highlightHTML(_ attributedString: inout AttributedString, _ code: String) {
        let string = String(attributedString.characters)
        
        // HTML tags
        let tagMatches = RegexCache.htmlTag.matches(in: string, options: [], range: NSRange(location: 0, length: string.count))
        
        for match in tagMatches {
            if let range = Range(match.range, in: string) {
                let startIndex = attributedString.characters.index(attributedString.startIndex, offsetBy: match.range.location)
                let endIndex = attributedString.characters.index(startIndex, offsetBy: match.range.length)
                
                attributedString[startIndex..<endIndex].foregroundColor = .blue
            }
        }
        
        // Attributes
        let attrMatches = RegexCache.htmlAttribute.matches(in: string, options: [], range: NSRange(location: 0, length: string.count))
        
        for match in attrMatches {
            if let range = Range(match.range, in: string) {
                let startIndex = attributedString.characters.index(attributedString.startIndex, offsetBy: match.range.location)
                let endIndex = attributedString.characters.index(startIndex, offsetBy: match.range.length)
                
                attributedString[startIndex..<endIndex].foregroundColor = Color(red: 0.7, green: 0.4, blue: 0.0)
            }
        }
        
        highlightStrings(&attributedString, code)
        highlightComments(&attributedString, code, commentPrefix: "<!--")
    }
    
    private static func highlightCSS(_ attributedString: inout AttributedString, _ code: String) {
        let string = String(attributedString.characters)
        
        // CSS selectors
        let selectorMatches = RegexCache.cssSelector.matches(in: string, options: [], range: NSRange(location: 0, length: string.count))
        
        for match in selectorMatches {
            if let range = Range(match.range, in: string) {
                let startIndex = attributedString.characters.index(attributedString.startIndex, offsetBy: match.range.location)
                let endIndex = attributedString.characters.index(startIndex, offsetBy: match.range.length)
                
                attributedString[startIndex..<endIndex].foregroundColor = .purple
                attributedString[startIndex..<endIndex].font = .system(size: 13, weight: .semibold, design: .monospaced)
            }
        }
        
        // CSS properties
        let propMatches = RegexCache.cssProperty.matches(in: string, options: [], range: NSRange(location: 0, length: string.count))
        
        for match in propMatches {
            if let range = Range(match.range, in: string) {
                let startIndex = attributedString.characters.index(attributedString.startIndex, offsetBy: match.range.location)
                let endIndex = attributedString.characters.index(startIndex, offsetBy: match.range.length)
                
                attributedString[startIndex..<endIndex].foregroundColor = Color(red: 0.0, green: 0.5, blue: 0.7)
            }
        }
        
        highlightStrings(&attributedString, code)
        highlightNumbers(&attributedString, code)
        highlightComments(&attributedString, code, commentPrefix: "/*")
        
        // Highlight colors
        highlightRegex(RegexCache.cssColor, in: &attributedString, color: Color(red: 0.8, green: 0.2, blue: 0.2))
    }
    
    // MARK: - Helper for direct regex highlighting
    private static func highlightRegex(_ regex: NSRegularExpression, in attributedString: inout AttributedString, color: Color, bold: Bool = false) {
        let string = String(attributedString.characters)
        let matches = regex.matches(in: string, options: [], range: NSRange(location: 0, length: string.count))
        
        for match in matches {
            if let range = Range(match.range, in: string) {
                let startIndex = attributedString.characters.index(attributedString.startIndex, offsetBy: match.range.location)
                let endIndex = attributedString.characters.index(startIndex, offsetBy: match.range.length)
                
                attributedString[startIndex..<endIndex].foregroundColor = color
                if bold {
                    attributedString[startIndex..<endIndex].font = .system(size: 13, weight: .semibold, design: .monospaced)
                }
            }
        }
    }
}

struct CodeBlockView: View {
    let codeBlock: CodeBlockData
    @Environment(\.colorScheme) var colorScheme
    
    private let fontSize: CGFloat = 13
    private let padding: CGFloat = 12
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header with language badge and copy button
            HStack {
                Text((codeBlock.language?.isEmpty == false ? codeBlock.language! : "text").uppercased())
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(languageColor)
                    .cornerRadius(4)
                
                Spacer()
                
                Button(action: {
                    UIPasteboard.general.string = codeBlock.code
                }) {
                    Image(systemName: "doc.on.doc")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, padding)
            .padding(.top, 8)
            .padding(.bottom, 4)
            
            Divider()
                .background(borderColor.opacity(0.3))
            
            // Code content with syntax highlighting
            ScrollView(.horizontal, showsIndicators: true) {
                Text(SyntaxHighlighter.highlight(codeBlock.code, language: codeBlock.language))
                    .font(.system(size: fontSize, design: .monospaced))
                    .padding(padding)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            
            // Bottom divider
            Divider()
                .background(borderColor.opacity(0.3))
                .padding(.top, 8)
        }
        .background(backgroundColor)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(borderColor, lineWidth: 1)
        )
    }
    
    // Language-specific colors for badges
    private var languageColor: Color {
        guard let language = codeBlock.language?.lowercased() else {
            return .gray
        }
        
        switch language {
        case "swift":
            return Color.orange
        case "python", "py":
            return Color(red: 0.2, green: 0.4, blue: 0.8)
        case "javascript", "js":
            return Color(red: 0.95, green: 0.85, blue: 0.0)
        case "typescript", "ts":
            return Color(red: 0.0, green: 0.5, blue: 0.9)
        case "ruby", "rb":
            return Color.red
        case "rust", "rs":
            return Color(red: 0.8, green: 0.3, blue: 0.0)
        case "go":
            return Color.cyan
        case "java", "kotlin":
            return Color(red: 0.9, green: 0.5, blue: 0.0)
        case "c", "cpp", "c++":
            return Color(red: 0.0, green: 0.4, blue: 0.7)
        case "shell", "bash", "sh", "zsh":
            return Color.green
        case "json":
            return Color.purple
        case "xml", "html":
            return Color.orange
        case "css", "scss":
            return Color(red: 0.2, green: 0.5, blue: 0.9)
        case "sql":
            return Color.indigo
        case "text", "markdown", "md":
            return Color.gray
        default:
            return Color.gray
        }
    }
    
    private var backgroundColor: Color {
        // Dark theme inspired by VS Code/GitHub dark
        if colorScheme == .dark {
            return Color(red: 0.1, green: 0.11, blue: 0.13)
        } else {
            // Light theme with slight gray tint
            return Color(red: 0.98, green: 0.98, blue: 0.98)
        }
    }
    
    private var borderColor: Color {
        colorScheme == .dark
            ? Color(red: 0.2, green: 0.22, blue: 0.25)
            : Color(red: 0.85, green: 0.85, blue: 0.85)
    }
}







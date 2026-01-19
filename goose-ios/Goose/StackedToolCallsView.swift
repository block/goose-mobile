//
//  StackedToolCallsView.swift
//  Goose
//
//  Created on 10/24/25.
//  Tool call stacking with Time Machine-style carousel
//

import SwiftUI

/// Represents a tool call that can be either active or completed
enum ToolCallState: Identifiable {
    case active(id: String, timing: ToolCallWithTiming)
    case completed(id: String, completed: CompletedToolCall)
    
    // Unique ID for SwiftUI ForEach
    var id: String {
        switch self {
        case .active(let id, _):
            return id
        case .completed(let id, _):
            return id
        }
    }
    
    var toolCall: ToolCall {
        switch self {
        case .active(_, let timing):
            return timing.toolCall
        case .completed(_, let completed):
            return completed.toolCall
        }
    }
    
    var isCompleted: Bool {
        if case .completed = self {
            return true
        }
        return false
    }
}

/// Main container for tool call stacking
/// Handles single card vs stacked cards vs expanded carousel
struct StackedToolCallsView: View {
    let toolCalls: [ToolCallState]
    let showGroupInfo: Bool
    
    @State private var isExpanded = false
    @State private var selectedIndex = 0
    @Namespace private var cardAnimation
    
    // Visual constants
    private let maxVisibleCards = 3
    private let cardOffsetIncrement: CGFloat = 12
    private let cardScaleDecrement: CGFloat = 0.02
    private let cardWidth: CGFloat = UIScreen.main.bounds.width * 0.75
    private let cardSpacing: CGFloat = 16
    
    init(toolCalls: [ToolCallState], showGroupInfo: Bool = false) {
        self.toolCalls = toolCalls
        self.showGroupInfo = showGroupInfo
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if toolCalls.count == 1 {
                // Single tool call - show normal card (no stacking)
                ToolCallCardView(toolCallState: toolCalls[0])
                    
            } else {
                // Multiple tool calls - unified view with matched geometry
                ZStack {
                    if !isExpanded {
                        stackView
                    } else {
                        carouselView
                    }
                }
                .animation(.spring(response: 0.4, dampingFraction: 0.8), value: isExpanded)
            }
        }
        .padding(.bottom, 8)
        .padding(.top, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .onChange(of: toolCalls.count) { oldValue, newValue in
            // Auto-collapse if only 1 tool call remains
            if newValue <= 1 && isExpanded {
                withAnimation {
                    isExpanded = false
                }
            }
            // Reset selected index if out of bounds
            if selectedIndex >= newValue {
                selectedIndex = max(0, newValue - 1)
            }
        }
    }
    
    // MARK: - Stack View (Collapsed State)
    
    private var stackView: some View {
        ZStack(alignment: .leading) {
            // Show only the top 3 cards
            ForEach(Array(toolCalls.prefix(maxVisibleCards).enumerated()), id: \.element.id) { index, call in
                ToolCallCardView(
                    toolCallState: call,
                    onTap: {
                        isExpanded = true
                    }
                )
                    .matchedGeometryEffect(id: call.id, in: cardAnimation)
                    .offset(x: CGFloat(index) * cardOffsetIncrement)
                    .scaleEffect(1.0 - CGFloat(index) * cardScaleDecrement)
                    .shadow(
                        color: .black.opacity(0.05),
                        radius: 5 - CGFloat(index),
                        x: CGFloat(index) * 2,
                        y: 0
                    )
                    .zIndex(Double(maxVisibleCards - index))
            }
            
            // Show "+X more" indicator if there are more than 3 cards
            if toolCalls.count > maxVisibleCards {
                Text("+\(toolCalls.count - maxVisibleCards) more")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(Color(.systemBackground))
                            .shadow(color: .black.opacity(0.1), radius: 2)
                    )
                    .offset(x: CGFloat(maxVisibleCards) * cardOffsetIncrement + 8)
            }
        }
        .padding(.trailing, 16)
        .contentShape(Rectangle())
    }
    
    // MARK: - Carousel View (Expanded State)
    
    private var carouselView: some View {
        VStack(spacing: 6) {
            // Combined header with close button and navigation
            HStack {
                // Left navigation
                Button(action: {
                    if selectedIndex > 0 {
                        withAnimation {
                            selectedIndex -= 1
                        }
                    }
                }) {
                    Image(systemName: "chevron.left")
                        .foregroundColor(selectedIndex > 0 ? .primary : .gray)
                        .frame(width: 32, height: 32)
                }
                .disabled(selectedIndex == 0)
                
                Spacer()
                
                // Counter
                Text("\(selectedIndex + 1) of \(toolCalls.count)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Spacer()
                
                // Right navigation
                Button(action: {
                    if selectedIndex < toolCalls.count - 1 {
                        withAnimation {
                            selectedIndex += 1
                        }
                    }
                }) {
                    Image(systemName: "chevron.right")
                        .foregroundColor(selectedIndex < toolCalls.count - 1 ? .primary : .gray)
                        .frame(width: 32, height: 32)
                }
                .disabled(selectedIndex == toolCalls.count - 1)
                
                // Close button
                Button(action: {
                    isExpanded = false
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundColor(.secondary)
                }
                .padding(.leading, 8)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .zIndex(1)
            
            // Horizontal scrollable cards with ScrollViewReader
            GeometryReader { geometry in
                ScrollViewReader { proxy in
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: cardSpacing) {
                            ForEach(Array(toolCalls.enumerated()), id: \.element.id) { index, call in
                                ToolCallCardView(
                                    toolCallState: call,
                                    onLongPress: selectedIndex == index ? nil : {
                                        // Not centered - do nothing on long press
                                    }
                                )
                                .matchedGeometryEffect(id: call.id, in: cardAnimation)
                                .frame(width: cardWidth)
                                .scaleEffect(selectedIndex == index ? 1.0 : 0.92)
                                .opacity(selectedIndex == index ? 1.0 : 0.7)
                                .id(index)
                                .background(
                                    GeometryReader { cardGeometry in
                                        Color.clear.preference(
                                            key: CardPositionPreferenceKey.self,
                                            value: [
                                                CardPosition(
                                                    index: index,
                                                    midX: cardGeometry.frame(in: .named("scroll")).midX
                                                )
                                            ]
                                        )
                                    }
                                )
                            }
                        }
                        .padding(.horizontal, (UIScreen.main.bounds.width - cardWidth) / 2)
                    }
                    .coordinateSpace(name: "scroll")
                    .onPreferenceChange(CardPositionPreferenceKey.self) { positions in
                        // Find the card closest to center
                        let screenCenter = geometry.size.width / 2
                        if let closestCard = positions.min(by: { abs($0.midX - screenCenter) < abs($1.midX - screenCenter) }) {
                            if selectedIndex != closestCard.index {
                                selectedIndex = closestCard.index
                            }
                        }
                    }
                    .frame(height: 160)
                    .onChange(of: selectedIndex) { _, newIndex in
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            proxy.scrollTo(newIndex, anchor: .center)
                        }
                    }
                    .onAppear {
                        // Scroll to selected card on appear
                        proxy.scrollTo(selectedIndex, anchor: .center)
                    }
                }
            }
            .frame(height: 160)
        }
        .padding(.horizontal, -16)
        
    }

}

// MARK: - Preference Key for Card Positions

struct CardPosition: Equatable {
    let index: Int
    let midX: CGFloat
}

struct CardPositionPreferenceKey: PreferenceKey {
    static var defaultValue: [CardPosition] = []
    
    static func reduce(value: inout [CardPosition], nextValue: () -> [CardPosition]) {
        value.append(contentsOf: nextValue())
    }
}





/// Card view that can display both active and completed tool calls
struct ToolCallCardView: View {
    let toolCallState: ToolCallState
    var onTap: (() -> Void)? = nil
    var onLongPress: (() -> Void)? = nil
    
    @State private var navigationActive = false
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header with tool name and status indicator
            HStack(spacing: 8) {
                if toolCallState.isCompleted {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                        .font(.caption)
                } else {
                    ProgressView()
                        .scaleEffect(0.7)
                }
                
                Text(toolCallState.toolCall.name)
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.primary)
                
                Spacer()
            }
            
            // Arguments snippet
            if !toolCallState.toolCall.arguments.isEmpty {
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
                .padding(.leading, 24)
            }
            
            // Status text
            Text(toolCallState.isCompleted ? "completed" : "executing...")
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
                        .stroke(Color(.systemGray4), lineWidth: 0.5)
                )
        )
        .frame(maxWidth: UIScreen.main.bounds.width * 0.7)
        .background(
            NavigationLink(
                destination: ToolCallDetailView(toolCallState: toolCallState)
                    .environmentObject(ThemeManager.shared),
                isActive: $navigationActive
            ) {
                EmptyView()
            }
            .opacity(0)
        )
        .onTapGesture {
            if let onTap = onTap {
                onTap()
            }
        }
        .onLongPressGesture {
            if let onLongPress = onLongPress {
                onLongPress()
            } else {
                // No custom handler - trigger navigation
                navigationActive = true
            }
        }

    }
    
    private func getArgumentSnippets() -> [(key: String, value: String)] {
        let maxArgs = 3
        let maxValueLength = 40
        
        return toolCallState.toolCall.arguments
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



// MARK: - Tool Call Detail View

struct ToolCallDetailView: View {
    let toolCallState: ToolCallState
    @EnvironmentObject var themeManager: ThemeManager
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        ZStack(alignment: .top) {
            VStack(spacing: 0) {
                // Tool call details with padding for nav bar
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        // Header with tool name and status
                        HStack {
                            if toolCallState.isCompleted {
                                Image(systemName: "checkmark.circle")
                                    .foregroundColor(.green)
                                    .font(.system(size: 16))
                            } else {
                                ProgressView()
                                    .scaleEffect(0.8)
                            }
                            
                            Text(toolCallState.toolCall.name)
                                .font(.system(size: 16, weight: .medium))
                                .foregroundColor(themeManager.primaryTextColor)
                            
                            Spacer()
                            
                            Text(toolCallState.isCompleted ? "completed" : "executing...")
                                .font(.system(size: 12))
                                .foregroundColor(toolCallState.isCompleted ? .green : themeManager.secondaryTextColor)
                        }
                        .padding(.bottom, 4)
                        
                        // Arguments
                        if !toolCallState.toolCall.arguments.isEmpty {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Arguments")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(themeManager.primaryTextColor)
                                    .padding(.bottom, 4)
                                
                                ForEach(Array(toolCallState.toolCall.arguments.keys.sorted()), id: \.self) { key in
                                    if let argValue = toolCallState.toolCall.arguments[key]?.value {
                                        Text("\(key): \(String(describing: argValue))")
                                            .font(.system(size: 12, design: .monospaced))
                                            .foregroundColor(themeManager.secondaryTextColor)
                                    }
                                }
                            }
                            .padding(.bottom, 8)
                        }
                        
                        // Tool Call ID
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Tool Call ID")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(themeManager.primaryTextColor)
                                .padding(.bottom, 4)
                            
                            Text(toolCallState.id)
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundColor(themeManager.secondaryTextColor)
                                .textSelection(.enabled)
                        }
                    }
                    .padding()
                    .padding(.top, 80) // Padding for nav bar (56pt top + 24pt bottom = 80pt total height)
                }
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
                    
                    // Title (left-aligned to match main pattern)
                    HStack(spacing: 4) {
                        Text("Tool Call Details")
                            .font(.system(size: 14))
                            .foregroundColor(themeManager.primaryTextColor)
                            .lineLimit(1)
                        
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12))
                            .foregroundColor(themeManager.primaryTextColor)
                        
                        Text(toolCallState.toolCall.name)
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





// MARK: - Preview Provider

#if DEBUG
struct StackedToolCallsView_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 40) {
            Text("Single Tool Call")
                .font(.headline)
            StackedToolCallsView(toolCalls: [mockToolCall(name: "web_scrape")])
            
            Text("Two Tool Calls")
                .font(.headline)
            StackedToolCallsView(toolCalls: [
                mockToolCall(name: "web_scrape"),
                mockToolCall(name: "automation_script")
            ])
            
            Text("Three Tool Calls")
                .font(.headline)
            StackedToolCallsView(toolCalls: [
                mockToolCall(name: "web_scrape"),
                mockToolCall(name: "automation_script"),
                mockToolCall(name: "pdf_tool")
            ])
            
            Text("Four Tool Calls")
                .font(.headline)
            StackedToolCallsView(toolCalls: [
                mockToolCall(name: "web_scrape"),
                mockToolCall(name: "automation_script"),
                mockToolCall(name: "pdf_tool"),
                mockToolCall(name: "xlsx_tool")
            ])
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
    
    
    static func mockToolCall(name: String) -> ToolCallState {
        .active(
            id: UUID().uuidString,
            timing: ToolCallWithTiming(
                toolCall: ToolCall(
                    name: name,
                    arguments: [
                        "url": AnyCodable("https://example.com"),
                        "save_as": AnyCodable("text")
                    ]
                ),
                startTime: Date()
            )
        )
    }
}
#endif

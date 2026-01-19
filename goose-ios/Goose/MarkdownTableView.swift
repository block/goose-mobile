//
//  MarkdownTableView.swift
//  Goose
//
//  Table rendering component for markdown tables
//

import SwiftUI

// MARK: - Table Data Model

struct TableData: Hashable, Identifiable {
    let id = UUID()
    let headers: [String]
    let rows: [[String]]
    let columnAlignments: [TextAlignment]
    
    init(headers: [String], rows: [[String]], columnAlignments: [TextAlignment]? = nil) {
        self.headers = headers
        self.rows = rows
        // Default to leading alignment if not specified
        self.columnAlignments = columnAlignments ?? Array(repeating: .leading, count: headers.count)
    }
}

// MARK: - Table View Component

struct MarkdownTableView: View {
    let tableData: TableData
    @Environment(\.colorScheme) var colorScheme
    @State private var columnWidths: [CGFloat] = []
    
    private let cellPadding: CGFloat = 8
    private let minCellWidth: CGFloat = 80
    private let headerFontSize: CGFloat = 14
    private let cellFontSize: CGFloat = 14
    private let initialLeftPadding: CGFloat = 28 // Match text alignment (12pt + 16pt)
    
    var body: some View {
        ScrollView(.horizontal, showsIndicators: true) {
            VStack(alignment: .leading, spacing: 0) {
                // Header row
                headerRow
                
                // Divider after header
                Divider()
                    .background(Color(.systemGray3))
                
                // Data rows
                ForEach(Array(tableData.rows.enumerated()), id: \.offset) { index, row in
                    dataRow(row, index: index)
                    
                    if index < tableData.rows.count - 1 {
                        Divider()
                            .background(Color(.systemGray5))
                    }
                }
            }
            .background(backgroundColor)
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(borderColor, lineWidth: 1)
            )
            .padding(.leading, initialLeftPadding) // Initial position aligned with text
            .padding(.trailing, 16) // Some padding on the right
        }
        .onAppear {
            calculateColumnWidths()
        }
    }
    
    // MARK: - Column Width Calculation
    
    private func calculateColumnWidths() {
        var widths: [CGFloat] = Array(repeating: minCellWidth, count: tableData.headers.count)
        
        // Measure header widths
        for (index, header) in tableData.headers.enumerated() {
            let width = header.widthOfString(usingFont: UIFont.systemFont(ofSize: headerFontSize, weight: .semibold))
            widths[index] = max(widths[index], width + cellPadding * 2)
        }
        
        // Measure cell widths
        for row in tableData.rows {
            for (index, cell) in row.enumerated() where index < widths.count {
                let width = cell.widthOfString(usingFont: UIFont.systemFont(ofSize: cellFontSize))
                widths[index] = max(widths[index], width + cellPadding * 2)
            }
        }
        
        columnWidths = widths
    }
    
    // MARK: - Header Row
    
    private var headerRow: some View {
        HStack(spacing: 0) {
            ForEach(Array(tableData.headers.enumerated()), id: \.offset) { index, header in
                Text(header)
                    .font(.system(size: headerFontSize, weight: .semibold))
                    .foregroundColor(.primary)
                    .padding(cellPadding)
                    .frame(width: columnWidths.indices.contains(index) ? columnWidths[index] : minCellWidth, alignment: alignmentFor(column: index))
                    .background(headerBackgroundColor)
                
                if index < tableData.headers.count - 1 {
                    Divider()
                        .background(Color(.systemGray4))
                }
            }
        }
    }
    
    // MARK: - Data Row
    
    private func dataRow(_ row: [String], index: Int) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(row.enumerated()), id: \.offset) { colIndex, cell in
                Text(cell)
                    .font(.system(size: cellFontSize))
                    .foregroundColor(.primary)
                    .padding(cellPadding)
                    .frame(width: columnWidths.indices.contains(colIndex) ? columnWidths[colIndex] : minCellWidth, alignment: alignmentFor(column: colIndex))
                    .background(rowBackgroundColor(index: index))
                
                if colIndex < row.count - 1 {
                    Divider()
                        .background(Color(.systemGray5))
                }
            }
        }
    }
    
    // MARK: - Styling Helpers
    
    private var backgroundColor: Color {
        Color(.systemBackground)
    }
    
    private var headerBackgroundColor: Color {
        colorScheme == .dark ? Color(.systemGray5) : Color(.systemGray6)
    }
    
    private func rowBackgroundColor(index: Int) -> Color {
        // Alternating row colors for better readability
        index.isMultiple(of: 2) ? Color(.systemBackground) : Color(.systemGray6).opacity(0.3)
    }
    
    private var borderColor: Color {
        Color(.systemGray4)
    }
    
    private func alignmentFor(column: Int) -> Alignment {
        guard column < tableData.columnAlignments.count else { return .leading }
        
        switch tableData.columnAlignments[column] {
        case .leading:
            return .leading
        case .center:
            return .center
        case .trailing:
            return .trailing
        }
    }
}

// MARK: - String Extension for Width Calculation

extension String {
    func widthOfString(usingFont font: UIFont) -> CGFloat {
        let fontAttributes = [NSAttributedString.Key.font: font]
        let size = self.size(withAttributes: fontAttributes)
        return size.width
    }
}

// MARK: - Preview

#if DEBUG
struct MarkdownTableView_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 20) {
            // Simple table
            MarkdownTableView(tableData: TableData(
                headers: ["Name", "Age", "City"],
                rows: [
                    ["Alice", "30", "New York"],
                    ["Bob", "25", "San Francisco"],
                    ["Charlie", "35", "Los Angeles"]
                ]
            ))
            
            // Calendar table
            MarkdownTableView(tableData: TableData(
                headers: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"],
                rows: [
                    ["", "", "", "", "", "1", "2"],
                    ["3", "4", "5", "6", "7", "8", "9"],
                    ["10", "11", "12", "13", "14", "15", "16"],
                    ["17", "18", "19", "20", "21", "22", "23"],
                    ["24", "25", "26", "27", "28", "29", "30"],
                    ["31", "", "", "", "", "", ""]
                ],
                columnAlignments: Array(repeating: .center, count: 7)
            ))
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
#endif

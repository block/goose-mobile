//
//  MarkdownTestView.swift
//  Goose
//
//  Test view to verify markdown rendering
//

import SwiftUI

struct MarkdownTestView: View {
    @Environment(\.dismiss) private var dismiss
    
    private let testMarkdown = """
# Markdown Rendering Test

This view tests all markdown features with proper styling.

## Text Formatting

This is a paragraph with **bold text**, *italic text*, and `inline code`.

Here's a [link to example](https://example.com).

## Lists

### Unordered List
- First item
- Second item with **bold**
- Third item with `code`

### Ordered List
1. First numbered item
2. Second numbered item
3. Third numbered item

## Code Blocks with Language Styling

### Swift
```swift
func greet(name: String) -> String {
    return "Hello, \\(name)!"
}
```

### Python
```python
def calculate_sum(a, b):
    return a + b

result = calculate_sum(5, 3)
print(f"Result: {result}")
```

### JavaScript
```javascript
const fetchData = async () => {
    const response = await fetch('/api/data');
    return response.json();
};
```

### Shell
```shell
#!/bin/bash
echo "Running deployment..."
npm run build
npm run deploy
```

### JSON
```json
{
    "name": "Goose",
    "version": "1.0.0",
    "features": ["markdown", "tables", "code"]
}
```

### Ruby
```ruby
class User
  attr_accessor :name, :email
  
  def initialize(name, email)
    @name = name
    @email = email
  end
end
```

## Tables

### Simple Table
| Name | Age | City |
|------|----:|------|
| Alice | 30 | New York |
| Bob | 25 | San Francisco |
| Charlie | 35 | Los Angeles |

### Wide Table (Test Scrolling)
| Sunday | Monday | Tuesday | Wednesday | Thursday | Friday | Saturday |
|:------:|:------:|:-------:|:---------:|:--------:|:------:|:--------:|
| | | | | | 1 | 2 |
| 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| 10 | 11 | 12 | 13 | 14 | 15 | 16 |
| 17 | 18 | 19 | 20 | 21 | 22 | 23 |
| 24 | 25 | 26 | 27 | 28 | 29 | 30 |
| 31 | | | | | | |

## Mixed Content

This paragraph comes **before** a table.

| Feature | Status |
|---------|--------|
| Bold | ✅ |
| Italic | ✅ |
| Code | ✅ |
| Tables | ✅ |
| Code Blocks | ✅ |

And this paragraph comes **after** the table.

## More Languages

### TypeScript
```typescript
interface User {
    name: string;
    age: number;
}

const user: User = { name: "Alice", age: 30 };
```

### Rust
```rust
fn main() {
    let numbers = vec![1, 2, 3, 4, 5];
    let sum: i32 = numbers.iter().sum();
    println!("Sum: {}", sum);
}
```

### Go
```go
package main

import "fmt"

func main() {
    fmt.Println("Hello from Go!")
}
```

## Edge Cases

Empty cells in table:

| A | B | C |
|---|---|---|
| 1 | | 3 |
| | 2 | |
| 4 | 5 | 6 |

Code without language:
```
This is a code block without a specified language.
It should still render properly with default styling.
```
"""
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    // Assistant message context
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Assistant Message Style")
                            .font(.headline)
                            .foregroundColor(.secondary)
                        
                        MarkdownText(text: testMarkdown)
                            .padding()
                            .background(Color(.systemGray6).opacity(0.3))
                            .cornerRadius(12)
                    }
                    
                    Divider()
                        .padding(.vertical)
                    
                    // User message context
                    VStack(alignment: .leading, spacing: 12) {
                        Text("User Message Style")
                            .font(.headline)
                            .foregroundColor(.secondary)
                        
                        MarkdownText(text: "**User message** with `code` and *formatting*", isUserMessage: true)
                            .padding()
                            .background(Color.blue.opacity(0.1))
                            .cornerRadius(12)
                    }
                }
                .padding()
            }
            .navigationTitle("Markdown Test")
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

#Preview {
    MarkdownTestView()
}

# Async Tools Implementation Plan

## Overview
This document outlines the implementation plan for supporting asynchronous tool calls in the Agent system, similar to Android's `startActivityForResult`/`onActivityResult` pattern.

## Current Architecture
- `Agent.kt` processes commands using coroutines in `processCommand`
- Tool calls are executed sequentially in `executeTools` 
- Each tool call is processed synchronously via `callTool`

## Implementation Strategy

### 1. Modify `executeTools` to Handle Async Tools

```kotlin
private suspend fun executeTools(
    toolCalls: List<InternalToolCall>?,
    context: Context
): Pair<List<Map<String, String>>, List<Map<String, Double>>> {
    // Existing code...
    
    for ((index, toolCall) in toolCalls.withIndex()) {
        // Handle async tool calls differently
        val result = if (isAsyncTool(toolCall.name)) {
            callAsyncTool(toolCall, context, toolCallId)
        } else {
            callTool(toolCall, context, GoslingAccessibilityService.getInstance())
        }
        // Rest of existing code...
    }
}
```

### 2. Implement Async Tool Handling

```kotlin
private suspend fun callAsyncTool(
    toolCall: InternalToolCall,
    context: Context,
    toolCallId: String
): String {
    val resultDeferred = CompletableDeferred<String>()
    AsyncToolRegistry.register(toolCallId, resultDeferred)
    
    val intent = Intent("com.example.ACTION_TOOL_CALL")
    intent.putExtra("tool_name", toolCall.name)
    intent.putExtra("parameters", toolCall.arguments.toString())
    intent.putExtra("tool_call_id", toolCallId)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    
    return withTimeout(30000) { resultDeferred.await() }
}
```

### 3. Create Registry for Async Operations

```kotlin
object AsyncToolRegistry {
    private val registry = ConcurrentHashMap<String, CompletableDeferred<String>>()
    
    fun register(id: String, deferred: CompletableDeferred<String>) {
        registry[id] = deferred
    }
    
    fun getDeferred(id: String): CompletableDeferred<String>? = registry[id]
    
    fun unregister(id: String) {
        registry.remove(id)
    }
}
```

### 4. Handle Results in MainActivity

In `MainActivity.kt`, we need to process results from async activities and forward them to our registry:

```kotlin
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    // Handle tool call results (like in MainScreen.kt example)
    if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
        val toolCallId = data?.getStringExtra("tool_call_id")
        val result = data?.getStringExtra("result") ?: "No result provided"
        
        // Complete the deferred to resume the suspended coroutine
        toolCallId?.let {
            AsyncToolRegistry.getDeferred(it)?.complete(result)
        }
    }
}
```

## Benefits
- Seamlessly integrates with existing coroutine structure
- LLM doesn't need to know about async implementation details
- Provides timeout mechanisms to prevent indefinite waiting
- Maintains the sequential execution model where needed

## Next Steps
1. Define which tools should be async vs sync
2. Implement the `isAsyncTool()` helper function
3. Create the AsyncToolRegistry
4. Update MainActivity to handle results
5. Test with real async operations
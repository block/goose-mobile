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
    
    fun completeToolCall(id: String, result: String): Boolean {
        val deferred = registry[id] ?: return false
        deferred.complete(result)
        registry.remove(id)
        return true
    }
}
```

### 4. Handle Results in MainActivity

In `MainActivity.kt`, we process results from async activities and forward them to our registry:

```kotlin
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    
    // Handle tool call results
    val toolCallId = data?.getStringExtra("tool_call_id")
    if (toolCallId != null) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                val result = data.getStringExtra("result") ?: "No result provided"
                AsyncToolRegistry.completeToolCall(toolCallId, result)
            }
            else -> {
                val error = data.getStringExtra("error") ?: "Operation was cancelled"
                AsyncToolRegistry.completeToolCall(toolCallId, "Error: $error")
            }
        }
    }
}
```

## Implementation Status

✅ Created AsyncToolRegistry to track pending operations  
✅ Modified executeTools to detect and handle async tools  
✅ Added callAsyncTool method using CompletableDeferred  
✅ Updated MainActivity to handle activity results  
✅ Verified the build compiles successfully  

## Next Steps
1. Create actual async tools by adding entries to the `isAsyncTool()` function
2. Test the implementation with real external activities
3. Consider adding timeout handling and error recovery mechanisms
4. Update documentation for tool developers
package xyz.block.gosling.features.agent

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for tracking and managing asynchronous tool operations.
 * This allows tools to start async operations and wait for their results.
 */
object AsyncToolRegistry {
    private val registry = ConcurrentHashMap<String, CompletableDeferred<String>>()
    
    /**
     * Register a deferred result for an async tool call
     */
    fun register(id: String, deferred: CompletableDeferred<String>) {
        registry[id] = deferred
    }
    
    /**
     * Get the deferred result for a given tool call ID
     */
    fun getDeferred(id: String): CompletableDeferred<String>? = registry[id]
    
    /**
     * Remove a tool call from the registry
     */
    fun unregister(id: String) {
        registry.remove(id)
    }
    
    /**
     * Complete a deferred result for a tool call
     */
    fun completeToolCall(id: String, result: String): Boolean {
        val deferred = registry[id] ?: return false
        deferred.complete(result)
        registry.remove(id)
        return true
    }
    
    /**
     * Complete a deferred result with an exception
     */
    fun completeToolCallExceptionally(id: String, exception: Throwable): Boolean {
        val deferred = registry[id] ?: return false
        deferred.completeExceptionally(exception)
        registry.remove(id)
        return true
    }
}
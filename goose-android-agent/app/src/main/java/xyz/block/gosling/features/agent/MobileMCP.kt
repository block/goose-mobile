package xyz.block.gosling.features.agent

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// a lightweight MCP client that will discover apps that can add tools
object MobileMCP {
    private const val TAG = "MobileMCP"

    // Map to store localId -> (packageName, appName) mappings to keep names short in function calls
    private val mcpRegistry = mutableMapOf<String, Pair<String, String>>()

    // Generate a unique 3-character localId (2 letters + 1 digit)
    private fun generateLocalId(): String {
        val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val letters = (1..2).map { charPool.filter { it.isLetter() }.random() }.joinToString("")
        val digit = charPool.filter { it.isDigit() }.random()
        val localId = "$letters$digit"

        // Ensure the ID is unique
        return if (mcpRegistry.containsKey(localId)) {
            generateLocalId() // Try again if this ID is already used
        } else {
            localId
        }
    }

    // discover MCPs that are on this device.
    fun discoverMCPs(context: Context): List<Map<String, Any>> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("Don't call this from the main thread!")
        }

        val action = "com.example.ACTION_MMCP_DISCOVERY" // TODO we need a permanent name for this.
        val intent = Intent(action)
        val packageManager = context.packageManager
        val resolveInfos = packageManager.queryBroadcastReceivers(intent, 0)

        val results = mutableListOf<Map<String, Any>>()
        val latch = CountDownLatch(resolveInfos.size) // Wait for all broadcasts to finish

        for (resolveInfo in resolveInfos) {
            val componentName = ComponentName(
                resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name
            )

            val broadcastIntent = Intent(action).apply {
                component = componentName
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    Log.d(TAG, "MCP receive from $componentName")
                    val extras = getResultExtras(true)
                    Log.d(TAG, "Extras: $extras")
                    if (extras != null) {
                        val packageName = resolveInfo.activityInfo.packageName
                        val appName = resolveInfo.activityInfo.name

                        // Generate or retrieve a localId for this MCP
                        val localId =
                            mcpRegistry.entries.find { it.value == Pair(packageName, appName) }?.key
                                ?: generateLocalId().also {
                                    mcpRegistry[it] = Pair(packageName, appName)
                                }

                        val result = mapOf(
                            "packageName" to packageName,
                            "name" to appName,
                            "localId" to localId,
                            "tools" to (extras.getStringArray("tools")?.toList() ?: emptyList())
                                .associateWith { tool ->
                                    mapOf(
                                        "description" to (extras.getString("$tool.description")
                                            ?: ""),
                                        "parameters" to (extras.getString("$tool.parameters")
                                            ?: "{}")
                                    )
                                }
                        )
                        Log.d(TAG, "Results adding: $result")
                        results.add(result)
                    }
                    latch.countDown() // ✅ Signal that this receiver has finished processing
                }
            }

            Log.d(TAG, "Sending broadcast to $componentName...")

            context.sendOrderedBroadcast(
                broadcastIntent,
                null, // permission
                receiver, // Attach the receiver here
                null, // scheduler
                0, // initial code
                null, // initial data
                null // No initial extras
            )

            Log.d(TAG, "Broadcast finished for $componentName")
        }

        try {
            // Wait for all broadcasts to finish (5-second timeout to avoid hanging forever), these should be fast
            // and this is a one time wait
            latch.await(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Latch interrupted: ${e.message}")
        }

        Log.d(TAG, "Returning results: $results")

        return results
    }

    // invoke a specific tool in an external app
    fun invokeTool(
        context: Context,
        localId: String,
        tool: String,
        params: String
    ): String? {
        // Look up the packageName and appName from the registry
        val (packageName, appName) = mcpRegistry[localId] ?: run {
            Log.e(TAG, "Error: Unknown MCP ID: $localId")
            return "Error: Unknown MCP ID: $localId"
        }

        val intent = Intent("com.example.ACTION_MMCP_INVOKE").apply {
            component = ComponentName(
                packageName,
                appName
            )
            putExtra("tool", tool)
            putExtra("params", params)
        }

        var result: String? = null

        val latch = CountDownLatch(1)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                result = resultData
                Log.d(TAG, "RESULT FROM MCP ----> " + result)
                latch.countDown()
            }
        }

        context.sendOrderedBroadcast(
            intent,
            null,
            receiver,
            null,
            0,
            null,
            null
        )

        try {
            // Wait for for the app to respond, 10 seconds as why not?
            latch.await(10, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Latch interrupted: ${e.message}")
        }

        return result
    }
}

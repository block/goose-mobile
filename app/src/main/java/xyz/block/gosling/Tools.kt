package xyz.block.gosling

import android.content.Context
import android.content.Intent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tool(
    val name: String,
    val description: String,
    val parameters: Array<ParameterDef> = [],
    val requiresContext: Boolean = false,
    val requiresAccessibility: Boolean = false
)

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ParameterDef(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)


object ToolHandler {
    /**
     * Helper function to perform a gesture using the Accessibility API
     */
    private fun performGesture(gesture: GestureDescription, accessibilityService: AccessibilityService): Boolean {
        var gestureResult = false
        val countDownLatch = java.util.concurrent.CountDownLatch(1)

        accessibilityService.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                gestureResult = true
                countDownLatch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                gestureResult = false
                countDownLatch.countDown()
            }
        }, null)

        try {
            countDownLatch.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            return false
        }

        return gestureResult
    }

    private fun serializeNodeHierarchy(node: AccessibilityNodeInfo, clean: Boolean): JSONObject {
        val json = JSONObject()
        
        try {
            json.put("class", node.className)
            json.put("package", node.packageName)
            
            node.text?.toString()?.takeIf { it.isNotEmpty() }?.let {
                json.put("text", it)
            }
            
            node.contentDescription?.toString()?.takeIf { it.isNotEmpty() }?.let {
                json.put("content-desc", it)
            }

            node.viewIdResourceName?.takeIf { it.isNotEmpty() }?.let {
                json.put("resource-id", it)
            }

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            json.put("bounds", JSONObject().apply {
                put("left", bounds.left)
                put("top", bounds.top)
                put("right", bounds.right)
                put("bottom", bounds.bottom)
            })

            if (!clean) {
                json.put("clickable", node.isClickable)
                json.put("focusable", node.isFocusable)
                json.put("enabled", node.isEnabled)
                json.put("scrollable", node.isScrollable)
                json.put("editable", node.isEditable)
            }

            val children = JSONArray()
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { childNode ->
                    try {
                        children.put(serializeNodeHierarchy(childNode, clean))
                    } finally {
                        childNode.recycle()
                    }
                }
            }
            if (children.length() > 0) {
                json.put("children", children)
            }
        } catch (e: Exception) {
            json.put("error", "Failed to serialize node: ${e.message}")
        }

        return json
    }

    @Tool(
        name = "getUiHierarchy",
        description = "Get the current UI hierarchy as a JSON string. This shows all UI elements, their properties, and locations on screen.",
        parameters = [
            ParameterDef(
                name = "clean",
                type = "boolean",
                description = "If true, omits additional properties like clickable, focusable, etc. Default is false.",
                required = false
            )
        ],
        requiresAccessibility = true
    )
    fun getUiHierarchy(accessibilityService: AccessibilityService, args: JSONObject): String {
        val clean = args.optBoolean("clean", false)
        val root = JSONObject()
        
        try {
            val activeWindow = accessibilityService.rootInActiveWindow
            if (activeWindow != null) {
                try {
                    root.put("package", activeWindow.packageName)
                    root.put("class", activeWindow.className)
                    root.put("nodes", serializeNodeHierarchy(activeWindow, clean))
                } finally {
                    activeWindow.recycle()
                }
            } else {
                root.put("error", "No active window found")
            }
        } catch (e: Exception) {
            root.put("error", "Failed to get UI hierarchy: ${e.message}")
        }
        
        return root.toString(2)
    }

    @Tool(
        name = "home",
        description = "Press the home button on the device"
    )
    fun home(args: JSONObject): String {
        Runtime.getRuntime().exec(arrayOf("input", "keyevent", "KEYCODE_HOME"))
        return "Pressed home button"
    }

    @Tool(
        name = "startApp",
        description = "Start an application by its package name",
        parameters = [
            ParameterDef(
                name = "package_name",
                type = "string",
                description = "Full package name of the app to start"
            )
        ],
        requiresContext = true
    )
    fun startApp(context: Context, args: JSONObject): String {
        val packageName = args.getString("package_name")
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "Error: App $packageName not found."
            
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or 
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            
        context.startActivity(launchIntent)
        return "Started app: $packageName"
    }

    @Tool(
        name = "click",
        description = "Click at specific coordinates on the device screen",
        parameters = [
            ParameterDef(
                name = "x",
                type = "integer",
                description = "X coordinate to click"
            ),
            ParameterDef(
                name = "y",
                type = "integer",
                description = "Y coordinate to click"
            )
        ],
        requiresAccessibility = true
    )
    fun click(accessibilityService: AccessibilityService, args: JSONObject): String {
        val x = args.getInt("x")
        val y = args.getInt("y")

        val clickPath = Path()
        clickPath.moveTo(x.toFloat(), y.toFloat())

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(clickPath, 0, 50))

        val clickResult = performGesture(gestureBuilder.build(), accessibilityService)
        return if (clickResult) "Clicked at coordinates ($x, $y)" else "Failed to click at coordinates ($x, $y)"
    }

    @Tool(
        name = "swipe",
        description = "Swipe from one point to another on the screen for example to scroll",
        parameters = [
            ParameterDef(
                name = "start_x",
                type = "integer",
                description = "Starting X coordinate"
            ),
            ParameterDef(
                name = "start_y",
                type = "integer",
                description = "Starting Y coordinate"
            ),
            ParameterDef(
                name = "end_x",
                type = "integer",
                description = "Ending X coordinate"
            ),
            ParameterDef(
                name = "end_y",
                type = "integer",
                description = "Ending Y coordinate"
            ),
            ParameterDef(
                name = "duration",
                type = "integer",
                description = "Duration of swipe in milliseconds. Default is 300. Use longer duration (500+) for text selection",
                required = false
            )
        ],
        requiresAccessibility = true
    )
    fun swipe(accessibilityService: AccessibilityService, args: JSONObject): String {
        val startX = args.getInt("start_x")
        val startY = args.getInt("start_y")
        val endX = args.getInt("end_x")
        val endY = args.getInt("end_y")
        val duration = if (args.has("duration")) args.getInt("duration") else 300

        val swipePath = Path()
        swipePath.moveTo(startX.toFloat(), startY.toFloat())
        swipePath.lineTo(endX.toFloat(), endY.toFloat())

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(swipePath, 0, duration.toLong()))

        val swipeResult = performGesture(gestureBuilder.build(), accessibilityService)
        return if (swipeResult) {
            "Swiped from ($startX, $startY) to ($endX, $endY) over $duration ms"
        } else {
            "Failed to swipe from ($startX, $startY) to ($endX, $endY)"
        }
    }

    @Tool(
        name = "enter_text",
        description = "Enter text into the current text field. Text will automatically submit unless you end on ---",
        parameters = [
            ParameterDef(
                name = "text",
                type = "string",
                description = "Text to enter"
            )
        ],
        requiresAccessibility = true
    )
    fun enterText(accessibilityService: AccessibilityService, args: JSONObject): String {
        val text = args.getString("text")

        val (textToEnter, shouldSubmit) = if (text.endsWith("---")) {
            Pair(text.substring(0, text.length - 3), false)
        } else {
            Pair(text, true)
        }

        val rootNode = accessibilityService.rootInActiveWindow
            ?: return "Error: Unable to access active window"

        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return "Error: No input field is currently focused"

        if (!focusedNode.isEditable) {
            focusedNode.recycle()
            rootNode.recycle()
            return "Error: The focused element is not an editable text field"
        }

        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            textToEnter
        )
        val setTextResult = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        if (shouldSubmit && setTextResult) {
            Runtime.getRuntime().exec(arrayOf("input", "keyevent", "KEYCODE_ENTER"))
        }

        focusedNode.recycle()
        rootNode.recycle()

        return if (setTextResult) {
            "Entered text: \"$textToEnter\"${if (shouldSubmit) " and submitted" else ""}"
        } else {
            "Failed to enter text"
        }
    }

    fun getToolDefinitions(provider: ModelProvider): List<Map<String, Any>> {
        val methods = ToolHandler::class.java.methods
            .filter { it.isAnnotationPresent(Tool::class.java) }

        return when (provider) {
            ModelProvider.OPENAI -> {
                methods.map { method ->
                    val tool = method.getAnnotation(Tool::class.java)

                    val parametersMap = if (tool.parameters.isNotEmpty()) {
                        val properties = tool.parameters.associate { param ->
                            param.name to mapOf(
                                "type" to param.type,
                                "description" to param.description
                            )
                        }

                        val required = tool.parameters
                            .filter { it.required }
                            .map { it.name }

                        mapOf(
                            "type" to "object",
                            "properties" to properties,
                            "required" to required
                        )
                    } else null

                    mapOf(
                        "type" to "function",
                        "function" to mapOf(
                            "name" to tool.name,
                            "description" to tool.description
                        ).let {
                            if (parametersMap != null)
                                it + ("parameters" to parametersMap)
                            else it
                        }
                    )
                }
            }

            ModelProvider.GEMINI -> {
                // Single tool object with function_declarations array
                val functionDeclarations = methods.map { method ->
                    val tool = method.getAnnotation(Tool::class.java)

                    // For functions with no parameters, we use a different approach
                    if (tool.parameters.isEmpty()) {
                        // Just return name and description for functions with no parameters
                        mapOf(
                            "name" to tool.name,
                            "description" to tool.description
                        )
                    } else {
                        // For functions with parameters, include the parameters object
                        val properties = mutableMapOf<String, Any>()
                        val required = mutableListOf<String>()

                        tool.parameters.forEach { param ->
                            properties[param.name] = mapOf(
                                "type" to when (param.type.toLowerCase()) {
                                    "integer" -> "string" // Use string for integers
                                    "boolean" -> "boolean"
                                    "string" -> "string"
                                    "double", "float" -> "number"
                                    else -> "string"
                                },
                                "description" to param.description
                            )

                            if (param.required) {
                                required.add(param.name)
                            }
                        }

                        val result = mutableMapOf(
                            "name" to tool.name,
                            "description" to tool.description,
                            "parameters" to mapOf(
                                "type" to "object",
                                "properties" to properties
                            )
                        )

                        if (required.isNotEmpty()) {
                            (result["parameters"] as MutableMap<String, Any>)["required"] = required
                        }

                        result
                    }
                }

                // Return a list with a single tool object that contains all function declarations
                listOf(
                    mapOf(
                        "function_declarations" to functionDeclarations
                    )
                )
            }
        }
    }

    /**
     * Call a tool by name with provided arguments
     */
    fun callTool(toolCall: JSONObject, context: Context, accessibilityService: AccessibilityService?, provider: ModelProvider): String {
        val functionName: String
        val arguments = JSONObject()

        when (provider) {
            ModelProvider.OPENAI -> {
                val functionObject = toolCall.getJSONObject("function")
                functionName = functionObject.getString("name")
                val argumentsString = functionObject.optString("arguments", "{}")
                val parsedArgs = JSONObject(argumentsString)

                parsedArgs.keys().forEach { key ->
                    arguments.put(key, parsedArgs.get(key))
                }
            }

            ModelProvider.GEMINI -> {
                functionName = toolCall.getString("name")
                val paramsArray = toolCall.optJSONArray("parameters") ?: JSONArray()
                for (i in 0 until paramsArray.length()) {
                    val param = paramsArray.getJSONObject(i)
                    arguments.put(param.getString("name"), param.get("value"))
                }
            }
        }

        val toolMethod = ToolHandler::class.java.methods
            .firstOrNull {
                it.isAnnotationPresent(Tool::class.java) &&
                        it.getAnnotation(Tool::class.java).name == functionName
            }
            ?: return "Unknown tool call: $functionName"

        val toolAnnotation = toolMethod.getAnnotation(Tool::class.java)

        return try {
            if (toolAnnotation.requiresAccessibility) {
                if (accessibilityService == null) {
                    return "Accessibility service not available."
                }
                if (toolAnnotation.requiresContext) {
                    return toolMethod.invoke(ToolHandler, accessibilityService, context, arguments) as String
                }
                return toolMethod.invoke(ToolHandler, accessibilityService, arguments) as String
            }
            if (toolAnnotation.requiresContext) {
                return toolMethod.invoke(ToolHandler, context, arguments) as String
            }
            return toolMethod.invoke(ToolHandler, arguments) as String
        } catch (e: Exception) {
            "Error executing $functionName: ${e.message}"
        }
    }
}

fun getToolDefinitions(provider: ModelProvider): List<Map<String, Any>> = ToolHandler.getToolDefinitions(provider)

fun callTool(
    provider: ModelProvider,
    toolCall: JSONObject,
    context: Context,
    accessibilityService: AccessibilityService?
): String = ToolHandler.callTool(toolCall, context, accessibilityService, provider)

# mMCP SPEC (Mobile Model Context Protocol)

THIS IS VERY MUCH A WIP/DRAFT AT THIS POINT - DON'T TAKE IT TOO LITERALLY YET.

## Overview

mMCP leverages Android’s Intent system to enable apps to **advertise**, **discover**, and **invoke** modular “tools” dynamically. Each tool is defined by a JSON-like manifest that specifies:
- **Name & Description:** A unique identifier and a brief explanation of the tool.
- **Input Parameters:** A JSON schema detailing the required inputs.
- **Return Format:** A JSON schema describing the expected output.

mMCP uses two primary Intent actions:
- **Advertisement:**  
  `com.example.mMCP.ACTION_TOOL_ADVERTISE`  
  Used by apps to publish their available tools.

- **Invocation:**  
  `com.example.mMCP.ACTION_TOOL_CALL`  
  Used by apps to call an advertised tool.

This unified approach allows one or more apps to expose functionality and dynamically discover/invoke tools.

---

## Tool Manifest Format

Each tool is defined by a manifest with these properties:
- **name:** Unique identifier (e.g., `"hello_world"`).
- **description:** A brief explanation of what the tool does.
- **parameters:** A JSON schema detailing expected inputs (can be empty if none).
- **return:** A JSON schema for the expected output.

**Example Manifest:**

```json
{
  "appDescription": "HelloWorld App",
  "protocolVersion": "1.0",
  "tools": [
    {
      "name": "hello_world",
      "description": "Returns a 'Hello, World!' message.",
      "parameters": {},
      "return": {
        "message": "String"
      }
    }
  ]
}
```

1. Advertising Tools
   To advertise tools, include an activity (or service) with:

An Intent filter for the advertisement action.
A <meta-data> element containing the tool manifest.

```xml
<activity android:name=".ToolAdvertiserActivity">
    <intent-filter>
        <action android:name="com.example.mMCP.ACTION_TOOL_ADVERTISE" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <meta-data
        android:name="mMCP_manifest"
        android:value='{
          "appDescription": "HelloWorld App",
          "protocolVersion": "1.0",
          "tools": [
            {
              "name": "hello_world",
              "description": "Returns a Hello, World! message",
              "parameters": {},
              "return": {"message": "String"}
            }
          ]
        }' />
</activity>
```

2. Invoking Tools
   To invoke a tool, create an Intent with the invocation action and include extras that specify:

"tool_name": The name of the tool to call.
"parameters": A JSON string (or Bundle) with the required parameters.
Invocation Example (Kotlin):

```kotlin
// Initiate a call to the 'hello_world' tool.
val callIntent = Intent("com.example.mMCP.ACTION_TOOL_CALL").apply {
putExtra("tool_name", "hello_world")
// For hello_world, no parameters are required. Include parameters as needed.
putExtra("parameters", "{}")
}
startActivityForResult(callIntent, TOOL_CALL_REQUEST_CODE)
```


3. Handling Tool Calls (Advertiser Side)
   The same component that advertises the tool also handles its invocation. The component must:

Check the incoming Intent's action.
Verify the tool name.
Execute the tool’s logic.
Return a result.
ToolAdvertiserActivity Example (Kotlin):

```kotlin
class ToolAdvertiserActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check the incoming intent's action.
        when (intent.action) {
            "com.example.mMCP.ACTION_TOOL_CALL" -> handleToolCall(intent)
            // Optionally, add cases for other actions if needed.
        }
    }
    
    private fun handleToolCall(intent: Intent) {
        val toolName = intent.getStringExtra("tool_name")
        if (toolName == "hello_world") {
            // Execute hello_world tool logic.
            val resultJson = "{\"message\": \"Hello, World!\"}"
            val resultIntent = Intent().apply {
                putExtra("result", resultJson)
            }
            setResult(Activity.RESULT_OK, resultIntent)
        } else {
            // Tool not found; return an error.
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }
}
```


4. Discovering Available Tools
   An app can discover advertised tools by querying the PackageManager for activities that support the advertisement action. The following snippet shows how to extract tool names from the manifest meta-data.

Discovery Example (Kotlin):

```kotlin

fun discoverTools(): List<String> {
    val queryIntent = Intent("com.example.mMCP.ACTION_TOOL_ADVERTISE")
    val resolveInfoList = packageManager.queryIntentActivities(queryIntent, 0)
    val toolList = mutableListOf<String>()
    
    resolveInfoList.forEach { info ->
        val metaData = info.activityInfo.metaData?.getString("mMCP_manifest")
        metaData?.let {
            // Parse the JSON manifest to extract tool names.
            // For this example, if "hello_world" is found in the JSON, add it to the list.
            if (it.contains("\"hello_world\"")) {
                toolList.add("hello_world")
            }
        }
    }
    return toolList
}
```


5. Running a Hello World Example
   In your Android project, add the ToolAdvertiserActivity and update your AndroidManifest.xml to include the advertisement intent filter along with a meta-data element that contains the tool manifest for the hello_world tool.
   In your main (or launch) activity, modify the onCreate method so that it immediately performs two actions: first, discover available tools by querying the PackageManager for activities that advertise the mMCP intent; second, if the hello_world tool is found, invoke it using an Intent (via startActivityForResult) that specifies the tool name and any required parameters.
   Override onActivityResult to capture and log the result from the tool invocation.
   Run the app on an emulator or device; since no graphical user interface is used, all actions will execute immediately, and you can verify that the hello_world tool has been discovered and executed correctly by checking the log output for the expected JSON response (for example, a message stating "Hello, World!").





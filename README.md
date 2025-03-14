# Gosling

An open agent for android. WIP.


https://github.com/user-attachments/assets/87b73419-c27a-4368-9b60-c544e4d1b575


## Introduction

This is sort but but very not a "port" of goose to android.

Using various capabilities of android, this allows gosling to automate end to end tasks, 
either when needed, or using it as a home screen replacement.

Very much a WIP - if you want to try this - fire it up in Android Studio!

Gosling can also react to notifications that come in and spring into action on your behalf (you can set the rules).
(automatically update people on your availability in a calendar for example)
You can try any multi step task that you like, it will use the apps on hand (literally if you run it on your phone).

in `benchmarking` there are some end to end scenarios (orchestrated with goose), but they are very simple to start with to establish a baseline.

## Extending via "mobile MCP"

MCP servers have been a wonderful addition to allow agents to take on additional functions they were never
hard coded to do from other apps. 

We are proposing a variant of this here, gosling can detect other apps that provide extensions which it can then use as tools
This allows it to perform background tasks without switching to another app (if you like).

For example: looking up the weather and arranging a dog park visit with coffee could involve a few apps. Maps, weather or a google search, calendar. 
With an extension then gosling can discover a "get_weather" tool and use that in a fraction of a second (vs switching apps).

This repo: https://github.com/michaelneale/breezy-weather/ - is currently an example of a very simple app that provides an extension that gosling can discover:


You can extend gosling with any app as simply as the following:

```kotlin
class WeatherMCP : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            "com.example.ACTION_MMCP_DISCOVERY" -> {
                val bundle = Bundle().apply {
                    putStringArray("tools", arrayOf("getWeather"))
                    putString("getWeather.description", "Returns current weather for given location.")
                    putString("getWeather.parameters", "{\"location\": \"string\"}")
                }
                setResultExtras(bundle)
            }

            "com.example.ACTION_MMCP_INVOKE" -> {
                val tool = intent.getStringExtra("tool")
                val params = intent.getStringExtra("params") // JSON string

                val result = when (tool) {
                    "getWeather" -> "Weather is sunny, 25°C"
                    else -> "Unknown tool"
                }

                setResultData(result)
            }
        }
    }
}
```

and on AndroidManifest.xml:

```xml
        <receiver android:name=".WeatherMCP" android:exported="true">
            <intent-filter>
                <action android:name="com.example.ACTION_MMCP_DISCOVERY" />
                <action android:name="com.example.ACTION_MMCP_INVOKE" />
            </intent-filter>
        </receiver>
```

Gosling will discover and make use of that - note the finer details of this contract/interface will be changing of course!
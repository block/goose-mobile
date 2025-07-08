# Goose Mobile

An experimental open agent for android to do your dirty work so you can spend more time not on your phone.

![Screenshot 2025-05-05 at 12 18 07 pm](https://github.com/user-attachments/assets/22b11dd7-0adf-428b-94db-6c1afc600b3e)

Goose Mobile as your home launcher 

https://github.com/user-attachments/assets/87b73419-c27a-4368-9b60-c544e4d1b575


## Introduction
Welcome to Goose Mobile, an interpretation of [Goose](https://github.com/block/goose), but for Android, providing maximal automation of every day tasks on a personal device.

Using various capabilities of android, this allows goose mobile to automate end to end tasks, 
either when needed, or using it as a home screen replacement.

> [!CAUTION]
> This is an experimental project, and requires deep access to your device, use at own risk

Goose Mobile can also react to notifications that come in and spring into action on your behalf (you can set the rules).
(automatically update people on your availability in a calendar for example)
You can try any multi step task that you like, it will use the apps on hand.

https://github.com/user-attachments/assets/19f77d1f-9db4-415f-9f87-9b1cad4b1ec9

in `benchmarking` there are some end to end scenarios (orchestrated with goose), but they are very simple to start with to establish a baseline.

## Usage 

It is recommended to try this on a spare Android phone, or an emulator (you can run this project from the free Android Studio which will launch an emulator for you)

> [!IMPORTANT]  
> Goose Mobile is a research project, not for production use

Goose will access information that you allow it to - so if you log in to personal email, calendar, ecommerce apps etc it will be able to make use of them.

One way to get access is to use this <a href="https://appdistribution.firebase.google.com/pub/i/3f111ea732d5f7f6">firebase link</a> to get an invitation to add the device to your phone. 
This firebase distribution we will endeavour to keep up to date, but it may be out dated, or non functional at this time.

## Extending via "mobile MCP"

MCP servers have been a wonderful addition to allow agents to take on additional functions they were never
hard coded to do from other apps. 

We are proposing a variant of this here, goose mobile can detect other apps that provide extensions which it can then use as tools
This allows it to perform background tasks without switching to another app (if you like).

For example: looking up the weather and arranging a dog park visit with coffee could involve a few apps. Maps, weather or a google search, calendar. 
With an extension then goose mobile can discover a "get_weather" tool and use that in a fraction of a second (vs switching apps).

This repo: https://github.com/michaelneale/breezy-weather/ - is currently an example of a very simple app that provides an extension that goose mobile can discover:


You can extend goose mobile with any app as simply as the following:

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

Goose Mobile will discover and make use of that - note the finer details of this contract/interface will be changing of course!

## Contributing

We welcome contributions to Goose Mobile! Whether you're fixing bugs, adding new features, improving documentation, or creating extensions, your efforts are greatly appreciated.

Please see our [Contributing Guide](CONTRIBUTING.md) for detailed information on Block Open Source
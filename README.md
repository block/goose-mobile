# Goose Mobile

An experimental open agent for android to do your dirty work so you can spend more time not on your phone.

![Screenshot_20250708_124558](https://github.com/user-attachments/assets/af9d7d83-54f4-4ace-ad66-9e19f86c8fb9)

## Introduction
Welcome to Goose Mobile, an interpretation of [Goose](https://github.com/block/goose), but for Android, providing maximal automation of every day tasks on a personal device.

Using various capabilities of android, this allows goose mobile to automate end to end tasks, 
either when needed, or using it as a home screen replacement.

> [!CAUTION]
> This is an experimental project, and requires deep access to your device, use at own risk

Here it is in action: 

https://github.com/user-attachments/assets/19f77d1f-9db4-415f-9f87-9b1cad4b1ec9


Goose Mobile can also react to notifications that come in and spring into action on your behalf (you can set the rules).
(automatically update people on your availability in a calendar for example)
You can try any multi step task that you like, it will use the apps on hand.



In the `benchmarking` directory there are some end to end scenarios (orchestrated with goose), but they are very simple to start with to establish a baseline.

## Getting Started

> [!IMPORTANT]  
> Goose Mobile is a research project, not for production use. It is recommended to try this on a spare Android phone or an emulator.

Goose will access information that you allow it to - so if you log in to personal email, calendar, ecommerce apps etc it will be able to make use of them.

There are two ways to get started with Goose Mobile:

### Option 1: Install Pre-built APK via Firebase

The quickest way to try Goose Mobile is to install a pre-built version:

1. On your Android device, visit the [Firebase distribution link](https://appdistribution.firebase.google.com/pub/i/3f111ea732d5f7f6)
2. Follow the prompts to join the testing program
3. Download and install the APK when prompted


> [!NOTE]
> The Firebase distribution is updated regularly but may occasionally be outdated or non-functional. If you encounter issues, try building from source (Option 2).

### Option 2: Build from Source

For developers or those who want the latest version, you can build Goose Mobile locally:

#### Prerequisites
- [Android Studio](https://developer.android.com/studio) (latest stable version)
- JDK 17 or higher
- Git

#### Building and Running

1. **Clone the repository:**
   ```bash
   git clone https://github.com/block/goose-mobile.git
   cd goose-mobile
   ```

2. **Open in Android Studio:**
   - Launch Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned repository and select it

3. **Sync the project:**
   - Android Studio will automatically prompt you to sync Gradle files
   - If not, click "Sync Project with Gradle Files" in the toolbar

4. **Run on an emulator:**
   - Click the "Device Manager" icon in Android Studio
   - Create a new Virtual Device (recommended: Pixel 6 with API 34+)
   - Select your virtual device from the dropdown
   - Click the "Run" button (green triangle)

5. **Run on a physical device:**
   - Enable Developer Options on your Android device:
     - Go to Settings → About Phone
     - Tap "Build Number" 7 times
   - Enable USB Debugging in Developer Options
   - Connect your device via USB
   - Select your device from the dropdown in Android Studio
   - Click the "Run" button

### First Launch Setup

When you first launch Goose Mobile:

[Installation Guide](INSTALLATION.md)


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

We welcome contributions to Goose Mobile! Whether you're fixing bugs, adding new features, improving documentation, or creating extensions, your help is appreciated.

Please see our [Contributing Guide](CONTRIBUTING.md) for detailed information

## Building and distribution

This is a standard android project, using gradle. 
The build for the distribution for people to try out is done with `./gradlew assembleDebug` and then uploaded to firebase.

## Help wanted

There are many things that could be done, but some practical things: 

* run reasonable tests in CI (perhaps using goose with openai to validate results as they are rarely deterministic)
* unit tests where applicable
* a pipeline to publish main to firebase
* enhance provider to work with other models

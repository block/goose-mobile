#!/bin/bash

# Default is not to start emulators
START_EMULATORS=0

# Check if parameter is provided
if [ "$1" = "start_emulators" ]; then
    START_EMULATORS=1
fi

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

SYMBOLS=("MSFT" "GOOG" "AAPL")

COMMANDS=()
for SYMBOL in "${SYMBOLS[@]}"; do
    COMMANDS+=("do a google news search for $SYMBOL, then look up what wallstreet bets is saying about and finally go to Yahoo finance get the fundamentals. Then start a google doc to report the results")
done


if [ $START_EMULATORS -eq 1 ]; then
    echo "Starting emulators..."
    emulator -avd worker1 &
    emulator -avd worker2 &
    emulator -avd worker3 &

    # Function to check if an emulator is booted
    is_emulator_booted() {
        local device_id="$1"
        local boot_completed=$(adb -s "$device_id" shell getprop sys.boot_completed 2>/dev/null)
        if [ "$boot_completed" = "1" ]; then
            return 0  # true, booted
        else
            return 1  # false, not booted
        fi
    }

    echo "Waiting for emulators to boot (this may take a few minutes)..."
    sleep 30  # Initial wait to let emulator processes start

    # Wait for devices to appear and boot
    for i in {1..60}; do
        # Get the list of connected devices
        DEVICES=$(adb devices | grep emulator | cut -f1)
        
        if [ -z "$DEVICES" ]; then
            echo "No emulators detected yet, waiting..."
            sleep 10
            continue
        fi
        
        # Check if all emulators are booted
        ALL_BOOTED=true
        for device in $DEVICES; do
            if ! is_emulator_booted "$device"; then
                ALL_BOOTED=false
                echo "Waiting for $device to complete boot..."
                break
            fi
        done
        
        if $ALL_BOOTED; then
            echo "All emulators are booted!"
            break
        fi
        
        sleep 5
        
        # If we've waited too long, exit
        if [ $i -eq 60 ]; then
            echo "Timeout waiting for emulators to boot. Continuing anyway..."
        fi
    done

    # Install APK on all devices
    echo "Installing APK on all emulators..."
    for device in $DEVICES; do
        echo "Installing on $device..."
        adb -s $device install -r "$APK_PATH"
    done
    sleep 10

    echo "Starting the app on all emulators..."
    for device in $DEVICES; do
        echo "Starting app on $device..."
        adb -s $device shell am start -n xyz.block.goosemobile/.features.app.MainActivity
    done

   echo "All apps will start. Configure them manually"
else
    echo "Using already running emulators..."
    DEVICES=$(adb devices | grep emulator | cut -f1)
    
    if [ -z "$DEVICES" ]; then
        echo "No emulators detected! Please check if they are running."
        exit 1
    fi
    
    echo "Found devices: $DEVICES"

    echo "Running commands on emulators..."
    i=0
    for device in $DEVICES; do
        # Get command for this device (cycle through commands if more devices than commands)
        COMMAND="${COMMANDS[$i % ${#COMMANDS[@]}]}"
    
        echo "Executing on $device: '$COMMAND'"
        adb -s $device shell "am start -a xyz.block.goosemobile.EXECUTE_COMMAND -n xyz.block.goosemobile/.features.agent.DebugActivity --es command \"$COMMAND\""
    
        # Increment index for next command
        ((i++))
    done
fi

echo "All done!"

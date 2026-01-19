#!/bin/bash

# Simple script to get the assistant's last response
# Usage: ./get_assistant_reply.sh "your command"

# Check if a command was provided
if [ $# -eq 0 ]; then
  echo "Usage: $0 \"your command\""
  exit 1
fi

# Execute the command
COMMAND="$1"
echo "Executing: $COMMAND"
adb shell "am start -a xyz.block.gosling.EXECUTE_COMMAND -n xyz.block.gosling/.features.agent.DebugActivity --es command '$COMMAND'"

# Wait a bit for the command to complete
echo "Waiting for response..."

adb shell 'rm -rf /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt'

sleep 5

# Keep checking for the result file to exist

while true; do
  if adb shell '[ -f /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt ]'; then
    break
  else
    echo -n "."
    sleep 1
  fi
done


# Extract just the assistant's last response using grep and sed
echo "Assistant's response:"
adb shell "cat /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt" | grep -o '"text": "[^"]*"' | tail -1 | sed 's/"text": "\(.*\)"/\1/'

# Take a screenshot and save it
echo "Taking screenshot..."
adb shell screencap -p /sdcard/latest_command_result.png
adb pull /sdcard/latest_command_result.png ./latest_command_result.png
echo "Screenshot saved as latest_command_result.png"
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
sleep 5

# Keep checking for the result file to be updated
while true; do
  # Get the last modification time of the file
  LAST_MOD=$(adb shell "stat -c %Y /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt 2>/dev/null || echo 0")
  CURRENT_TIME=$(adb shell "date +%s")
  
  # If file was modified in the last 3 seconds, we're probably still writing to it
  if [ $((CURRENT_TIME - LAST_MOD)) -gt 3 ]; then
    break
  fi
  
  echo -n "."
  sleep 2
done

echo ""

# Extract just the assistant's last response using grep and sed
echo "Assistant's response:"
adb shell "cat /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt" | grep -o '"text": "[^"]*"' | tail -1 | sed 's/"text": "\(.*\)"/\1/'
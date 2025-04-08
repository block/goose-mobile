#!/bin/bash

# gosling_command.sh - Helper script to execute commands in Gosling and retrieve rich results
# Usage: ./gosling_command.sh "your command here"

# Check if a command was provided
if [ $# -eq 0 ]; then
  echo "Error: No command provided."
  echo "Usage: $0 \"your command here\""
  exit 1
fi

# The command to execute
COMMAND="$1"
echo "Executing command: $COMMAND"

# Execute the command in Gosling
adb shell "am start -a xyz.block.gosling.EXECUTE_COMMAND -n xyz.block.gosling/.features.agent.DebugActivity --es command '$COMMAND'"

# Wait for the command to execute and results to be written
# Start with a small delay and then check periodically
echo "Waiting for command to execute..."
sleep 3

# Function to check if the result file exists and has been updated recently
check_result_file() {
  # Get the timestamp of the latest result file
  TIMESTAMP=$(adb shell "stat -c %Y /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt 2>/dev/null || echo 0")
  
  # Get the current time
  CURRENT_TIME=$(adb shell "date +%s")
  
  # Calculate how many seconds ago the file was modified
  TIME_DIFF=$((CURRENT_TIME - TIMESTAMP))
  
  # If the file was modified in the last 10 seconds, it's likely our result
  if [ $TIME_DIFF -le 10 ]; then
    return 0  # Success
  else
    return 1  # Not found or too old
  fi
}

# Wait for the result file to be updated, with a timeout
MAX_WAIT=60  # Maximum wait time in seconds
WAITED=0
RESULT_FOUND=0

while [ $WAITED -lt $MAX_WAIT ]; do
  if check_result_file; then
    RESULT_FOUND=1
    break
  fi
  
  # Wait a bit and increment the counter
  sleep 2
  WAITED=$((WAITED + 2))
  echo -n "."
done

echo ""  # New line after the dots

if [ $RESULT_FOUND -eq 0 ]; then
  echo "Timeout waiting for results. The command may still be executing."
  echo "Check the result file manually with:"
  echo "adb shell cat /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt"
  exit 1
fi

# Get the result file size
FILE_SIZE=$(adb shell "stat -c %s /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt 2>/dev/null || echo 0")

echo "Command execution completed. Result file size: $FILE_SIZE bytes"

# Ask the user if they want to see the result
read -p "Do you want to see the result? (y/n): " SHOW_RESULT

if [[ $SHOW_RESULT == "y" || $SHOW_RESULT == "Y" ]]; then
  # Ask what format they want to see
  echo "How would you like to view the result?"
  echo "1. Full result (may be very long)"
  echo "2. Summary (first 20 lines)"
  echo "3. Assistant's response only"
  echo "4. Save to local file"
  read -p "Enter your choice (1-4): " VIEW_CHOICE
  
  case $VIEW_CHOICE in
    1)
      # Show the full result
      echo "=== FULL RESULT ==="
      adb shell "cat /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt"
      ;;
    2)
      # Show just the first 20 lines
      echo "=== RESULT SUMMARY (first 20 lines) ==="
      adb shell "head -n 20 /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt"
      ;;
    3)
      # Show just the assistant's response
      echo "=== ASSISTANT'S RESPONSE ==="
      adb shell "grep -A 15 '--- ASSISTANT ---' /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt | grep -v 'Tool Call:'"
      ;;
    4)
      # Save to a local file
      FILENAME="gosling_result_$(date +%Y%m%d_%H%M%S).txt"
      adb shell "cat /storage/emulated/0/Android/data/xyz.block.gosling/files/latest_command_result.txt" > "$FILENAME"
      echo "Result saved to: $FILENAME"
      ;;
    *)
      echo "Invalid choice. Exiting."
      ;;
  esac
fi

echo "Done."
exit 0
#!/bin/bash

# Weather query test script for Gosling App

# Fixed message for weather query
MESSAGE="What is the weather like?"

# Function to escape spaces in a string
escape_spaces() {
    # Replace each space with a backslash followed by a space
    echo "${1// /\\ }"
}

# Create a timestamp for this run
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULTS_DIR="./benchmark_results/weather_${TIMESTAMP}"
mkdir -p "${RESULTS_DIR}"

# Record the start time
START_TIME=$(date +%s.%N)
START_TIME_HUMAN=$(date)

echo "Test started at: $START_TIME_HUMAN"

# Just use hardcoded coordinates based on the XML we already analyzed
# Input field center (from previous analysis)
INPUT_X=640
INPUT_Y=2460

# Submit button center (from previous analysis)
SUBMIT_X=640
SUBMIT_Y=2664

echo "Using coordinates - Input: $INPUT_X,$INPUT_Y | Submit: $SUBMIT_X,$SUBMIT_Y"

# Click on input field
echo "Clicking input field..."
adb shell input tap $INPUT_X $INPUT_Y
sleep 1

# Type text - escape spaces in the message
ESCAPED_MESSAGE=$(escape_spaces "$MESSAGE")
echo "Typing text: $MESSAGE"
adb shell input text "$ESCAPED_MESSAGE"
sleep 1

# Click submit
echo "Clicking submit button..."
adb shell input tap $SUBMIT_X $SUBMIT_Y

# Initial wait for response to start processing
echo "Waiting for initial response..."
sleep 5

# Poll for the completion of the weather query
echo "Polling for weather response completion..."
MAX_POLLS=30  # Maximum number of polls (30 * 2 seconds = 1 minute max wait time)
POLL_COUNT=0
FOUND_COMPLETE_RESPONSE=false

# Create a function to check if the UI shows the response is complete
check_response_complete() {
    # Pull the UI hierarchy
    adb shell uiautomator dump > /dev/null 2>&1
    adb pull /sdcard/window_dump.xml /tmp/window_dump.xml > /dev/null 2>&1
    
    # Check if the UI shows a completed response
    # Look for text indicating weather information or completion
    if grep -q "weather" /tmp/window_dump.xml || grep -q "temperature" /tmp/window_dump.xml || grep -q "forecast" /tmp/window_dump.xml; then
        return 0  # Found weather-related response
    fi
    
    # Also check if the input field is active again, indicating completion
    if grep -q "edittext.*focused=\"true\"" /tmp/window_dump.xml; then
        return 0  # Input field is active again, indicating completion
    fi
    
    return 1  # Response not complete yet
}

while [ $POLL_COUNT -lt $MAX_POLLS ] && [ "$FOUND_COMPLETE_RESPONSE" = false ]; do
    POLL_COUNT=$((POLL_COUNT+1))
    echo "Poll $POLL_COUNT: Checking if response is complete..."
    
    if check_response_complete; then
        FOUND_COMPLETE_RESPONSE=true
        END_TIME=$(date +%s.%N)
        echo "Response appears to be complete on screen"
        break
    else
        echo "Response not complete yet. Waiting 2 seconds..."
        sleep 2
    fi
done

# Calculate time difference for UI response
if [ "$FOUND_COMPLETE_RESPONSE" = true ]; then
    UI_TIME_DIFF=$(echo "$END_TIME - $START_TIME" | bc)
    echo "Weather query UI response completed in $UI_TIME_DIFF seconds"
else
    echo "Warning: UI response completion not detected within timeout."
    # Continue anyway to get the session dumps
    END_TIME=$(date +%s.%N)
    UI_TIME_DIFF=$(echo "$END_TIME - $START_TIME" | bc)
fi

# Now pull the session dumps
echo "Pulling session dumps from device..."
DUMPS_DIR="${RESULTS_DIR}/session_dumps"
mkdir -p "${DUMPS_DIR}"

# Pull all session dumps
adb pull /storage/emulated/0/Android/data/xyz.block.gosling/files/session_dumps/ "${DUMPS_DIR}/" > /dev/null 2>&1

# Find weather-related JSON files
WEATHER_FILES=$(find "${DUMPS_DIR}" -name "*weather*" -type f | sort -t_ -k2 2>/dev/null)

if [ -z "$WEATHER_FILES" ]; then
    echo "No weather-related session dumps found."
    
    # List all files to debug
    echo "Available session dumps:"
    find "${DUMPS_DIR}" -type f -name "*.json" | sort
    
    # Save basic results anyway
    RESULTS_FILE="${RESULTS_DIR}/results.txt"
    {
        echo "Weather Query Test Results"
        echo "=========================="
        echo "Timestamp: $START_TIME_HUMAN"
        echo "Query: $MESSAGE"
        echo "UI Response time: $UI_TIME_DIFF seconds"
        echo "Status: No weather session dump found"
    } > "$RESULTS_FILE"
    
    echo "Results saved to $RESULTS_FILE"
    echo "WARNING: No weather session dump found but UI response may have completed."
    exit 0
fi

# Get the most recent weather file
LATEST_WEATHER_FILE=$(echo "$WEATHER_FILES" | tail -n 1)
echo "Found weather session dump: $LATEST_WEATHER_FILE"

# Extract basic information from the JSON file
echo "Extracting information from session dump..."
echo "File size: $(du -h "$LATEST_WEATHER_FILE" | cut -f1)"

# Extract token counts and timing information if available
TOKEN_INFO=$(grep -A 10 "total_input_tokens" "$LATEST_WEATHER_FILE" | head -n 10 2>/dev/null)
if [ -n "$TOKEN_INFO" ]; then
    echo "Session statistics:"
    echo "$TOKEN_INFO"
fi

# Save the results
RESULTS_FILE="${RESULTS_DIR}/results.txt"
{
    echo "Weather Query Test Results"
    echo "=========================="
    echo "Timestamp: $START_TIME_HUMAN"
    echo "Query: $MESSAGE"
    echo "UI Response time: $UI_TIME_DIFF seconds"
    echo ""
    echo "Session dump: $(basename "$LATEST_WEATHER_FILE")"
    if [ -n "$TOKEN_INFO" ]; then
        echo "Session statistics:"
        echo "$TOKEN_INFO"
    else
        echo "No session statistics available"
    fi
} > "$RESULTS_FILE"

# Take a screenshot of the final result
echo "Taking screenshot of the result..."
SCREENSHOT_FILE="${RESULTS_DIR}/weather_result.png"
adb exec-out screencap -p > "$SCREENSHOT_FILE"

echo "Results saved to $RESULTS_DIR"
echo "SUCCESS. UI response time: $UI_TIME_DIFF seconds"
exit 0
#!/bin/bash

# Beer garden maps test script for Gosling App

# Fixed message for maps query
MESSAGE="Show me the best beer garden in Berlin in maps"

# Function to escape spaces in a string
escape_spaces() {
    # Replace each space with a backslash followed by a space
    echo "${1// /\\ }"
}

# Create a timestamp for this run
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULTS_DIR="./benchmark_results/beer_garden_${TIMESTAMP}"
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

# Wait for 30 seconds instead of polling
echo "Waiting for 30 seconds..."
sleep 30

# Take a screenshot of the final result
echo "Taking screenshot of the result..."
SCREENSHOT_FILE="${RESULTS_DIR}/beer_garden_result.png"
adb exec-out screencap -p > "$SCREENSHOT_FILE"

# Record end time for timing purposes
END_TIME=$(date +%s.%N)
UI_TIME_DIFF=$(echo "$END_TIME - $START_TIME" | bc)

# Save basic results
RESULTS_FILE="${RESULTS_DIR}/results.txt"
{
    echo "Beer Garden Maps Query Test Results"
    echo "=================================="
    echo "Timestamp: $START_TIME_HUMAN"
    echo "Query: $MESSAGE"
    echo "Wait time: 30 seconds"
    echo "Total time: $UI_TIME_DIFF seconds"
} > "$RESULTS_FILE"

echo "Results saved to $RESULTS_DIR"
echo "SUCCESS. Total time: $UI_TIME_DIFF seconds"
exit 0
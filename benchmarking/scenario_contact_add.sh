#!/bin/bash

# Super Simple Gosling App Test Script

CONTACT="James Gosling"

# Default message if none provided
MESSAGE=${1:-"Add contact named $CONTACT"}

# Function to escape spaces in a string
escape_spaces() {
    # Replace each space with a backslash followed by a space
    echo "${1// /\\ }"
}

# Get UI hierarchy
#echo "Dumping UI hierarchy..."
#adb shell uiautomator dump
#adb pull /sdcard/window_dump.xml

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

# Click submit and record start time
echo "Clicking submit button..."
START_TIME=$(date +%s.%N)
adb shell input tap $SUBMIT_X $SUBMIT_Y

echo "Waiting for contact '$CONTACT' to appear in contacts database..."
CONTACT_FOUND=false
POLL_COUNT=0

# Poll for the contact every second until found or timeout
while [ "$CONTACT_FOUND" = false ] && [ $POLL_COUNT -lt 60 ]; do
    POLL_COUNT=$((POLL_COUNT+1))

    # Query the contacts database
    CONTACT_QUERY=$(adb shell content query --uri content://com.android.contacts/data)

    if echo "$CONTACT_QUERY" | grep -q "$CONTACT"; then
        CONTACT_FOUND=true
        END_TIME=$(date +%s.%N)

        # Calculate time difference
        TIME_DIFF=$(echo "$END_TIME - $START_TIME" | bc)

        echo "SUCCESS: Contact '$CONTACT' found after $TIME_DIFF seconds!"
        echo "Total polls: $POLL_COUNT"
        break
    else
        #echo "Poll $POLL_COUNT: Contact not found yet. Waiting 1 second..."
        sleep 1
    fi
done

if [ "$CONTACT_FOUND" = false ]; then
    echo "TIMEOUT: Contact '$CONTACT' was not found after 60 seconds."
    exit 1
fi

echo "Test completed successfully!"
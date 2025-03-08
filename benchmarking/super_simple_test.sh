#!/bin/bash

# Super Simple Gosling App Test Script

# Get UI hierarchy
echo "Dumping UI hierarchy..."
adb shell uiautomator dump
adb pull /sdcard/window_dump.xml

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

# Type text
echo "Typing text..."
adb shell input text "This\ is\ a\ simple\ test\ message"
sleep 1

# Click submit
echo "Clicking submit button..."
adb shell input tap $SUBMIT_X $SUBMIT_Y

echo "Test completed!"
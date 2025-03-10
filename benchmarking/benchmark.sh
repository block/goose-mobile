#!/bin/bash

# Benchmark script for running all scenario scripts and recording results
# Created: $(date)

# Source common functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/benchmark_common.sh"

echo "======================================"
echo "Gosling Benchmarking Tool"
echo "======================================"
echo

# Function to return to Gosling app screen
return_to_gosling() {
    echo "Returning to Gosling app..."
    # Press home button
    adb shell input keyevent KEYCODE_HOME
    sleep 1
    
    # Try different possible package names for the Gosling app
    echo "Attempting to launch Gosling app..."
    
    # First try with com.block.gosling
    if adb shell pm list packages | grep -q "com.block.gosling"; then
        echo "Found package: com.block.gosling"
        adb shell monkey -p com.block.gosling -c android.intent.category.LAUNCHER 1
    # Try with com.block.goose
    elif adb shell pm list packages | grep -q "com.block.goose"; then
        echo "Found package: com.block.goose"
        adb shell monkey -p com.block.goose -c android.intent.category.LAUNCHER 1
    # Try with com.gosling
    elif adb shell pm list packages | grep -q "com.gosling"; then
        echo "Found package: com.gosling"
        adb shell monkey -p com.gosling -c android.intent.category.LAUNCHER 1
    # Try with gosling
    elif adb shell pm list packages | grep -q "gosling"; then
        PACKAGE=$(adb shell pm list packages | grep "gosling" | head -1 | sed 's/package://')
        echo "Found package: $PACKAGE"
        adb shell monkey -p $PACKAGE -c android.intent.category.LAUNCHER 1
    # If we can't find it, try to list all packages and look for likely candidates
    else
        echo "Could not find Gosling package. Listing all packages:"
        adb shell pm list packages
        echo "WARNING: Could not automatically launch Gosling app. Please launch it manually."
        # Give user time to manually launch the app
        sleep 5
    fi
    
    # Wait for app to launch
    sleep 2
}

# Function to collect diagnostic data after each test
collect_diagnostics() {
    local test_dir="$1"
    echo "Collecting diagnostic data..."
    
    # Create test directory if it doesn't exist
    mkdir -p "$test_dir"
    
    # Pull session dumps
    echo "Pulling session dumps..."
    DUMPS_DIR="${test_dir}/session_dumps"
    mkdir -p "$DUMPS_DIR"
    adb pull /storage/emulated/0/Android/data/xyz.block.gosling/files/session_dumps/ "${DUMPS_DIR}/" > /dev/null 2>&1
    
    # Take screenshot
    echo "Taking screenshot..."
    adb shell screencap -p /sdcard/screen.png
    adb pull /sdcard/screen.png "${test_dir}/screenshot.png" > /dev/null 2>&1
    adb shell rm /sdcard/screen.png
    
    # Dump UI hierarchy
    echo "Dumping UI hierarchy..."
    adb shell uiautomator dump
    adb pull /sdcard/window_dump.xml "${test_dir}/window_dump.xml" > /dev/null 2>&1
    adb shell rm /sdcard/window_dump.xml
    
    echo "Diagnostic data collection complete"
}

# Create results directory if it doesn't exist
RESULTS_DIR="benchmark_results"
mkdir -p "$RESULTS_DIR"

# Results file with timestamp
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULTS_FILE="$RESULTS_DIR/benchmark_results_$TIMESTAMP.txt"

# CSV for easy parsing
CSV_FILE="$RESULTS_DIR/benchmark_results_$TIMESTAMP.csv"

# Initialize results files
echo "Benchmark Results - $(date)" > "$RESULTS_FILE"
echo "Scenario,Status,Time (seconds)" > "$CSV_FILE"

# Find all scenario scripts
SCENARIO_SCRIPTS=$(find . -name "scenario_*.sh" -type f | sort)

# Check if any scenario scripts were found
if [ -z "$SCENARIO_SCRIPTS" ]; then
    echo "No scenario scripts found!"
    exit 1
fi

echo "Found $(echo "$SCENARIO_SCRIPTS" | wc -l | tr -d ' ') scenario scripts to run"
echo

# Function to extract scenario name from filename
get_scenario_name() {
    local filename=$(basename "$1")
    echo "${filename#scenario_}" | sed 's/\.sh$//'
}

# Counter for successful scenarios
SUCCESSFUL=0
TOTAL=0

# Run each scenario script and record results
for script in $SCENARIO_SCRIPTS; do
    TOTAL=$((TOTAL+1))
    SCENARIO_NAME=$(get_scenario_name "$script")
    
    echo "======================================"
    echo "Running scenario: $SCENARIO_NAME"
    echo "======================================"
    
    # Create test-specific directory for diagnostics
    TEST_DIR="$RESULTS_DIR/${TIMESTAMP}_${SCENARIO_NAME}"
    mkdir -p "$TEST_DIR"
    
    # Make sure the script is executable
    chmod +x "$script"
    
    # Return to Gosling app before running the scenario
    return_to_gosling
    
    # Run the script and capture output
    OUTPUT=$(bash "$script" 2>&1)
    
    # Save the script output to the test directory
    echo "$OUTPUT" > "$TEST_DIR/script_output.txt"
    
    # Check if the script was successful
    if echo "$OUTPUT" | grep -q "BENCHMARK_SUCCESS"; then
        STATUS="SUCCESS"
        SUCCESSFUL=$((SUCCESSFUL+1))
        
        # Extract the time measurement if available
        if echo "$OUTPUT" | grep -q "BENCHMARK_TIME:"; then
            TIME=$(echo "$OUTPUT" | grep "BENCHMARK_TIME:" | sed 's/.*BENCHMARK_TIME: \([0-9.]*\).*/\1/')
        else
            TIME="N/A"
        fi
    else
        STATUS="FAILURE"
        TIME="N/A"
    fi
    
    # Record results
    echo "" >> "$RESULTS_FILE"
    echo "Scenario: $SCENARIO_NAME" >> "$RESULTS_FILE"
    echo "Status: $STATUS" >> "$RESULTS_FILE"
    echo "Time: $TIME seconds" >> "$RESULTS_FILE"
    echo "--------------------" >> "$RESULTS_FILE"
    
    # Add to CSV
    echo "$SCENARIO_NAME,$STATUS,$TIME" >> "$CSV_FILE"
    
    # Collect diagnostics after test completes
    collect_diagnostics "$TEST_DIR"
    
    echo "Test completed with status: $STATUS"
    echo "------------------------------------"
    echo
done

# Print summary
echo "======================================"
echo "Benchmark Summary"
echo "======================================"
echo "Total scenarios: $TOTAL"
echo "Successful: $SUCCESSFUL"
echo "Failed: $((TOTAL-SUCCESSFUL))"
echo "Success rate: $(( (SUCCESSFUL*100) / TOTAL ))%"
echo "Results saved to: $RESULTS_FILE"
echo "CSV data saved to: $CSV_FILE"
echo "Diagnostic data saved to: $RESULTS_DIR"
echo "======================================"

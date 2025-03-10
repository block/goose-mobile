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
    
    adb shell monkey -p "xyz.block.gosling" -c android.intent.category.LAUNCHER 1

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
    
    echo "Diagnostic data collection complete, look in benchmark_results"
}

# Create results directory if it doesn't exist
RESULTS_DIR="benchmark_results"
mkdir -p "$RESULTS_DIR"

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
    TEST_DIR="$RESULTS_DIR/${SCENARIO_NAME}"
    rm -rf "$TEST_DIR"
    mkdir -p "$TEST_DIR"
    
    # Make sure the script is executable
    chmod +x "$script"
    
    # Return to Gosling app before running the scenario
    return_to_gosling

    # clear out old session dumps:
     adb shell rm -rf /storage/emulated/0/Android/data/xyz.block.gosling/files/session_dumps/
    
    # Run the script and capture output
    OUTPUT=$(bash "$script" 2>&1)
    
    # Save the script output to the test directory
    echo "$OUTPUT" > "$TEST_DIR/script_output.txt"


    # Collect diagnostics after test completes
    collect_diagnostics "$TEST_DIR"
    
    # Check if goose binary is available
    if command -v goose &> /dev/null; then
        echo "Running goose analysis..."
        goose run --text "In ${TEST_DIR} look in the session_dumps dir for a json file, 'total_wall_time' has the time taken, the last json result should conclude if it thinks it did the task (can also look further up for evidence). Consider it with the screenshot.png and window_dump.xml if needed, and conclude if test was successful or not, return 'STATUS:PASS/FAIL, TIME:wall time'. Add to analysis.txt in that dir."
    else
        echo "PLEASE INSTALL GOOSE FOR MORE ANALYSIS OF RESULTS"
    fi
    
done


if command -v goose &> /dev/null; then
    echo "Running final analysis with goose..."
    goose run --text "look in for all analysis.txt files in benchmark_results, and summarise them up in a neat result.md file here with pass/fail/time".
else
    echo "PLEASE INSTALL GOOSE FOR MORE ANALYSIS OF RESULTS"
fi



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
    
    # Make sure the script is executable
    chmod +x "$script"
    
    # Return to Gosling app before running the scenario
    return_to_gosling
    
    # Run the script and capture output
    OUTPUT=$(bash "$script" 2>&1)
    
done

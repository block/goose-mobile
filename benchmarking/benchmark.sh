#!/bin/bash

# Benchmark script for running all scenario scripts and recording results
# Created: $(date)

echo "======================================"
echo "Gosling Benchmarking Tool"
echo "======================================"
echo

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
    
    # Run the script and capture output
    OUTPUT=$(bash "$script" 2>&1)
    EXIT_CODE=$?
    
    # Check if the script was successful
    if [ $EXIT_CODE -eq 0 ] && echo "$OUTPUT" | grep -q "SUCCESS"; then
        # Extract the time from the output
        TIME=$(echo "$OUTPUT" | grep -o "SUCCESS.*time: [0-9.]*" | grep -o "[0-9.]*$")
        
        if [ -z "$TIME" ]; then
            # Try alternative format
            TIME=$(echo "$OUTPUT" | grep -o "found after [0-9.]* seconds" | grep -o "[0-9.]*")
        fi
        
        if [ -n "$TIME" ]; then
            STATUS="SUCCESS"
            SUCCESSFUL=$((SUCCESSFUL+1))
            echo "✅ Success - Time: $TIME seconds"
            
            # Add to results file
            echo "- $SCENARIO_NAME: SUCCESS - $TIME seconds" >> "$RESULTS_FILE"
            echo "$SCENARIO_NAME,SUCCESS,$TIME" >> "$CSV_FILE"
        else
            STATUS="SUCCESS (time not found)"
            echo "✅ Success - Time: unknown"
            
            # Add to results file
            echo "- $SCENARIO_NAME: SUCCESS - time not found" >> "$RESULTS_FILE"
            echo "$SCENARIO_NAME,SUCCESS,N/A" >> "$CSV_FILE"
        fi
    else
        STATUS="FAILED"
        echo "❌ Failed"
        
        # Add to results file
        echo "- $SCENARIO_NAME: FAILED" >> "$RESULTS_FILE"
        echo "$SCENARIO_NAME,FAILED,N/A" >> "$CSV_FILE"
    fi
    
    echo
done

# Summary
echo "======================================"
echo "Benchmark Summary"
echo "======================================"
echo "Total scenarios: $TOTAL"
echo "Successful: $SUCCESSFUL"
echo "Failed: $((TOTAL-SUCCESSFUL))"
echo
echo "Results saved to: $RESULTS_FILE"
echo "CSV data saved to: $CSV_FILE"
echo "======================================"

# Print successful scenarios with their times
if [ $SUCCESSFUL -gt 0 ]; then
    echo
    echo "Successful Scenarios:"
    echo "------------------------------------"
    grep "SUCCESS" "$RESULTS_FILE" | sort
fi
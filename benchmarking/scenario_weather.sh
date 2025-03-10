#!/bin/bash

# Weather query test script for Gosling App

# Source common functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/benchmark_common.sh"

# Fixed message for weather query
MESSAGE="What is the weather like?"

# Input text and click submit
input_text "$MESSAGE"
click_submit

sleep 30
#!/bin/bash

# Source common functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/benchmark_common.sh"

# Input text and click submit
input_text "Show me the best beer garden in Berlin in maps"
click_submit
sleep 30

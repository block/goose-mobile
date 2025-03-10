#!/bin/bash

# Source common functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/benchmark_common.sh"

# Input text and click submit
input_and_submit "Show me the best beer garden in Berlin in maps"

sleep 30

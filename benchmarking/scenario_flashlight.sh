#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/benchmark_common.sh"

MESSAGE="Turn on the flash light"

# Input text and click submit
input_text "$MESSAGE"
click_submit

echo "should show the flashlight screen"
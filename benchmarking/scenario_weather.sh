#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/benchmark_common.sh"

MESSAGE="What is the weather like?"

# Input text and click submit
input_text "$MESSAGE"
click_submit

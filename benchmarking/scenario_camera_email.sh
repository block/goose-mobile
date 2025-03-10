#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/benchmark_common.sh"

MESSAGE="Take a picture using the camera and attach that to a new email. Save the email in drafts"

# Input text and click submit
input_text "$MESSAGE"
click_submit

sleep 30

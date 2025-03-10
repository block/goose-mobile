#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/benchmark_common.sh"

# Generate a random contact name
FIRST_NAMES=("James" "Emma" "Michael" "Olivia" "William" "Sophia" "John" "Isabella" "David" "Charlotte")
LAST_NAMES=("Smith" "Johnson" "Williams" "Brown" "Jones" "Miller" "Davis" "Garcia" "Rodriguez" "Wilson")

RANDOM_FIRST=${FIRST_NAMES[$RANDOM % ${#FIRST_NAMES[@]}]}
RANDOM_LAST=${LAST_NAMES[$RANDOM % ${#LAST_NAMES[@]}]}
CONTACT="$RANDOM_FIRST $RANDOM_LAST"

MESSAGE=${1:-"Add contact named $CONTACT"}

# Input text and click submit
input_text "$MESSAGE"
click_submit

sleep 30
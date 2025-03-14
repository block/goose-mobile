#!/bin/bash
while true; do
    adb pull /storage/emulated/0/Android/data/xyz.block.gosling/files/session_dumps/ .
    adb shell settings put secure enabled_accessibility_services xyz.block.gosling/.features.accessibility.GoslingAccessibilityService
    sleep 1
done

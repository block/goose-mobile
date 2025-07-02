#!/bin/bash
while true; do
    adb pull /storage/emulated/0/Android/data/xyz.block.goosemobile/files/session_dumps/ .
    adb shell settings put secure enabled_accessibility_services xyz.block.goosemobile/.features.accessibility.GooseMobileAccessibilityService
    sleep 1
done

#!/bin/bash
while true; do
    adb pull /storage/emulated/0/Android/data/xyz.block.gosling/files/session_dumps/ ./session_dumps/
    sleep 1
done

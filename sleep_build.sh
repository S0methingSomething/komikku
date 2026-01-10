#!/bin/bash
termux-wake-lock

# Settings
LOG_FILE="build_log.txt"
BT_PATH="/data/data/com.termux/files/usr/opt/android-sdk/build-tools/35.0.0"

echo "--- Script Started: $(date) ---" > "$LOG_FILE"

# 2. Update and Install
echo "--- Updating cmdline-tools & installing build-tools 35 ---" >> "$LOG_FILE"
yes | sdkmanager "cmdline-tools;latest" >> "$LOG_FILE" 2>&1
yes | sdkmanager "build-tools;35.0.0" >> "$LOG_FILE" 2>&1

# 3. Verify & Build (Fail if 35 is missing)
if [ -d "$BT_PATH" ] && [ "$(ls -A "$BT_PATH")" ]; then
    echo "--- Verified: Build Tools 35 installed. Starting Gradle... ---" >> "$LOG_FILE"
    ./gradlew assembleDebug -Pandroid.injected.build.abi.filters=arm64-v8a >> "$LOG_FILE" 2>&1
else
    echo "!!! FATAL ERROR: Build Tools 35.0.0 failed to install or is empty. Build Aborted. !!!" >> "$LOG_FILE"
fi

termux-wake-unlock
echo "Done. Check build_log.txt"

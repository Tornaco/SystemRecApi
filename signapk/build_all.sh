#!/usr/bin/env bash
echo "Building app."
./gradlew clean
./gradlew app:assembleRelease

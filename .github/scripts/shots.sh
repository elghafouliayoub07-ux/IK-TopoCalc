#!/bin/bash
set -x
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.topocalcbtp.${TOUR:-ScreenshotTour} com.topocalcbtp.test/androidx.test.runner.AndroidJUnitRunner | tee instrument.txt
mkdir -p shots
adb exec-out run-as com.topocalcbtp tar -cf - -C files/shots . > shots.tar
tar -xf shots.tar -C shots
cp instrument.txt shots/ || true
ls -la shots
cat shots/log.txt || true

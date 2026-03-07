export JAVA_HOME := "/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Build debug APK
build:
    ./gradlew assembleDebug

# Install debug APK on connected emulator/device
install:
    ./gradlew installDebug

# Build and install
deploy: build install

# Run lint checks
lint:
    ./gradlew lintDebug

# Clean build artifacts
clean:
    ./gradlew clean

# Show connected devices
devices:
    ~/Library/Android/sdk/platform-tools/adb devices

# Clear message cache without losing pairing data
clear-cache:
    adb shell am force-stop rip.build.courier
    adb shell run-as rip.build.courier rm -f databases/courier_cache.db databases/courier_cache.db-shm databases/courier_cache.db-wal

# View app logs (filtered to our package)
logs:
    ~/Library/Android/sdk/platform-tools/adb logcat --pid=$(~/Library/Android/sdk/platform-tools/adb shell pidof rip.build.courier)

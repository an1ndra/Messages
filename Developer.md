# Developer Guide

This guide explains how to set up and develop the Messages app locally **without Android Studio**.

## Prerequisites

### System Requirements

- **OS**: Linux (Ubuntu 20.04+, Fedora 36+), macOS (12+), or Windows with WSL2
- **RAM**: 8GB minimum, 16GB recommended
- **Disk**: 20GB free space for SDK + emulator + build artifacts
- **CPU**: x86_64 with virtualization support (Intel VT-x or AMD-V)

### Required Software

| Tool | Version | Purpose |
|------|---------|---------|
| JDK | 21 LTS | Build system (Gradle) |
| Android SDK | API 35 | Compile app |
| Android Emulator | Latest | Test app |
| Gradle | 9.2.1 | Build automation (included via wrapper) |

## Step-by-Step Setup

### 1. Install JDK 21

#### Linux (Ubuntu/Debian)

```bash
# Using Adoptium (recommended)
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo apt-key add -
echo "deb https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install temurin-21-jdk

# Verify installation
java -version
```

#### Linux (Fedora/RHEL)

```bash
# Using SDKMAN (works on all distros)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.4-tem

# Verify
java -version
```

#### macOS

```bash
# Using Homebrew
brew install --cask temurin21

# Or using SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.4-tem
```

#### Windows (WSL2)

```bash
# Inside WSL2 Ubuntu
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.4-tem
```

### 2. Install Android SDK (No Android Studio)

#### Download Command-Line Tools

```bash
# Create SDK directory
mkdir -p ~/android/cmdline-tools

# Download latest command-line tools (check https://developer.android.com/studio#command-line-tools for updates)
# Linux:
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708.zip -O /tmp/cmdline-tools.zip

# macOS (Intel):
# wget https://dl.google.com/android/repository/commandlinetools-mac-11076708.zip -O /tmp/cmdline-tools.zip

# macOS (Apple Silicon):
# wget https://dl.google.com/android/repository/commandlinetools-mac_arm64-11076708.zip -O /tmp/cmdline-tools.zip

# Extract and organize
unzip /tmp/cmdline-tools.zip -d ~/android/cmdline-tools
mv ~/android/cmdline-tools/cmdline-tools ~/android/cmdline-tools/latest
rm /tmp/cmdline-tools.zip
```

#### Set Environment Variables

Add to `~/.bashrc` or `~/.zshrc`:

```bash
# Android SDK
export ANDROID_HOME=$HOME/android
export ANDROID_SDK_ROOT=$HOME/android
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/emulator

# JDK (if using SDKMAN)
export JAVA_HOME=$HOME/.sdkman/candidates/java/current

# Gradle (project-specific)
export PATH=$PATH:$HOME/tools/gradle-9.2.1/bin
```

Reload your shell:

```bash
source ~/.bashrc  # or source ~/.zshrc
```

#### Accept Licenses and Install SDK

```bash
# Accept all licenses
yes | sdkmanager --licenses

# Install required SDK components
sdkmanager \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "platform-tools" \
  "emulator" \
  "system-images;android-35;google_apis;x86_64"

# Verify installation
sdkmanager --list_installed
```

### 3. Install Gradle (Optional)

The project includes a Gradle wrapper, but for faster builds you can install Gradle globally:

```bash
# Download Gradle 9.2.1
mkdir -p ~/tools
wget https://services.gradle.org/distributions/gradle-9.2.1-bin.zip -O /tmp/gradle.zip
unzip /tmp/gradle.zip -d ~/tools
rm /tmp/gradle.zip

# Verify
~/tools/gradle-9.2.1/bin/gradle --version
```

### 4. Set Up Android Emulator

#### Create AVD (Android Virtual Device)

```bash
# List available system images
sdkmanager --list | grep "system-images;android-35"

# Create AVD
# Format: avdmanager create avd -n <name> -k <system-image> -d <device>
avdmanager create avd \
  -n "Pixel_7_API_35" \
  -k "system-images;android-35;google_apis;x86_64" \
  -d "pixel_7"

# Verify AVD creation
avdmanager list avd
```

#### Configure AVD (Optional)

Edit `~/.android/avd/Pixel_7_API_35.avd/config.ini`:

```ini
# Display
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420

# Performance
hw.ramSize=2048
vm.heapSize=256

# Storage
disk.dataPartition.size=2048M
hw.sdCard.size=512M

# Navigation (gesture or 3-button)
hw.mainKeys=no
hw.trackBall=no
```

#### Start Emulator

```bash
# Start emulator in background
emulator -avd Pixel_7_API_35 -no-window -no-audio &

# Or start with GUI (requires display)
emulator -avd Pixel_7_API_35

# Wait for emulator to boot (check status)
adb wait-for-device
adb shell getprop sys.boot_completed
# Should return "1" when ready
```

### 5. Clone and Build the Project

```bash
# Clone repository
git clone https://github.com/anindra/Messages.git
cd Messages

# Make scripts executable
chmod +x scripts/*.sh

# Build debug APK
./gradlew assembleDebug

# Or using project's Gradle
~/tools/gradle-9.2.1/bin/gradle assembleDebug --no-daemon
```

### 6. Install and Run

```bash
# Install on emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.anindra.messages/.MainActivity

# Set as default SMS app (required for full functionality)
adb shell cmd role add-role-holder android.app.role.SMS com.anindra.messages
```

## Development Environment

### Complete Environment Setup Script

Save as `setup-dev.sh` and run:

```bash
#!/bin/bash
set -e

echo "=== Setting up Messages development environment ==="

# Create directories
mkdir -p ~/tools ~/android/cmdline-tools

# Install SDKMAN
if [ ! -d "$HOME/.sdkman" ]; then
    curl -s "https://get.sdkman.io" | bash
    source "$HOME/.sdkman/bin/sdkman-init.sh"
fi

# Install JDK 21
sdk install java 21.0.4-tem 2>/dev/null || true

# Download Android command-line tools
if [ ! -d "$HOME/android/cmdline-tools/latest" ]; then
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708.zip -O /tmp/cmdline-tools.zip
    unzip -q /tmp/cmdline-tools.zip -d ~/android/cmdline-tools
    mv ~/android/cmdline-tools/cmdline-tools ~/android/cmdline-tools/latest
    rm /tmp/cmdline-tools.zip
fi

# Set environment
export ANDROID_HOME=$HOME/android
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

# Accept licenses and install SDK
yes | sdkmanager --licenses > /dev/null 2>&1
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools" "emulator" "system-images;android-35;google_apis;x86_64" > /dev/null 2>&1

# Create AVD
if ! avdmanager list avd | grep -q "Pixel_7_API_35"; then
    avdmanager create avd -n "Pixel_7_API_35" -k "system-images;android-35;google_apis;x86_64" -d "pixel_7"
fi

# Install Gradle
if [ ! -d "$HOME/tools/gradle-9.2.1" ]; then
    wget -q https://services.gradle.org/distributions/gradle-9.2.1-bin.zip -O /tmp/gradle.zip
    unzip -q /tmp/gradle.zip -d ~/tools
    rm /tmp/gradle.zip
fi

echo "=== Setup complete! ==="
echo "Add to ~/.bashrc or ~/.zshrc:"
echo "  export ANDROID_HOME=\$HOME/android"
echo "  export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/emulator"
echo "  export JAVA_HOME=\$HOME/.sdkman/candidates/java/current"
```

### IDE Setup (Optional)

If you prefer an IDE (without Android Studio):

#### VS Code

1. Install [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
2. Install [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle)
3. Open the `Messages` folder
4. VS Code will auto-detect the project

#### IntelliJ IDEA Community Edition

1. Download from [jetbrains.com](https://www.jetbrains.com/idea/download/)
2. Open project folder
3. Import Gradle project when prompted
4. Set JDK to 21 in Project Settings

## Project Structure

```
Messages/
├── app/
│   ├── src/main/
│   │   ├── java/com/anindra/messages/
│   │   │   ├── MessagesApplication.kt     # App entry point
│   │   │   ├── MainActivity.kt            # Single activity + navigation
│   │   │   ├── AppViewModel.kt            # Main ViewModel
│   │   │   ├── data/                      # Database + models
│   │   │   │   ├── Repository.kt          # SQLite + Flow queries
│   │   │   │   ├── Models.kt              # Data classes
│   │   │   │   ├── SettingsStore.kt       # SharedPreferences
│   │   │   │   └── DemoData.kt            # Demo conversations
│   │   │   ├── sms/                       # SMS handling
│   │   │   │   ├── SmsReceiver.kt         # Incoming SMS
│   │   │   │   ├── SmsSupport.kt          # Send + notifications
│   │   │   │   └── ScheduledMessageSender.kt
│   │   │   └── ui/                        # Compose screens
│   │   │       ├── ConversationsScreen.kt # Home list
│   │   │       ├── ChatScreen.kt          # Message view
│   │   │       ├── SettingsScreen.kt      # App settings
│   │   │       └── Components.kt          # Shared composables
│   │   └── res/                           # Resources
│   └── build.gradle.kts                   # App dependencies
├── scripts/                               # Test automation
├── screenshots/                           # App screenshots
├── AGENTS.md                             # AI agent instructions
├── TODO.md                               # Task tracking
└── README.md                             # Project overview
```

## Architecture

### Single-Activity Architecture

The app uses a single `MainActivity` with Compose Navigation:

```kotlin
// MainActivity.kt
setContent {
    when (navRoute) {
        "list" -> ConversationsScreen(...)
        "chat" -> ChatScreen(...)
        "settings" -> SettingsScreen(...)
    }
}
```

### Reactive Data Flow

```
SQLite DB → Flow queries → Repository → ViewModel → Compose UI
```

All data updates automatically when the database changes.

### Key Patterns

- **State Hoisting**: UI state lifted to ViewModel
- **CollectAsState**: Flow collection in Compose
- **Side Effects**: LaunchedEffect for async operations
- **Dependency Injection**: Manual via Application class

## Testing

### Manual Testing

Use the test scripts in `scripts/`:

```bash
# Run all tests
scripts/run-all-tests.sh

# Test specific feature
scripts/test-back-nav.sh      # Back navigation
scripts/test-chat-menu.sh     # Chat menu options
scripts/test-message-lock.sh  # Message locking
```

### Test Scripts Environment

Scripts assume:
- **Emulator**: `emulator-5554`
- **Resolution**: 1080x2400 @ 420dpi
- **Shell**: Bash (not zsh)

### Automated UI Testing

For comprehensive testing, use the provided test scripts as examples for:
- UI navigation
- Gesture simulation
- Screenshot capture
- State verification

## Building for Release

### Debug Build

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build

```bash
# Requires release keystore in project root
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### Signing

Release builds are signed automatically when `release.keystore` exists in the
project root **and** both password environment variables are set (never commit
the keystore or passwords):

```bash
keytool -genkey -v -keystore release.keystore \
  -alias messages -keyalg RSA -keysize 2048 -validity 10000

export KEYSTORE_PASSWORD=your_password
export KEY_PASSWORD=your_password
./gradlew assembleRelease
```

Without the keystore/env vars, `assembleRelease` produces an unsigned APK
(`app-release-unsigned.apk`) — this is what F-Droid's build server expects.

## Common Development Tasks

### Adding a New Feature

1. Create feature branch: `git checkout -b feature/new-feature`
2. Add data model in `data/Models.kt` if needed
3. Add database column/migration in `data/Repository.kt`
4. Add UI in `ui/` directory
5. Update `TODO.md` with completion status
6. Create test script in `scripts/`
7. Build and test: `./gradlew assembleDebug`

### Database Migrations

When modifying the database:

1. Increment version in `Db` class (currently v10)
2. Add migration in `onUpgrade()`
3. Test with existing data (don't clear app data)
4. Update `TODO.md` with migration notes

### UI Changes

Follow Material 3 guidelines:
- Use `MaterialTheme.colorScheme.*` for colors
- Never hardcode hex colors (except avatar palette)
- Test in both light and dark modes
- Maintain accessibility (contrast ratios)

## Troubleshooting

### Build Errors

```bash
# Clean and rebuild
./gradlew clean assembleDebug

# Check for dependency conflicts
./gradlew app:dependencies
```

### Emulator Issues

```bash
# Restart emulator
adb emu kill
emulator -avd Pixel_7_API_35

# Clear app data
adb shell pm clear com.anindra.messages

# Check emulator status
adb devices
adb shell getprop ro.build.version.sdk
```

### Permission Errors

```bash
# Grant all permissions
scripts/grant-permissions.sh

# Reset to see permission dialogs
scripts/reset-permissions.sh
```

### SDK Manager Issues

```bash
# Update SDK tools
sdkmanager --update

# Reinstall specific package
sdkmanager --install "platforms;android-35"

# Check installed packages
sdkmanager --list_installed
```

## Code Style

- **Kotlin**: Follow official conventions
- **Compose**: Prefer readability over brevity
- **Comments**: Only for non-obvious logic
- **Naming**: Descriptive, follow Android conventions

## Performance

- **Database**: Use Flow for reactive updates
- **Images**: Lazy loading with Coil (if added)
- **Lists**: LazyColumn with keys for stable animations
- **State**: Minimize recompositions

## Security

- No internet permission (offline-only)
- Local database only (no cloud sync)
- Biometric lock for sensitive messages
- No analytics or tracking

## Contributing

1. Fork the repository
2. Create feature branch
3. Commit changes
4. Push to branch
5. Create Pull Request

Follow existing code style and add tests where applicable.

## License

This project is licensed under GPL-3.0 - see [LICENSE](LICENSE) for details.

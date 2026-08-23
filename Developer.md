# Developer Guide

This guide explains how to set up and develop the Messages app locally.

## Prerequisites

### Required Software

| Tool | Version | Installation |
|------|---------|--------------|
| JDK | 21 LTS | [Adoptium](https://adoptium.net/) or SDKMAN |
| Android SDK | API 35 | [Android Studio](https://developer.android.com/studio) or command-line tools |
| Gradle | 9.2.1 | Included via wrapper (`./gradlew`) |

### System Requirements

- **OS**: Linux, macOS, or Windows with WSL2
- **RAM**: 8GB minimum, 16GB recommended
- **Disk**: 10GB free space for SDK + build artifacts

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/anindra/Messages.git
cd Messages
```

### 2. Set Up Android SDK

If you don't have Android Studio installed:

```bash
# Download command-line tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708.zip
mkdir -p ~/android/cmdline-tools
unzip commandlinetools-linux-11076708.zip -d ~/android/cmdline-tools
mv ~/android/cmdline-tools/cmdline-tools ~/android/cmdline-tools/latest

# Set environment variables
export ANDROID_HOME=~/android
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Accept licenses and install SDK
sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0"
```

### 3. Build the App

```bash
# Using the project's Gradle wrapper
./gradlew assembleDebug

# Or using the pinned Gradle version (recommended)
~/tools/gradle-9.2.1/bin/gradle assembleDebug --no-daemon
```

### 4. Install on Emulator

```bash
# Start emulator (if not already running)
~/android/emulator/emulator -avd Pixel_7_API_34

# Install the app
~/android/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Development Environment

### Recommended Setup

For the best development experience, use the exact toolchain specified in the project:

```bash
# Set up in ~/.bashrc or ~/.zshrc
export JAVA_HOME=/home/$USER/tools/jdk21
export ANDROID_HOME=/home/$USER/android
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

# Use project's Gradle wrapper
alias gradle="~/tools/gradle-9.2.1/bin/gradle"
```

### Emulator Configuration

The project is tested on **emulator-5554** with:
- **Resolution**: 1080x2400
- **DPI**: 420
- **Navigation**: Gesture (default) or 3-button

To create a matching emulator in Android Studio:
1. Tools → Device Manager → Create Virtual Device
2. Select "Pixel 7" or similar
3. System Image: API 34 (Google APIs)
4. Advanced Settings:
   - RAM: 2048MB
   - VM Heap: 256MB
   - Internal Storage: 2048MB
   - SD Card: 512MB

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
└── README.md                             # This file
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

For release builds, place `release.keystore` in the project root:

```bash
keytool -genkey -v -keystore release.keystore \
  -alias messages -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass messages123
```

Update `app/build.gradle.kts` with your keystore passwords or use environment variables:

```bash
export KEYSTORE_PASSWORD=your_password
export KEY_PASSWORD=your_password
```

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
~/android/platform-tools/adb emu kill
~/android/emulator/emulator -avd Pixel_7_API_34

# Clear app data
~/android/platform-tools/adb shell pm clear com.anindra.messages
```

### Permission Errors

```bash
# Grant all permissions
scripts/grant-permissions.sh

# Reset to see permission dialogs
scripts/reset-permissions.sh
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

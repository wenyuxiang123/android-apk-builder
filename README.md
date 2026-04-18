# DeltaAim

Android Auto-Aim Application using Shizuku + screencap

## Features

- **Shizuku Integration**: Uses Shizuku for privileged screenshot access without root
- **Screen Capture**: Real-time screen capture via `screencap` command
- **Color Detection**: Detects targets based on customizable color matching
- **Accessibility Service**: Gesture simulation for auto-aim functionality
- **Preview**: Real-time screenshot preview

## Architecture

```
com.deltaaim/
├── DeltaAimApplication.java    # Application initialization, Shizuku setup
├── MainActivity.java           # Main UI and control logic
├── capture/
│   └── ScreenCapture.java       # Screenshot capture via Shizuku/screencap
├── detector/
│   └── ColorDetector.java      # Color-based target detection
├── service/
│   └── AimAccessibilityService.java  # Accessibility service for gestures
└── shizuku/
    ├── IUserService.aidl        # Shizuku UserService interface
    └── ShizukuUserService.java  # UserService implementation
```

## Requirements

- Android 8.0+ (API 26+)
- Shizuku app installed
- Accessibility service enabled

## Setup

1. Install Shizuku from Play Store or F-Droid
2. Grant Shizuku permissions
3. Enable Accessibility Service for DeltaAim
4. Configure target color in settings

## Build

```bash
./gradlew assembleDebug
```

APK will be generated at: `app/build/outputs/apk/debug/`

## Usage

1. Launch DeltaAim
2. Wait for Shizuku connection
3. Configure target color (default: red enemies)
4. Press START to begin auto-aim
5. Press STOP to end auto-aim

## Permissions

- `moe.shizuku.manager.permission.API_V23` - Shizuku permission
- `android.permission.accessibility.service` - Accessibility service
- `android.permission.BIND_ACCESSIBILITY_SERVICE` - Gesture simulation

## License

MIT License

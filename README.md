# BumpCompanion

[![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

BumpCompanion is an open-source Android app that uses phone motion sensors and driving context to detect likely road speed bumps, save their locations, and warn when they are approached again.

## What it does

- Detects motion patterns consistent with road speed bumps during a user-started Drive Mode.
- Associates detections with location and lets the user review possible bumps after driving.
- Saves confirmed bump locations and provides approach warnings using voice, tone, or vibration.
- Offers balanced, conservative, and sensitive detection settings.
- Keeps Drive Mode active through a foreground service while another app, such as navigation, is on screen.
- Shows drive history and supports optional route-history recording.
- Imports and exports BumpCompanion bump lists through Android's system file picker.
- Supports mount calibration and metric or imperial units.

Detection accuracy depends on the device, phone mounting position, vehicle, road conditions, available sensors, and GPS quality. BumpCompanion can miss bumps or classify unrelated road motion as a bump.

## Screenshots

Screenshots are not included in this source release yet. Repository maintainers can add approved images under `docs/images/` and reference them here without changing the application package.

## Requirements

- Android 8.0 (API 26) or later.
- Android SDK platform 37 for compilation.
- JDK 21 for the Gradle build and CI environment.
- Android Studio with support for Android Gradle Plugin 9.3.1, or the included Gradle wrapper for command-line builds.

## Building from source

1. Clone the repository.
2. Open it in Android Studio and allow Gradle sync to finish.
3. Install Android SDK platform 37 if Android Studio requests it.
4. Build and test the debug application.

macOS/Linux:

```bash
chmod +x gradlew
./gradlew testDebugUnitTest assembleDebug
```

Windows:

```powershell
gradlew.bat testDebugUnitTest assembleDebug
```

Android Studio creates `local.properties` with the local SDK path. That file is machine-specific and must not be committed. The project does not require an API key, account, backend URL, or signing credential to build the debug variant. A release APK can be compiled with `assembleRelease`; official distribution signing remains the maintainer's responsibility.

## Permissions

- **Precise or approximate location:** places detected bumps and determines when the device is approaching a saved bump. Precise location is needed for reliable coordinates and warnings.
- **Notifications:** keeps user-started Drive Mode visible and provides its controls and warnings on supported Android versions.
- **Foreground service and foreground-service location:** allows Drive Mode to continue collecting motion and location while the app is not in the foreground.
- **Vibration:** provides optional haptic warnings.

The app requires an accelerometer. It does not request unrestricted background-location, Internet, microphone, contacts, or broad storage permissions.

## How detection works

BumpCompanion combines motion sensor data with speed, location quality, phone stability, and recent driving context to identify movement patterns consistent with road speed bumps while attempting to reject potholes, rough roads, turns, braking, and phone movement. Detection is heuristic and intentionally exposes uncertain events for later review.

More implementation detail is available in [ARCHITECTURE.md](ARCHITECTURE.md) and [ALGORITHM.md](ALGORITHM.md). See [TESTING.md](TESTING.md) for the automated and field-validation approach and [FILE_FORMAT.md](FILE_FORMAT.md) for the import/export format.

## Privacy

BumpCompanion has no accounts, analytics, advertising SDK, crash-reporting SDK, backend, or automatic uploads. The manifest does not request Internet access.

During user-started Drive Mode, the app processes motion and location data. It stores bump and candidate coordinates, drive summaries, encounters, calibration data, and settings in app-private local storage. Optional route-history recording is off by default; when enabled, it also stores recent location track points with drive history. Continuous raw sensor samples are processed in memory and are not retained in normal release use. BumpCompanion does not upload stored bump, drive, or sensor data. Location fixes are supplied by Android or Google Play services according to the device's location settings. Stored bump data leaves the app only when the user explicitly exports or shares it through Android.

See [PRIVACY.md](PRIVACY.md) for the detailed data inventory and deletion controls.

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request, and follow the [Code of Conduct](CODE_OF_CONDUCT.md).

Please report security issues privately as described in [SECURITY.md](SECURITY.md).

## License

BumpCompanion source code is available under the [MIT License](LICENSE). Third-party dependencies remain subject to their own licenses and terms; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Disclaimer

BumpCompanion should not be relied upon as the sole source for road-safety decisions. Drivers must remain attentive and follow road conditions, traffic laws, and safe-driving practices.

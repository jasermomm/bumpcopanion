# Privacy

BumpCompanion is local-first by design.

## Stored on the device

- confirmed/imported bump coordinates and metadata;
- pending candidate coordinates, detector features and review decision;
- drive summaries, including times, duration, distance, speed summary and event counts;
- encounter summaries;
- calibration profiles;
- application settings;
- import-batch metadata;
- a minimal boolean recovery flag indicating that Drive Mode was active before process termination.

Route-history storage is off by default. When enabled, recent location track points are stored with drive history. Continuous raw sensor streams are processed in memory and are not retained by the release build.

## Not collected or transmitted

- account identity;
- advertising identifier;
- contacts, calls or SMS;
- microphone/audio recording;
- another app's screen, accessibility tree or notifications;
- Google Maps/Waze routes or content;
- analytics events;
- public submissions;
- automatic cloud uploads;
- exact-coordinate crash reports;
- device identifiers in exports.

The manifest disables Android backup for the app. No backend endpoint or networking permission is included.

Developer-instrumented debug builds contain an internal diagnostic recorder that can write sensor and location data to app-specific storage. It is disabled by default, guarded by `BuildConfig.DEBUG`, and unavailable in the release build. Any such file must be treated as sensitive.

## Location behavior

Location is collected only during user-started Drive Mode. The main workflow uses a visible location foreground service rather than unrestricted hidden background tracking. When Drive Mode stops, location callbacks and sensor listeners are released.

Location fixes are obtained from Android's location framework or Google Play services, according to the device configuration. BumpCompanion contains no network client and does not upload stored bumps, drive records, route history or sensor data to a project server. Platform location providers remain subject to the device's settings and their own privacy terms.

## User-controlled sharing

Export happens only after the user selects a destination through Android's Storage Access Framework. Sharing a resulting file is controlled by the user and the selected external app. Imported files are treated as untrusted input.

## Deletion

The Settings screen supports:

- deleting drive history;
- deleting all bump, candidate, encounter, calibration and import data;
- resetting app settings.

Android's system **Clear storage** action also removes all local application data.

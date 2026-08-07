# Architecture and implementation map

## 1. Final architecture

BumpCompanion uses a single Android application module with disciplined package boundaries. Android-specific APIs remain behind platform interfaces; Room entities never cross into Compose; the foreground service owns live collection, while domain classes own detector and approach decisions.

```text
Compose UI + ViewModels
        |
        v
Domain repositories, models, detector interfaces
        |
  +-----+-------------------+
  |                         |
  v                         v
Room / DataStore       DriveDetectionService
                            |
             +--------------+--------------+
             |              |              |
             v              v              v
        SensorProvider  LocationProvider  WarningOutput
             \              |              /
              +----- pure domain logic -----+
                    detector + approach
```

The project is intentionally local-first. It has no account, backend, analytics SDK, map SDK, route reader, accessibility service, or notification listener.

## 2. Package/module structure

| Package | Responsibility |
|---|---|
| `domain.model` | Immutable domain models, settings, runtime/service states, import decisions, detector inputs and outputs. |
| `domain.detection` | Ring buffers, timestamp-aware signal bands, adaptive baseline, orientation/vehicle-axis handling, ML-ready features, stateful fusion classifier, CSV replay and event-location estimation. |
| `domain.approach` | Geographic calculations, trajectory corridor, approach scoring, warning phases, pass and cooldown state. |
| `domain.repository` | Storage contracts used by services and ViewModels. |
| `data.local` | Room entities, converters, DAOs, database and migrations. |
| `data.repository` | Mapping, geographic queries, duplicate matching, coordinate refinement and persistence. |
| `data.preferences` | DataStore-backed application settings. |
| `data.exchange` | Versioned `.bumpcompanion` export/import, preview, validation and checksum handling. |
| `data.calibration` | Stationary mount calibration profile collection and persistence. |
| `platform.sensors` | Sensor discovery, registration, sampling and device-to-world acceleration transformation. |
| `platform.location` | Fused location with framework fallback and monotonic timestamp conversion. |
| `platform.warnings` | Retained TTS engine, short tone, vibration and transient-ducking audio focus. |
| `platform.navigation` | Standard Android intents for launching compatible navigation apps or external coordinates. |
| `service` | Foreground-service lifecycle, persistent notification, actions and runtime state. |
| `ui.*` | Onboarding, dashboard, active drive, bump list/editing, candidate review, drive history, calibration and settings. |

## 3. Runtime data flow

1. A visible activity explains permissions and mount requirements.
2. The user explicitly starts Drive Mode.
3. `DriveDetectionService` immediately posts the foreground notification and creates a drive session.
4. Sensor and location providers emit timestamped samples on background dispatchers.
5. Orientation processing derives vertical, lateral and approximate longitudinal acceleration.
6. Detector v2 opens a candidate from speed-adaptive envelope/SNR, waits for complete event and settling context, matches seven physical profiles, evaluates five competing false-positive hypotheses, and emits explainable local/database confidence. GPS quality changes context confidence but is not an absolute veto.
7. The event-location estimator aligns the physical sensor timestamp with before/after location samples and records uncertainty.
8. A first observation is persisted as a candidate. It is not silently promoted to a trusted bump.
9. Candidate review can confirm, reject, defer, classify experimentally, annotate or merge the event.
10. Confirmed/imported bumps are queried geographically on each location update without loading the entire database.
11. The approach predictor considers trajectory, distance trend, bearing, directionality, corridor compatibility, GPS accuracy, pass state and cooldown.
12. The warning layer speaks or plays a tone and optionally vibrates without taking over navigation audio.
13. Stop and failure paths unregister all live listeners, flush bounded pending writes and finalize or mark the drive incomplete.

## 4. Foreground-service lifecycle

```text
Idle -> Preparing -> Active <-> Paused -> Stopping -> Idle
                       |          |
                       +-> GPS degraded
                       +-> Sensor degraded
                       +-> Permission lost -> Stopping
                       +-> Failed          -> Stopping
```

Lifecycle rules:

- User initiation from a visible activity is mandatory.
- Duplicate starts are ignored.
- The notification is posted before lengthy setup.
- Service mutation is serialized with a coroutine mutex.
- Pausing unregisters high-rate collection while retaining the session and notification.
- Essential permission loss, disabled location or a missing accelerometer stops detection cleanly.
- Recovery metadata distinguishes an interrupted drive from a clean stop.
- Compose and ViewModels do not own or directly manage sensor listeners.

## 5. Sensor pipeline

```text
SensorManager callbacks
    -> timestamp normalization
    -> bounded raw sample buffers
    -> gravity/rotation estimate
    -> device-to-world transformation
    -> vertical/longitudinal/lateral components
    -> placement and motion-quality score
    -> feature extraction
    -> full-event state machine + profile/competing-hypothesis classifier
    -> confidence + human-readable reasons
    -> timestamp-aligned coordinate estimate
```

Detector v2 is deterministic, replayable and versioned. It does not perform uncontrolled self-training. All filters, baseline limits, state timings, weights, penalties and sensitivity presets are centralized in `DetectorConfiguration`.

The detector uses a 46-value event vector covering waveform, peak structure/width, signal bands, pre/post isolation, settling, dynamic lateral dominance, speed context, sampling quality and explicit rough-road, pothole, phone, turn and ramp likelihoods. Short, long, table, low-profile, axle, asymmetric and consecutive profiles are valid alternatives.

The release build does not retain raw full-drive sensor streams. Developer-instrumented debug builds contain an internal, `BuildConfig.DEBUG`-guarded CSV recorder for controlled testing; it is disabled by default and is not exposed in the normal user interface. `DetectorCsvReplay` streams test rows through the same production core.

## 6. Approach-warning state machine

Per saved bump, `ApproachPredictor` retains a short distance history and state:

```text
Untracked
  -> Tracking (ahead + compatible path + decreasing distance)
  -> Warned (inside adaptive distance and cooldown allows)
  -> Inside pass radius
  -> Passed (distance consistently increases / bump moves behind)
  -> Suppressed for current encounter
```

A circular-radius match alone cannot warn. The scoring gate includes:

- stable recent travel bearing;
- relative bearing within a speed/accuracy-aware cone;
- decreasing distance across multiple samples;
- projected path-corridor compatibility;
- bump directionality;
- confidence and GPS quality;
- adaptive warning distance;
- prior warning time and per-drive pass state.

This reduces, but cannot eliminate, false warnings on parallel roads or near junctions because the app has no access to the active navigation route.

## 7. Database schema

Room database version: **3**.

| Entity | Key purpose | Important indices/relationships |
|---|---|---|
| `SpeedBumpEntity` | Trusted, imported, archived or removed bump record. | Status, last encountered, latitude/longitude bounding search. |
| `CandidateEventEntity` | Pending or reviewed sensor/manual candidate. | Review state, drive session, event time. |
| `EncounterEntity` | Individual observations associated with a bump/candidate/drive. | Bump, candidate and drive foreign-key-style identifiers. |
| `DriveSessionEntity` | Drive summary, quality, counts and interruption state. | Start time and incomplete state. |
| `LocationTrackPointEntity` | Optional route-history point when explicitly enabled. | Drive ID and timestamp. |
| `CalibrationProfileEntity` | Device/mount sensor noise and gravity profile. | Creation time and active profile use. |
| `ImportBatchEntity` | Import metadata for traceability and safe future undo. | Import timestamp/source. |
| `DiagnosticFileEntity` | Metadata only for optional diagnostic files. | Drive and creation time. |

Migration `1 -> 2` adds optional route-history storage. Migration `2 -> 3` adds diagnostic-file metadata without changing existing bump, candidate or drive data.

## 8. Permission model

Declared:

- coarse and fine location;
- foreground service;
- foreground-service location type;
- notifications;
- vibration.

Runtime behavior:

- Precise location is requested at Drive Mode start with an explanation.
- Notification permission is requested separately on Android 13+ and denial does not create a nag loop.
- The main workflow uses a user-started location foreground service and does not require unrestricted background location.
- Essential permission loss stops Drive Mode and records the interruption.
- Import/export uses the Storage Access Framework rather than broad storage access.

Never requested or declared:

- accessibility service;
- notification listener;
- screen capture;
- microphone;
- contacts, calls or SMS;
- broad storage access.

## 9. Testing strategy

### Pure JVM tests

- bounded ring-buffer behavior;
- orientation and gravity transformation;
- feature extraction and timestamp alignment;
- 56 positive profile/context variants and 77 adversarial non-bump variants;
- recorded pothole/phone/smooth/bump fixture replay;
- full-event grouping, GPS dropout, diagnostic explanation and CSV replay;
- stationary, smooth-road and phone-movement rejection;
- event-location interpolation and stale-fix handling;
- direct approach, moving away, parallel road, turn-away, pass and cooldown;
- import schema/value/checksum validation;
- duplicate matching and coordinate weighting.

### Android tests

- Room CRUD and bounding queries;
- database migration behavior;
- onboarding navigation and persistence;
- key Compose states and permission-denial surfaces.

### Field validation

Deterministic unit tests and synthetic signal fixtures exercise the detector, but they do not replace physical road testing. Field tuning should cover multiple phones, mounts, vehicles, suspensions, speeds and road surfaces, with false-positive and false-negative measurements recorded by detector version.

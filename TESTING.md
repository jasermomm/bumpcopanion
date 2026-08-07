# Testing detector version 2

## Automated JVM tests

Run the complete suite:

```bash
./gradlew testDebugUnitTest
```

Detector-focused tests now cover:

- timestamped filtering, orientation/gravity transformation, dashboard-vertical placement, ring buffers and feature extraction;
- deterministic speed-bump, smooth-road, phone-movement and pothole CSV fixtures containing only relative time and motion axes, with no GPS coordinates or device identifiers;
- diagnostic CSV header/row integrity and exact replay through `RoadEventDetector.addSample`;
- rejected-candidate explanations;
- temporary GPS dropout using recent moving evidence;
- axle-impulse merging and detection of physically separate bumps;
- 56 positive synthetic combinations: seven profile families across low/normal/high speed, no/heavy braking, rough asphalt, total GPS dropout and orientation fallback;
- 77 negative synthetic combinations: pothole, crack, continuous roughness, braking, turn, phone handling, ramp, repeated joints, stationary bump-shaped motion, engine vibration and random impacts, each at seven amplitudes;
- detector-adjacent location interpolation and trajectory warning behavior.

The matrix is deterministic and named so a regression reports the physical family/context, not only a
numeric assertion. See `DetectorScenarioMatrixTest` and the 110-situation post-implementation audit in
`docs/DETECTOR_FAILURE_AUDIT.md`.

Synthetic coverage proves deterministic behavior for modeled signals; it does not prove real-world
precision. Do not tune the detector to this generator alone.

## Android instrumentation tests

Run on an emulator or device:

```bash
./gradlew connectedDebugAndroidTest
```

The Android suite covers Room CRUD/bounding queries and onboarding persistence/navigation. Future
contributions can extend device coverage for foreground-service restart, sensor absence variants,
diagnostic file lifecycle, and manufacturer-specific sensor batching behavior.

## Debug diagnostic recording

The codebase contains an internal diagnostic recorder for deliberately instrumented debug builds. It
is guarded by `BuildConfig.DEBUG`, disabled by default, and not exposed through the normal user
interface. A contributor using it for controlled field validation should use a passenger or closed-course
operator, stop Drive Mode cleanly so buffered data is flushed, and retrieve the resulting app-specific
file with Android development tools.

Diagnostic files may contain precise locations and raw or derived motion data and must be handled as
sensitive data. Never commit them to this repository or attach them to a public issue.

## Offline replay

Application/JVM code can replay with:

```kotlin
FileReader(file).use { reader ->
    val result = DetectorCsvReplay.replay(
        reader = reader,
        detector = HeuristicRoadEventDetector(
            DetectorConfiguration.forSensitivity(Sensitivity.BALANCED)
        ),
    )
    println("samples=${result.samplesRead}, malformed=${result.malformedRows}")
    result.events.forEach { println(it.explanation) }
}
```

For A/B tuning, replay the same file into detectors with different immutable configurations and compare
candidate explanations. Keep a train/tuning corpus separate from a held-out acceptance corpus.

## Field corpus requirements

Each labeled crossing should record:

- ground-truth event type and boundaries;
- phone/OEM/Android version and actual sample-rate distribution;
- mount position, orientation and rigidity;
- vehicle class, wheelbase, load, tire and suspension notes;
- entry/minimum/exit speed and driver braking behavior;
- surface before/after the event;
- GPS speed/position accuracy and dropout;
- detector version, sensitivity, profile, confidence, disposition and score contributions.

Measure event-level precision/recall, false detections per driving hour and per distance, database-worthy
precision, double-count rate, location error and performance by vehicle/device subgroup. Database-worthy
precision should be the release gate because a false shared location has a larger cost than a missed
uncertain local candidate.

## Release acceptance

In addition to the existing lifecycle, permission, import/export, UI/accessibility and approach-warning
checks, detector version 2 requires:

1. all JVM and Android tests green;
2. all fixture files replay without unexpected event-count changes;
3. no regression in the 133 positive/negative matrix combinations;
4. no duplicate report for normal front/rear axle response;
5. nearby separate bumps remain detectable;
6. missing gyro/linear/rotation/GPS cases degrade without crashing;
7. diagnostic off means no CSV is created;
8. diagnostic on records and replays the same event decisions;
9. held-out database-worthy precision meets the product target;
10. every threshold/weight change increments or explicitly reviews the detector version.

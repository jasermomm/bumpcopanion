# Contributing to BumpCompanion

Thank you for helping improve BumpCompanion.

## Workflow

1. Fork the repository.
2. Create a focused branch from `main`.
3. Make your changes while following the existing Kotlin and Android style.
4. Run the relevant tests and build the debug app:

   ```bash
   ./gradlew testDebugUnitTest assembleDebug
   ```

5. Open a pull request explaining what changed, why it is needed, and how it was tested.

Keep changes focused and avoid unrelated rewrites, dependency churn, or generated build files. Update documentation when behavior, permissions, data handling, or the public file format changes. Never commit credentials, signing material, personal location data, or diagnostic recordings.

## Detector changes

The detector is sensitive to changes in filtering, timing, thresholds, and event grouping. A pull request that affects bump detection should explain:

- the behavior that changes;
- the false positives or false negatives it is intended to address;
- the devices, mounts, vehicles, recordings, fixtures, or synthetic cases used to test it; and
- any known trade-offs or remaining uncertainty.

Add or update regression tests where practical. Synthetic tests are useful, but they are not a substitute for carefully labeled real-world validation.

By participating, you agree to follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

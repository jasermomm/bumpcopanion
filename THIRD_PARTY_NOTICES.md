# Third-Party Notices

BumpCompanion's original source code is licensed under the MIT License. Dependencies, build tools, and their transitive components are not relicensed by this repository and remain subject to their own licenses and terms.

The direct dependency catalogue is maintained in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Major dependency families include:

- AndroidX libraries, including Compose, Room, DataStore, Navigation, Lifecycle, Activity, and Core, under the Apache License 2.0;
- Kotlin, kotlinx.coroutines, and kotlinx.serialization components under the Apache License 2.0;
- Dagger and Hilt components under the Apache License 2.0;
- Google Play services Location under the [Google Mobile Developer Services Terms](https://developers.google.com/mobile/terms) and applicable [Google APIs Terms](https://developers.google.com/terms); this component is not covered by BumpCompanion's MIT License;
- JUnit 4, used only for tests, under the Eclipse Public License 1.0; and
- the Gradle wrapper under the Apache License 2.0.

No third-party fonts, sound files, map datasets, or copied source trees were identified in this repository. Test signal fixtures are project test data and contain no GPS coordinates or device identifiers.

This notice is an inventory aid, not legal advice. Consult each component's published metadata and license text before redistribution, especially when producing an application package for a third-party store.

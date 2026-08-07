# Parallel Operation with Navigation Apps

## What “parallel” means

BumpCompanion and the navigation application are separate Android applications. BumpCompanion collects its own phone sensor and location data. It does not integrate with, inspect, modify, overlay, scrape or automate Google Maps, Waze or another navigation app.

The normal sequence is:

1. Open BumpCompanion.
2. Press **Start Drive** and confirm that the phone is mounted.
3. Confirm required location permission.
4. Observe the persistent Drive Mode notification.
5. Press **Open navigation app**.
6. Android opens a compatible maps/navigation application or chooser.
7. Navigate normally while BumpCompanion remains active in its user-started foreground service.
8. Stop or pause from the app or notification.

No fake destination is passed. Opening the navigation app uses Android's maps application category. Opening a saved bump coordinate uses a standard `geo:` URI and is a separate management action.

## Information BumpCompanion cannot access

It does not know:

- destination;
- selected route or route polyline;
- next turn;
- whether the driver will turn before a nearby bump;
- traffic;
- road names;
- speed limits;
- lane or carriageway with guaranteed accuracy;
- content or notifications from the navigation app.

Warnings therefore use current location, speed, recent movement path, bearing, GPS accuracy, saved coordinates, directionality, decreasing distance and cooldown state.

## Audio coexistence

Spoken warnings use `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` and transient ducking. The app does not use the alarm stream and does not intentionally pause navigation audio. One TTS engine is initialized asynchronously and reused. Stale utterances are flushed rather than accumulated indefinitely.

Exact behavior still depends on the current audio route, Bluetooth system, vehicle head unit, media app and Android version.

## Foreground-service limitations

The service is explicitly started from a visible activity. It declares location as its foreground-service type and posts the foreground notification immediately. It does not use WorkManager as a substitute for live sensing.

Android may restrict the notification experience when notification permission is denied. Manufacturer battery managers may still terminate a correctly implemented foreground service. The app records incomplete sessions and shows recovery information but cannot guarantee that every manufacturer will preserve the process with the screen off.

## Android Auto

The implementation remains a phone-side foreground service. It does not include an Android Auto template or claim in-car-screen integration. It may continue while the phone is connected to Android Auto when the operating system and manufacturer allow the service to remain active.

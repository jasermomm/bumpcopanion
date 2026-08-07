# Road-event detector version 2

## Why version 1 produced false positives

Version 1 was a staged threshold detector, but the physical decision was still dominated by one
sample crossing a fixed vertical threshold followed by any opposing peak in a partially complete
window. That design failed in several important ways:

- it did not wait for post-event suspension settling before classifying;
- it used a fixed noise floor, so identical amplitudes meant the same thing on smooth and rough road;
- it had no sustained rough-road or event-isolation model;
- pothole rejection was one jerk/timing condition rather than a competing physical hypothesis;
- it had one waveform family and therefore traded small/wide-bump recall against sharp-impact precision;
- it treated earth north/east as vehicle longitudinal/lateral, which made braking and turns orientation-dependent;
- it treated stale or missing GPS as an absolute veto and stopped live sensor processing in GPS-degraded service state;
- it used a fixed multi-second refractory interval that hid nearby bumps;
- its phone-stability input was a small additive term rather than an explicit manipulation likelihood;
- it retained only a compact feature subset and did not log rejected candidates for tuning.

The result was predictable: a crack, pothole, phone motion, or rough-road oscillation could supply the
two extrema and timing needed by the old classifier even though the complete event was not a coherent
whole-vehicle crossing.

## Version 2 data flow

```text
timestamped Android sensors + timestamped location
    -> device-to-earth rotation / gravity fallback
    -> earth-to-vehicle projection when GPS bearing is valid
    -> median deglitching and timestamp-aware filters
    -> bounded asymmetric road/noise baseline
    -> speed-adaptive envelope trigger (candidate only)
    -> CAPTURING -> SETTLING full-event state machine
    -> pre/event/post window extraction
    -> 46-value feature vector
    -> seven bump-profile matches
    -> rough-road, pothole, phone, turn, and hill/ramp hypotheses
    -> weighted evidence and explicit penalties
    -> event-aware axle grouping / short refractory
    -> rejected, local candidate, or database-worthy evidence
```

The core is `RoadEventDetector.addSample`. It has no Android dependency. Live collection and
`DetectorCsvReplay` call the same function with the same monotonic timestamps.

## Sensors and fallbacks

The live provider requests accelerometer and linear acceleration at 100 Hz, gyroscope at 50 Hz, and
gravity/rotation-vector updates at the Android game rate. Every emitted `MotionSample` contains the
latest synchronized sensor values and the accelerometer event timestamp.

Preferred path:

1. Rotation vector maps device acceleration into earth east/north/up.
2. Gravity is removed from world-up acceleration.
3. GPS bearing rotates east/north into vehicle forward/side axes.

Fallbacks:

- without GPS bearing, vertical remains valid and horizontal magnitude remains a rejection feature;
- without rotation vector, the gravity sensor defines the vertical unit vector;
- without a gravity sensor, the estimator retains its last/default gravity direction and reports lower orientation reliability;
- without gyroscope or linear acceleration, accelerometer/gravity/GPS detection still functions with less independent rejection evidence;
- stale GPS can reuse a recent moving fix for five seconds; a valid speed is never discarded merely because position accuracy is poor;
- no reliable evidence of vehicle motion remains a hard rejection for database events.

The rotation estimator calculates a real angular step from the relative rotation-matrix trace. It no
longer uses the Frobenius difference and labels that value as radians.

## Signal processing

All recursive filters calculate their coefficient from the actual sensor timestamp interval. A gap
over 180 ms resets filter state instead of manufacturing a large transient.

| Stage | Default | Purpose |
|---|---:|---|
| 3-sample median | 30 ms at 100 Hz | Removes an isolated callback glitch without erasing a vehicle impulse. |
| Drift low pass | 0.20 Hz | Estimates gravity leakage, mount drift, hills, and slopes that are too slow to be bumps. |
| Event low pass | 5.2 Hz | Retains chassis rise/fall and suspension response while rejecting mount/road buzz. |
| Event band | 0.20-5.2 Hz | Primary vertical waveform used for candidate and profile features. |
| Broad-motion low pass | 1.35 Hz | Preserves long humps/tables and supplies ramp discrimination. |
| High-frequency residual | above 9 Hz | Supplies crack, gravel, joint, mount-vibration, and rough-road evidence. |
| Rectified envelope low pass | 2.2 Hz | Requires sustained event energy rather than one threshold callback. |

Jerk uses adjacent filtered samples and real `dt`; values are bounded only to keep corrupt timestamps
from destabilizing statistics. Filtering does not assume perfectly uniform callbacks.

## Adaptive baseline and rough-road state

`AdaptiveRoadBaseline` tracks vertical RMS, lateral RMS, jerk RMS, and high-frequency RMS. Learning is:

- automatic and continuous;
- excluded while a candidate is active;
- excluded for candidate-sized outliers;
- 7.1 times slower upward than downward;
- bounded between 0.10 and 1.35 m/s² vertical RMS.

This prevents a long rough road from quickly poisoning the reference floor. A separate two-second
context follows current road condition using broadband elevation, high-frequency elevation,
multi-axis energy, and peak density. It emits `SMOOTH`, `NORMAL`, `ROUGH`, or `VERY_ROUGH`.

Rough road raises the trigger modestly but does not disable detection. A candidate on very rough road
must have both a strong profile match and isolation from the surrounding vibration.

## State machine and event grouping

```text
CALIBRATING/NORMAL
    -> CAPTURING      envelope exceeds adaptive, speed-scaled SNR trigger
    -> SETTLING       envelope stays below release level for 320 ms
    -> evaluation     after another 430 ms of post-event context
    -> REFRACTORY     260 ms same-event guard
    -> NORMAL

obvious rejected phone motion -> SUPPRESSED -> stable phone -> NORMAL
```

The trigger never means “bump detected.” It only freezes baseline learning and starts a candidate.
The state machine keeps related front/rear axle impulses together while the envelope is continuous.
A new coherent trigger after a genuine quiet gap finalizes the old event and immediately starts the
next, avoiding a large fixed cooldown. Candidate duration is bounded at 4.8 seconds; longer motion is
normally a ramp, hill, or sustained roughness.

## Feature vector

`EventFeatures.toMlFeatureVector()` exposes a fixed 46-value numeric vector:

- signed positive/negative peaks, peak-to-peak, peak gap, duration;
- vertical, lateral, low-frequency and high-frequency RMS/energy;
- jerk peak/RMS and high-frequency ratio;
- zero crossings, prominent peaks, coherent opposing-pair count, dominant peak width;
- event isolation, lobe balance, temporal symmetry, vertical dominance;
- settling and whole-vehicle coherence;
- bounded double-integrated displacement approximation;
- pre/post road RMS;
- pre/minimum/post speed, absolute/relative reduction, deceleration/acceleration;
- optional braking and post-acceleration evidence;
- gyro RMS, orientation step/reliability;
- sample rate, dropped-sample fraction, signal quality;
- roughness, pothole, phone, turn, hill/ramp and bump-waveform likelihoods;
- selected profile and all profile scores.

The displacement approximation is supporting shape evidence only; accelerometer bias makes it
unsuitable as an absolute height estimate.

## Bump profiles

The feature extractor scores these families independently and retains every score:

- `SHORT_SHARP`: compact balanced opposing lobes with limited high-frequency energy;
- `LONG_SMOOTH_HUMP`: longer low-frequency coherent rise/fall;
- `FLAT_TOP_TABLE`: structured entrance/exit transitions over a longer window;
- `LOW_PROFILE`: low amplitude but high SNR and clean isolation;
- `DOUBLE_AXLE`: multiple related peaks/pairs inside one continuous event;
- `ASYMMETRIC`: coherent vertical motion with moderate legitimate roll/lateral response;
- `CONSECUTIVE`: multiple coherent pairs over a longer candidate.

The strongest profile supplies waveform quality. Opposing polarity can occur in either order; order is
only weak pothole evidence because Android/device conventions and vehicle dynamics vary.

## Competing false-positive hypotheses

Rough-road likelihood combines the adaptive road state, high-frequency ratio, event peak density,
and pre/post vibration. It strongly penalizes a candidate that is not isolated from continuing noise.

Pothole likelihood combines amplitude-normalized jerk, narrow peak width, high-frequency energy,
unbalanced lobes, lateral/gyro evidence, and (weakly) downward-first ordering. A high likelihood plus
a mediocre bump-profile match is a hard rejection.

Phone-motion likelihood combines mount stability, actual orientation step, gyro RMS, dynamic lateral
motion, and clipping. Obvious manipulation is rejected and enters a temporary suppressed state;
moderate body pitch/roll remains a penalty rather than an absolute veto.

Turn likelihood uses lateral acceleration and yaw, but applies a confidence penalty only to the extent
that the event is not independently vertical-dominant. Hill/ramp likelihood uses long duration,
low-frequency dominance, sustained orientation change, and lobe imbalance.

Hard braking and acceleration cannot arm the detector without an independent vertical envelope.
Longitudinal braking is removed from vertical-dominance shock energy and contributes only optional
approach context, fixing the “brake -> stop -> accelerate” false-positive family.

## Confidence and sensitivity

Positive weights are 30% waveform, 13% isolation, 10% vertical dominance, 10% adaptive SNR, 7%
duration, 6% settling, 6% whole-vehicle coherence, 6% sensor quality, 3.5% braking context, and 2.5%
post-event acceleration. Penalties are separate and visible: rough road up to 30%, pothole 37%, phone
motion 52%, turn 13%, hill/ramp 23%, high frequency 14%, and clipping 30%.

Physical safeguards run after scoring: a valid opposing-lobe structure or smooth-wide alternative is
required; very rough, strong pothole, obvious phone motion, and no-motion cases have additional gates.

Balanced defaults:

- below 0.59: rejected and available only to diagnostic logging;
- 0.59-0.72: possible/local candidate;
- at least 0.72: likely/local candidate;
- database confidence at least 0.86: database-worthy single-device evidence.

Database confidence also includes isolation, signal quality and whole-vehicle coherence. The app still
stores detections as reviewable candidates; it does not silently publish one-device observations.

Sensitivity presets change SNR, subtle-event floor, minimum waveform quality and decision thresholds
together. They do not remove rough-road, pothole, phone, stationary, or clipping safeguards.

## Speed adaptation

The candidate threshold is scaled down at very low driving speed, nominal from 4-15 m/s, and scaled up
at high speed where ordinary impacts are stronger. Features retain pre/minimum/post speed and both
absolute and relative reductions. GPS accuracy changes the weight of speed context, not the existence
of the physical vertical signature.

## Location confidence and repeated evidence

`LOCAL_CANDIDATE` and `DATABASE_WORTHY` are distinct detector dispositions. Geographic persistence
continues to use candidate review, adaptive duplicate matching, bearing compatibility, weighted
coordinate refinement, encounter counts, and confirmation/rejection counts. One crossing's axle
impulses create one event and therefore cannot count as independent confirmations. This repository
contains no backend or cross-user data aggregation.

## Diagnostics and replay

Developer-instrumented debug builds contain an internal diagnostic recorder guarded by
`BuildConfig.DEBUG`. When deliberately enabled for controlled testing, it writes a versioned CSV under
the app-specific `diagnostics` directory. The file can include raw and derived sensor values, GPS data,
detector state, and candidate explanations, so it must be treated as sensitive. Recording is disabled
by default and unavailable in release builds.

Offline tools/tests call:

```kotlin
val result = DetectorCsvReplay.replay(csv.reader(), HeuristicRoadEventDetector())
```

No alternate replay classifier exists, so live/replay behavior cannot drift. CSV schema version is 2.

## Remaining limitations

No deterministic phone IMU can guarantee road-feature identity. Vehicle suspension, phone mount,
sampling implementation, speed, wheelbase and road geometry can create overlapping signals. GPS
bearing is weak at very low speed; the vehicle forward axis then degrades safely. The current weights
are engineering priors validated by synthetic and small deterministic fixtures, not a field-calibrated
population model. Real-world performance should continue to be evaluated with labeled
multi-phone/multi-vehicle drives, precision/recall measurement by detector version, and a held-out
validation corpus.

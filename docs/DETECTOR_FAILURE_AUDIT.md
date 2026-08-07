# Detector v2 failure audit

This audit is deliberately adversarial. It supplements, rather than repeats, the original 100-item
requirements list. “FP” means a non-bump could be reported; “FN” means a true bump could be missed.
Synthetic tests cover clean abstractions of these mechanisms, but field recordings are still needed.

## Pass 1 — vehicle and road physics

| # | Additional situation | Failure analysis | v2 response / remaining risk |
|---:|---|---|---|
| 1 | Empty pickup rear axle hops after a bump | FN if rear bounce looks irregular | Continuous envelope groups it; profile need not be exactly two peaks. |
| 2 | Fully loaded van crosses the same bump | FN from much lower peak amplitude | Adaptive SNR and long-smooth profile normalize against baseline. |
| 3 | Vehicle towing a light trailer | FP from trailer hitch shock after road event | Post-event peak density/settling reduce score; field data needed. |
| 4 | Vehicle towing a heavy trailer | FN/FP from delayed second body impulse | 4.8 s window can retain it, but trailer-specific labels are absent. |
| 5 | Articulated bus body flex | FP from slow multi-body oscillation | Long duration and low isolation invoke ramp/rough penalties. |
| 6 | Motorcycle crossing a bump | FN because chassis/phone dynamics differ radically | Not a calibrated vehicle class; current app should be treated as car-focused. |
| 7 | Three-wheeled vehicle | FN from nonstandard axle timing | Profile matching tolerates asymmetric/multiple peaks, not guaranteed. |
| 8 | Very long-wheelbase limousine | FN if axle interval exceeds normal grouping | Wide/consecutive profiles allow long gaps up to 2.9 s. |
| 9 | Very short-wheelbase microcar | FP pothole confusion from close axle response | Peak width, balance, isolation and high-frequency evidence compete. |
| 10 | Air suspension actively leveling | FN because controller suppresses peaks | Low-profile and long-smooth profiles help; field calibration required. |
| 11 | Hydropneumatic suspension | FN from unusually slow body motion | 0.2-5.2 Hz plus broad band retains slow coherent motion. |
| 12 | Adaptive dampers switch mode mid-drive | Baseline discontinuity | Slow bounded baseline prevents instant threshold jump. |
| 13 | Broken shock absorber oscillates after every seam | FP from bump-like settling | Sustained pre/post peak density and rough-road state penalize it. |
| 14 | One tire severely underinflated | FP pothole-like asymmetric roll | Asymmetric profile requires vertical structure; gyro/lateral penalties remain. |
| 15 | Run-flat tire transmits sharp modular bump | FN as crack/pothole | Balanced lobes and axle continuity can override sharpness softly. |
| 16 | Vehicle crosses bump diagonally at 20° | FN from separated left/right hits | Asymmetric/multiple-peak profiles tolerate roll with vertical coherence. |
| 17 | Vehicle crosses bump diagonally at 45° | FN from four separated wheel inputs | Consecutive/asymmetric profile may retain it; risk remains at very low speed. |
| 18 | Only one wheel clips the end of a speed bump | FN because physics resembles obstacle/pothole | Conservative by design; likely local-only unless structure is strong. |
| 19 | Split speed cushions straddled by vehicle | FN from minimal body heave | Low-profile detector may find it; database precision intentionally wins. |
| 20 | Narrow vehicle hits both speed-cushion lobes | Different signature by track width | Feature profiles are vehicle-normalized; no fixed axle amplitude. |
| 21 | Bump immediately after a tight downhill curve | FN from turn and braking penalties | Turn penalty is multiplied by lack of vertical dominance, not an absolute veto. |
| 22 | Bump immediately after an uphill crest | FN from slow drift removal | Event band subtracts the crest; isolated bump lobes remain. |
| 23 | Bump on a banked road | Earth vertical differs from road normal | Whole-vehicle heave remains in world up; extreme banking can lower recall. |
| 24 | Bump on a steep cambered driveway | FN/FP from roll plus ramp | Ramp/turn competition; strong isolated waveform can remain local. |
| 25 | Raised table longer than the vehicle | Split entrance/exit events | Related transition profile groups normal plateaus; very long tables may split. |
| 26 | Raised intersection crossed while turning | FN from sustained yaw | Independent vertical structure can overcome scaled turn penalty. |
| 27 | Speed bump with missing center section | Asymmetric/one-wheel response | Asymmetric profile plus coherence; high database threshold protects precision. |
| 28 | Partially demolished bump | Random debris-like peaks | Likely local/rejected; correct global label is inherently ambiguous. |
| 29 | Fresh asphalt feathering before a bump | Rough lead-in lowers isolation | Rough-mode structural gate still permits a strong coherent event. |
| 30 | Water-filled pothole visually anticipated by driver | Braking falsely strengthens pothole | Braking weight is only 3.5%; pothole hypothesis remains independent. |
| 31 | Pothole with rounded repaired edges | FP because lobes broaden | Lateral/gyro, down-first, isolation and database threshold provide defense. |
| 32 | Sunken manhole spanning both wheels | FP because whole body moves | May remain ambiguous; crowd/review layer is required. |
| 33 | Convex manhole cover spanning both wheels | Physically indistinguishable small bump | Correct local physical classification may be bump-like; semantic label needs map evidence. |
| 34 | Frost heave with one isolated crest | FP because it is a natural bump | Detector classifies motion, not legal traffic-control intent; remaining limitation. |
| 35 | Road subsidence forming a broad basin edge | FP as long hump | Hill/ramp duration and weak paired structure suppress most cases. |

Pass-1 implementation changes: seven profile families replaced one waveform; polarity became soft,
longitudinal braking was removed from dynamic lateral dominance, and pothole/rough/ramp hypotheses
became explicit competitors rather than isolated Boolean rules.

## Pass 2 — sensor, timing, mount, and data quality

| # | Additional situation | Failure analysis | v2 response / remaining risk |
|---:|---|---|---|
| 36 | Accelerometer callback burst after batching | FP from assumed uniform timing | Every filter/jerk calculation uses sensor timestamps. |
| 37 | 250 ms sensor delivery gap during the bump | Artificial filter edge / FN | Recursive filters reset over 180 ms; signal-quality score drops. |
| 38 | Duplicate sensor timestamp | Division by zero / duplicate energy | Non-increasing samples are ignored. |
| 39 | Sensor timestamp jumps backward after device bug | Corrupt event | Non-increasing sample rejected; reset on drive restart. |
| 40 | Location epoch clock changes during drive | Mislocated event | Event alignment uses elapsed realtime, not wall clock. |
| 41 | User manually changes phone time | Same risk | Monotonic event timestamp remains authoritative. |
| 42 | GPS reports speed but no bearing | Wrong longitudinal axis | World vertical valid; braking context omitted/degraded rather than invented. |
| 43 | GPS bearing flips 180° at walking speed | Longitudinal sign reversal | Bearing only changes context; vertical waveform remains primary. |
| 44 | GPS speed accuracy is 5 m/s | False motion confidence | Accuracy is logged and slightly lowers database confidence. |
| 45 | GPS freezes last 60 km/h speed after parking | Stationary shock FP | Stale quality only bridges five seconds of recent motion. |
| 46 | First GPS fix arrives after bump | FN from no motion proof | Strong unknown-GPS candidates are analyzed but rejected without prior motion; precision choice. |
| 47 | Urban-canyon speed oscillates rapidly | False braking/acceleration evidence | Speed context is low weight; physical signature is mandatory. |
| 48 | Fused location switches provider | Timestamp/accuracy discontinuity | Each fix carries monotonic time and accuracy; no sample-rate assumption. |
| 49 | No linear-acceleration virtual sensor | Missing optional evidence | Raw accelerometer plus rotation/gravity path is primary. |
| 50 | Vendor linear sensor has 500 ms lag | Misaligned duplicate signal | It is recorded for analysis, not used blindly as primary vertical. |
| 51 | No gyroscope | Phone/pothole rejection weaker | Detector continues; orientation/signal quality expose reduced evidence. |
| 52 | Gyroscope bias slowly grows with temperature | False phone motion | Phone likelihood emphasizes RMS spikes/orientation, baseline context limits drift impact. |
| 53 | Rotation vector magnetically disturbed | Bad heading axis | Vertical uses gravity component; GPS bearing supplies vehicle projection. |
| 54 | Game rotation vector drifts in yaw | Wrong horizontal axis | Vertical unaffected; horizontal evidence is supporting only. |
| 55 | Gravity sensor updates at 10 Hz | Lag during sharp pitch | Rotation vector preferred; gravity fallback reliability is lower. |
| 56 | Accelerometer clips on a severe impact | Bump score saturates high | Explicit 75 m/s² clipping penalty and reason. |
| 57 | Low-resolution accelerometer quantizes subtle bump | FN low profile | Adaptive floor bottoms at 0.10 m/s²; cannot recover absent information. |
| 58 | OEM caps requested sample rate at 25 Hz | Missed sharp peaks | Timestamp quality score drops; broad profiles remain usable. |
| 59 | OEM supplies 400 Hz despite request | Baseline/energy scaling bias | RMS and time features are rate-independent; ring capacity may shorten history. |
| 60 | CPU overload drops every other sample | Sharpness distortion | Gap fraction/sample-rate quality reduce confidence; event shape may survive. |
| 61 | Thermal throttling causes irregular callbacks | Same risk | Actual `dt`, dropped-sample features, bounded channel. |
| 62 | Phone mount resonance at 8 Hz | Falls inside event edge band | Sustained pre/post energy and peak density reduce isolation. |
| 63 | Phone mount resonance at 12 Hz | FP buzz | High-frequency residual and roughness penalty. |
| 64 | Magnetic mount briefly detaches then reseats | Strong phone event | Orientation/gyro/lateral/stability likelihood rejects and suppresses. |
| 65 | Soft dashboard mat lets phone bounce vertically | FP with little orientation change | Repeated peak density and poor settling help; difficult if one clean bounce. |
| 66 | Phone in a handbag on the seat | Continuous independent motion | Stability/gyro/multi-axis penalties; app guidance should recommend fixed mount. |
| 67 | Phone in driver’s pocket while seated | Body motion coupled to car | Very high ambiguity; no production-quality guarantee. |
| 68 | Phone rotates only around gravity (yaw) | Vertical remains coherent | Actual rotation step/yaw raises phone/turn likelihood without changing gravity axis. |
| 69 | Screen orientation toggles portrait/landscape | Android UI rotation, not sensor pose | Sensor coordinates/rotation vector are independent of UI orientation. |
| 70 | App process pauses then service recovers | Filter history discontinuity | Detector resets; recovery session records interruption instead of joining stale events. |

Pass-2 implementation changes: earth axes are now projected by GPS bearing instead of being mislabeled
vehicle axes; speed accuracy and gravity/rotation values are recorded; filters reset on timestamp gaps;
degraded GPS no longer disables sensor processing; and a bounded channel replaced per-callback coroutine
launches.

## Pass 3 — combinations, adversarial sequences, and operations

| # | Additional situation | Failure analysis | v2 response / remaining risk |
|---:|---|---|---|
| 71 | Clean bump 600 ms after rough patch ends | FN from contaminated pre-window | Isolation uses both sides and profile can override moderate roughness. |
| 72 | Rough patch begins during bump settling | FN from high post RMS | Waveform/axle evidence retained; database threshold may defer it to local review. |
| 73 | Pothole during bump approach, then real bump | First event consumes cooldown | Quiet-gap finalization and 260 ms refractory allow second event. |
| 74 | Real bump then pothole during settling | Combined waveform ambiguous | Pothole likelihood rises; conservative local/reject is intentional. |
| 75 | Two bumps only 1.2 s center-to-center | Accidental grouping | Quiet-gap branch can finalize and immediately recapture; exact limit needs field data. |
| 76 | Flat table entrance/exit 1.2 s apart | Accidental splitting | 700 ms related-impulse rule and continuous broad motion usually group them. |
| 77 | Three speed cushions with alternating wheel hits | Many asymmetric peaks | Consecutive/asymmetric profile; may represent one traffic-calming installation. |
| 78 | Bump while ABS chatters from wet road | FN from high-frequency penalty | Strong coherent body waveform may remain local; database conservatism is intended. |
| 79 | Bump while traction control cuts power | Longitudinal oscillation | Braking/acceleration is contextual; dynamic lateral vertical dominance is unaffected. |
| 80 | Bump exactly during automatic gearshift | Added pitch impulse | Slow longitudinal mean removed from vertical dominance; full shape still required. |
| 81 | Bump while regenerative braking changes level | Longitudinal step | Same protection as braking; no independent vertical trigger from regen. |
| 82 | Bump while adaptive cruise brakes | No driver approach pattern | Braking is optional; bump can still pass. |
| 83 | Bump while vehicle is pushed in traffic below GPS floor | FN at near-zero speed | Sensitive floor is 0.9 m/s; below that precision intentionally wins. |
| 84 | Bump crossed immediately after starting drive | Baseline not calibrated | Initial bounded floor and calibration-progress quality; still analyzes candidates. |
| 85 | Smooth highway followed instantly by gravel | Initial gravel peaks look significant | Two-second rough context rises quickly; isolated-event check rejects continuity. |
| 86 | Gravel followed instantly by smooth bump | Raised baseline may hide bump | Upward baseline learns slowly; profile/isolation can recover it. |
| 87 | Repeating concrete slabs at axle-like spacing | FP as consecutive bumps | Sustained pre/post peak density and low isolation penalize the sequence. |
| 88 | Road grooves excite a single suspension resonance packet | FP isolated waveform | One of the hardest cases; high-frequency/profile balance and crowd review remain. |
| 89 | Wind gust rocks a high-sided van | FP from roll/yaw | Low vertical dominance and long duration/turn penalties. |
| 90 | Passing truck pressure wave rocks vehicle | Similar risk | No strong paired vertical profile in normal cases. |
| 91 | Car-wash conveyor while GPS last fix is moving | FP repeated shocks | Stale motion expires in five seconds; rough continuity suppresses. |
| 92 | Ferry/train carries stationary vehicle with phone inside | False moving GPS/IMU context | Physical bumps may be detected locally; mode cannot infer carrier vehicle. |
| 93 | Multi-storey car park with poor GPS and many ramps | FP ramps / FN real humps | Drift/ramp model plus recent-speed bridge; location confidence stays low. |
| 94 | Tunnel loses GPS immediately before bump | FN | Five-second moving memory explicitly covers it. |
| 95 | Tunnel lasts minutes then bump occurs | FN without motion evidence | Deliberate precision limit; wheel-speed/OBD input would solve it. |
| 96 | GNSS multipath places event on parallel road | Database contamination | Coordinate confidence and later duplicate/crowd evidence remain necessary. |
| 97 | Same user loops and crosses bump twice in one drive | Crowd overcount | Separate encounters can exist but are not separate users; backend must key trips/users. |
| 98 | U-turn crosses the same bump opposite direction | Direction cluster ambiguity | Bearing-aware persistence can form opposite direction evidence. |
| 99 | Phone movement masks a real bump exactly | FN due severe phone penalty | Intentional: independently moving sensor cannot support database-quality evidence. |
| 100 | Passenger steadies loose phone during real bump | Mixed orientation signature | Moderate penalty permits strong coherent waveform; likely local review. |
| 101 | Door closes while car rolls above speed floor | FP shock | Vertical pair/profile/isolation normally weak; phone/multi-axis evidence applies. |
| 102 | Cargo shifts and hits trunk floor while moving | FP clean vertical impulse | Pothole/phone may not catch remote cargo; one difficult remaining case. |
| 103 | Roof rack load bounces after seam | FP delayed oscillation | Rough/settling/peak density penalties; field data needed. |
| 104 | Child repeatedly kicks seat holding mount | FP independent impulses | Multi-axis/gyro/stability and non-isolated peak train suppress. |
| 105 | Audio subwoofer creates low-frequency vibration | FP event-band energy | Sustained baseline/low isolation; isolated bass transient remains a risk. |
| 106 | Active phone haptic motor during drive | High-frequency local signal | Above-9-Hz residual and tiny chassis coherence suppress. |
| 107 | Incoming-call vibration on loose mount | Repeating pulse packet | Phone/high-frequency/peak-density rejection. |
| 108 | Camera optical stabilization rattles | Very small local high frequency | Below physical/SNR floor. |
| 109 | Sensor diagnostic logging disk stalls | Dropped sensor processing | Buffered writes run on service background queue; 100-row flush cadence. |
| 110 | Diagnostic CSV is truncated on process kill | Replay malformed tail | Streaming replay counts malformed rows and retains preceding samples. |

Pass-3 implementation changes: candidate decisions now wait for post-event context, every rejection has
an evidence/penalty explanation, related axle impulses use envelope continuity, a new trigger after a
quiet gap can start immediately, and accepted evidence is split into local versus database-worthy.

## Conclusions from the three passes

The audit led to concrete code changes rather than threshold inflation. The highest remaining risks are
events that are physically indistinguishable to one cabin IMU: a convex repair spanning both wheels,
cargo striking the vehicle, a single clean mount bounce, and bumps after a long total GPS outage. These
should be resolved with labeled field data, repeat/crowd confirmation, optional vehicle telemetry, and
review—not by claiming deterministic certainty.

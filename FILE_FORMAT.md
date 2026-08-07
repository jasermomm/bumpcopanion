# `.bumpcompanion` Exchange Format

The extension is `.bumpcompanion`; the content is UTF-8 JSON. Schema version 1 is implemented with Kotlin serialization.

## Top-level object

```json
{
  "schemaVersion": 1,
  "appVersion": "1.0.0",
  "exportId": "UUID",
  "exportedAt": 1785400000000,
  "listName": "Family list",
  "sourceLabel": "Dad's Cairo bumps",
  "bumps": [],
  "checksum": "lowercase SHA-256"
}
```

`checksum` is SHA-256 of the deterministic serialized top-level object with the checksum field omitted. Readers may accept a missing checksum for schema-1 compatibility, but when one is present it must match.

## Bump fields

A serialized `SpeedBump` contains:

- `id`;
- corrected `latitude` and `longitude`;
- raw latitude/longitude;
- horizontal accuracy and coordinate confidence;
- detector confidence;
- status and source;
- directionality, primary/opposite bearings and tolerance;
- first/last detected and last warned timestamps;
- encounter, confirmation, rejection and missing counts;
- imported source and notes;
- warning enabled and optional custom distance;
- algorithm version;
- archive/removed state;
- optional region and road labels.

No route history, raw sensor stream, device identifier, diagnostic log, API key or secret is exported.

## Import limits and validation

Current limits:

- maximum file size: 5 MiB;
- maximum bump items: 20,000;
- supported schema: 1;
- export ID: 1–100 characters;
- list/source label: at most 160 characters;
- no duplicate bump IDs in one file;
- timestamp cannot be negative or more than one day in the future;
- latitude: −90 to 90;
- longitude: −180 to 180;
- confidence values: 0 to 1;
- notes and labels are truncated to local safe limits;
- custom warning distance: 10–1,000 metres;
- checksum must match when supplied.

Unknown JSON keys are ignored for forward compatibility. Malformed JSON, invalid values, unsupported versions, excessive files and checksum failures return a user-visible error rather than crashing.

## Merge behavior

Import never replaces the whole local library. Each valid item is passed through adaptive duplicate matching. The result is either a new local record or a merge/refinement of a nearby compatible record. Imported-source metadata is retained.

The Room schema contains import-batch tables for extending this implementation to full preview conflict resolution and safe undo without changing the core bump records.

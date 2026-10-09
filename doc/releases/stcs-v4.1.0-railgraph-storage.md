# STCS 4.1.0 — Dynamic point range and RailGraph storage

On 2026-10-09 the maintainer confirmed STCS 4.1.0 testing passed. The initial local suite build against Minecraft/Folia 26.2 passed 82 Java test entry points, including 32 STCS entries. The GitHub updates were then merged, retaining newer TIMS integrity monitoring, dispatcher MA revocation and dual 26.2/26.3 display adapters. Current companion sources are STF 4.0.8, STA 2.1.4 and SkyPCC 2.0.3; the maintainer's earlier test report does not assert live acceptance of the combined newer companion features.

Final merged validation: all four plugins built; **86 Java test entry points passed on 26.2 and 86 on 26.3**. The desktop dispatcher passed 9 Python tests; the RailGraph testbench ran 60 tests with 3 skipped. PCC edge-line and four-language resource checks passed, and local links in 13 README/release files resolved. Dynamic-lock telemetry fixtures explicitly report confirmed integrity so TIMS checks remain active.

## Dynamic FS point eligibility

FS updates use fresh telemetry speed to calculate `max(ma.lock-distance-meters, v²/(2a) + v*lead-seconds + buffer-meters)`, capped at 10000 metres. This lets a fast train reserve a proven route through an approaching point earlier instead of always clipping EoA at the fixed range boundary. Unknown points, pending changes, conflicting resources and whole-train throat clearance continue to block extension.

```yaml
ma:
  lock-distance-meters: 150.0
  dynamic-lock:
    enabled: true
    lead-seconds: 5.0
    deceleration-mps2: 1.0
    buffer-meters: 25.0
```

Missing new keys use these defaults; set `enabled: false` to restore the fixed range. The deceleration is a planning parameter, not a brake command: configure it no greater than the onboard ATP curve deceleration. SH and SR retain their existing separate limits. Already-issued resources still follow the original grant-acknowledgement and occupancy-retention rules.

`ma.look-ahead-meters` (default 600 m) and `ma.max-authority-distance-meters` (default 300 m) remain independent configured caps. At 420 km/h the default dynamic calculation gives about 7414 m; the defaults cannot provide that much MA. Longer search and MA caps must be configured for a high-speed route, with throat clearance and the actual onboard ATP curve considered. This update does not automatically enlarge either cap. [Chinese configuration guide](../STCS-DYNAMIC-SWITCH-LOCK.zh.md).

## RailGraph storage

STCS now stores an edge between infrastructure nodes as a compact `straightPath` when every rail step is contiguous, follows the same single coordinate axis and direction, and has the expected distance increment. Corners, slopes and all other paths retain the complete `path` array. The in-memory graph and the `graphJson()` service response still expose full paths; SkyPCC does not need a protocol change for this release.

The saved `railgraph.json` uses `schemaVersion: 4`. STCS can load older v2/v3 graphs, then starts a world rescan once rail infrastructure is ready. The old file stays in place until a new scan has produced a graph revision. Before the first replacement, STCS copies it to `railgraph.json.legacy.bak`. If the scan cannot reach an unloaded chunk, the existing incomplete-edge preservation rules apply, and the unresolved port remains visible. A subsequent rebuild can refresh that edge when the chunk is available.

The saved v4 file is not directly readable by older offline graph tools that require `edge.path` on every edge. The `graphJson()` service response continues to provide full paths until those tools gain v4 support.

## Included earlier fixes

Directed SR searches check unresolved ports on the used path instead of rejecting an unrelated unresolved branch. The attended shadow-ledger reset uses a one-minute, one-use operator-bound code from an empty `/st list`; it requires STF 4.0.3 or newer challenge support and never proves physical clearance. See [the reset procedure](stcs-v4.0.4-ledger-reset.md).

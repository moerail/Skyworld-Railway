# SAL shortest directed paths and track previews

**[ADDED / FIXED · 2026-11-26 · SAL0.7.5 / PCC2.6.4]** Rail coordinates and heading now come from one STF telemetry frame. Uncertainty brakes immediately with a default2s recheck; bounded endpoint tolerance0.75blocks and projection tolerance1.5blocks are configurable. Independent wrong-branch confirmation recovers earlier. No new/extended MA is granted. Original failures persist in /sal status and are visible in PCC event text. [Detailed policy](SAL-LOCALISATION-TOLERANCE.en.md)

2026-11-26: SAL0.7.2 / PCC2.6.4 / STA2.9.1.

**Changed:** ~~Reject every waypoint pair with multiple possible paths.~~ Each ordered leg now chooses the shortest legal directed RailGraph path by accumulated track length. A longer detour/loop no longer invalidates a shorter connection. Stable edge IDs and path ordering resolve equal lengths independent of graph array order.

Explicit devices/directed edges remain mandatory constraints, allowing an administrator to force another track or a longer trip. This minimizes distance, not predicted time or congestion. It neither chooses arbitrary reversals nor ignores direction, allowed switch transitions, graph faults, localisation or finite search budgets (4096 iterations /256 edges).

A repeated same-position target requires positive travel; zero length is not a loop. Station endpoints, whole-route closure and arrival direction checks remain. Greedy shortest legs are not exhaustive global optimization over all arrival headings; add explicit waypoints for intended branches. STCS still grants MA, STA carries intentions and STF actuates entities. Current physical switch setting does not select the plan.

Validation, entry rotation, station-to-station execution and preview use the same plan routine. Depot movements share the shortest-path engine with existing full-body obstacle filters. Published task snapshots remain.

PCC draws actual sampled rail geometry for complete valid routes and any confirmed prefix of an unfinished draft. Unfinished status does not make a confirmed track prefix an estimated straight line. After the first unresolved leg, later arrival directions are unknown; those unconfirmed connections remain visibly estimated. Preview never proves clearance or grants MA.

Acceptance: short connection despite long alternatives; deterministic ties after graph reordering; explicit longer branch; positive same-position loop; broken/illegal transitions still rejected; curved confirmed draft preview. Save→Validate→Publish is still explicit.

[Chinese contract](SAL-ROUTE-PATHS.md) · [Production test (Chinese)](../../SkyATO-Lite/TEST.zh.md) · [Validation](../../VALIDATION.md)

# SAL public stops and shared dispatch — 2026-11-26

**[FIX · SAL0.7.6 · 2026-11-26]** Return berth/obstacle/reselection holds renew the already accepted route lease, avoiding1500ms heartbeat starvation. A default2s recheck retains the target; transient body uncertainty is not labelled topology change. Persistent unknown geometry faults; true berth/space changes reselect only after stopping. Detailed reasons and explicit dispatch-history labels are added. [Details](SAL-RETURN-RECHECK.en.md)

**[ADDED / FIXED · 2026-11-26 · SAL0.7.6 / PCC2.6.4]** Rail coordinates and heading now come from one STF telemetry frame. Uncertainty brakes immediately with a default2s recheck; bounded endpoint tolerance0.75blocks and projection tolerance1.5blocks are configurable. Independent wrong-branch confirmation recovers earlier. No new/extended MA is granted. Original failures persist in /sal status and are visible in PCC event text. [Detailed policy](SAL-LOCALISATION-TOLERANCE.en.md)

**[ADDED / FIXED · 2026-11-26]** STA2.9.2 / STF5.1.2 / SAL0.7.6: admin /sal override <train name or UUID> brakes, confirms MA withdrawal, releases SAL and reserves manual control; use /st drive after completion. Body occupancy, TIMS and TR/PT remain, with no movement authority granted. Slope localisation uses STF-observed railXYZ through existing STA telemetry instead of flooring entity coordinates. [Full contract](SAL-OVERRIDE-AND-SLOPES.en.md)

**[ADDED · SAL0.7.6 · 2026-11-26]** Pending/expired calls explain each vehicle’s eligibility, forward/reverse path attempts, pickup estimate cap, other trains with stale/unknown/off-graph bodies, obstructed edges and stale computation/graph results. Existing safety gates remain; another train’s TIMS hold alone does not globally block routing. Reports are bounded, not exhaustive network analysis. Existing PCC displays the reason field without an API change.

**[CHANGED · SAL0.7.6 / PCC2.6.4 · 2026-11-26]** Ordered visits select the shortest legal directed track path; longer alternatives no longer cause ambiguity rejection. Explicit devices/edges constrain the choice. Direction, graph faults, closure and MA checks remain. PCC follows actual rail geometry, including confirmed prefixes of unfinished drafts; only unconfirmed connections are estimated. [Path policy](SAL-ROUTE-PATHS.en.md)

**[ADDED / CHANGED · 2026-11-26]** PCC2.6.4 / SAL0.7.6 / STA2.9.2: validation identifies the first failing directed visit pair by index, device label and ID. Both list endpoints are marked; click the diagnostic to jump there. Fix it and validate again rather than guessing later arrival directions. Reorder with the right-hand ≡ grip using mouse/touch; release submits once, cancellation submits nothing. Focus the grip and use arrow keys for keyboard sorting. Visit UUID, dwell and reversal stay attached. Reordering is staged; Save→Validate→Publish remains explicit.

STA2.9.2 / STF5.1.2 / STCS4.3.5 / SAL0.7.6 / PCC2.6.4.

**Breaking:** station line4 is a unique public stop name; line3 is default dwell. No route is auto-populated by placing a sign. awake line3 is route, line4 is stop or stop@visitUUID. Depot/group signs remain unchanged.

New routes default to GLOBAL with optional preferred groups. RESTRICTED requires selected groups. Legacy routes without policy retain restricted scope. Existing draft/publication isolation, pinned jobs and persistent edit-conflict freeze remain.

Visit has a stable UUID, retained on reorder; duplicate IDs reject saving. The final duplicate first stop is a closure alias. Other repeated stops require an explicit visit UUID for departure. Starting at B rotates A-B-C-A into B-C-A-B without changing the publication. Arrival direction follows the original visit path, and a necessary stopped end change is completed before pickup dwell. Special terminal overrides on a closure alias are not an independent selectable entry; use another service definition for different terminal treatment.

Requests accumulate for500ms, reuse/merge an existing matching pickup first, then match eligible PARKED SAL vehicles. Body evidence, driver/owner, manual-standby state and existing jobs are hard filters. Blocked or unknown track cannot be made usable by a large score.

A separate SAL-dispatch-compute JVM worker evaluates immutable observations and graph paths, returning proposals only. It never accesses Bukkit or actuates trains. Route rotation/input preparation remains on the SAL coordinator. Submitted proposals are fenced by graph/publication/request lifetime, body composition/position/direction, home group and ownership; a result older than5s is discarded. STCS alone grants MA and arbitrates infrastructure.

ETA is an initial approximation: path distance/default cruise plus2s preparation,8s for each required departure/arrival end change. It does not yet model all train profiles, acceleration or congestion. Candidate ETA must meet maxPickupSeconds. Cost adds a finite nonpreferred-group penalty and an optional last-parked-car penalty. Maximum-cardinality minimum-cost matching repairs greedy assignments and weights waiting requests; a batch-wide optimum does not guarantee the single-request preference difference for every rider. No claimed ETA is a guaranteed travel time.

Default preference allowance30s; pickup estimate cap120s. A queue times out after120s with visible reasons. Last-car penalty is disabled by default. Queue64, candidates256, compute budget500ms; incomplete rounds may wait rather than exhaustively search. No proactive redistribution, prediction, arbitrary multi-reversal routing or external daemon is delivered.

GLOBAL return can use any suitable registered group; RESTRICTED return stays within selected groups. Cross-road cost considers preference; a single-ended road still fills deep first. Successful return updates home to the actual group. Automatic centre parking and explicit six-digit manual consent remain distinct.

Restart cancels uncommitted requests and keeps existing tasks in RECOVERY_REQUIRED. Schema1 station names migrate to unique legacy-name_Sn aliases while old physical text remains readable; legacy awake signs stay disabled until rewritten. Trains, depot berths, homes, ledgers and route references remain. No production server is automatically edited.

PCC provides policy fields, copyable entry references, request queue and recent decisions through the additive STA operating snapshot. Existing token authentication and conflict handling remain mandatory.

Configuration: dispatch.batch-window-ms500 (100–5000), request-timeout-ms120000 (5000–600000), compute-budget-ms500 (50–2000), last-vehicle-penalty-seconds0 (0–3600). CLI: /sal route supply MainTour global 30 120; /sal route depot MainTour main or none; /sal awake MainTour MainStart; /sal station list. Save/validate/publish explicitly.

[Chinese full contract](SAL-SHARED-DISPATCH.md) · [Chinese production test](../../SkyATO-Lite/TEST.zh.md) · [Validation](../../VALIDATION.md)

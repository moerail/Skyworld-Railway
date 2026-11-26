# SAL centre-aligned multi-berth storage — 2026-11-25

**[ADDED / FIXED · SAL 0.8.0 / STF 5.1.4 · 2026-11-26]** Depot prompts retry and refresh expired consent with the STF stop threshold. Pickup and return share segmented intermediate-reversal routing through STCS release and STA reversal. Chunk-loading holds retain manual hotbar power selection while inhibiting actual traction. [New behavior](SAL-MANEUVERS-AND-HANDOVER.en.md)

**[FIX · SAL 0.7.7 / STCS 4.3.6 · 2026-11-26]** Concurrent returns retain their selected path and berth; STCS handles temporary throat traffic by limiting/extending MA. Automatic point alignment cannot withdraw another train’s MA. Destination clearance, stored-train obstacles and real-fault checks remain. [New return rules](SAL-RETURN-TRAFFIC.en.md)

**[FIX · SAL0.7.6 · 2026-11-26]** Return berth/obstacle/reselection holds renew the already accepted route lease, avoiding1500ms heartbeat starvation. A default2s recheck retains the target; transient body uncertainty is not labelled topology change. Persistent unknown geometry faults; true berth/space changes reselect only after stopping. Detailed reasons and explicit dispatch-history labels are added. [Details](SAL-RETURN-RECHECK.en.md)

**[2026-11-26]** Public stops and shared supply are now described in [the current dispatch contract](SAL-SHARED-DISPATCH.en.md). Centre parking and whole-body berth geometry remain unchanged. GLOBAL may return to any suitable group; RESTRICTED retains group boundaries. Existing manual six-digit consent stays mandatory.

**[FIX · 2026-11-25]** STF5.1.1 / SAL0.6.1: default manual FS/MA request waits for forward/reverse selection, not neutral driver acquisition. A stopped train overlapping a depot marker receives a random six-digit handover code after3seconds. `/sal handover` requests one immediately while stopped over the marker; `/sal handover <code>` confirms, then `/st release` commits. Automatic return still uses centre alignment.

The precise centre rule below applies to automatic parking; manual consent now captures the current stopped position while the body overlaps the depot marker.


**[BREAKING]** STA2.8.0 / STF5.1.0 / STCS4.3.5 / SAL0.6.0 / PCC2.5.0.

~~Depot capture uses the leading car.~~ Depot capture, manual handover, automatic return and dispatcher-approved SR return now use the consist middle. Station stopping semantics and sign text stay unchanged. Reposition old front-aligned consists manually; the upgrade never teleports or moves parked trains.

## Geometry and dispatch

SAL follows the STA RailGraph through intermediate balises, bounded by switches and END nodes. ORIGIN is an observed frontier, not a physical buffer stop. Missing reverse edges, unknown or faulty connectivity, loops and coincident markers are unavailable. No chunks are loaded. Confirmed unloaded topology remains usable.

Neighbouring depot markers on the same road, including markers owned by different depots, split the along-track distance at the midpoint. Each side gives up half the net gap. Outer boundaries retain a switch/end clearance. With usable range [A,B], full conservative train length L and stopping tolerance T, the legal centre range is [A+L/2+T, B−L/2−T]. Clamp the sign position into that range. An empty range rejects that berth; another reachable berth is considered. Curved paths and blocksPerMeter are respected.

Single-ended switch-to-END roads fill the deepest reachable berth first. Double-ended switch-to-switch roads evaluate legal directed approaches and prefer shorter reachable paths. Dispatch excludes a consist blocked by another stored train; inbound selection checks both the path and full fitted footprint. All body samples and their connecting graph paths form the obstruction envelope. Missing or stale observations never prove clearance. There is no automatic reshuffling, cross-berth merging, manual boundary override or newly configurable entrance-role restriction in this release.

STCS still arbitrates resources, points, MA and ATP limits; a geometric candidate is not permission to move. Existing graph directions are respected without treating current physical switch state as a topology restriction.

## Contract and execution

AutomaticTrainService.View adds immutable ordered body samples. STF checks every member for freshness and world consistency; old multi-car feedback without body evidence cannot capture a berth. ParkingTarget uses Q_TP_Alignment=1 and an explicit Q_Stop_Location_Tolerance. SAL computes a front-member destination for a centre-aligned stop, while STF rechecks actual middle position at capture/handover.

PCC ParkingGeometry carries Q_TP_Alignment, D_Location, Q_Stop_Location_Tolerance, usable length, maximum train length, access type, reason and curved boundary geometry. Active-task projection includes stoppingCenter and stoppingOffsetBlocks.

Names follow [SUBSET-126 v1.0.0](https://www.era.europa.eu/system/files/2023-09/index085_-_SUBSET-126_v100.pdf). Values here are resolved Minecraft blocks / graph metres, not the standard's centimetre encoding, 5-bit tolerance enumeration or an interoperable wire protocol. Midpoint partition and target clamping are project policies.

## Configuration, display and recovery

SAL configuration defaults: depot.net-gap-blocks=2, depot.boundary-margin-blocks=2, depot.stopping-tolerance-blocks=0.5. Missing old keys use those defaults. Gap must be nonnegative, margin at least1, tolerance0.06–0.75 blocks; non-finite values reject enablement.

Select a depot in PCC to inspect capacity, single/double-ended access, unavailable reasons and the along-track berth envelope. Planned-centre rings show task offsets. Stale observations do not count as parked evidence.

Layout caching depends on graph revision, markers and settings. A selected target stays fixed rather than drifting with tiny observation changes. Invalidated capacity causes a stop and reselection. Persisted targets do not resume trains: restart still places tasks in RECOVERY_REQUIRED and requires existing explicit administrator recovery. Keep all occupancy ledgers, trains, graph, depot bindings, drafts and publications.

[Chinese design and acceptance cases](SAL-CENTRE-STABLING.md) · [Validation scope](../../VALIDATION.md)

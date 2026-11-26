# SkyRail — Your first manually driven train

**[ADDED / FIXED · SAL 0.8.0 / STF 5.1.4 · 2026-11-26]** Depot prompts retry and refresh expired consent with the STF stop threshold. Pickup and return share segmented intermediate-reversal routing through STCS release and STA reversal. Chunk-loading holds retain manual hotbar power selection while inhibiting actual traction. [Details](../SAL-MANEUVERS-AND-HANDOVER.en.md)

**[FIX · SAL 0.7.7 / STCS 4.3.6 · 2026-11-26]** Concurrent returns retain their selected path and berth; STCS handles temporary throat traffic by limiting/extending MA. Automatic point alignment cannot withdraw another train’s MA. Destination clearance, stored-train obstacles and real-fault checks remain. [Details](../SAL-RETURN-TRAFFIC.en.md)

**[CHANGED · STF 5.1.3 · 2026-11-26]** Automatic driving replaces fixed P2/B4 with P4 departure, tapered traction near cruise, B6 service braking and B7 hold. Handle ceilings are configurable; MA, ATP, TIMS and vehicle limits remain effective. [Details](../AUTOMATIC-DRIVING-HANDLES.en.md)

**[FIX · SAL0.7.6 · 2026-11-26]** Return berth/obstacle/reselection holds renew the already accepted route lease, avoiding1500ms heartbeat starvation. A default2s recheck retains the target; transient body uncertainty is not labelled topology change. Persistent unknown geometry faults; true berth/space changes reselect only after stopping. Detailed reasons and explicit dispatch-history labels are added.

**[ADDED / FIXED · 2026-11-26 · SAL0.7.6 / PCC2.6.4]** Rail coordinates and heading now come from one STF telemetry frame. Uncertainty brakes immediately with a default2s recheck; bounded endpoint tolerance0.75blocks and projection tolerance1.5blocks are configurable. Independent wrong-branch confirmation recovers earlier. No new/extended MA is granted. Original failures persist in /sal status and are visible in PCC event text.

**[ADDED / FIXED · 2026-11-26]** STA2.9.2 / STF5.1.2 / SAL0.7.6: admin /sal override <train name or UUID> brakes, confirms MA withdrawal, releases SAL and reserves manual control; use /st drive after completion. Body occupancy, TIMS and TR/PT remain, with no movement authority granted. Slope localisation uses STF-observed railXYZ through existing STA telemetry instead of flooring entity coordinates.

**[ADDED · SAL0.7.6 · 2026-11-26]** Pending/expired calls explain each vehicle’s eligibility, forward/reverse path attempts, pickup estimate cap, other trains with stale/unknown/off-graph bodies, obstructed edges and stale computation/graph results. Existing safety gates remain; another train’s TIMS hold alone does not globally block routing. Reports are bounded, not exhaustive network analysis. Existing PCC displays the reason field without an API change.

**[CHANGED · SAL0.7.6 / PCC2.6.4 · 2026-11-26]** Ordered visits select the shortest legal directed track path; longer alternatives no longer cause ambiguity rejection. Explicit devices/edges constrain the choice. Direction, graph faults, closure and MA checks remain. PCC follows actual rail geometry, including confirmed prefixes of unfinished drafts; only unconfirmed connections are estimated.

**[ADDED / CHANGED · 2026-11-26]** PCC2.6.4 / SAL0.7.6 / STA2.9.2: validation identifies the first failing directed visit pair by index, device label and ID. Both list endpoints are marked; click the diagnostic to jump there. Fix it and validate again rather than guessing later arrival directions. Reorder with the right-hand ≡ grip using mouse/touch; release submits once, cancellation submits nothing. Focus the grip and use arrow keys for keyboard sorting. Visit UUID, dwell and reversal stay attached. Reordering is staged; Save→Validate→Publish remains explicit.

**[FIX · PCC2.6.4 · 2026-11-26]** Polling preserves unchanged SAL click targets and route-list buttons. The narrow-screen editor stays visible without a selected train. SAL clicks bypass map drag capture; previews do not intercept stop selection. Temporary catalog failure preserves staged work and typed input while stale data disables writes. Actual cross-editor conflicts still require manual resolution. After replacing PCC, refresh with Ctrl+F5.

**[2026-11-26] Public stops and shared dispatch.** Existing SAL parked stock needs no new consent. A stopped manual train overlapping depot gets a six-digit code after3s, or request it immediately with /sal handover. Confirm /sal handover <code>, then the driver executes /st release. Release alone is not consent. /sal release releases returned standby stock, then /st drive takes manual control.

station line4 is now a unique stop name; placing a sign never appends a route. awake line3 is operating route and line4 is departure stop. Legacy awake signs are disabled until rewritten; legacy stations migrate to unique names: inspect /sal station list first. Depot signs and existing vehicle homes are retained.

[Chinese production test guide](../../../SkyATO-Lite/TEST.zh.md) · [Shared-dispatch contract](../SAL-SHARED-DISPATCH.en.md)

**[FIX · 2026-11-25]** STF5.1.2 / SAL0.7.6: default manual FS/MA request waits for forward/reverse selection, not neutral driver acquisition. A stopped train overlapping a depot marker receives a random six-digit handover code after3seconds. `/sal handover` requests one immediately while stopped over the marker; `/sal handover <code>` confirms, then `/st release` commits. Automatic return still uses centre alignment.


**[CHANGED · 2026-11-21]** STA2.9.2 / STF5.1.2 / STCS4.3.5 / SAL0.7.6 / PCC2.6.4

[Detailed English manual](../../README.en.md) · [README](../../../README.md)

Default FS request does not mean FS has already been granted. Manual driving explicitly acquires a driving lease; SAL also needs executable authority. SB, recovery or unavailable authority keeps brakes held.

FS supervises speed/EoA against the granted curve. SH/SR default to40km/h, subject to any lower curve. After a TR stop: ack→PT, release→SB, inspect and request again. TIMS holds first require genuine integrity restoration and acknowledgment.

After the initial path submission, silent renewal keeps the route lease while STCS extends MA. Do not submit a new visible request every half-second. Changed path/version, lost control or expired lease needs a new valid request; a late heartbeat cannot revive it.

Explicit shadow testing remains simulationOnly=true/executable=false and cannot stand in for executable authority. BYPASS, ISOLATED and RECOVERING retain their separate interlocks.

MA release keeps body occupancy and releases only eligible forward reservations. Unloading, timeout and disconnection do not prove clearance. Explicit train removal needs complete STF destruction receipts and virtual-driver cleanup before scoped evidence release; other trains remain protected.


**[CHANGED · 2026-11-21]** Default FS request does not mean FS has already been granted. Manual driving explicitly acquires a driving lease; SAL also needs executable authority. SB, recovery or unavailable authority keeps brakes held.

Place several minecarts on a straight, loaded test track with no other trains nearby. Use a small scan radius to avoid including a cart on an adjacent track.

As administrator:

```text
/st scan 6
/st create demo 6
/st info demo
/st property demo trainnumber T001
/st property demo mode manual
```

Board a cart, then:

```text
/st lang en
/st drive
/st forward
/p1
/n
/b3
/b7
```

- `P1..P4`: power notches; `B1..B7`: service-brake notches; `EB`: emergency brake.
- `/n` is neutral on the master controller; `/st neutral` is neutral on the reverser.
- Reversing is subject to a low-speed/standstill check. It is not a substitute for braking.
- Claim control with `/st drive` after every boarding. Other driving commands do not claim it implicitly.
- Leaving, disconnecting, dying, changing trains or losing the driving-seat entity revokes the driver's control, removes traction and applies EB. Control is not handed to another passenger automatically.
- `/st release` explicitly releases control and holds the brakes. Automatic handover requires prior SAL consent at a depot berth.

### Hotbar Control

Select **slot 5** before enabling `/st hotbar`. Enabling it does not input N or release an existing brake application. Subsequent slot changes select notches:

| Slot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Notch | B7 | B5 | B3 | B1 | N | P1 | P2 | P3 | P4 |

Disabling with `/st hotbar` does not require slot 5. `/st cab` opens the graphical cab. Both require driving control first.



## Manual/automatic handover

Automatic→manual: `/sal release` only for a completely stopped, complete PARKED depot-standby train without a running job or fault hold. Wait for release, then `/st drive` from an end cart. A train still returning cannot use this shortcut.

Manual→automatic: keep the driving lease, stop at a valid berth continuously for3seconds, confirm SAL handover, then explicitly `/st release` within30seconds or click the second button. Success assigns the actual depot and enters standby; it does not depart.

Release without prior confirmation, leaving, disconnecting, movement, another driver, leaving the berth or expiration cannot complete handover. Manual state persists. Drivers need skyatolite.driver and skytrain.use; route/home administration needs skyatolite.admin.

## Permissions, persistence and diagnosis

- No call: check publication, the awake rising edge, depot/route names, manual standby, freshness, TIMS and existing jobs. Opposite train orientation alone should not require manually turning the train.
- Disconnected stop: inspect RailGraph/routingIssues and locally rescan new loaded track. A chunk_not_loaded entry in unresolved alone is not a broken route.
- Depot hold: check available berth, whole-train space, path, physical point confirmation and MA reason; do not spam awake or clear ledgers.
- Occupancy after removal: verify the removed UUID, full destruction receipt and virtual-driver cleanup. A missing/unknown entity is not proof of safe deletion.
- Operating display unavailable: check SAL/STA services, graph revision and freshness; hard-refresh scripts. Infrastructure and operational display have distinct semantics.

~~Shadow MA only; ATP inactive / 默认影子MA，ATP不动作~~

[Complete previous manual (historical baseline, not current instructions)](../manual-baseline-20261120/driver/README.en.md)


## **[2026-11-24] Added / fixed: PCC 2.4.1 map display**

~~Browser-native focus outlines around selected track or devices.~~ Selection now follows the actual track polyline or a small device ring with screen-sized strokes. Map zoom cannot inflate the outline; mouse selection and keyboard focus retain visible feedback.

Beside the A− / A+ text controls, **H− / H+** adjusts highlight size (50%–250%). Click its percentage to reset to100%; use the colour swatch to change colour. Size and colour are stored in this browser; controls still work if storage is unavailable.

Infrastructure devices has **Balise, END, ORIGIN and Switch** children. The parent selects or clears all; children can be toggled independently, with an indeterminate parent for a partial selection. Markers, labels and switch-direction arrows are filtered together. Base track and SAL layers remain independent.

These are display settings only: they do not edit RailGraph, devices, routes, MA or occupancy. Use Ctrl+F5 when updating an old browser page.


**[BREAKING · 2026-11-25]** Depot stops now align the consist middle. Clamp the planned centre inside its own berth; reject an oversized train and consider another berth. Neighbouring markers split along-track space with a net gap. Single-ended roads fill deep first; double-ended roads consider both legal approaches. Body obstructions exclude blocked departures and arrivals. PCC displays capacity, access type, boundaries and target offset. Manually reposition old front-aligned trains; station semantics stay unchanged.

[Centre parking contract](../SAL-CENTRE-STABLING.en.md) · [中心停车契约](../SAL-CENTRE-STABLING.md)

## Operation notes · SAL 0.8.0 / STF 5.1.4

Stop with any body part covering depot for3seconds, or use /sal handover immediately. Confirm within30seconds using /sal handover CODE, then explicitly /st release. Release alone is not consent. Undelivered prompts retry; expired codes refresh. Pickup/return allow up to4 reversals with whole-body clearance; short/occupied roads and protection holds remain blocking. Restarted tasks need administrator recovery. Chunk waits keep handles but inhibit actual traction; newer neutral/brake input wins.

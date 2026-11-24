# SkyRail — Your first manually driven train

**[CHANGED · 2026-11-21]** STA2.7.0 / STF5.0.2 / STCS4.3.5 / SAL0.5.0 / PCC2.4.1

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

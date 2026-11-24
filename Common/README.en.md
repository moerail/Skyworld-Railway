# SkyRail Suite — Detailed English manual

## **[2026-11-24] Added / fixed: PCC 2.4.1 map display**

~~Browser-native focus outlines around selected track or devices.~~ Selection now follows the actual track polyline or a small device ring with screen-sized strokes. Map zoom cannot inflate the outline; mouse selection and keyboard focus retain visible feedback.

Beside the A− / A+ text controls, **H− / H+** adjusts highlight size (50%–250%). Click its percentage to reset to100%; use the colour swatch to change colour. Size and colour are stored in this browser; controls still work if storage is unavailable.

Infrastructure devices has **Balise, END, ORIGIN and Switch** children. The parent selects or clears all; children can be toggled independently, with an indeterminate parent for a partial selection. Markers, labels and switch-direction arrows are filtered together. Base track and SAL layers remain independent.

These are display settings only: they do not edit RailGraph, devices, routes, MA or occupancy. Use Ctrl+F5 when updating an old browser page.


## **[ADDED / CHANGED · 2026-11-24] Unified PCC map and route editing**

One RailGraph canvas has independent infrastructure, SAL, operating-route and live layers. Enter the route editor with the existing switch-control token; select a route and Edit selected route, or select at least one registered depot and create a draft.

Register depot first. Pick balises, points, directed graph edges and matching station signs; reorder visits and set dwell/reversal without rewriting signs. Save, validate and publish separately. Existing jobs retain their own published version.

Concurrent in-game/PCC editing freezes both parties and rejects saving, validation, publication and deletion, including after restart. An administrator must discard or adopt a proposal, then save/publish explicitly. Finish game editing with `/sal route close <line>`; successful publish closes its game session.

`/sal route create Line1 maintown` · `/sal route depot Line1 maintown,otherdepot` · `/sal route resolve Line1 DISCARD`

[Unified PCC map and route editing](doc/PCC-ROUTE-EDITING.en.md)


[中文详细手册](README.zh.md) · [Detailed English manual](README.en.md) · [Manuel détaillé français](README.fr.md) · [Uitgebreide Nederlandse handleiding](README.nl.md) · [日本語詳細マニュアル](README.ja.md) · [Подробное руководство на русском](README.ru.md) · [文語體鐵道運轉詳細規程](README.ja.classical.md)

Distribution dated 2026-11-24, consolidated from the previous detailed Manuel and the final implementation of this development round. Unchanged driving, vehicle, infrastructure and sound explanations are retained. Bold labels identify additions, changes, removals and query changes; strikethrough preserves superseded instructions. The previous manual is historical only.

## Read these breaking changes first

- The legacy STF automatic station/cruise/start/stop/spawn/destroy/property execution chain and sign poller are removed, with no compatibility switch to reactivate them. Existing signs and files remain but no longer execute automation.
- ~~`/st property demo mode auto` / `mode automatic`~~ is removed. SAL owns automatic operation; STF retains manual driving, vehicle actuation and infrastructure equipment.
- ~~Running `/st release` alone consents to automatic takeover~~: the driver must first confirm SAL handover at a berth, then explicitly release the same driving lease.
- ~~Shadow MA by default; no ATP intervention~~: new driving sessions request FS by default with executable protection; SAL uses the same MA/ATP protection. Brakes remain held until a valid authority is granted. Explicit shadow testing remains available.
- ~~`scan.only-loaded-chunks: false` loads chunks while building the graph~~: topology maintenance now scans loaded areas only and migrates that value to true. Unloading proves neither disconnection nor clearance.
- Replace the five plugins as one compatible set. Preserve worlds, consists, RailGraph, SAL state, confirmed destruction receipts and every occupancy ledger. Roll back binaries and data together from the same backup.

**[ADDED · 2026-11-21]** This release delivers SAL preset routes and depot movements, bidirectional manual/automatic handover, incremental RailGraph maintenance, executable MA/ATP presentation, an independent PCC operating map and themed commands. SAL uses a blue `[SkyATO-Lite]` prefix; STF is grey and STCS yellow. Legacy default prefixes migrate; custom prefixes remain. Version output includes SAL and no longer claims ATP is inactive.

| File / component | Version | Responsibility |
| --- | --- | --- |
| SkyworldTrainAPI / STA | **2.7.0** | Typed contracts, telemetry and driving/dispatch services; not a server core |
| SkyTrainFolia / STF | **5.0.2** | Consists, entity physics, chunk tickets, manual driving, TIMS, ATP and actuation |
| STCS | **4.3.5** | Infrastructure, RailGraph, localisation, occupancy, reservations, MA and route/point arbitration |
| SkyATO-Lite / SAL | **0.5.0** | Operating plans, calls, dwell, passenger titles, depot return and handover; no direct entity manipulation |
| SkyPCC / PCC | **2.4.1** | Infrastructure/operating web maps, depot lists and controlled dispatch actions |

## Contents

1. [Purpose, components and final deliverables](#section-1)
2. [Installation, migration and rollback](#section-2)
3. [Permissions and languages](#section-3)
4. [Your first manually driven train](#section-4)
5. [Command reference](#section-5)
6. [Vehicle profiles and properties](#section-6)
7. [Building lines and turnouts](#section-7)
8. [SAL routes, signs and driving-control transfer](#section-8)
9. [Executable MA, default FS and ATP](#section-9)
10. [SkyPCC infrastructure and operating maps](#section-10)
11. [Sounds and HMI](#section-11)
12. [STA contracts and HTTP endpoints](#section-12)
13. [Persistence and troubleshooting](#section-13)
14. [Acceptance checklist and limits](#section-14)
15. [Development, source and distribution](#section-15)

<a id="section-1"></a>

## 1. Purpose, components and final deliverables

**[CHANGED · 2026-11-21]** A SAL operating route is an ordered service/stop plan. A STCS infrastructure line provides attribution and mileage. They are different concepts. SAL consumes RailGraph; STCS arbitrates routes and authority, using STA/STF for physical execution. Executable game ATP is not a certified ETCS product.

| File / component | Version | Responsibility |
| --- | --- | --- |
| SkyworldTrainAPI / STA | **2.7.0** | Typed contracts, telemetry and driving/dispatch services; not a server core |
| SkyTrainFolia / STF | **5.0.2** | Consists, entity physics, chunk tickets, manual driving, TIMS, ATP and actuation |
| STCS | **4.3.5** | Infrastructure, RailGraph, localisation, occupancy, reservations, MA and route/point arbitration |
| SkyATO-Lite / SAL | **0.5.0** | Operating plans, calls, dwell, passenger titles, depot return and handover; no direct entity manipulation |
| SkyPCC / PCC | **2.4.1** | Infrastructure/operating web maps, depot lists and controlled dispatch actions |

**[ADDED · 2026-11-21]** This release delivers SAL preset routes and depot movements, bidirectional manual/automatic handover, incremental RailGraph maintenance, executable MA/ATP presentation, an independent PCC operating map and themed commands. SAL uses a blue `[SkyATO-Lite]` prefix; STF is grey and STCS yellow. Legacy default prefixes migrate; custom prefixes remain. Version output includes SAL and no longer claims ATP is inactive.

**[CHANGED · 2026-11-21]** Directional preferences from `<line>:up|down`, whole single-track-section reservations, general routing with multiple intermediate reversals, and the Python computation daemon remain design proposals. Existing depot routing can evaluate both departure directions at standstill; it does not promise arbitrary multi-reversal shunting.

[TRAFFIC-PLANNING-DAEMON-DESIGN](doc/TRAFFIC-PLANNING-DAEMON-DESIGN.md)

<a id="section-2"></a>

## 2. Installation, migration and rollback

**[CHANGED · 2026-11-21]** Requirements: Java25 and the adapted Shiroha/Folia26.2 or26.3. PCC needs a normal browser, not a client mod or TrainCarts/BKCommonLib. This does not guarantee every Folia fork/version.

1. Extract the complete ZIP. Back up worlds and plugins, stop the server, then copy the five JARs from the distribution plugins/ into the server plugins/. Remove older copies of the same modules; do not install the documentation folder as a plugin.
2. Start to generate defaults and verify all five plugins enable. Stop, merge configuration, and restart fully. Do not hot-unload the suite. Not every missing setting is automatically written into older files.
3. Check `/st version en`, `/stcs status` and `/sal help`. Open http://127.0.0.1:8765/ on the server machine and hard-refresh after web updates. Remote publishing needs administrator-managed proxy/authentication.
4. `/st reload` also reloads train data: stop every train first. STCS/PCC configuration requires a restart. Persisted SAL jobs restart as RECOVERY_REQUIRED; inspect them and resume by returning to the depot, not by blindly continuing.
5. Never clear ledgers as an upgrade step. SHA256 lists identify files, not compatibility of arbitrary world/data combinations. Source is archived under Source; linked contracts and images are included.

```text
SkyworldTrainAPI-2.7.0.jar
SkyTrainFolia-5.0.2.jar
STCS-4.3.5.jar
SkyATO-Lite-0.5.0.jar
SkyPCC-2.4.1.jar
```

[SHA256](../Latest/SHA256.json) · [LICENSE](LICENSE) · [ASSET-LICENSE](ASSET-LICENSE.md)

<a id="section-3"></a>

## 3. Permissions and languages

**[ADDED · 2026-11-21]** Release without prior confirmation, leaving, disconnecting, movement, another driver, leaving the berth or expiration cannot complete handover. Manual state persists. Drivers need skyatolite.driver and skytrain.use; route/home administration needs skyatolite.admin.

| Permission | Default | Purpose |
| --- | --- | --- |
| `skytrain.use` | Everyone | Basic queries, personal language/units and normal driving commands |
| `skytrain.admin` | OP | Consists, properties, templates, remote control, turnout management, save/reload |
| `stcs.use` | Everyone | Nearby infrastructure and graph status |
| `stcs.ma` | Everyone | Driver MA demand/release; valid driving control and an allowed mode are also required |
| `stcs.admin` | OP | Infrastructure registration, rebuild/export, occupancy/MA diagnostics, protection modes and turnout management |

Permission is not a driving-control lease. `stcs.ma` does not allow one player to request authority for another driver. Protection-mode administration requires the administrator to be seated in the target train. Location-dependent commands are not all usable from the console.

PCC write access uses a separate token plus loopback and same-origin checks; it does not inherit Minecraft OP permissions. Do not distribute the control token to ordinary passengers.

```text
/st lang en
/st lang zh
/st lang fr
/st lang jp
/st help 2 en
/stcs help 1 fr
/sta version ja
/skypcc help
```

Both `ja` and `jp` select Japanese. All four roots provide `help` and `version`. HMI/protection/MA text follows the player's STF language preference. Some older administrative diagnostics remain in fixed Chinese or English. **Dutch documentation does not add a Dutch in-game language.**

PCC stores its language selection separately in the browser.

<a id="section-4"></a>

## 4. Your first manually driven train

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

<a id="section-5"></a>

## 5. Command reference

`<...>` is required and `[...]` optional. These are the recommended forms, not every legacy alias.

### Driver and Query Commands

| Command | Purpose |
| --- | --- |
| `/st help [page] [language]`, `/st version [language]` | Help and installed versions |
| `/st list`, `/st info [train]` | Trains, state and automatic-sign blocking reasons |
| `/st scan [radius]` | Find nearby minecarts |
| `/st drive`, `/st release` | Claim/release driving control |
| `/st cab`, `/st hotbar` | Cab/hotbar interface |
| `/st forward`, `/st neutral`, `/st backward` | Reverser |
| `/st p1` through `/st p4`, `/st n`, `/st b1` through `/st b7`, `/st eb` | Master controller; root shortcuts such as `/p1`, `/n`, `/b7` also exist |
| `/st horn`, `/st bell` | Horn/bell |
| `/st lang zh\|en\|fr\|jp\|ru` | Player language |
| `/st speedunit kph\|mph\|block/tick` | Display units, alias `/st unit`; does not change numeric management-command units |
| `/st balise [info]`, `/st origin [info]`, `/st end [info]` | Nearby infrastructure |
| `/st mileage [train]` | Mileage; naming a specific train requires admin permission |

### STF Administration

| Command | Purpose |
| --- | --- |
| `/st connect [radius]` | Couple nearby carts |
| `/st create <name> [radius]` | Create a consist |
| `/st append <train> [radius]` | Add members; use fixed consists for initial train-control tests |
| `/st unlink` | Unbind the nearest cart |
| `/st remove <train>` | Remove the train definition, not a shortcut for clearing STCS blockers |
| `/st start <train> [speed]`, `/st stop <train>` | Legacy target-speed control/stop, not MA approval |
| `/st reverse <train>` | Reverse, subject to standstill checks |
| `/st speed <train> <speed>`, `/st maxspeed <train> <speed>` | Target/cap in blocks per tick |
| `/st spacing <train> <spacing>` | Member spacing, subject to global tight-spacing settings |
| `/st property <train> <key> [value]` | Read without a value; write with a value |
| `/st tag <train> add\|remove\|list [tag]` | Tags |
| `/st owner <train> add\|remove\|list [player]` | Owner metadata |
| `/st route <train> set\|add\|clear\|list [destination...]` | Destination list, not complete automatic route setting |
| `/st savedtrain list` | List templates |
| `/st savedtrain save <train> <template>` | Save a template |
| `/st savedtrain spawn <template> [train]` | Spawn near the player |
| `/st switch list`, `/st switch scan [radius]` | Registered turnouts/loaded-area scan; default 32, maximum 128 blocks |
| `/st switch info`, `/st switch set straight\|diverging` | Query/request a state for the nearest turnout within 8 blocks |
| `/st switch remove`, `/st switch cleanup` | Remove nearest definition/clean invalid registrations; remove leaves world blocks intact |
| `/st balise list`, `/st origin list`, `/st end list` | Infrastructure lists |
| `/st clearkm <line>` | Clear mileage calibration, not occupancy |
| `/st admin release\|p1..p4\|b1..b7\|n\|eb <train>` | Explicit remote administration |
| `/st syncstatus` | Display-synchronisation diagnostics |
| `/st save`, `/st reload` | Save/reload; stop all trains before reload |

Property syntax also accepts `property <train> get <key>` and `property <train> set <key> <value>`. Examples use the shorter form.

### STCS

| Command | Permission / meaning |
| --- | --- |
| `/stcs help [page] [language]`, `/stcs version [language]` | Help/version |
| `/stcs inspect` | `stcs.use`; marker within 3 blocks |
| `/stcs status` | `stcs.use`; graph nodes/edges/revision, not the train's ATP mode |
| `/stcs ma fs` | **[CHANGED]** Default FS request does not mean FS has already been granted. Manual driving explicitly acquires a driving lease; SAL also needs executable authority. SB, recovery or unavailable authority keeps brakes held. |
| `/stcs ma sh` | Request bounded SH permission after stopping |
| `/stcs ma sr` | Request SR after stopping; dispatcher approval is required |
| `/stcs ma ack` | At standstill, acknowledge TR and enter PT |
| `/stcs ma release` | `stcs.ma` and current driver; release forward reservations, not body occupancy |
| `/stcs ma status` | `stcs.admin`; authority/blocker diagnostics |
| `/stcs admin status` | `stcs.admin`; protection state of the train being ridden |
| `/stcs admin isolate true\|false` | Train-control isolation |
| `/stcs admin bypass true\|false` | Supervision bypass |
| `/stcs admin shadow true\|false` | Shadow testing |
| `/stcs admin enforce true\|false` | Explicit experimental active channel; `false` returns to RECOVERING |
| `/stcs sr <train-name> <target-device-uuid>` | `stcs.admin`; approve pending SR to a reachable infrastructure node |
| `/stcs occupancy [train-name\|uuid]` | `stcs.admin`; retained occupancy/member observations |
| `/stcs rebuild` | **[CHANGED]** STCS owns the physical graph. Equipment/track edits trigger local updates; point setting alone is not topology. rebuild prioritises loaded sources near the administrator, then other loaded sources, stopping at unloaded boundaries. |
| `/stcs export` | `stcs.admin`; export current graph, not a rescan |
| `/stcs switch info` | `stcs.use`; nearest turnout within 8 blocks |
| `/stcs switch change` | `stcs.admin`; change nearby turnout |

~~The player command is **`demand`, not `request`**. Internal events may still be named `MA_REQUESTED`. `/sta` and `/skypcc` primarily expose help/version, not a second set of driving controls.~~

**[CHANGED · 2026-11-21]** `/stcs ma fs` · `/stcs ma demand` (alias)

**[QUERY · 2026-11-21]** `/sal help` · `/sal status` · `/sal route list Line1` · `/sal train home`

Release without prior confirmation, leaving, disconnecting, movement, another driver, leaving the berth or expiration cannot complete handover. Manual state persists. Drivers need skyatolite.driver and skytrain.use; route/home administration needs skyatolite.admin.

<a id="section-6"></a>

## 6. Vehicle profiles and properties

### Server-wide Profile

In `plugins/SkyTrainFolia/config.yml`:

```yaml
settings:
  default-vehicle-profile: minecraft-comfort
  server-speed-limit-kmh: 420.0
```

Files live in `plugins/SkyTrainFolia/vehicles/<id>.yml`:

| ID | Reference |
| --- | --- |
| `crh380b` | CRH380B |
| `cr400bf` | CR400BF |
| `keikyu-n1000` | Keikyu New 1000 series |
| `minecraft-comfort` | Fictional game-oriented profile |

Real-world profiles are tuning approximations, not certified performance data. The current selection is **server-wide**; there is no per-train `/st property <train> profile ...` command.

A profile represents a complete reference train. Minecraft cart count does not automatically scale its reference mass/performance. Actual consist length still matters for occupancy, station stopping and the capacity needed beyond a junction.

| Profile field | Effect |
| --- | --- |
| `physics-mode` | Physics model; bundled profiles use `force` |
| `mass-tonnes` | Complete reference-train mass |
| `max-speed-kmh` | Vehicle cap, not a guarantee that traction can reach it |
| `traction.acceleration-mps2.p1` through `p4` | Low-speed gross traction acceleration before resistance |
| `traction.base-speed-kmh` | Base speed of the traction curve |
| `traction.field-weakening-speed-kmh`, `minimum-ratio` | High-speed traction reduction/minimum ratio |
| `brake.deceleration-mps2.b1` through `b7` | Gross service-brake deceleration |
| `brake.emergency-mps2` | Emergency-brake contribution |
| `resistance.rolling-mps2` | Rolling resistance |
| `resistance.air-mps2-at-100-kmh` | Air-resistance reference at 100 km/h, scaling with speed squared |
| `resistance.grade-mps2` | Gradient acceleration factor |
| `control.direction-change-speed-kmh` | Low-speed threshold for reversing |
| `automatic.*` | **[CHANGED]** Consists, entity physics, chunk tickets, manual driving, TIMS, ATP and actuation / V_target |


Old scattered keys such as `drive-power-acceleration-p1` are not the current profile-editing interface. Back up before editing and stop all trains before reloading, or edit while stopped and restart.

### Why a Train May Still Be Limited to 130 km/h

The vehicle cap, server cap and persisted per-train `maxspeed` can all limit speed. Changing the default profile does not erase an existing train cap.

At the nominal 1 block = 1 metre and 20 TPS scale, `km/h = blocks/tick × 72`:

```text
/st property demo maxspeed
/st maxspeed demo 5
```

Here `5` means 360 km/h, not 5 km/h or the string `5kmh`. Lower vehicle/server caps still apply, and traction/resistance balance may prevent reaching the cap. Personal display units do not change command parsing.

### Train Identity and Service Number

```text
/st property demo name test01
/st property test01 displayname Test Train
/st property test01 trainnumber G001
/st property test01 trainnumber
/st property test01 trainnumber clear
/sal release
/st property test01 pushable true
```

- `name` identifies the managed train in commands. Use the new name after renaming.
- `trainnumber` is a separate service/run number. Leading zeros are preserved; maximum 32 characters; no control characters or `|`; uniqueness is not enforced. `clear` or `-` removes it.
- `displayname` is presentation metadata, not the internal UUID.
- Other properties include `destination`, `collision`, `playersenter`, `playersexit`, `pickupitems`, `invincible`, `allowplayertake`, `requirepoweredcart`, `sound`, `keepchunksloaded`, `gravity`, `friction`, `waitticks`, `speed`, `maxspeed`, `spacing`.
- `pushable=true` is not sufficient on its own: driver ownership, an unreleased manual takeover or running state can still block pushing. Inspect `/st info`.
- Owner/tag/route metadata does not imply complete routing, dispatching or owner-based access isolation.

<a id="section-7"></a>

## 7. Building lines and turnouts

**[CHANGED · 2026-11-21]** STCS owns the physical graph. Equipment/track edits trigger local updates; point setting alone is not topology. rebuild prioritises loaded sources near the administrator, then other loaded sources, stopping at unloaded boundaries.

### Physical Topology vs Line Attribution

RailGraph stores nodes, ports, legal transitions, directed edges and geometry. Line names/mileage are annotations, not information that may spread arbitrarily through every connected turnout.

- Use unique line names, such as `test_up` and `test_down`, consistently on the relevant balises.
- Origin/End delimit line attribution; they need not be physical track ends. Track beyond them should retain its physical connection.
- An unlabelled siding balise marks the siding without propagating the main-line identity indefinitely.
- If several paths connect the same line-labelled anchors, add a labelled balise on the intended main-line branch. Current turnout position is not permanent line attribution.
- Unknown mileage remains unknown; proximity to another marker is not enough to invent it.

### Four-line STCS Signs

Place signs where association with the intended rail is unambiguous, especially near parallel tracks.

Origin:

```text
[STCS]
origin
test_up
right
```

Main-line balise:

```text
[STCS]
balise
test_up
0010
```

End:

```text
[STCS]
end
test_up
right
```

Siding balise, with line 3 blank:

```text
[STCS]
balise

5010
```

Origin line 4 points into the line: mileage starts at zero towards that side. End line 4 points towards the side from which it receives mileage; it explicitly ends this line's attribution, not the physical track. Both use `left`/`right`, never a name. Relative directions are interpreted against the sign face, not the left/right of the PCC screen. `signal` can be registered as a node, but does not imply implemented signal-based ATP.

### STF Turnout Signs

STCS imports registered STF turnouts, not every curved rail. Registration recognises `[stf]`, `[+stf]`, `[SkyTrain]`, `[+SkyTrain]` case-insensitively. Redstone activation and infrastructure existence are separate: adding `+` is not a general missing-edge fix.

Example using an existing front-mounted layout:

```text
[SkyTrain]
switch
fl
S01
```

Line 4 names the turnout; the UUID remains its identity. **STF `switch` is not TC `switcher`.**

Wall signs support the existing side/below-track layouts. The `fl/fr` front layout expects the pivot rail two blocks above the sign's backing block. A ceiling-hanging sign supports sign → support block above → pivot rail above that, aligned to a cardinal direction rather than diagonally. Use normal switchable rail for the pivot. A bound lever follows turnout changes and notifies neighbouring redstone blocks; validate complex circuits in-game.

After construction:

```text
/st switch scan 32
/st switch list
/stcs inspect
/stcs rebuild
/stcs status
/stcs export
```

Rebuild starts from registered infrastructure; it is not an unlimited world scan. Confirm registration after WorldEdit. By default only loaded chunks are scanned, retaining existing topology where scanning is incomplete. Previously unobserved track cannot be reconstructed from nothing.

```yaml
scan:
  max-distance-meters: 256.0
  blocks-per-meter: 1.0
  marker-rail-search-radius: 3.0
  only-loaded-chunks: true
graph:
  file: railgraph.json
  pretty-print: true
```

This scan distance is not the MA look-ahead distance. Long sections need appropriate anchors or a larger scan bound. Keep STF/STCS distance scales consistent, normally 1 block per metre.

<a id="section-8"></a>

## 8. SAL routes, signs and driving-control transfer

**[ADDED · 2026-11-21]** Preset service runs once per call, not indefinitely or to a timetable. The first and last visits must be stations; repeated visits are supported. Bind intermediate balises/turnouts where needed to remove branch ambiguity. SAL does not spawn trains: create an STF consist and valid depot first.

### Commands and device selection

1. Create Line1 facing its initial departure direction, or set a cardinal direction explicitly.
2. Place the first station, then bind graph devices in service order while standing beside them. Multiple candidates within8blocks require choose within60seconds; an explicit device UUID or station ID is also accepted.
3. New station signs append visits in placement order. Check list and remove a visit by its one-based index. Bind the first station again if the service ends there.
4. Add reverse to a station binding for reversal after that visit. This is not unrestricted automatic reversal anywhere.
5. Preview lasts two minutes: green for a valid loop, red otherwise. Green means neither clear track nor MA. validate persists validation; publish enables service calls.
~~6. Routes with jobs cannot be edited. Edits, changed station references or changed dependencies need validation; unrelated graph changes preserve publication.~~

**[ADDED / CHANGED]** Register depot first. Pick balises, points, directed graph edges and matching station signs; reorder visits and set dwell/reversal without rewriting signs. Save, validate and publish separately. Existing jobs retain their own published version.
7. Install depot, speed and message signs; create and assign a train, then call via a button or awake.

```text
/sal route create Line1
/sal route direction Line1 east
/sal route bind Line1
/sal route choose 1
/sal route bind Line1 <deviceUUID>
/sal route bind Line1 <stationID> reverse
/sal route list Line1
/sal route remove Line1 <index>
/sal route preview Line1
/sal route preview Line1 off
/sal route validate Line1
/sal route publish Line1
```

### Four-line sign layouts

`[SAL]` = `[SkyATOL]`

#### depot

```text
[SAL]
depot
maintown
L1
```

Line3 is depot maintown, line4 is independent berth L1. Make separate L2/L3 signs; berths may be distributed. Depot is a parking position, not the first station. Except awake, signs must attach to registered RailGraph track columns.

#### station

```text
[SAL]
station
15
Line1
```

station line3 is dwell seconds and line4 is the SAL route. There is no separate station-name field. Create the route first; PCC labels stops by route and order.

#### awake

```text
[SAL]
awake
maintown
Line1
```

awake line3 is depot and line4 route. A rising redstone edge to the sign/support calls a train. No track is required under awake. Continuous power and startup do not call; duplicate requests coalesce.

#### property / v_target

```text
[SAL]
property
V_target
36km/h
```

property currently supports only V_target with speed on line4. The v_target shorthand puts speed on line3 and leaves line4 empty. Units: km/h, kmh, m/s, m/t, b/t, mph; no unit means blocks/tick. Vehicle limits, train limits and ATP still apply.

```text
[SkyATOL]
v_target
36km/h

```

#### message

```text
[SAL]
message
Skyworld
5
```

message line3 is literal text, line4 display seconds. A central Title is sent to passengers of this train; new messages immediately replace old ones, never queue or execute commands.

### Trains, calls and execution

**[CHANGED]** Cirno — SAL

An administrator seated in an assembled, stopped train assigns home. Home binds the depot, not L1. A newly assigned driverless train can be pushed onto its depot marker for low-speed STF parking capture. Dispatch candidates need fresh complete observations, no TIMS hold, no manual driver and no other job.

```text
/st create demo 6
/sal train home maintown
/sal train home
/sal awake maintown Line1
/sal status
```

One call: leave depot→board at first station→ordered running/dwell→last station→reserve a suitable berth→return and stop. It still completes if nobody boards. Passengers may return to the depot; another loop requires a new platform call. An eligible same-route train still at the first station is reused: cancel return, safely restore direction if required and restart the full dwell. Departed/faulted trains are not reused.

Depot paths use RailGraph rather than the current physical point setting. STCS sets the route and requests point execution through STA/STF. Reversal releases old MA, confirms standstill and reversal, then replans from the actual leading cart. No suitable space, berth or path means hold, not another train or forced clearance. Job phase is not instantaneous speed.

### Manual/automatic handover

Automatic→manual: `/sal release` only for a completely stopped, complete PARKED depot-standby train without a running job or fault hold. Wait for release, then `/st drive` from an end cart. A train still returning cannot use this shortcut.

Manual→automatic: keep the driving lease, stop at a valid berth continuously for3seconds, confirm SAL handover, then explicitly `/st release` within30seconds or click the second button. Success assigns the actual depot and enters standby; it does not depart.

Release without prior confirmation, leaving, disconnecting, movement, another driver, leaving the berth or expiration cannot complete handover. Manual state persists. Drivers need skyatolite.driver and skytrain.use; route/home administration needs skyatolite.admin.

```text
/sal release
/st drive
```

### Empty trains, faults and job control

```text
/sal stop <trainUUID>
/sal resume <trainUUID>
/sal finish <trainUUID>
```

stop holds the train; resume requires inspection of standstill, integrity and graph and restores a depot-return task; finish requires trustworthy localisation and a complete stop, releases MA and ends the job. Arguments are train UUIDs.

STF chunk tickets do not depend on passengers. SAL requests look-ahead retention; STF loads asynchronously and applies tickets on the owning region. Unready chunks prevent traction. Server-wide empty-server pause is separate (pause-when-empty-seconds=-1 to disable); tickets cannot tick a paused server.

TIMS recovery first holds the train, then retries only after real integrity returns, at most3times. It never changes membership, respawns the train or clears body occupancy. Missing carts, abnormal spacing and manual trips require inspection.

Path deviation raises a PCC alarm. Only after standstill, complete integrity and trustworthy position may the driver request pending SR. Each segment needs an operator approval; there is no endless automatic SR grant. Unknown position means no movement.

[SAL-MANUAL-HANDOVER](doc/SAL-MANUAL-HANDOVER.md) · [SAL-DEPOT-AND-RETIREMENT](doc/SAL-DEPOT-AND-RETIREMENT.md)

<a id="section-9"></a>

## 9. Executable MA, default FS and ATP

**[CHANGED · 2026-11-21]** Default FS request does not mean FS has already been granted. Manual driving explicitly acquires a driving lease; SAL also needs executable authority. SB, recovery or unavailable authority keeps brakes held.

FS supervises speed/EoA against the granted curve. SH/SR default to40km/h, subject to any lower curve. After a TR stop: ack→PT, release→SB, inspect and request again. TIMS holds first require genuine integrity restoration and acknowledgment.

After the initial path submission, silent renewal keeps the route lease while STCS extends MA. Do not submit a new visible request every half-second. Changed path/version, lost control or expired lease needs a new valid request; a late heartbeat cannot revive it.

Explicit shadow testing remains simulationOnly=true/executable=false and cannot stand in for executable authority. BYPASS, ISOLATED and RECOVERING retain their separate interlocks.

MA release keeps body occupancy and releases only eligible forward reservations. Unloading, timeout and disconnection do not prove clearance. Explicit train removal needs complete STF destruction receipts and virtual-driver cleanup before scoped evidence release; other trains remain protected.

```text
/stcs ma fs
/stcs ma status
/stcs ma ack
/stcs ma release
```

### Configuration and local graph updates

STCS owns the physical graph. Equipment/track edits trigger local updates; point setting alone is not topology. rebuild prioritises loaded sources near the administrator, then other loaded sources, stopping at unloaded boundaries.

Previously confirmed unloaded connections remain available for static planning. Never-observed directions remain unknown; an observed broken rail stays broken after unloading. Occupancy, physical point confirmation and authority are checked separately.

Loaded-track review defaults to60seconds, configurable1–86400; batch size1–64sources. Healthy review does not increment structural revision. SAL rechecks only changed dependencies; executable MA still has global graph-version fencing.

```yaml
graph:
  review-interval-seconds: 60
  sources-per-tick: 4
ma:
  enabled: true
  look-ahead-meters: 600.0
  lock-distance-meters: 150.0
  max-authority-distance-meters: 300.0
  check-exit-capacity: true
```

`look-ahead` ≠ `lock-distance` ≠ `max-authority-distance`

`FS lock = max(lock-distance, v²/(2a) + v×lead + buffer)`

`a = 1 m/s²; lead = 5 s; buffer = 25 m; v = m/s`

[RAILGRAPH-INCREMENTAL](doc/RAILGRAPH-INCREMENTAL.md) · [STCS-DYNAMIC-SWITCH-LOCK](doc/STCS-DYNAMIC-SWITCH-LOCK.zh.md) · [CONFIGURATION-SWITCHES](doc/CONFIGURATION-SWITCHES.md)

<a id="section-10"></a>

## 10. SkyPCC infrastructure and operating maps

~~**[ADDED · 2026-11-21]** Infrastructure mode keeps devices, infrastructure lines, occupancy, MA and dispatch controls. Map layer→Operating routes opens an independent canvas/camera without balise, turnout or origin devices. Filter all routes or one SAL route.~~

**[ADDED / CHANGED]** One RailGraph canvas has independent infrastructure, SAL, operating-route and live layers. Enter the route editor with the existing switch-control token; select a route and Edit selected route, or select at least one registered depot and create a draft.

DRAFT=dotted; VALIDATED=long dash after validation with current dependencies; PUBLISHED=solid after publication with current dependencies. Estimated draft connectors explicitly say the track path is unconfirmed.

Selecting a depot highlights every registered berth. Total includes flagged-invalid registrations. Parked requires a fresh complete train without TIMS hold at exactly one valid berth. Operating means a SAL job, including dwell, pause and recovery, even at zero speed.

Other/unknown lists manual trains, idle trains elsewhere and missing observations. An empty outline is not free-space authority. Stale/missing providers show unknown/unavailable; old positions are not drawn as current and missing data is not treated as zero trains.

Click a berth to locate it or a train name for its inspector. Independent pan/zoom, labels, themes and world selection are supported. PCC has Chinese/English/French/Japanese; STF also has Russian. Dutch/classical-Japanese documentation does not add game locales.

Write actions still require web.control-enabled and a Bearer token. web.require-https-for-control defaults true; false relaxes HTTPS only, never authentication. No deployment token is bundled.

```yaml
web:
  enabled: true
  bind-address: 127.0.0.1
  port: 8765
  require-https-for-control: true
```

[PCC-OPERATING-MAP](doc/PCC-OPERATING-MAP.md)

<a id="section-11"></a>

## 11. Sounds and HMI

~~The driver-only BossBar shows `Shadow MA | information | ATP limit: xx km/h`; the same shadow limit is on sidebar line two. Invalid curves show `--`. `settings.cab-ma-bar-range-meters` defaults to 300 m and changes only the bar scale.~~

**[CHANGED]** Default FS request does not mean FS has already been granted. Manual driving explicitly acquires a driving lease; SAL also needs executable authority. SB, recovery or unavailable authority keeps brakes held. FS supervises speed/EoA against the granted curve. SH/SR default to40km/h, subject to any lower curve. After a TR stop: ack→PT, release→SB, inspect and request again. TIMS holds first require genuine integrity restoration and acknowledgment.

### MA Audio Configuration

At the **top level** of STF configuration, not under `settings`:

```yaml
ma-sounds:
  enabled: true
  granted:
    enabled: true
    sound: minecraft:block.anvil.land
    category: MASTER
    volume: 1.0
    pitch: 2.0
    count: 2
    interval-ticks: 5
```

`changed`, `released`, `shrinking`, `low` support the same fields. Merge into the existing section; do not introduce duplicate YAML keys.

| Cue | Default sound | Behaviour |
| --- | --- | --- |
| granted | `minecraft:block.anvil.land` | Requested authority granted; pitch 2, twice |
| changed | `minecraft:block.anvil.land` | Significant jump; pitch 2, once |
| released | `minecraft:block.iron_trapdoor.close` | Release |
| shrinking | `minecraft:block.note_block.bit` | Remaining rolling MA starts shrinking while moving |
| low | `minecraft:block.note_block.pling` | Low remaining distance |

Use Java Edition `namespace:path` IDs; omitted namespace defaults to minecraft. Custom IDs need a client resource pack. Ranges: volume 0..4, pitch 0.5..2, count 1..16, interval-ticks 1..200.

STCS trigger settings:

```yaml
ma:
  sound:
    enabled: true
    jump-threshold-meters: 20.0
    cooldown-ms: 1500
    low-remaining-meters: 50.0
```

Zero disables the low-distance threshold. Hysteresis prevents repeated warnings at the boundary. Normal smooth extension should not repeatedly produce the jump cue. Delayed cues recheck driving control rather than continuing to address a former driver.

### Rolling and Brake Sounds

Rolling volume/pitch vary with speed: by default silent below 10 km/h, reaching the configured cap at 120 km/h. See `settings.trackside-running-sound-*`.

Increasing braking or entering braking from N/power produces an application sound; leaving braking completely produces a release sound. Partial brake reduction does not produce a release sound at every notch. See `settings.brake-sound-*`. No traction-motor sound is currently included.

Stop all trains before `/st reload`; this reloads data, not just audio.

<a id="section-12"></a>

## 12. STA contracts and HTTP endpoints

**[CHANGED · 2026-11-21]** STA is an in-process Java contract, not a deployed Python/TCP RBC. Message, Packet and interface family are distinct; validate session, sequence, version and freshness.

ATO uses private Message6508. SUBSET_139 Packet1031/1032 honour31/32+1000. Private Packet5115–5118 cover route renewal, parking and handover; they are not official ATO numbers. MA retains Message1003/Packet1015; telemetry Message1136.

v8 OperatingNetworkService is a read-only display snapshot (schemaVersion1), not authority. v9 TopologyDependency checks edge dependencies. SAL publishes; PCC refreshes a background cache, and HTTP threads never inspect entities. Endpoint, DTO and JAR versions are independent.

```text
GET /api/v5/graph
GET /api/v5/trains
GET /api/v5/messages
GET /api/v5/railway-events
GET /api/v5/shadow-ma
GET /api/v5/config
GET /api/v5/events
GET /api/v6/operational-ma
GET /api/v7/station-areas
GET /api/v8/operating-network
POST /api/v5/switch
POST /api/v6/sr
POST /api/v6/ma/revoke
```

[STA-V8-ATO-PROTOCOL](doc/STA-V8-ATO-PROTOCOL.md) · [STA-V5-MIGRATION](doc/STA-V5-MIGRATION.md)

<a id="section-13"></a>

## 13. Persistence and troubleshooting

**[CHANGED · 2026-11-21]** SAL state.json stores signs, routes, homes, manual standby and jobs, with a .bak backup. Uncompleted handover consent does not survive restart. Failed writes block new dispatch. occupancy-ledger.json and legacy shadow-occupancy.json are different ledgers; deleting one is not a valid bypass. Preserve confirmed-destructions.json receipts on upgrade.

Paths relative to the server directory:

| Path | Data |
| --- | --- |
| `plugins/SkyTrainFolia/config.yml`, `plugins/SkyTrainFolia/vehicles/` | Global/audio/profile configuration |
| `plugins/SkyTrainFolia/trains.yml` | Consists, properties, protection modes |
| `plugins/SkyTrainFolia/savedtrains.yml` | Templates |
| `plugins/SkyTrainFolia/switches.yml` | Turnout registrations |
| `plugins/SkyTrainFolia/infrastructure.yml` | STF infrastructure/calibration |
| `plugins/SkyTrainFolia/stations.yml` (display metadata; legacy automatic-signs.yml is no longer read) | Station/sign records |
| `plugins/STCS/markers.yml` | STCS registrations |
| `plugins/STCS/railgraph.json` | Graph snapshot, not occupancy ledger |
| `plugins/STCS/occupancy-ledger.json` | Retained evidence; never delete just to force MA allocation |
| `plugins/SkyPCC/config.yml` | Web settings/private credentials |

The graph is still a single logical graph/JSON export, not a production one-file-per-line sharded store. Do not sever cross-line physical connections based on line names. Analyse exported copies rather than editing live graph/ledger files.

| Symptom | Check first |
| --- | --- |
| ~~auto shown but station inactive~~ | ~~Driver, explicit release, protection mode, redstone, sign-column association and blocking reason~~ |
| Request accepted but no HMI authority | `/stcs ma status` and PCC reason; acceptance is not allocation |
| SWITCH_UNKNOWN | Physical turnout validation, loaded/owned region, registration and location; graph existence is insufficient |
| FLEET_UNCERTAIN / unlocated retained train | Ledger/UUID; absence of a plotted train does not remove its evidence |
| NO_EXIT_CAPACITY | Complete-train exit space, MA cap, missing/incorrect path |
| Parallel routes block each other | Actual path, spatial intervals, older retained resources, fresh full-consist observations |
| Missing/incorrect mileage | Origin/End directions, named balises, siding boundaries, path ambiguity; load and rebuild |
| PCC INVALID_REQUEST | Format/revision/expected state/location and frontend/backend version, not necessarily token |
| PCC writes disabled | Enable flag, token length, loopback, origin, dependencies and exact rejection |
| Persistent PENDING | Async loading and physical revalidation/logs, not success |
| High-speed cart problems | Server adaptation, region movement, other entity controllers/removal, not just the speed cap |

Unloading is not tail clearance; restored evidence is not a fresh position. The ledger's RECOVERING quality is also distinct from the onboard RECOVERING mode.

Reports should include all plugin/server versions, time, train name/UUID, equipment IDs, reproduction steps, logs, exported graph and occupancy diagnostics. Remove tokens before sharing configuration.

- No call: check publication, the awake rising edge, depot/route names, manual standby, freshness, TIMS and existing jobs. Opposite train orientation alone should not require manually turning the train.
- Disconnected stop: inspect RailGraph/routingIssues and locally rescan new loaded track. A chunk_not_loaded entry in unresolved alone is not a broken route.
- Depot hold: check available berth, whole-train space, path, physical point confirmation and MA reason; do not spam awake or clear ledgers.
- Occupancy after removal: verify the removed UUID, full destruction receipt and virtual-driver cleanup. A missing/unknown entity is not proof of safe deletion.
- Operating display unavailable: check SAL/STA services, graph revision and freshness; hard-refresh scripts. Infrastructure and operational display have distinct semantics.

<a id="section-14"></a>

## 14. Acceptance checklist and limits

**[CHANGED · 2026-11-21]** Build and Java regressions run independently against26.2 and26.3. This patch checks version/protection wording, command themes and SAL help. Recorded results are included in the distribution.

Completed earlier: one empty-train pickup→loop→depot on26.3; real26.2 PCC displayed published line1 and two maintown berths. Operating browser regression covers styles, filters, counts and stale data; all363four-language keys pass. This is not acceptance of every layout, multiple trains or passenger service.

Recipients should test reverse-facing dispatch, turnout movements, first-stop reuse, L1/L2 competition, whole-train turnout clearance/reversal, empty-server loading, Title replacement, TIMS/disconnection/restart, two-step handover and scoped train removal.

[VALIDATION](../VALIDATION.md)

<a id="section-15"></a>

## 15. Development, source and distribution

**[CHANGED · 2026-11-21]** The source archive contains all five modules, shared code, defaults and tests. Extract it and run Common/build.ps1 with two prepared test-server roots and JavaHome. Latest is published only after both version passes. This ZIP contains no Minecraft server core, world, private deployment configuration, token or certificate.

```powershell
./Common/build.ps1 -ServerRoot <server26.2> -ServerRoot26_3 <server26.3> -JavaHome <jdk25>
node SkyPCC/tests/edge-line.cjs
node SkyPCC/tests/i18n.cjs
node SkyPCC/tests/operating-map-browser.cjs
./Common/package-source.ps1
```

MIT code/document licensing and separate image permissions are included. The new command presentation does not alter STA contracts, SAL actions, entity-thread ownership or STCS authority decisions.

[LICENSE](LICENSE) · [ASSET-LICENSE](ASSET-LICENSE.md) · [TrainCarts](https://github.com/bergerhealer/TrainCarts) · [BKCommonLib](https://github.com/bergerhealer/BKCommonLib)

## Previous manual and removed instructions

**[REMOVED · 2026-11-21]** These are removed legacy STF sign instructions, preserved with strikethrough: do not follow them. The complete old manual is linked separately.

~~[+stf]~~
~~station~~
~~5~~
~~continue 40kmh~~

~~/st property demo mode auto~~

~~[+stf]~~
~~spawn~~

~~[+stf]~~
~~destroy~~

[Complete previous manual (historical baseline, not current instructions)](doc/manual-baseline-20261120/README.en.md)

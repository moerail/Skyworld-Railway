# SkyRail Suite

[Home](README.md) | [中文](README.zh.md) | **English** | [Nederlands](README.nl.md)

**English edition. Self-contained: no companion documentation is required to read this manual.**

A train-operation, railway-infrastructure, shadow train-control and dispatching-display suite for Minecraft / Folia. SkyRail Suite is the new repository name for the SkyTrain Suite development line; existing runtime plugin names, commands and data directories remain unchanged.

This manual describes the local source baseline documented on **13 September 2026**. English edition prepared on **14 September 2026**. It is intended for server administrators, drivers, railway builders and plugin developers. Ideas discussed for future development are not necessarily implemented.

> **Development-build limitation:** the suite calculates, allocates and displays shadow MA/EoA information, but ATP does not apply brakes in response to it. An accepted request, an apparently clear track on PCC, or an online RBC indicator is not permission to assume safe movement. Driver-loss emergency braking, manual emergency braking and the RECOVERING brake hold are separate, active control functions.

**For TrainCarts developers:** TrainCarts is a reference for parts of the sign interface and operating workflow. This document does not claim complete TrainCarts compatibility, equivalent physics, or compatibility with every TC extension. STF's `switch` is not TC's `switcher`. The suite does not require TC or BKCommonLib, and two controllers should not manage the same minecart simultaneously.

SkyRail Suite code and documentation are licensed under the MIT License: Copyright (c) 2026 Skyworld Minecraft Server contributors. The maintainer contact is GitHub account moerail. The complete license is distributed in the source package and all four plugin JARs. Logos and character artwork, including day_logo.png and night_logo.png, are excluded from this code license; no new artwork reuse or redistribution permission is granted. Verify artwork permissions before publishing packages containing those images.

The existing STF resources include META-INF/NOTICE-TrainCarts.txt, preserving the MIT notice and reference commit 9813810aa7e751d3a00d3d087186d44f6e90df10 for the sign compatibility work. This third-party notice is retained unchanged in source and STF JAR distributions; the suite copyright statement does not replace it.

## Contents

1. [Purpose and Components](#1-purpose-and-components)
2. [Installation and Upgrades](#2-installation-and-upgrades)
3. [Permissions and Languages](#3-permissions-and-languages)
4. [Your First Manually Driven Train](#4-your-first-manually-driven-train)
5. [Command Reference](#5-command-reference)
6. [Vehicle Profiles and Properties](#6-vehicle-profiles-and-properties)
7. [Building Lines and Turnouts](#7-building-lines-and-turnouts)
8. [Automatic Signs and Station Stops](#8-automatic-signs-and-station-stops)
9. [Shadow MA and Protection Modes](#9-shadow-ma-and-protection-modes)
10. [SkyPCC Web Interface](#10-skypcc-web-interface)
11. [Sounds and HMI](#11-sounds-and-hmi)
12. [STA Contracts and Messages](#12-sta-contracts-and-messages)
13. [Persistence and Troubleshooting](#13-persistence-and-troubleshooting)
14. [Acceptance Tests and Next Steps](#14-acceptance-tests-and-next-steps)
15. [Implementation Notes for Developers](#15-implementation-notes-for-developers)

## 1. Purpose and Components

SkyTrain Suite uses Minecraft as an interactive railway environment. Players build physical tracks and drive minecart consists; the plugins manage movement, identify the railway network, and present position, occupancy and authority information in the cab and on a dispatching display.

The project introduces explicit driving control, resource occupancy, conflicting routes, Movement Authority (MA), End of Authority (EoA), and retention of uncertain observations. It takes inspiration from ETCS's separation of responsibilities, but is **not a SUBSET-026 implementation, a certified interlocking, or a real-world railway safety system**.

| Component | Version | Responsibility |
| --- | --- | --- |
| SkyTrainFolia / STF | `2.1.4` | Consists, movement and cornering, driving control, traction/braking, vehicle profiles, signs, physical turnout actuation, HMI and sounds |
| STCS | `2.2.1` | Infrastructure, directed RailGraph, line mileage, localisation, retained occupancy ledger, shadow MA/EoA and local turnout checks |
| SkyworldTrainAPI / STA | `0.8.1` | Versioned inter-plugin contracts, telemetry, member observations, cab state, authorities and events |
| SkyPCC | `0.8.1` | Web track diagram, train/infrastructure inspector, occupancy/reservations, event log and authenticated turnout control |

```text
Minecraft players / minecarts / rails / redstone
                       |
                      STF   Movement, driving and actuation
                       |
                      STA   Contracts, telemetry and events
                       |
                     STCS   Graph, localisation, occupancy, shadow MA
                       |
                     SkyPCC Web observation and operator interface
```

This illustrates responsibilities, not a mandatory serial call chain. Browser interpolation is not a safety-position source, and the browser does not decide which track resources may be authorised.

### Implemented vs Planned

| Area | Current status |
| --- | --- |
| Manual driving, explicit control, driver-loss EB | Implemented; control must be claimed after each boarding |
| Consists, track-coordinate movement, high-speed display adaptation | Implemented; high speed, region transfers and third-party combinations still need live-server validation |
| Station pseudo-automatic driving | MVP using vehicle power/brake notches, not a complete ATO |
| Graph, line attribution, mileage, retained occupancy | Implemented; timeout/unloading does not establish clearance |
| Online MA/EoA and spatial reservations | Shadow implementation; no ATP braking |
| Web turnout control | Authentication, local checks and asynchronous PENDING implemented |
| Onboard speed curves and ATP overspeed/EoA intervention | Not implemented |
| Complete destination routing and timetable ATO | Not implemented as a complete system; route metadata is not an established route |
| ETCS FS/SR/SH/SB/TR/PT modes | Not implemented; current modes are not full substitutes |
| Separate SIR, SkyCBI or Python RBC service | Architectural ideas, not current installable components |

## 2. Installation and Upgrades

### Requirements

- The current adaptation baseline is **Shiroha / Folia 26.2 with Java 25**. Use a server build that has been tested with this suite.
- STF contains version-dependent movement/display integration. `folia-supported: true` does not guarantee all Folia versions; `api-version: 1.13` is not a promise that this binary runs on Minecraft 1.13.
- PCC runs in a normal browser. It does not require a client mod.
- TC/BKCommonLib are not required. Avoid overlapping control of the same minecart by different plugins.

### First Installation

1. Back up the world and the entire `plugins` directory. Start with a test server.
2. Stop the server, place the four JARs below in `plugins`, and remove older JARs of the same plugins.
3. Start once to generate defaults and check that all four plugins enable successfully.
4. Stop, edit configuration, and restart fully. Do not hot-unload these plugins to replace their binaries.
5. Run `/st version` and `/stcs status`. On the server machine, open `http://127.0.0.1:8765/`.

Current installation files:

```text
SkyTrainFolia-2.1.4.jar
STCS-2.2.1.jar
SkyworldTrainAPI-0.8.1.jar
SkyPCC-0.8.1.jar
```

STF/STCS declare STA as a soft dependency, but install all four for the complete suite. PCC requires STCS and STA. Standalone STF does not provide the complete graph, authority and dispatching functionality.

### Upgrade Rules

- Use a compatible set, especially when STA contracts change. Do not install every historical JAR in `artifacts`.
- Preserve existing data and merge new configuration keys from the current defaults. Missing keys are not necessarily written into an existing file automatically.
- `/st reload` reloads train data as well as configuration: **stop every train first**. It is not an audio-only reload.
- Apply STCS/PCC configuration by a full restart. This manual does not invent `/stcs reload` or `/skypcc reload` commands.
- After topology or graph-recognition changes, load the affected railway and run `/stcs rebuild`. A web/audio-only update normally does not need a rebuild.
- Refresh the browser cache after web updates. Roll back binaries, world and data from a matching backup, not a random mixture of ledger versions.

## 3. Permissions and Languages

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

## 4. Your First Manually Driven Train

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
- `/st release` explicitly releases control and applies EB. Automatic operation additionally requires standstill and an explicit `mode auto` setting.

### Hotbar Control

Select **slot 5** before enabling `/st hotbar`. Enabling it does not input N or release an existing brake application. Subsequent slot changes select notches:

| Slot | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Notch | B7 | B5 | B3 | B1 | N | P1 | P2 | P3 | P4 |

Disabling with `/st hotbar` does not require slot 5. `/st cab` opens the graphical cab. Both require driving control first.

## 5. Command Reference

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
| `/st lang zh\|en\|fr\|jp` | Player language |
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
| `/stcs ma demand` | `stcs.ma` and current driver; request shadow MA |
| `/stcs ma release` | `stcs.ma` and current driver; release forward reservations, not body occupancy |
| `/stcs ma status` | `stcs.admin`; authority/blocker diagnostics |
| `/stcs admin status` | `stcs.admin`; protection state of the train being ridden |
| `/stcs admin isolate true\|false` | Train-control isolation |
| `/stcs admin bypass true\|false` | Supervision bypass |
| `/stcs admin shadow true\|false` | Shadow testing |
| `/stcs occupancy [train-name\|uuid]` | `stcs.admin`; retained occupancy/member observations |
| `/stcs rebuild` | `stcs.admin`; rebuild graph from registered infrastructure |
| `/stcs export` | `stcs.admin`; export current graph, not a rescan |
| `/stcs switch info` | `stcs.use`; nearest turnout within 8 blocks |
| `/stcs switch change` | `stcs.admin`; change nearby turnout |

The player command is **`demand`, not `request`**. Internal events may still be named `MA_REQUESTED`. `/sta` and `/skypcc` primarily expose help/version, not a second set of driving controls.

## 6. Vehicle Profiles and Properties

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
| `automatic.*` | Legacy target-speed controller parameters, not the entire station-notch strategy |

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
/st property test01 mode auto
/st property test01 pushable true
```

- `name` identifies the managed train in commands. Use the new name after renaming.
- `trainnumber` is a separate service/run number. Leading zeros are preserved; maximum 32 characters; no control characters or `|`; uniqueness is not enforced. `clear` or `-` removes it.
- `displayname` is presentation metadata, not the internal UUID.
- Other properties include `destination`, `collision`, `playersenter`, `playersexit`, `pickupitems`, `invincible`, `allowplayertake`, `requirepoweredcart`, `sound`, `keepchunksloaded`, `gravity`, `friction`, `waitticks`, `speed`, `maxspeed`, `spacing`.
- `pushable=true` is not sufficient on its own: driver ownership, an unreleased manual takeover or running state can still block pushing. Inspect `/st info`.
- Owner/tag/route metadata does not imply complete routing, dispatching or owner-based access isolation.

## 7. Building Lines and Turnouts

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

## 8. Automatic Signs and Station Stops

The station/spawn/destroy interface follows TC-inspired conventions. Rail association uses the sign column below the rail and attachment relationships, not an arbitrary nearby sphere. **Support for these forms does not mean complete TC expression, remote-sign or extension compatibility.**

A manually controlled train is not taken over by station/destroy. Stop, have the driver `/st release`, then explicitly set `/st property <train> mode auto`. ISOLATED, RECOVERING and unreleased manual takeover can also block automatic handling.

Common headers: `[stf]` uses redstone activation, `[+stf]` is continuously enabled, `[!stf]` inverted, `[-stf]` disabled. The parser also supports rising/falling-edge forms; actual triggering depends on the action.

### Station

```text
[+stf]
station
5
continue 40kmh
```

| Line | Meaning |
| --- | --- |
| 1 | Header/activation |
| 2 | station, optionally supported launch distance/time/acceleration and stop-offset options |
| 3 | Dwell; a plain number is seconds, with forms such as `5s`, `100t`, `00:05` also supported |
| 4 | Direction/speed, e.g. `continue 40kmh` or `reverse 0.4`; no speed unit means blocks/tick |

Automatic trains can now discover Station signs ahead by following actual rails, without line names, balises or STCS calibration. Search follows the selected turnout branch; it neither loads chunks nor detects trains ahead. This is not ATP or collision protection.

The stopping reference is the centre of the leading cart in the direction of travel, plus the station offset; no half-consist length is added. Since 2.1.3, the virtual driver selects braking from remaining distance, coasts below the curve, and uses low-speed recovery with hysteresis only after a genuine undershoot. Dwell and HOLD still apply B7; manual driving is unchanged.

Under the existing STF `settings` section, set `station-local-look-ahead-blocks: 256.0`. The range is 0–1024 blocks, with 0 disabling local search. Queries run at most every 200 ms and read only loaded rails owned by the current Folia thread. This is a search ceiling, not guaranteed braking distance. Optional STA/STCS graph advice retains the separate `station-look-ahead-blocks` setting (default 8192). Test at low speed with enough loaded track first.

### V_target Property Sign (Since 2.1.2)

This sign changes the automatic train's target speed, not its speed cap or control mode. Manual trains ignore it. Only `V_target` is currently permitted on property signs; other properties use administrator commands.

```text
[+stf]
property
V_target
60km/h
```

Unitless values are blocks/tick: `0.4`, `8m/s` and `28.8km/h` are equivalent at 20 ticks/s and one block per metre. The property is persisted, respects train/profile speed caps, and does not start a stopped train by itself. An explicit station departure speed takes precedence; otherwise the station inherits V_target, then falls back to `settings.station-launch-speed` (default 0.4). `[+stf]` is always enabled; `[stf]` uses redstone activation. Administrators set it with `/st property demo V_target 60km/h` and read it with `/st property demo get V_target`.

### Spawn and Destroy

```text
[stf]
spawn 0.0
mmm

```

Use controlled redstone to spawn three normal minecarts. Line 2 accepts `spawn [velocity] [interval]`, with an interval such as `00:30`; lines 3 and 4 concatenate into the spawn pattern. Basic cart symbols: `m` normal, `s` chest, `p` furnace, `h` hopper, `t` TNT, alongside supported template patterns. Do not use TNT for initial tests.

Templates used for automatic spawning must be explicitly saved in auto mode. Do not continuously spawn trains onto an unprotected line.

```text
[+stf]
destroy


```

Destroy really removes entities. Test on a backed-up, separate track; a manually controlled train should not be destroyed. Validate station handling first, then spawn/destroy separately.

## 9. Shadow MA and Protection Modes

| Term | Meaning here |
| --- | --- |
| MA / Movement Authority | Shadow allocation along a legal directed path |
| EoA / End of Authority | Current authority endpoint, not necessarily the end of a line |
| Credit | Remaining path distance to EoA, not Euclidean distance |
| Occupancy | Track occupied according to member observations/retained evidence |
| Reservation | Forward shadow allocation, not certified route locking |
| RBC link | Shadow information-channel status, not proof of radio RBC or effective ATP |

### Driver Workflow

1. Check topology, turnout state, localisation and the actual test route.
2. Board, `/st drive`, stop and select direction in a mode that permits requests.
3. `/stcs ma demand`; inspect the allocation result/reason, not just request acceptance.
4. The driver's BossBar shows remaining MA; HMI shows EoA line/mileage; PCC shows reserved intervals.
5. Control speed and stopping manually. `/stcs ma release` releases forward reservations.

Driverless trains do not actively acquire new MA. A stopped train with a driver is different from a train without one. Releasing MA neither clears body occupancy nor acts as a stop command.

### State Machine

| State | MA/channel | Active behaviour |
| --- | --- | --- |
| SHADOW | Shadow requests/recognition allowed | No MA/speed ATP intervention |
| BYPASS | Channel retained; shadow MA can be retained/requested | Supervision bypass, not communication isolation |
| ISOLATED | New requests rejected; onboard control channel isolated | Read-only mileage and STA telemetry retained; automatic signs disabled |
| RECOVERING | No new valid onboard MA | Brake hold, traction rejected, explicit next mode required |

Modes persist with train data. Setting an applicable switch to `false` enters RECOVERING. Enabling a target with `true` requires RECOVERING, fresh complete-train observations and standstill. Modes cannot simply be changed across at speed.

Administrator seated in the target train, for example:

```text
/stcs admin shadow false
/stcs admin status
```

After the complete train stops:

```text
/stcs admin bypass true
```

To return, use `bypass false`, stop, then `shadow true`. Mode changes remove traction/apply braking, not automatic departure. Ground reservations/evidence retained after isolation are not an onboard usable MA or grounds for extending it.

### Three Distance Settings

In STCS configuration:

```yaml
ma:
  enabled: true
  look-ahead-meters: 600.0
  lock-distance-meters: 150.0
  max-authority-distance-meters: 300.0
  margin-meters: 2.0
```

| Key | Meaning |
| --- | --- |
| `look-ahead-meters` | Search bound; not everything found is reserved |
| `lock-distance-meters` | Approach range within which shadow MA may cross a turnout; farther away it stops before it, not proof of a physical lock |
| `max-authority-distance-meters` | Actual credit cap, also limited by look-ahead |
| `margin-meters` | Margin before an obstacle/conflict, not a complete braking distance |

Legacy `ma.horizon-meters` supplies compatibility defaults for missing new keys. Set all three explicitly in new configurations. STF `switch-approach-distance`, station look-ahead and BossBar scale are separate settings.

### Following, Parallel Routes and Junctions

Spatial MA uses physical rail cells and intervals within edges rather than exclusively allocating an entire balise-to-balise edge. Opposite directed edges share the same physical resource.

- Following trains should be bounded by the preceding occupancy/reservation boundary, not always the previous balise.
- Parallel routes without shared resources/conflicts should coexist, rather than locking an entire station throat just because they are nearby.
- A turnout's unused branch should not automatically become train-body occupancy.
- Before entering a throat, the exit must have capacity for the complete train; otherwise the train waits before entry.
- Unknown graph/turnout state, missing members and retained evidence may still block allocation. Appearance alone cannot justify bypassing checks.

Body mapping and asynchronous consistency remain conservative, not a proven integrity/swept-envelope solution. The implementation samples obstacles at quarter-block intervals along the path but allocates physical rail-cell resources: it is block-scale shadow allocation, not certified continuous moving-block ATP. Reservations stop at the granted interval. A shared physical turnout cell may colour a short endpoint of an unused branch without reserving that entire branch.

Resource identifiers use `cell@world:x:y:z`. Older whole-edge occupancy records remain conservatively blocking until replaced by a complete, fresh observation. Unloading, restart, missing members and graph mismatch do not themselves clear evidence. An offline train with legacy records can therefore still block a larger area. Do not downgrade while reusing a newer ledger without its matching backup.

## 10. SkyPCC Web Interface

```yaml
web:
  enabled: true
  bind-address: 127.0.0.1
  port: 8765
  control-enabled: false
  control-token: ''
  update-mode: auto
  poll-interval-millis: 1000
  sse-keepalive-seconds: 15
```

Open `http://127.0.0.1:8765/` on the server machine. On another computer, that address refers to that computer, not the server. Use an SSH local tunnel for remote control; binding a service publicly is not a security solution.

### Display

- Chinese/English/French/Japanese, light/dark themes and bundled day/night logos.
- Line filtering, zoom, fit-to-view and text scaling.
- Waiting-for-localisation trains remain in the list; retained entries are not fresh positions.
- Map labels: `<service number> | <train name> | <speed> km/h`, with a placeholder for absent numbers.
- Train inspector and camera follow/cancel: ATP mode, MA/EoA, reason, direction, reverser, handle, driver, mileage, edge and data age.
- Turnout names use yellow-backed bold black text; arrows are separate: purple for straight, orange-yellow for diverging.
- Click balises, turnouts or edges for infrastructure inspection. Confirmed main-line mileage is shown; otherwise adjacent graph-node/port distances are used without inventing siding mileage.
- Turnout state is a received snapshot, not a fresh physical validation performed by the browser.
- Occupied, frozen/uncertain, shadow-reserved and unallocated intervals have different colours. Grey/unallocated is not proof of clearance.
- Operations log supports severity filtering, collapsing and expanding upwards.

### Turnout Control

Enable `control-enabled`, set a private random token of **at least 32 characters**, then restart. Enter it through the web control interface; never put it in URLs, screenshots or public logs.

Select a turnout, inspect it and confirm a change. The request includes graph revision, identity, expected/target state and position. STCS checks locally; STF actuates.

- Unrelated distant trains do not automatically block the request, but local occupancy, reservation conflicts, uncertainty and mismatched state/location can.
- Unloaded locations can enter PENDING, with asynchronous loading and revalidation on the owning region. PENDING is not success and must not be used as clearance to proceed.
- A correct token is not sufficient: loopback and same-origin checks also apply.
- Public read-only viewing should have separate access control; observation access and control credentials are different concerns.

### Events

Events cover driving-control acquisition/release/loss, turnout changes, suspected run-throughs, EB entry, MA requests/releases and ATP mode changes. Mode events identify the actor and old/new states; an administrator actor is not necessarily the driver.

STA retains the latest 500 events in the current session by default, not a permanent audit archive. Smooth MA updates do not all create events. A suspected run-through is not a fully proven failure diagnosis.

## 11. Sounds and HMI

The MA BossBar is driver-only. `settings.cab-ma-bar-range-meters` in STF defaults to 300 m and affects only the bar scale; text shows actual distance. The sidebar ATP speed limit remains a not-implemented placeholder, not an existing speed curve.

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
| shrinking | `minecraft:entity.experience_orb.pickup` | Remaining rolling MA starts shrinking while moving |
| low | `minecraft:block.note_block.pling` | Low remaining distance |

Use Java Edition `namespace:path` IDs; omitted namespace defaults to minecraft. Custom IDs need a client resource pack. Ranges: volume 0..4, pitch 0.5..2, count 1..5, interval-ticks 1..200.

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

## 12. STA Contracts and Messages

STA primarily exposes Java services inside the server JVM. It is not an automatically exposed Python TCP/WebSocket RBC. PCC provides the existing HTTP/SSE observation and restricted turnout-control gateway.

| Contract/data | Main producer | Main consumer | Meaning |
| --- | --- | --- | --- |
| v2 TELEMETRY_REPORT / 1001 | STF | STCS/subscribers | Physical telemetry |
| v2 TRACK_REPORT / 1002 | STCS | PCC/subscribers | Graph localisation bound to the original observation |
| v2 TRAIN_REMOVED / 1003 | Source cleanup | Registries/subscribers | Not permission to clear retained occupancy |
| v2 RailNetworkService | STCS | STF/others | Graph, navigation, edge position, station look-ahead |
| v3 ConsistObservation | STF | STCS | Member observations/lifecycle |
| v3 RailwayEvent | STF/STCS | PCC/subscribers | Operational events |
| v4 DriverDeskService | STF | STCS | Driver, control lease, ATP mode |
| v4 ShadowAuthorityService | STCS | STF/PCC | Non-executable MA/EoA and sections |
| v4 SwitchControl | Caller such as PCC; checked by STCS, actuated by STF | Caller | Version/state/location checks and PENDING |

There are three generic v2 message kinds, but the whole API also contains separate snapshots, events and services. These are not all instances of those three messages.

v2 headers include version, kind, source, sessionId, sequence, emittedAt and trainId. Quality states include VALID, UNLOCATED, STALE, EXPIRED, GRAPH_CHANGED, SOURCE_UNAVAILABLE and SCALE_MISMATCH. Consumers must not use coordinates while ignoring identity, ordering or quality.

v4 shadow snapshots carry `simulationOnly=true` and `executable=false`. Authority data includes path, EoA edge/offset, remaining distance and observation provenance. Sections may include `fromMeters/toMeters`; several intervals may occur on the same edge. One interval must not be rendered/interpreted as whole-edge occupancy.

The leader's centre is not a proven train-front position, nominal consist length is not integrity proof, and nominal 20 TPS speed is not wall-clock speed during lag. These are project contracts inspired by ETCS, **not SUBSET-026 wire messages or interoperability**.

### PCC HTTP Endpoints

| Endpoint | Content |
| --- | --- |
| `GET /api/v1/graph` | RailGraph |
| `GET /api/v1/trains` | Train display snapshot |
| `GET /api/v2/messages` | Telemetry snapshot |
| `GET /api/v3/railway-events` | Events |
| `GET /api/v4/shadow-ma` | Shadow authorities/sections |
| `GET /api/v1/config` | Public web settings, not the control token |
| `GET /api/v1/events` | SSE |
| `POST /api/v4/switch` | Authenticated turnout control |

Endpoint versions and JAR versions differ. Do not replay display snapshots as executable authorities. This is an interface overview, not a complete generated SDK/schema reference; an external client must validate the exact payload shapes and contracts of the deployed build before sending commands. In particular, nullable section endpoints retain legacy whole-edge meaning, whereas explicit endpoints delimit an interval.

## 13. Persistence and Troubleshooting

Paths relative to the server directory:

| Path | Data |
| --- | --- |
| `plugins/SkyTrainFolia/config.yml`, `plugins/SkyTrainFolia/vehicles/` | Global/audio/profile configuration |
| `plugins/SkyTrainFolia/trains.yml` | Consists, properties, protection modes |
| `plugins/SkyTrainFolia/savedtrains.yml` | Templates |
| `plugins/SkyTrainFolia/switches.yml` | Turnout registrations |
| `plugins/SkyTrainFolia/infrastructure.yml` | STF infrastructure/calibration |
| `plugins/SkyTrainFolia/stations.yml`, `plugins/SkyTrainFolia/automatic-signs.yml` | Station/sign records |
| `plugins/STCS/markers.yml` | STCS registrations |
| `plugins/STCS/railgraph.json` | Graph snapshot, not occupancy ledger |
| `plugins/STCS/occupancy-ledger.json` | Retained evidence; never delete just to force MA allocation |
| `plugins/SkyPCC/config.yml` | Web settings/private credentials |

The graph is still a single logical graph/JSON export, not a production one-file-per-line sharded store. Do not sever cross-line physical connections based on line names. Analyse exported copies rather than editing live graph/ledger files.

| Symptom | Check first |
| --- | --- |
| auto shown but station inactive | Driver, explicit release, protection mode, redstone, sign-column association and blocking reason |
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

## 14. Acceptance Tests and Next Steps

### Minimum Regression Checklist

1. All four plugins load; versions/help and configuration are valid.
2. Each boarding requires drive; passengers do not inherit control; driver loss applies EB; reconnect does not restore old power.
3. Mode false enters recovery; recovery prevents traction; target true requires standstill; restart preserves state.
4. Test straight track, siding, crossover, back-to-back origins, track beyond End and hanging turnout signs; line attribution stays local.
5. Test moving/stopped/unloaded/restarted trains; retained evidence does not disappear without justification.
6. Test driverless handling, demand/release, following, opposing conflicts, parallel routes, throat capacity and tail clearance.
7. Test local/PCC switching, conflict rejection, unloaded PENDING, actual rail shape and lever redstone.
8. Verify driver-only BossBar, EoA mileage, interval colours, infrastructure inspector, log expansion, four languages and themes.
9. Verify grant/jump/shrink/low/release cues, no repetitive smooth-extension cue and no delayed audio to a former driver.
10. Manual trains ignore automatic takeover; release + auto enables station handling; test spawn/destroy separately.

The offline Python testbench exercises topology, direction, occupancy, reservations and EoA, but not Bukkit entity lifecycle, Folia scheduling, networking or actual braking. These are game regressions, not railway certification.

M0 contracts/modes and M1 observations/retention have implementations and player testing. M2 now includes online shadow MA and spatial-resource refinements. Previous acceptance does not replace regression tests or establish ATP readiness.

The next step can be **onboard shadow speed curves**: use MA/EoA and vehicle brake parameters to display/log a curve without applying brakes. Intervention comes only after validation.

Before real ATP, remaining work includes full train envelopes/consistency, authority identity/acknowledgement/revocation, loss-of-contact/freeze/bypass policies, speed restrictions/braking models, justified resource release and fault-injection testing.

## 15. Implementation Notes for Developers

This repository has four module directories: `SkyTrainFolia`, `STCS`, `STA` and `SkyPCC`. Shared compiled code is in `shared`, and development notes are in `doc`. Use each module's `plugin.yml` or version command for runtime versions; the repository rename does not rename the plugins.

### Movement and Folia Boundaries

STF's movement model is based on track coordinates and consist spacing, rather than only copying the leader's velocity into trailing carts. Position correction, passenger smoothing and display synchronisation are separate concerns. This does not establish unlimited safe speed: curves, slopes, region transitions and entity removal remain important test cases.

Physical entity/block work must respect Folia's owning entity/region schedulers. Asynchronous graph/authority calculations use snapshots; they are not a licence to access arbitrary Bukkit objects from worker threads. Chunk availability, graph knowledge and live physical turnout validation are distinct states. A loaded chunk alone does not prove that a previously unknown turnout has been validated.

Minecraft upgrades can affect entity internals, packet/display integration and scheduling assumptions even when ordinary command/configuration code is unchanged. Those adaptation boundaries require review and regression testing against the new server build. An abstraction layer reduces the affected surface; it does not make the suite maintenance-free.

### Comparison Boundary with TrainCarts

- The current sign work concerns station, spawn and destroy, with STF/SkyTrain headers and the supported parsing/activation rules described above.
- Rail-to-sign association follows a sign-column/attachment model. This is not a claim to support every TC sign action or expression.
- STF turnout definitions describe explicit geometry/ports and an actuator. They must not be interpreted as TC switcher route-selection expressions.
- A station sign's familiar user interface does not imply an identical controller underneath: this MVP feeds vehicle notches, with separate final docking behaviour.
- Destination metadata, advisory station look-ahead, resource reservations and an executable movement authority are different layers. Presence of one does not imply the others are complete.

### Build and Validation Scope

The source tree has a PowerShell build/regression entry named `build.ps1`. Supply `-ServerRoot /path/to/prepared-server` and `-JavaHome /path/to/jdk-25` (or set JAVA_HOME). It reads the server's `versions/26.2/shiroha-26.2.jar` and `libraries/` as dependencies, builds STA first, then the other modules, and runs Java tests. It does not start, deploy to or modify the server. Dependencies are not bundled. Installing packaged JARs does not require compiling the source. Output goes to ignored `artifacts/` and `target/` directories. `package-source.ps1` creates a source-only ZIP under `dist/`.

Automated checks exercise contracts, mode transitions, spatial conflicts/retention and browser rendering. Live-server tests remain necessary for mount/driver lifecycle, Folia ownership, physical redstone, chunk loading and motion. Documentation translation is not a new runtime validation or release.

This file is intentionally standalone. Installation, permissions, supported command forms, configuration, operating tutorials, API boundaries and known limitations are included here without requiring another README or historical release note.

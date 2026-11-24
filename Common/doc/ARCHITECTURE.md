# Architecture and Maintenance Boundaries

## Current release — 2026-11-24

STA2.7.0 / STF5.0.2 / STCS4.3.5 / SAL0.5.0 / PCC2.4.1. The older protocol baselines below are historical. PCC exposes the v5 display API and authenticated v10 route-editing API; SAL owns route drafts, publications and conflict state. STCS owns topology and MA; STF executes physical actions through STA.

[Route editor, ownership and restart contract](PCC-ROUTE-EDITING.en.md). PCC highlight and equipment filters are browser-only display preferences; they do not affect arbitration or graph state. Up/down preference, whole single-track section reservation, general multi-reversal search and the Python daemon remain [design only](TRAFFIC-PLANNING-DAEMON-DESIGN.md).


## Traffic planning and Python daemon design draft — 2026-11-20

The proposed four-contract design covers mileage-based up/down direction rules, atomic single-track section reservations,
general reversal-aware planning, and a snapshot-based Python computation interface. STCS retains authoritative commits;
fault and restart recovery preserve occupancy and possibly entered reservations. This is a design draft, not an implemented protocol.
See [traffic planning and daemon contracts](TRAFFIC-PLANNING-DAEMON-DESIGN.md).

## STF 5.0 / SAL 0.3 control ownership — 2026-11-20

SAL owns operation and human consent, STCS owns topology/authority/routing, STF owns vehicle execution through STA.
The legacy STF automatic-sign controller and station-motion state machine have been removed, together with their automatic-mode entry.
Parked-to-manual handback and manual-to-parked handover use immutable driver leases and explicit one-shot release receipts;
absence of a driver is never consent. See [handover and breaking-change contract](SAL-MANUAL-HANDOVER.md).

## Incremental RailGraph — 2026-11-19

STCS 4.3.0 owns incremental physical topology, loaded-only scans and configurable 60-second rail reviews.
STA 2.4.0 adds v9 opaque edge-dependency contracts; SAL 0.2.0 consumes STCS routing conclusions and revalidates only affected routes.
Unload diagnostics do not invalidate known connections; physical faults and unknown connections remain distinct.
See [contract, versions and migration](RAILGRAPH-INCREMENTAL.md). The v5 baseline below is historical; executable v6 permissions and additive v7–v9 contracts coexist.

## Current Protocol Baseline

STF 3.0.0 / STCS 3.0.0 / STA 1.0.0 / SkyPCC 1.0.0 use STA v5.
Telemetry/network/desk/authority/switch contracts live in `net.skyworld.sta.api.v5`;
unchanged supporting v1 DTOs and v3 observation/event contracts remain.
PCC exposes `/api/v5/` only. See [breaking migration and identifier namespaces](STA-V5-MIGRATION.md).
This is an interface baseline for M3 development, not executable ATP or certified interlocking.

SkyRail Suite is the repository name. Runtime identities remain SkyTrainFolia, STCS, SkyworldTrainAPI and SkyPCC. Renaming this repository does not migrate player data or change plugin dependency names.

## Modules

| Module | Owns | Must not imply |
| --- | --- | --- |
| SkyTrainFolia | Minecart motion, driving leases, notches, physical point actuation, HMI | That shadow MA is executable ATP |
| STCS | Physical graph, line attribution, localisation, retained occupancy and shadow allocation | That unloaded/unknown means clear |
| STA | Versioned in-process service contracts and event distribution | That a Java service is already a public Python network protocol |
| SkyPCC | HTTP/SSE gateway, display and authenticated operator requests | That browser interpolation is authoritative train position |
| shared | Common command/help/version UI compiled into each plugin | A fifth runtime plugin or replacement for STA |

The initial source-tree migration copied production Java/resources without behavioural edits. The subsequent [TrainManager responsibility split](TRAIN-MANAGER-REFACTOR.md) separates STF internals while retaining commands, persistence formats, STA contracts and scheduler boundaries. Runtime API/package renames remain out of scope.

## Threads and Dependencies

World/entity access remains subject to Folia ownership. Graph calculations consume snapshots; they must not gain arbitrary asynchronous Bukkit access during refactoring. Server internals/display integration remain version-sensitive. The supplied build uses an explicitly provided server installation as a read-only dependency source; no server library is vendored.

The existing javac/PowerShell build was retained rather than introducing unverified Maven coordinates for the custom server. Future Maven/Gradle migration should first establish a reproducible dependency source, then preserve existing Java and browser tests.

## Tests

Each module uses `src/test/java`; executable tests expose a public static main entry point and run with assertions enabled. STF test support classes compile alongside test entry points. Test working directories are isolated under root `target/`.

SkyPCC JavaScript tests live in `SkyPCC/tests`. Current tests cover language tables, line filtering and the inspector/expanded-log UI. Historical browser tests remain separately callable because visual expectations can lag behind current UI changes.

STCS/tools/testbench contains the offline Python solver/simulator and synthetic tests. It is a reference environment, not proof of Folia entity handling, braking safety or an executable RBC.

# Architecture and Maintenance Boundaries

SkyRail Suite is the repository name. Runtime identities remain SkyTrainFolia, STCS, SkyworldTrainAPI and SkyPCC. Renaming this repository does not migrate player data or change plugin dependency names.

## Modules

| Module | Owns | Must not imply |
| --- | --- | --- |
| SkyTrainFolia | Minecart motion, driving leases, notches, physical point actuation, HMI | That shadow MA is executable ATP |
| STCS | Physical graph, line attribution, localisation, retained occupancy and shadow allocation | That unloaded/unknown means clear |
| STA | Versioned in-process service contracts and event distribution | That a Java service is already a public Python network protocol |
| SkyPCC | HTTP/SSE gateway, display and authenticated operator requests | That browser interpolation is authoritative train position |
| shared | Common command/help/version UI compiled into each plugin | A fifth runtime plugin or replacement for STA |

Production Java/resources are copied without behavioural edits. Only source layout, build configuration and test paths were adapted. API/package renames are deliberately outside this migration.

## Threads and Dependencies

World/entity access remains subject to Folia ownership. Graph calculations consume snapshots; they must not gain arbitrary asynchronous Bukkit access during refactoring. Server internals/display integration remain version-sensitive. The supplied build uses an explicitly provided server installation as a read-only dependency source; no server library is vendored.

The existing javac/PowerShell build was retained rather than introducing unverified Maven coordinates for the custom server. Future Maven/Gradle migration should first establish a reproducible dependency source, then preserve existing Java and browser tests.

## Tests

Each module uses `src/test/java`; executable tests expose a public static main entry point and run with assertions enabled. STF test support classes compile alongside test entry points. Test working directories are isolated under root `target/`.

SkyPCC JavaScript tests live in `SkyPCC/tests`. Current tests cover language tables, line filtering and the inspector/expanded-log UI. Historical browser tests remain separately callable because visual expectations can lag behind current UI changes.

STCS/tools/testbench contains the offline Python solver/simulator and synthetic tests. It is a reference environment, not proof of Folia entity handling, braking safety or an executable RBC.


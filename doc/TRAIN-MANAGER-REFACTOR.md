# TrainManager Responsibility Split

Date: 2026-09-15. STF version: `2.1.0-alpha.8`. This is an internal refactor, not new ATP functionality.
Runtime commands, configuration keys, train/template YAML and STA contracts are
unchanged. No scheduling operation has intentionally moved to a different thread.

## Ownership

| Class | Responsibility |
| --- | --- |
| `TrainManager` | Existing caller-facing facade; train/name/cart registries; creation, coupling, spawning, removal and reload/shutdown ordering; member task registration; shared driver/automatic-operation gate |
| `TrainPersistence` | Train/template YAML load/save, corrupt-file backups, atomic replacement and the I/O lock |
| `DriverControlService` | Driver/seat/lease indexes, per-player checks, lease revocation, driver events and explicit release |
| `TrainDrivingControls` | Reverser and power/brake inputs, push/start/stop, manual takeover and automatic rearming rules |
| `TrainMotionController` | Member tick orchestration, speed-model selection, track-path progression, target layout, reversal and local obstacle checks |
| `TrainMemberActuator` | Physical movement, passenger recovery, owned-region movement/fallback, pending async teleports and motion counters |
| `TrainSignActions` | Legacy sign/redstone dispatch, station latches, final station motion and parsing; existing `AutomaticSigns` remains the automatic station/spawn/destroy controller |
| `TrainAudio` | Leader-attached horn/bell dispatch, rolling and brake audio; existing `TrainSoundState` retains transition logic |
| `ConsistLabels` | Temporary member-number labels and version-guarded restoration |
| `TrainSettings` | Existing named setting getters, defaults and clamps; suppliers read current config/profile after reload, not a cached copy |

The split is by behaviour and owned state, not by an arbitrary line limit. It
does not introduce additional plugins. The existing `Train`, `VehicleProfile`,
`TrainRailPath`, `AutomaticSigns`, `TrainDisplaySync`, `TrainChunkLoader` and
`OwnedTrainMover` retain their responsibilities.

## Calls and Dependencies

Commands, listeners, UI and existing integrations can still call `TrainManager`.
Its moved entry points delegate to components; the static rule entry points used
by existing tests remain compatible.

`TrainPersistence` receives the existing registries explicitly; it does not own
another copy of live trains. It builds temporary indexes before replacing the
registries on load, preserving the previous I/O-lock scope. It accepts a data
folder, logger and settings rather than requiring a running plugin instance.

Driver control uses lookup/save callbacks instead of depending on the entire
manager. Motion uses a small `Host` interface for lifecycle validation, driver
information, index refresh, same-consist checks and gated automatic signs. Sign
actions still call the manager's registration/property facade; they do not own
train indexes or grant driving control.

## Threading Invariants

- Member ticks still originate from each minecart's entity scheduler.
- Lifecycle validation still happens before the pending-relocation gate.
- A pending relocation still prevents the tick from updating that entity.
- Synchronous owned moves and teleport fallback retain their previous ordering.
- Completion callbacks remove only their own pending teleport reservation.
- Horn/bell emission still rechecks the selected cart on its entity scheduler.
- Driver checks still run on the player scheduler and compare lease IDs, so an
  old callback cannot revoke a newly acquired driving session.
- The same `driverLock` instance is shared by lease management, manual takeover,
  automatic eligibility, destruction, mode/property changes and automatic ticks.
- Persistence retains its separate I/O lock. Save calls have not been made
  asynchronous or moved outside driver critical sections as part of this work.
- Shutdown retains the original order: stop automatic/display/autosave work,
  clear telemetry/member tasks/relocations, stop chunk loading, revoke drivers,
  then clear label state.

Concurrent collections do not make cross-region Bukkit access safe. This
refactor preserves the existing model; it is not a new proof of thread safety,
atomic whole-train snapshots or end-to-end braking correctness.

## Verification

`build.ps1` compiles all four modules and runs the Java test entry points.
The completed local build passed all 53 entry points (52 existing plus the new
refactor test). Existing deprecated Bukkit/native-access warnings remain. No
live-server or browser test was run for this internal STF-only change.
`TrainManagerRefactorTest` additionally covers:

- Settings defaults/clamps and replacement of the configuration object.
- Train/template persistence, all current protection modes, member/name
  indexes, service numbers and mileage.
- Unreleased manual takeover restoring with braking, not inherited traction.
- Corrupt train YAML retaining the live registry and creating a backup;
  corrupt template YAML retaining the prior definitions.
- RECOVERING/driver-hold rejecting traction, manual input cancelling an
  automatic run, braking and explicit rearming.
- Pending async relocation preventing duplicate moves and further tick work;
  successful or unsuccessful completion allowing a subsequent move.

No production-server data is required by the added test. Its temporary files
are created under the isolated build test working directory.

## In-Game Smoke Tests

Before deploying this refactor to production, verify:

1. Create/spawn a consist; save, stop/restart and confirm members, properties and
   protection mode. Load/unload a parked train and destroy a test member.
2. Board, declare driving control, power/brake, leave/disconnect and reconnect.
   Confirm EB, no passenger takeover and no stale control restoration.
3. Stop, release and rearm automatic operation; test station approach, dwell,
   departure and automatic spawn/destroy separately from manual trains.
4. Test tight curves, slopes, reversal and high-speed region crossings with and
   without a seated player. Check physical motion against HMI and telemetry.
5. Confirm turnout preparation, passenger recovery, moving horn/rolling/brake
   sounds, label restoration, shadow MA and PCC position updates.

Automated tests do not replace these Folia/entity-lifecycle tests. No new ATP
intervention, source push or server deployment is part of this refactor.

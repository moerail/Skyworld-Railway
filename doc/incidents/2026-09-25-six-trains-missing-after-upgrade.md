# Production incident: six parked trains lost their minecart entities after an update

**Status:** Open; root cause unknown
**Observed:** 2026-09-25
**Affected components:** SkyTrainFolia, STCS, SkyPCC (exact deployed versions to confirm)

## Summary

After a SkyRail Suite update, all six trains that had been parked in the depot lost their visible minecart entities and live location. The SkyTrainFolia train registry still lists all six consists, while SkyPCC lists their names but reports zero trains online and shows each as waiting for location.

This is a production incident report, not a confirmed attribution to the update. The trigger and the state of the world's saved entity data still need investigation.

## Confirmed observations

- Before the incident, the six trains were parked in the depot. The operator reports no other player or train activity and checked the rail network.
- `/st list` still shows `test1` through `test6`, including their saved member counts. Their current speed is shown as zero, with brake/hold state displayed.
- SkyPCC lists `test1` through `test6`, reports `0` trains online, and displays “waiting for location” for each.
- In the loaded depot, `/st scan 16` finds zero minecarts; the operator visually confirmed the trains are absent.
- The supplied `plugins/SkyTrainFolia/trains.yml` still has six train entries and member UUID lists. This file preserves consist identity but does not contain coordinates from which the original entities can be reconstructed.

## Expected behavior

Updating and restarting the plugin suite must not remove parked train entities. Saved consist membership, world entities, telemetry, and PCC location should agree after startup. If an entity is unavailable, the system should retain uncertainty and report a useful reason.

## Impact and containment

- All six production consists are unavailable for service.
- Their stale registry entries and retained occupancy must not be treated as evidence that track is clear.
- Preserve the current world, plugin data folders, and startup logs before any cleanup or reconstruction.
- Do not delete occupancy ledger files wholesale. If the original entities cannot be restored, retire each stale STF consist first, then use STCS's per-train, console-only shadow-clearance procedure after physical absence has been verified. The historical M1 ledger is intentionally retained.

## Investigation needed

1. Record the exact versions before and after the update, server/Folia build, Java version, update procedure, and whether the server was fully stopped.
2. Inspect the startup and shutdown logs for SkyTrainFolia data-load failures, entity removal causes, world/chunk loading failures, and any errors from Folia or other plugins.
3. Compare the world entity data and `trains.yml` against a backup from immediately before the update. Determine whether the original member UUIDs remain anywhere in the saved world.
4. Reproduce on an isolated copy of the production data, starting with six parked trains. Check loaded/unloaded chunks, restart, and matching versus mixed plugin versions.
5. Identify whether the entities were removed, never loaded, or remained in another world/chunk. Add a regression test and server acceptance scenario for the confirmed failure path.

## Evidence still requested

- Relevant production startup/shutdown log excerpts.
- Exact installed JAR versions and their prior versions.
- Result of checking a pre-update world backup for the original member UUIDs.

Do not mark this incident resolved solely because stale occupancy was manually cleared or replacement trains were spawned. Resolution requires an identified cause or a documented inability to reproduce, plus a verified recovery and regression check.

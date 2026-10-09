# test1 loses positioning after an abrupt shutdown

Status: production service restored by operator mode transition; exact root cause unconfirmed. Local hardening candidate must not be treated as the proven incident fix.

## Production observations

- QZYZL is the driver, not the train name. He requested MA while `test1` was stopped; the server then lost power before the train moved.
- After restart, the six minecarts remain visible and rideable. `/st scan 16` sees twelve nearby carts across `test1` and `test2`. The scan count alone does not prove train membership.
- `/stcs occupancy test1` reports six of six fresh observed members. This is an M1 observation, not MA clearance.
- `/stcs ma status` while driving `test1` reports `WAITING / POSITION_UNCERTAIN`, zero global blockers. SkyPCC shows `test1` awaiting position with no location or update time.
- `test2` in the adjacent depot track localizes, receives MA, completes a circuit and returns normally. The shared telemetry and graph services are therefore operational.
- Production `trains.yml` has `test1` reversed, six ordered members, 1.15 block spacing, `protection-mode: ACTIVE`, and a saved mileage travel direction of negative Z. It does not contain the motion controller's remembered direction. `manual-release-confirmed: false` and emergency brake are separate driving holds.
- `/st info test1` labels cars 1 through 6. With `reversed: true`, car 6 is the active leader, parked at the depot buffer. A second photograph confirms it remains on rail.
- The operator switched `test1` to Shadow, requested MA successfully, then returned to Enforce. Positioning and MA operation remained normal afterward. No candidate JAR was deployed to production.

## Investigated hypothesis, not confirmed

On restart, `TrainPersistence` restores the reverse flag and member UUIDs but the old format does not restore `Train.rememberedDirection()`. With the parked cart's velocity at zero, the motion controller can fall back to minecart yaw. At a buffer, guessing the opposite direction causes `TrainRailPath.create` to walk out of the track while trying to reconstruct the full six-car body. It returns null without a warning. This is a real code-level failure mode reproduced in a local test, but the operator's successful Shadow/Enforce recovery does not establish that it happened in production.

`changeProtectionMode` resets the operating mode to SB, clears active ATP state, applies a stop, and saves the train. It does not directly clear the rail path or restore remembered direction. The observed recovery may involve mode/MA session resynchronization or another condition that changed during the procedure. Preserve the earlier screenshots and seek a timestamped log/diagnostic if the fault recurs.

## Local candidate

- Save and restore remembered direction in `trains.yml` for future restarts.
- For legacy saves without that field, infer the direction from the active leader and its adjacent freshly observed car. Hold the train stationary until that evidence exists instead of guessing from yaw.
- Rebind precisely rostered minecarts whose entity tag is missing after a crash, and refresh nearby carts when a player boards.
- Regression test: a reverse six-car train stopped at a buffer cannot construct its path using the wrong yaw direction; the direction derived from adjacent cars constructs it successfully.

`build.ps1` completed with exit code 0: four plugins built and 75 Java test entry points passed. JDK 25.0.4 printed an `AccessDeniedException` while closing a Netty dependency archive, but compilation and tests completed. No production server files were touched.

The candidate binaries and SHA-256 manifest are in `artifacts/recovery-candidate-2026-09-25/`. They are **not a verified fix for this production incident and should not be deployed on this evidence alone**. The plugin versions remain STF/STCS 4.0.2 and STA/PCC 2.0.0; distinguish these candidate files by their folder and SHA-256 values, not the version strings.

## Validation plan if the hardening candidate is tested separately

1. Keep `test1` stopped. Stop the server completely and take a matched backup of the world, `plugins/SkyTrainFolia/`, `plugins/STCS/`, and the four current plugin JARs. Preserve occupancy ledgers.
2. In a staging clone of that snapshot, replace the suite JARs with the matching candidate set. Remove the prior copies from the staging `plugins/` directory so duplicate plugin JARs cannot load. Start normally; do not use `/st reload` for this check.
3. Before requesting MA, verify that SkyPCC shows a fresh position and line for `test1`; `/stcs ma status` should no longer report `POSITION_UNCERTAIN` for it. Verify `test2` still localizes and neither train moves unexpectedly.
4. Only after localization and the ordinary driver control checks pass, let QZYZL request MA and verify it becomes executable. Do not bypass EB or an unconfirmed driver release to force departure.
5. If localization does not return, stop staging and collect the fresh startup log plus STF train path diagnostics before changing train, graph or ledger data. Do not clear occupancy or recreate the consist.

The incident's immediate service recovery is complete. Hardening deployment and live validation remain separate work.

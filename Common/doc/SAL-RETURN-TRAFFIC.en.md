# Concurrent depot returns · SAL 0.7.7 / STCS 4.3.6

**[ADDED / FIXED · SAL 0.8.0 / STF 5.1.4 · 2026-11-26]** Depot prompts retry and refresh expired consent with the STF stop threshold. Pickup and return share segmented intermediate-reversal routing through STCS release and STA reversal. Chunk-loading holds retain manual hotbar power selection while inhibiting actual traction. [New behavior](SAL-MANEUVERS-AND-HANDOVER.en.md)

**[FIX · 2026-11-26]** Separate geometric reachability, destination clearance and live traffic. Removed: ~~discarding a stopped return plan/parking centre when another train intersects its remaining route~~ and ~~automatic point alignment withdrawing another stopped train's MA through the dispatcher handshake~~.

The production report described repeated path changes, MA loss/reallocation and creeping during concurrent returns. Original events were lost after restart, so these confirmed code defects are not proof of every production loss's cause.

## Responsibilities

- SAL selects legal directed RailGraph paths. Other normal running SAL jobs are transient traffic; stored/manual/faulted/paused trains still obstruct entrance selection. Full destination-body clearance against all trains and exclusive task berth reservations remain mandatory.
- Once selected, a return path, berth and centre remain stable during throat traffic. Real dependency changes, missing signs, insufficient space, unknown body geometry and deviation retain their existing recheck/fault handling.
- STCS alone resolves live occupation, reservation, point locks and MA extent. Congestion can shorten/withhold permission; clearing it extends the existing route, rather than changing geometric reachability.
- Automatic point requests cannot withdraw another train's MA. They wait on MA_CONFLICT and retry through existing alignment. Explicit PCC dispatcher operations retain the stopped-hold handshake; controlled self-rerouting remains possible, with every occupancy/locking guard intact.
- At each automation update SAL first maintains established route heartbeats across normal running/dwell tasks, before dispatch and per-train geometry work. The 1500ms watchdog is unchanged: real stalls can still expire a lease. Renewal never grants an unaccepted or expired route.

STA APIs/identifiers are unchanged; physical operations remain STA→STF. Geometric reachability never authorizes passage through occupied resources. No general deadlock-preemption algorithm is introduced.

## Upgrade and acceptance

Stop and back up, replace SAL with 0.7.7 and STCS with 4.3.6. Compatible companions: STA 2.9.2, STF 5.1.3, PCC 2.6.4. Preserve state, graph, trains and ledgers; no ledger reset is needed.

Test two managed trains returning to separate free berths via a shared throat requiring different point positions. The second must wait without withdrawing the first train's MA or discarding its own berth/centre. Once the first clears, the original second task aligns points and proceeds. Repeat with a stored train obstructing a single-ended entrance, an occupied berth and a genuine graph/sign change; protections must remain active. Capture time/train/graph revision and raw `/api/v5/railway-events` on further failures.

Regression exercises two real automatic runtime requests, heartbeats under point conflict, no foreign onboard hold, and continuation after clearance without a new MA request. Geometry tests separate route/destination filters, retain immobile blockers, and check stable path identity. Full production acceptance is still required; no servers were operated.

[中文](SAL-RETURN-TRAFFIC.md) · [Return rechecks](SAL-RETURN-RECHECK.en.md)

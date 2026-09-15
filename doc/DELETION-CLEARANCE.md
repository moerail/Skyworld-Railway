# Deletion clearance patch

Matching versions: STF 2.1.1, STCS 2.2.1, STA 0.8.1; SkyPCC remains 0.8.1.
The version numbers no longer use alpha suffixes, but MA remains shadow-only:
this release does not implement ATP supervision or certify safety.

## Automatic confirmation

The ordinary v2 TRAIN_REMOVED message only removes a telemetry source. It is
also used at shutdown and is never sufficient evidence that a track is clear.

STF now takes a snapshot of expected members at explicit train retirement.
Each loaded cart is removed on its entity scheduler. Only a successful remove
call followed by invalidation acknowledges that member. Missing entities,
unloaded entities, retired schedulers, failures and empty rosters do not count.
All expected acknowledgements are required. Missing position evidence also
prevents a confirmation from being issued.

Complete receipts are atomically persisted to STF's confirmed-destructions.json
by its asynchronous telemetry publisher before publication. They are replayed
through v3 ConsistObservation with removed=true and every expected member in
REMOVED state. The wire shape is unchanged; confirmedDestruction() validates the
complete receipt. The current source session and fresh publication time identify
the replay; original member evidence times remain unchanged.

STCS requires matching source sessions, a complete receipt covering its retained
member positions, no live roster entry, no driver and no tracking report. It
then removes this UUID's shadow body occupancy, reservations and control intent,
and persists the result. Repeated receipts are idempotent. The historical M1
occupancy-ledger.json is intentionally retained as observational history.

Unconfirmed deletions remain conservative. In particular, a crash before receipt
persistence or deletion with unloaded members still needs operator inspection.
This patch does not load chunks to hunt down missing carts or automatically
discard old records based on absence. Keep the receipt file across upgrades;
do not reuse a destroyed train UUID. Receipts currently have no automatic pruning.

## Historical records

Back up the plugin data before upgrading. Stop the server and replace all three
changed plugin JARs; do not mix them with old STA versions or hot-reload them.

After checking the complete former consist is physically gone, use the server
console (not player chat):

```text
stcs ma clear <full-train-uuid> confirm
```

This is an explicit operator attestation of clearance, not an automatic world
inspection. It rejects unavailable telemetry, a live roster, a driver or a
remaining tracking report. It creates a unique .bak beside shadow-occupancy.json
and appends shadow-clearance-audit.log before removing only the specified UUID.
A persistence failure disables MA instead of continuing allocation.
Success is logged as CLEARED_SHADOW_ONLY. The M1 historical ledger is not erased.

For the reported auto-iz16 record, the UUID is
81ef0ac9-35e6-4c46-b556-907f91498b6f. Do not clear unrelated records or delete the
whole ledger. Updating the JAR does not by itself clear this historical record.

## MA display correction

A new calculation that ends inside the current safety margin preserves its real
blocking reason (for example RESOURCE_CONFLICT), rather than overwriting it with
EOA_OVERRUN. No positive path means WAITING, not ALLOCATED_SHADOW. This does not
implement historical issued-authority overrun supervision.

## Server acceptance

1. Delete a completely loaded test consist and verify its shadow occupancy clears.
2. Restart and verify confirmed deleted occupancy does not return.
3. Unload a consist or disconnect: occupancy must remain, not clear.
4. Delete with a missing/unloaded member: retain uncertainty for inspection.
5. Attempt manual clearance of a live train: expect TRAIN_STILL_REPORTED.
6. Confirm the historical train is gone; clear its UUID, check backup and audit,
   then request MA again. Other trains' occupancy must remain unchanged.

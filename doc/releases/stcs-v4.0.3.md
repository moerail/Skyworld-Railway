# STCS 4.0.3 — console recovery for a failed shadow ledger

If `shadow-occupancy.json` is corrupt, STCS 4.0.2 leaves shadow MA unavailable and preserves the file. Version 4.0.3 adds:

```text
stcs ma reset-ledger confirm
```

Run this from the **server console** only after backing up the server and verifying every former cart is gone, including carts in unloaded chunks. The command works only while the shadow MA service is unavailable. It refuses to run if SkyTrainFolia is unavailable, `trains.yml` still lists trains, a live consist or driver exists, a TRACK_REPORT remains, or the telemetry services disagree. It preserves the original file as a unique `.reset-*.bak`, appends `shadow-ledger-reset-audit.log`, and writes an empty v2 shadow ledger. `occupancy-ledger.json` (M1 history) is untouched.

The command does not prove physical track clearance or restore MA during the current server process. Fully restart the server, verify STCS starts without a shadow-ledger error, check `/st list` and `/stcs ma status`, then resume traffic in a controlled test. If the damaged file was already renamed to `.bak`, the command creates the empty file and reports that there was no source file to back up again.

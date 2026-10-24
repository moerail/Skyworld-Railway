# SkyTrainFolia 4.0.3 + STCS 4.0.4 — attended shadow-ledger reset

This replaces the console-only `stcs ma reset-ledger confirm` command in STCS 4.0.3. Deploy both new JARs together; an older SkyTrainFolia cannot validate the challenge. STA and SkyPCC need no code changes for this command.

1. Back up the server. Pause train creation and driving. Inspect the full line and relevant unloaded chunks for old minecart entities. `/st list` alone cannot prove physical clearance.
2. As an administrator with `stcs.admin`, run `/st list`. Only when SkyTrainFolia has finished loading and the managed train list is empty will it show a random six-digit code. Console can do the same. A nonempty list gives no code and revokes the previous one for that operator.
3. Within 60 seconds, as the **same operator**, run `/stcs ma reset-ledger <six-digit-code> confirm`. Any submitted code is one-use, including an incorrect attempt.
4. STCS independently checks that SkyTrainFolia is enabled, telemetry sessions agree, the live roster has no trains, no driver or tracking report remains, and `trains.yml` contains no train entries. It accepts the code only while shadow MA is unavailable. It backs up the original `shadow-occupancy.json`, appends `shadow-ledger-reset-audit.log`, and writes an empty v2 shadow ledger. The M1 historical `occupancy-ledger.json` is untouched.
5. Fully restart the server. Verify STCS starts normally, `/st list` is still empty, and `/api/v5/shadow-ma` reports `SHADOW` before controlled traffic resumes. The reset does not grant an MA or prove unloaded carts absent.

If the file had already been renamed away, the audit records that there was no current source file to back up. Keep the previously renamed file and the full server backup for investigation.

## SR route gap check

The SR target search now checks unresolved **ports on the directed edges actually used**. An unrelated missing branch on the source or destination node no longer rejects a proven approach. An unresolved entry or exit port on the requested path still returns `GRAPH_GAP`; this does not override occupancy, train position, switch state, or grant checks.

Local test case: a stopped train west of balise 0026 approached it from the eastbound 0028→0026 edge. After loading that section and rebuilding RailGraph, 0026 was resolved but 0028's west port remained unresolved. The earlier node-wide check rejected the SR despite the east port being resolved. Retest with the train stopped, a fresh SR request, and the target set to 0026; inspect the issued grant before moving.

# SAL coherent localisation, bounded tolerance and root causes

**[FIX · SAL0.7.6 · 2026-11-26]** Return berth/obstacle/reselection holds renew the already accepted route lease, avoiding1500ms heartbeat starvation. A default2s recheck retains the target; transient body uncertainty is not labelled topology change. Persistent unknown geometry faults; true berth/space changes reselect only after stopping. Detailed reasons and explicit dispatch-history labels are added. [Details](SAL-RETURN-RECHECK.en.md)

2026-11-26: SAL0.7.6/PCC2.6.4 with STA2.9.2/STF5.1.2/STCS4.3.5.

The recorded production incident was a failed STA rail query at693,70,-707 after executable FS allocation, followed by recovery. It does not alone prove physical route deviation. SAL now queries STCS using railXYZ and motionXYZ from the same physical telemetry sample, rather than mixing a cached rail block with a newer orthogonal vehicle heading at a curve. Continuous projection still follows actual geometry, never the requested route as an assumed position. Zero-motion samples use the current vehicle heading.

On temporary query/matching failure SAL immediately sends a brake intent and retains the plan for a bounded recheck, default2s. A valid route fix clears the window. Persistent failure enters the existing SR recovery. Two independently timed physical samples confirming another branch beyond the node tolerance enter recovery earlier; rereading one sample is not confirmation. A new plan resets its predecessor's window.

Only an already accepted route lease may be renewed while braking. No new path or extended MA is requested during uncertainty. STCS/ATP authority, topology faults, TIMS and TR/PT remain independent. Recovery of SAL localisation does not acknowledge a protection trip.

Default SAL config:

```yaml
localisation:
  position-tolerance-blocks: 1.5
  endpoint-tolerance-blocks: 0.75
  confirmation-ms: 2000
```

Position tolerance accepts finite0.25–2blocks; endpoint tolerance finite0–1block, converted by graph scale; confirmation accepts integer100–10000ms. Missing keys use defaults; invalid values disable SAL without replacing state. The default spatial threshold remains1.5blocks. Endpoint tolerance applies to the same edge/shared node, not arbitrary track snapping. Restart retains existing recovery holds.

Confirmed recovery persists original cause/time in the job, exposed through `/sal status` and the existing STA operating reason. Waiting for SR no longer erases the cause. ROUTE_DEVIATION retains its event identity and adds bounded phase/graph/position/plan context. PCC displays the original cause as safe text and labels the incident route-tracking failure rather than asserting physical departure. One confirmation log is emitted, not per-tick spam. Old incidents without stored details cannot be reconstructed.

Upgrade SAL/PCC only if the other compatible versions are already installed; preserve all data, refresh PCC with Ctrl+F5, and explicitly recover/override existing jobs before a new test. Recheck the original depot exit and slope at low speed. Actual production driving remains unverified; offline regressions cover coherent frames, transient recovery, timeout, independent wrong-branch evidence, scaled endpoints and root-cause persistence. No test-server startup or production operation is performed.

[Chinese rules and test procedure](SAL-LOCALISATION-TOLERANCE.md) · [Validation](../../VALIDATION.md)

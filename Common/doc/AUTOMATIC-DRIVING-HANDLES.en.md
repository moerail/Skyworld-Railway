# Automatic cab handles · STF 5.1.3

**[ADDED / FIXED · SAL 0.8.0 / STF 5.1.4 · 2026-11-26]** Depot prompts retry and refresh expired consent with the STF stop threshold. Pickup and return share segmented intermediate-reversal routing through STCS release and STA reversal. Chunk-loading holds retain manual hotbar power selection while inhibiting actual traction. [New behavior](SAL-MANEUVERS-AND-HANDOVER.en.md)

**[CHANGED · 2026-11-26]** ~~Fixed automatic P2 traction / B4 braking~~ is replaced by P4 departure and large speed-error traction, tapering through P3/P2/P1 near cruise. Default service braking is B6; final holding remains B7. These are existing vehicle handles, not a physics boost. Manual driving is unaffected.

SAL still sends intent through STA to STF; STCS remains the sole MA authority. Vehicle/server/train speed caps, ATP, TIMS, TR/PT, expired-intent holding and the remaining-distance movement fence still apply. Low authority limits can still prevent rapid clearance.

The stop envelope uses 85% of the selected service-notch deceleration, two physics ticks of reaction distance and a 0.04-block end margin. This permits later normal braking than the previous B4 envelope without weakening ATP. Actual performance still depends on profile and grade; this game controller is not certified railway ATO.

```yaml
automatic-driving:
  max-power-notch: 4
  service-brake-notch: 6
```

Add these keys to STF config if desired; existing files automatically receive these defaults at runtime. Power accepts integers 1–4, brake 1–7; invalid values fall back to defaults. Stop all trains before `/st reload`. P2/B4 can be selected for gentler handling at the cost of slower clearance and earlier braking.

Only STF changes to 5.1.3; SAL 0.7.6 / STA 2.9.2 / STCS 4.3.5 / PCC 2.6.4 remain compatible. Stop and back up before replacing JARs. No route rebuild or ledger reset is needed.

Acceptance: compare departure handles and station-clearance time on the same train/route; verify cruise taper, B6 approach and B7 hold at stations and berths; repeat under low ATP limits, short stops and grades. TIMS/Trip/authority holds must still prevent traction. Record profile, slope, speed, intent and remaining distance if hunting or docking errors occur.

Regression exercises all bundled profiles with actual force-model integration: 30-block clearance compared with P2, 300-block docking, configuration bounds, cruise taper, ATP cap, invalid telemetry and mutually exclusive traction/braking. Production acceptance remains separate.

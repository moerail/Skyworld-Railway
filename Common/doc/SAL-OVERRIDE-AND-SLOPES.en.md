# SAL admin override and slope localisation

**[ADDED / FIXED · 2026-11-26 · SAL0.7.5 / PCC2.6.4]** Rail coordinates and heading now come from one STF telemetry frame. Uncertainty brakes immediately with a default2s recheck; bounded endpoint tolerance0.75blocks and projection tolerance1.5blocks are configurable. Independent wrong-branch confirmation recovers earlier. No new/extended MA is granted. Original failures persist in /sal status and are visible in PCC event text. [Detailed policy](SAL-LOCALISATION-TOLERANCE.en.md)

STA2.9.2 / STF5.1.2 / STCS4.3.5 / SAL0.7.5 / PCC2.6.4, 2026-11-26.

Administrator-only `/sal override <train name or UUID>` works from chat/console. Duplicate names require UUID. It persists manual reservation, brakes, confirms fresh full-body stop evidence, withdraws MA through STA/STCS, requests STF manual handoff, waits for observed release, then removes the task. Use `/sal status` for progress; after completion use `/st drive` and normal direction/MA/TIMS/Trip procedures. It ends the task, not resumes the service.

After a server restart destroys the old virtual-driver lease, SAL uses existing OperationalAuthorityService channel-change revocation and requires a fresh AVAILABLE snapshot with no executable grant or pending SR for that train. The same fallback handles TIMS blocking ordinary release. Unknown occupancy/required locks remain; unavailable or unconfirmed authority never implies success. The override intent/confirmed withdrawal stage persist across restart.

AutomaticTrainService adds default `handoffToManual(train,owner,administrator)`, with an existing RELEASE Packet header overload. No new ETCS/ATO number is claimed. SAL authenticates the administrator and confirms withdrawal; STF verifies owner and full stop, applies release on its entity thread, preserves TIMS/TR/PT, and switches to manual mode. Null owner is accepted only for parked standby. Existing manual drivers cannot be displaced. SAL's persistent manual reservation prevents immediate recapture; rejoining SAL still requires explicit depot consent and `/st release`.

Slope fix: RailQuery takes observed rail-block coordinates, not floor(entityXYZ). SAL now uses fresh same-session STF telemetry railX/Y/Z via existing STA v5, checks train/world/composition, then continuously projects the entity onto the STCS-confirmed edge. Direction/adjacent-edge and1.5block checks remain. Missing/stale/future/wrong-train/graph data and real deviations remain failures. No chunk/entity access is added to SAL.

Regression reproduces a slope where entityY floors to65 but railY is64, and checks both directions. Admin flow tests cover pending/failed release, stop/owner fences, restart, inactive-authority proof and control handoff ordering. These are offline regressions, not production driving acceptance. No local server startup or production deployment in this round.

[Chinese contract and production procedure](SAL-OVERRIDE-AND-SLOPES.md) · [Validation](../../VALIDATION.md)
